package com.smartworkalarm.data.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.os.CancellationSignal
import android.provider.CalendarContract
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.CalendarSelectionRepository
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class CalendarContractEventSource private constructor(
    private val calendarIds: (CancellationSignal?) -> List<Long>,
    private val selectionRepository: CalendarSelectionRepository,
    private val eventQuery: (List<Long>, Instant, Instant, CancellationSignal?) -> List<CalendarEvent>,
    private val zone: () -> ZoneId,
    private val cancellationSignal: () -> CancellationSignal?,
) : CalendarEventSource {
    internal constructor(
        calendarIds: () -> List<Long>,
        selectionRepository: CalendarSelectionRepository,
        eventQuery: (List<Long>, Instant, Instant) -> List<CalendarEvent>,
        zone: () -> ZoneId = ZoneId::systemDefault,
    ) : this(
        { calendarIds() }, selectionRepository,
        { ids, start, end, _ -> eventQuery(ids, start, end) }, zone, { null },
    )

    @Inject
    constructor(
        contentResolver: ContentResolver,
        selectionRepository: CalendarSelectionRepository,
    ) : this(
        calendarIds = { signal -> queryGoogleCalendarIds(contentResolver, checkNotNull(signal)) },
        selectionRepository = selectionRepository,
        eventQuery = { ids, start, end, signal -> queryEvents(contentResolver, ids, start, end, checkNotNull(signal)) },
        zone = ZoneId::systemDefault,
        cancellationSignal = { CancellationSignal() },
    )

    override suspend fun getEventsOverlapping(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Result<List<CalendarEvent>> = try {
        val signal = cancellationSignal()
        Result.success(awaitCalendarProvider(cancel = { signal?.cancel() }) {
            val readableIds = calendarIds(signal)
            signal?.throwIfCanceled()
            val selectedIds = selectionRepository
                .reconcile(readableIds.toSet())
                .getOrThrow()
                .sorted()
            check(readableIds.isNotEmpty()) { "No readable Google calendars" }
            check(selectedIds.isNotEmpty()) { "No selected Google calendars" }
            val localZone = zone()
            // CalendarContract stores all-day dates at UTC midnight, not local midnight.
            // Pad the provider query so date normalization cannot omit an edge-day event.
            signal?.throwIfCanceled()
            eventQuery(selectedIds, startInclusive.minusSeconds(86400), endExclusive.plusSeconds(86400), signal)
                .map { event ->
                    if (!event.allDay) event else event.copy(
                        start = event.start.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(localZone).toInstant(),
                        end = event.end.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(localZone).toInstant(),
                    )
                }
                .filter { it.start < endExclusive && it.end > startInclusive }
        })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    companion object {
        private fun queryGoogleCalendarIds(contentResolver: ContentResolver, signal: CancellationSignal): List<Long> {
            val projection = arrayOf(CalendarContract.Calendars._ID)
            val selection = "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
                "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.SYNC_EVENTS} = 1"
            val cursor = checkNotNull(
                contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    selection,
                    arrayOf("com.google"),
                    null,
                    signal,
                ),
            ) { "Calendar provider returned no cursor" }
            return cursor.use {
                signal.throwIfCanceled()
                buildList {
                    while (it.moveToNext()) {
                        signal.throwIfCanceled()
                        add(it.getLong(0))
                    }
                }
            }
        }

        private fun queryEvents(
            contentResolver: ContentResolver,
            calendarIds: List<Long>,
            startInclusive: Instant,
            endExclusive: Instant,
            signal: CancellationSignal,
        ): List<CalendarEvent> {
            val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(uriBuilder, startInclusive.toEpochMilli())
            ContentUris.appendId(uriBuilder, endExclusive.toEpochMilli())
            val placeholders = calendarIds.joinToString(",") { "?" }
            val selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
            )
            val cursor = checkNotNull(
                contentResolver.query(
                    uriBuilder.build(),
                    projection,
                    selection,
                    calendarIds.map(Long::toString).toTypedArray(),
                    null,
                    signal,
                ),
            ) { "Calendar provider returned no cursor" }
            return cursor.use {
                signal.throwIfCanceled()
                buildList {
                    while (it.moveToNext()) {
                        signal.throwIfCanceled()
                        add(
                            CalendarEvent(
                                title = it.getString(0),
                                start = Instant.ofEpochMilli(it.getLong(1)),
                                end = Instant.ofEpochMilli(it.getLong(2)),
                                allDay = it.getInt(3) == 1,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// A provider may ignore cancellation. Never make the alarm coroutine wait for its
// blocking Binder call, and never accumulate unbounded threads or queued lookups.
private val calendarProviderExecutor = ThreadPoolExecutor(
    2, 2, 30, TimeUnit.SECONDS, SynchronousQueue(),
    { task -> Thread(task, "Calarm-calendar-query").apply { isDaemon = true } },
).apply { allowCoreThreadTimeOut(true) }

// CancellationSignal.cancel may itself invoke a remote provider; keep it off the
// coroutine cancellation thread with a separately bounded executor.
private val calendarCancellationExecutor = ThreadPoolExecutor(
    2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue(2),
    { task -> Thread(task, "Calarm-calendar-cancel").apply { isDaemon = true } },
).apply { allowCoreThreadTimeOut(true) }

internal suspend fun <T> awaitCalendarProvider(cancel: () -> Unit = {}, query: () -> T): T =
    suspendCancellableCoroutine { continuation ->
        val task = FutureTask<Unit> {
            val result = runCatching(query)
            continuation.resumeWith(result)
        }
        continuation.invokeOnCancellation {
            task.cancel(true)
            runCatching { calendarCancellationExecutor.execute { runCatching(cancel) } }
        }
        try {
            calendarProviderExecutor.execute(task)
        } catch (error: Exception) {
            continuation.resumeWith(Result.failure(error))
        }
    }
