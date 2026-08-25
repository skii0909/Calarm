package com.smartworkalarm.alarm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class AlarmTerminalUiState(
    val sessionKey: RingingSessionKey,
    val inFlightRequestId: String? = null,
    val persistentError: String? = null,
) {
    val actionsEnabled: Boolean get() = inFlightRequestId == null
}

internal enum class AlarmTerminalUiEffect {
    IGNORE,
    STAY_OPEN,
    FINISH,
}

internal class AlarmTerminalActionController(initialState: AlarmTerminalUiState) {
    constructor(initialKey: RingingSessionKey) : this(AlarmTerminalUiState(initialKey))

    var state by mutableStateOf(initialState)
        private set

    @Synchronized
    fun tryStartRequest(requestedKey: RingingSessionKey, requestId: String): Boolean {
        if (
            requestedKey != state.sessionKey ||
            requestId.isBlank() ||
            state.inFlightRequestId != null
        ) {
            return false
        }
        state = state.copy(inFlightRequestId = requestId, persistentError = null)
        return true
    }

    @Synchronized
    fun acceptServiceResult(serviceResult: AlarmTerminalServiceResult): AlarmTerminalUiEffect {
        if (
            serviceResult.sessionKey != state.sessionKey ||
            serviceResult.requestId != state.inFlightRequestId
        ) {
            return AlarmTerminalUiEffect.IGNORE
        }
        if (serviceResult.result == TerminalActionResult.SUCCESS) {
            state = state.copy(inFlightRequestId = null, persistentError = null)
            return AlarmTerminalUiEffect.FINISH
        }

        state = state.copy(
            inFlightRequestId = null,
            persistentError = serviceResult.message ?: DEFAULT_ACTION_ERROR,
        )
        return AlarmTerminalUiEffect.STAY_OPEN
    }

    @Synchronized
    fun acceptDispatchFailure(
        requestKey: RingingSessionKey,
        requestId: String,
        message: String,
    ) {
        if (requestKey != state.sessionKey || requestId != state.inFlightRequestId) return
        state = state.copy(inFlightRequestId = null, persistentError = message)
    }

    @Synchronized
    fun replaceSession(sessionKey: RingingSessionKey) {
        if (sessionKey == state.sessionKey) return
        state = AlarmTerminalUiState(sessionKey)
    }

    private companion object {
        const val DEFAULT_ACTION_ERROR = "요청을 처리하지 못했습니다. 다시 시도해 주세요."
    }
}

internal fun restoreAlarmTerminalUiState(
    initialSessionKey: RingingSessionKey,
    savedSessionKey: String?,
    savedInFlightRequestId: String?,
    savedPersistentError: String?,
): AlarmTerminalUiState {
    if (RingingSessionKey.decode(savedSessionKey) != initialSessionKey) {
        return AlarmTerminalUiState(initialSessionKey)
    }
    return AlarmTerminalUiState(
        sessionKey = initialSessionKey,
        inFlightRequestId = savedInFlightRequestId?.takeIf { it.isNotBlank() },
        persistentError = savedPersistentError,
    )
}
