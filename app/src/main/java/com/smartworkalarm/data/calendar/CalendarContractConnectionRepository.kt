package com.smartworkalarm.data.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.smartworkalarm.domain.model.CalendarConnectionState
import com.smartworkalarm.domain.model.ConnectedCalendar
import com.smartworkalarm.domain.repository.CalendarConnectionRepository
import com.smartworkalarm.domain.repository.CalendarSelectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CalendarContractConnectionRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val contentResolver: ContentResolver,
    private val selectionRepository: CalendarSelectionRepository,
) : CalendarConnectionRepository {
    override fun getState(): CalendarConnectionState {
        val permissionGranted = hasPermission()
        return if (permissionGranted) {
            CalendarConnectionStateResolver.resolve(
                permissionGranted = true,
                calendars = queryCalendars(),
                selectionRepository = selectionRepository,
            )
        } else {
            CalendarConnectionEvaluator.evaluate(
                permissionGranted = false,
                calendars = emptyList(),
            )
        }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private fun queryCalendars(): Result<List<ConnectedCalendar>> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        val selection = "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
            "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.SYNC_EVENTS} = 1"
        return calendarProviderRows(
            query = {
                contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    selection,
                    arrayOf("com.google"),
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                )
            },
            readRows = { cursor ->
                cursor.use {
                    buildList {
                        while (it.moveToNext()) {
                            add(
                                ConnectedCalendar(
                                    id = it.getLong(0),
                                    displayName = it.getString(1).orEmpty(),
                                    accountName = it.getString(2).orEmpty(),
                                    accountType = it.getString(3).orEmpty(),
                                ),
                            )
                        }
                    }
                }
            },
        )
    }
}

internal fun <T> calendarProviderRows(
    query: () -> T?,
    readRows: (T) -> List<ConnectedCalendar>,
): Result<List<ConnectedCalendar>> = runCatching {
    val cursor = checkNotNull(query()) { "Calendar provider returned no cursor" }
    readRows(cursor)
}
