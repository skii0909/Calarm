package com.smartworkalarm.alarm

import com.smartworkalarm.domain.repository.ScheduleResult

internal data class ActiveRingingSession(
    val key: RingingSessionKey,
    val snoozeEnabled: Boolean,
    val snoozeMinutes: Int,
) {
    val alarmId: Long get() = key.alarmId
    val isShiftAlarm: Boolean get() = key.isShiftAlarm
    val scheduledOccurrenceAt get() = key.scheduledOccurrenceAt
}

internal enum class TerminalActionResult(val wireValue: String) {
    SUCCESS("success"),
    REJECTED("rejected"),
    FAILED("failed"),
    ;

    companion object {
        fun fromWireValue(value: String?): TerminalActionResult? =
            entries.firstOrNull { it.wireValue == value }
    }
}

internal class RingingSessionStateMachine {
    private var activeSession: ActiveRingingSession? = null

    @Synchronized
    fun start(session: ActiveRingingSession) {
        activeSession = session.takeIf { it.key.alarmId >= 0L }
    }

    @Synchronized
    fun dismiss(requestedKey: RingingSessionKey): TerminalActionResult {
        val session = activeSession ?: return TerminalActionResult.REJECTED
        if (session.key != requestedKey) return TerminalActionResult.REJECTED

        activeSession = null
        return TerminalActionResult.SUCCESS
    }

    @Synchronized
    fun requestSnooze(
        requestedKey: RingingSessionKey,
        schedule: (ActiveRingingSession) -> ScheduleResult,
    ): TerminalActionResult {
        val session = activeSession ?: return TerminalActionResult.REJECTED
        if (session.key != requestedKey || !session.snoozeEnabled) {
            return TerminalActionResult.REJECTED
        }

        val scheduled = runCatching { schedule(session) }.getOrNull() == ScheduleResult.Scheduled
        if (!scheduled) return TerminalActionResult.FAILED

        activeSession = null
        return TerminalActionResult.SUCCESS
    }

    @Synchronized
    fun clear() {
        activeSession = null
    }
}
