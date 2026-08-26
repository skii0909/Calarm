package com.smartworkalarm.prealarm

import android.content.SharedPreferences
import java.time.Instant

class SharedPreferencesOccurrenceSkipStore(
    private val preferences: SharedPreferences,
) : OccurrenceSkipStore {
    override fun mark(key: AlarmOccurrenceKey) = synchronized(LOCK) {
        val entries = readEntries().apply { add(key.encode()) }
        persist(entries)
    }

    override fun consume(key: AlarmOccurrenceKey): Boolean = synchronized(LOCK) {
        val entries = readEntries()
        if (!entries.remove(key.encode())) return false

        persist(entries)
        true
    }

    override fun prune(now: Instant) = synchronized(LOCK) {
        val cutoff = now.minusSeconds(RETENTION_SECONDS)
        val retained = readEntries().filterTo(mutableSetOf()) { encoded ->
            AlarmOccurrenceKey.decode(encoded)?.scheduledAt?.isBefore(cutoff) == false
        }
        persist(retained)
    }

    private fun readEntries(): MutableSet<String> = preferences
        .getStringSet(SKIPPED_OCCURRENCES_KEY, emptySet())
        .orEmpty()
        .toMutableSet()

    private fun persist(entries: Set<String>) {
        preferences.edit()
            .putStringSet(SKIPPED_OCCURRENCES_KEY, entries.toMutableSet())
            .apply()
    }

    private companion object {
        val LOCK = Any()
        const val SKIPPED_OCCURRENCES_KEY = "skipped_alarm_occurrences"
        const val RETENTION_SECONDS = 24 * 60 * 60L
    }
}
