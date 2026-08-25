package com.smartworkalarm.alarm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class AlarmTerminalUiState(
    val sessionKey: RingingSessionKey,
    val actionsEnabled: Boolean = true,
    val persistentError: String? = null,
)

internal enum class AlarmTerminalUiEffect {
    IGNORE,
    STAY_OPEN,
    FINISH,
}

internal class AlarmTerminalActionController(initialKey: RingingSessionKey) {
    private var gate = TerminalAlarmActionGate()

    var state by mutableStateOf(AlarmTerminalUiState(initialKey))
        private set

    @Synchronized
    fun tryStartRequest(requestedKey: RingingSessionKey): Boolean {
        if (requestedKey != state.sessionKey || !gate.tryAcquire()) return false
        state = state.copy(actionsEnabled = false, persistentError = null)
        return true
    }

    @Synchronized
    fun acceptServiceResult(
        resultKey: RingingSessionKey,
        result: TerminalActionResult,
        message: String?,
    ): AlarmTerminalUiEffect {
        if (resultKey != state.sessionKey) return AlarmTerminalUiEffect.IGNORE
        if (result == TerminalActionResult.SUCCESS) return AlarmTerminalUiEffect.FINISH

        gate.release()
        state = state.copy(
            actionsEnabled = true,
            persistentError = message ?: DEFAULT_ACTION_ERROR,
        )
        return AlarmTerminalUiEffect.STAY_OPEN
    }

    @Synchronized
    fun acceptDispatchFailure(requestKey: RingingSessionKey, message: String) {
        if (requestKey != state.sessionKey) return
        gate.release()
        state = state.copy(actionsEnabled = true, persistentError = message)
    }

    @Synchronized
    fun replaceSession(sessionKey: RingingSessionKey) {
        if (sessionKey == state.sessionKey) return
        gate = TerminalAlarmActionGate()
        state = AlarmTerminalUiState(sessionKey)
    }

    private companion object {
        const val DEFAULT_ACTION_ERROR = "요청을 처리하지 못했습니다. 다시 시도해 주세요."
    }
}
