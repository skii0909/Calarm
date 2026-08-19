package com.smartworkalarm.data.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.CalendarContract
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.repository.CalendarEventSource
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalendarContractEventSource @Inject constructor(
    private val contentResolver: ContentResolver,
) : CalendarEventSource {
    override suspend fun getEventsOverlapping(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Result<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        runCatching {
            val calendarIds = queryGoogleCalendarIds()
            if (calendarIds.isEmpty()) return@runCatching emptyList()

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

            cursor.use {
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

    private fun queryGoogleCalendarIds(): List<Long> {
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
}
