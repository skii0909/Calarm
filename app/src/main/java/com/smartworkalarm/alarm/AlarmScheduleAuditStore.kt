package com.smartworkalarm.alarm

import android.content.SharedPreferences
import java.time.Clock
import java.time.Instant

data class AlarmScheduleAudit(
    val lastSuccessfulScheduleAt: Instant?,
    val nextTriggerAt: Instant?,
    val activeAlarmCount: Int,
)

interface AlarmScheduleAuditStore {
    fun recordScheduled(key: String, triggerAt: Instant)
    fun recordCancelled(key: String)
    fun read(): AlarmScheduleAudit
}

class SharedPreferencesAlarmScheduleAuditStore(
    private val preferences: SharedPreferences,
    private val clock: Clock = Clock.systemUTC(),
) : AlarmScheduleAuditStore {
    override fun recordScheduled(key: String, triggerAt: Instant) = synchronized(LOCK) {
        val active = readActive().toMutableMap().apply { put(key, triggerAt.toEpochMilli()) }
        persist(active, clock.instant().toEpochMilli())
    }

    override fun recordCancelled(key: String) = synchronized(LOCK) {
        val active = readActive().toMutableMap().apply { remove(key) }
        persist(active, preferences.getLong(LAST_SUCCESS_EPOCH_MS, NO_TIMESTAMP))
    }

    override fun read(): AlarmScheduleAudit = synchronized(LOCK) {
        val active = readActive()
        AlarmScheduleAudit(
            lastSuccessfulScheduleAt = preferences.getLong(LAST_SUCCESS_EPOCH_MS, NO_TIMESTAMP)
                .takeIf { it != NO_TIMESTAMP }
                ?.let(Instant::ofEpochMilli),
            nextTriggerAt = active.values.minOrNull()?.let(Instant::ofEpochMilli),
            activeAlarmCount = active.size,
        )
    }

    private fun readActive(): Map<String, Long> = preferences
        .getStringSet(ACTIVE_ALARMS_KEY, emptySet())
        .orEmpty()
        .mapNotNull { entry ->
            val separator = entry.lastIndexOf(ENTRY_SEPARATOR)
            if (separator <= 0) return@mapNotNull null
            entry.substring(0, separator) to entry.substring(separator + 1).toLongOrNull()
        }
        .mapNotNull { (key, triggerAt) -> triggerAt?.let { key to it } }
        .toMap()

    private fun persist(active: Map<String, Long>, lastSuccessEpochMs: Long) {
        val persisted = preferences.edit()
            .putStringSet(
                ACTIVE_ALARMS_KEY,
                active.map { (key, triggerAt) -> "$key$ENTRY_SEPARATOR$triggerAt" }.toSet(),
            )
            .putLong(LAST_SUCCESS_EPOCH_MS, lastSuccessEpochMs)
            .commit()
        check(persisted) { "Unable to persist alarm scheduling audit" }
    }

    private companion object {
        val LOCK = Any()
        const val ACTIVE_ALARMS_KEY = "active_alarm_entries"
        const val LAST_SUCCESS_EPOCH_MS = "last_success_epoch_ms"
        const val NO_TIMESTAMP = Long.MIN_VALUE
        const val ENTRY_SEPARATOR = '|'
    }
}
