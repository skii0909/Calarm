package com.smartworkalarm.prealarm

import android.content.SharedPreferences
import java.time.Clock
import java.time.Instant

class SharedPreferencesOccurrenceSkipStore(
    private val preferences: SharedPreferences,
    private val clock: Clock,
) : OccurrenceSkipStore {
    override fun contains(key: AlarmOccurrenceKey): Boolean = synchronized(LOCK) {
        key.encode() in readEntries()
    }
    override fun mark(key: AlarmOccurrenceKey) = synchronized(LOCK) {
        val entries = readEntries().apply { add(key.encode()) }
        persist(retainedEntries(entries, clock.instant()))
    }

    override fun consume(key: AlarmOccurrenceKey): Boolean = synchronized(LOCK) {
        val entries = retainedEntries(readEntries(), clock.instant())
        val consumed = entries.remove(key.encode())
        persist(entries)
        consumed
    }

    override fun prune(now: Instant) = synchronized(LOCK) {
        persist(retainedEntries(readEntries(), now))
    }

    private fun retainedEntries(entries: Set<String>, now: Instant): MutableSet<String> {
        val cutoff = now.minusSeconds(RETENTION_SECONDS)
        return entries.filterTo(mutableSetOf()) { encoded ->
            AlarmOccurrenceKey.decode(encoded)?.scheduledAt?.isBefore(cutoff) == false
        }
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
