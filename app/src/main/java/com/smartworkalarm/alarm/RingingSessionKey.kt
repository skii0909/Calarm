package com.smartworkalarm.alarm

import android.content.Intent
import java.time.Instant

internal data class RingingSessionKey(
    val isShiftAlarm: Boolean,
    val alarmId: Long,
    val scheduledOccurrenceAtMillis: Long,
) {
    val scheduledOccurrenceAt: Instant?
        get() = scheduledOccurrenceAtMillis
            .takeIf { it != NO_SCHEDULED_OCCURRENCE }
            ?.let(Instant::ofEpochMilli)

    fun encode(): String = buildString {
        append(if (isShiftAlarm) SHIFT_KIND else REGULAR_KIND)
        append(SEPARATOR)
        append(alarmId)
        append(SEPARATOR)
        append(scheduledOccurrenceAtMillis)
    }

    companion object {
        private const val REGULAR_KIND = "regular"
        private const val SHIFT_KIND = "shift"
        private const val SEPARATOR = ':'
        const val NO_SCHEDULED_OCCURRENCE = Long.MIN_VALUE

        fun create(
            isShiftAlarm: Boolean,
            alarmId: Long,
            scheduledOccurrenceAt: Instant?,
        ): RingingSessionKey = RingingSessionKey(
            isShiftAlarm = isShiftAlarm,
            alarmId = alarmId,
            scheduledOccurrenceAtMillis = scheduledOccurrenceAt?.toEpochMilli()
                ?: NO_SCHEDULED_OCCURRENCE,
        )

        fun decode(encoded: String?): RingingSessionKey? {
            val parts = encoded?.split(SEPARATOR) ?: return null
            if (parts.size != 3) return null
            val isShiftAlarm = when (parts[0]) {
                REGULAR_KIND -> false
                SHIFT_KIND -> true
                else -> return null
            }
            val alarmId = parts[1].toLongOrNull()?.takeIf { it >= 0L } ?: return null
            val scheduledOccurrenceAtMillis = parts[2].toLongOrNull() ?: return null
            return RingingSessionKey(isShiftAlarm, alarmId, scheduledOccurrenceAtMillis)
        }
    }
}

internal fun Intent.putRingingSessionKey(key: RingingSessionKey) {
    putExtra(AlarmRingingService.EXTRA_RINGING_SESSION_KEY, key.encode())
}

internal fun Intent.ringingSessionKey(): RingingSessionKey? =
    RingingSessionKey.decode(getStringExtra(AlarmRingingService.EXTRA_RINGING_SESSION_KEY))

internal fun Intent.putTerminalActionResult(result: TerminalActionResult, message: String?) {
    putExtra(AlarmRingingService.EXTRA_TERMINAL_ACTION_RESULT, result.wireValue)
    message?.let { putExtra(AlarmRingingService.EXTRA_TERMINAL_ACTION_MESSAGE, it) }
}

internal fun Intent.terminalActionResult(): TerminalActionResult? =
    TerminalActionResult.fromWireValue(
        getStringExtra(AlarmRingingService.EXTRA_TERMINAL_ACTION_RESULT),
    )
