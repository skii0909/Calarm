package com.smartworkalarm.alarm

import com.smartworkalarm.domain.repository.ScheduleResult
import java.time.Instant

internal data class ActiveRingingSession(
    val alarmId: Long,
    val isShiftAlarm: Boolean,
    val snoozeEnabled: Boolean,
    val snoozeMinutes: Int,
    val scheduledOccurrenceAt: Instant?,
)

internal enum class SnoozeRequestOutcome {
    Rejected,
    Scheduled,
    Failed,
}

internal class RingingSessionStateMachine {
    private var activeSession: ActiveRingingSession? = null

    fun start(session: ActiveRingingSession) {
        activeSession = session.takeIf { it.alarmId >= 0 }
    }

    fun dismiss() {
        activeSession = null
    }

    fun requestSnooze(
        requestedAction: String?,
        schedule: (ActiveRingingSession) -> ScheduleResult,
    ): SnoozeRequestOutcome {
        val session = activeSession ?: return SnoozeRequestOutcome.Rejected
        if (!canScheduleSnooze(session.snoozeEnabled, requestedAction)) {
            return SnoozeRequestOutcome.Rejected
        }

        val scheduled = runCatching { schedule(session) }.getOrNull() == ScheduleResult.Scheduled
        if (!scheduled) return SnoozeRequestOutcome.Failed

        activeSession = null
        return SnoozeRequestOutcome.Scheduled
    }
}
