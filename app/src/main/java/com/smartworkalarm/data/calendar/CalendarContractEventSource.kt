package com.smartworkalarm.data.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.CalendarContract
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.CalendarSelectionRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalendarContractEventSource internal constructor(
    private val calendarIds: () -> List<Long>,
    private val selectionRepository: CalendarSelectionRepository,
    private val eventQuery: (List<Long>, Instant, Instant) -> List<CalendarEvent>,
) : CalendarEventSource {
    @Inject
    constructor(
        contentResolver: ContentResolver,
        selectionRepository: CalendarSelectionRepository,
    ) : this(
        calendarIds = { queryGoogleCalendarIds(contentResolver) },
        selectionRepository = selectionRepository,
        eventQuery = { ids, start, end -> queryEvents(contentResolver, ids, start, end) },
    )

    override suspend fun getEventsOverlapping(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Result<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        runCatching {
            val readableIds = calendarIds()
            val selectedIds = selectionRepository
                .reconcile(readableIds.toSet())
                .getOrThrow()
                .sorted()
            check(readableIds.isNotEmpty()) { "No readable Google calendars" }
            check(selectedIds.isNotEmpty()) { "No selected Google calendars" }
            eventQuery(selectedIds, startInclusive, endExclusive)
        }
    }

    companion object {
        private fun queryGoogleCalendarIds(contentResolver: ContentResolver): List<Long> {
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
                ),
            ) { "Calendar provider returned no cursor" }
            return cursor.use {
                buildList {
                    while (it.moveToNext()) add(it.getLong(0))
                }
            }
        }

        private fun queryEvents(
            contentResolver: ContentResolver,
            calendarIds: List<Long>,
            startInclusive: Instant,
            endExclusive: Instant,
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
                ),
            ) { "Calendar provider returned no cursor" }
            return cursor.use {
                buildList {
                    while (it.moveToNext()) {
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
