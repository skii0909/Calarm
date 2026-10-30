package com.smartworkalarm.data.calendar

import android.content.SharedPreferences
import com.smartworkalarm.domain.repository.CalendarSelectionRepository
import javax.inject.Inject

class SharedPreferencesCalendarSelectionRepository @Inject constructor(
    private val preferences: SharedPreferences,
) : CalendarSelectionRepository {
    override fun getSelectedCalendarIds(): Result<Set<Long>> = synchronized(SELECTION_LOCK) {
        runCatching { readIds(SELECTED_IDS_KEY) }
    }

    override fun reconcile(availableCalendarIds: Set<Long>): Result<Set<Long>> = synchronized(SELECTION_LOCK) {
        runCatching {
            val knownIds = readIds(KNOWN_IDS_KEY)
            val selectedIds = readIds(SELECTED_IDS_KEY)
            val nextSelectedIds = (selectedIds intersect availableCalendarIds) +
                (availableCalendarIds - knownIds)
            // Hidden or temporarily unavailable calendars must not return as newly
            // discovered selections and silently override the user's deselection.
            persist(knownIds + availableCalendarIds, nextSelectedIds)
            nextSelectedIds
        }
    }

    override fun setCalendarSelected(calendarId: Long, selected: Boolean): Result<Unit> = synchronized(SELECTION_LOCK) {
        updateSelection(setOf(calendarId), selected)
    }

    override fun setCalendarsSelected(calendarIds: Set<Long>, selected: Boolean): Result<Unit> = synchronized(SELECTION_LOCK) {
        updateSelection(calendarIds, selected)
    }

    private fun updateSelection(calendarIds: Set<Long>, selected: Boolean): Result<Unit> = runCatching {
        val nextSelectedIds = readIds(SELECTED_IDS_KEY).toMutableSet().apply {
            if (selected) addAll(calendarIds) else removeAll(calendarIds)
        }
        persist(readIds(KNOWN_IDS_KEY), nextSelectedIds)
    }

    private fun readIds(key: String): Set<Long> =
        preferences.getStringSet(key, emptySet())
            .orEmpty()
            .map(String::toLong)
            .toSet()

    private fun persist(knownIds: Set<Long>, selectedIds: Set<Long>) {
        val persisted = preferences.edit()
            .putStringSet(KNOWN_IDS_KEY, knownIds.map(Long::toString).toSet())
            .putStringSet(SELECTED_IDS_KEY, selectedIds.map(Long::toString).toSet())
            .commit()
        check(persisted) { "Unable to persist calendar selection" }
    }

    private companion object {
        val SELECTION_LOCK = Any()
        const val KNOWN_IDS_KEY = "known_ids"
        const val SELECTED_IDS_KEY = "selected_ids"
    }
}
