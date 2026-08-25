package com.smartworkalarm.alarm

import android.content.Intent
import java.util.UUID
import javax.inject.Inject

internal data class RingingSessionKey(
    val isShiftAlarm: Boolean,
    val alarmId: Long,
    val attemptToken: String,
) {
    fun encode(): String = buildString {
        append(VERSION)
        append(SEPARATOR)
        append(if (isShiftAlarm) SHIFT_KIND else REGULAR_KIND)
        append(SEPARATOR)
        append(alarmId)
        append(SEPARATOR)
        append(attemptToken)
    }

    companion object {
        private const val VERSION = "v2"
        private const val REGULAR_KIND = "regular"
        private const val SHIFT_KIND = "shift"
        private const val SEPARATOR = ':'

        fun create(
            isShiftAlarm: Boolean,
            alarmId: Long,
            attemptToken: String,
        ): RingingSessionKey {
            require(alarmId >= 0L) { "alarmId must be non-negative" }
            require(isValidToken(attemptToken)) { "attemptToken is invalid" }
            return RingingSessionKey(isShiftAlarm, alarmId, attemptToken)
        }

        fun decode(encoded: String?): RingingSessionKey? {
            val parts = encoded?.split(SEPARATOR) ?: return null
            if (parts.size != 4 || parts[0] != VERSION) return null
            val isShiftAlarm = when (parts[1]) {
                REGULAR_KIND -> false
                SHIFT_KIND -> true
                else -> return null
            }
            val alarmId = parts[2].toLongOrNull()?.takeIf { it >= 0L } ?: return null
            val attemptToken = parts[3].takeIf(::isValidToken) ?: return null
            return RingingSessionKey(isShiftAlarm, alarmId, attemptToken)
        }

        private fun isValidToken(token: String): Boolean =
            token.isNotBlank() && token.length <= 128 && SEPARATOR !in token
    }
}

class RingingAttemptTokenFactory @Inject constructor() {
    fun next(): String = UUID.randomUUID().toString()
}

internal fun resolveStartSessionKey(
    suppliedKey: RingingSessionKey?,
    isShiftAlarm: Boolean,
    alarmId: Long,
    nextAttemptToken: () -> String,
): RingingSessionKey {
    if (
        suppliedKey != null &&
        suppliedKey.isShiftAlarm == isShiftAlarm &&
        suppliedKey.alarmId == alarmId
    ) {
        return suppliedKey
    }
    return RingingSessionKey.create(isShiftAlarm, alarmId, nextAttemptToken())
}

internal fun Intent.putRingingSessionKey(key: RingingSessionKey) {
    putExtra(AlarmRingingService.EXTRA_RINGING_SESSION_KEY, key.encode())
}

internal fun Intent.ringingSessionKey(): RingingSessionKey? =
    RingingSessionKey.decode(getStringExtra(AlarmRingingService.EXTRA_RINGING_SESSION_KEY))

internal fun newTerminalRequestId(): String = UUID.randomUUID().toString()

internal fun Intent.putTerminalRequestId(requestId: String) {
    require(requestId.isNotBlank()) { "requestId must not be blank" }
    putExtra(AlarmRingingService.EXTRA_TERMINAL_REQUEST_ID, requestId)
}

internal fun Intent.terminalRequestId(): String? =
    getStringExtra(AlarmRingingService.EXTRA_TERMINAL_REQUEST_ID)?.takeIf { it.isNotBlank() }

internal fun Intent.putTerminalActionResult(result: TerminalActionResult, message: String?) {
    putExtra(AlarmRingingService.EXTRA_TERMINAL_ACTION_RESULT, result.wireValue)
    message?.let { putExtra(AlarmRingingService.EXTRA_TERMINAL_ACTION_MESSAGE, it) }
}

internal fun Intent.terminalActionResult(): TerminalActionResult? =
    TerminalActionResult.fromWireValue(
        getStringExtra(AlarmRingingService.EXTRA_TERMINAL_ACTION_RESULT),
    )
