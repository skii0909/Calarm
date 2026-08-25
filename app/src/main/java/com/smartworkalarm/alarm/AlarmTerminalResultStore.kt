package com.smartworkalarm.alarm

internal data class AlarmTerminalServiceResult(
    val sessionKey: RingingSessionKey,
    val result: TerminalActionResult,
    val message: String?,
)

internal class AlarmTerminalResultStore {
    private var pending: AlarmTerminalServiceResult? = null

    @Synchronized
    fun publish(result: AlarmTerminalServiceResult) {
        pending = result
    }

    @Synchronized
    fun take(sessionKey: RingingSessionKey): AlarmTerminalServiceResult? {
        val result = pending?.takeIf { it.sessionKey == sessionKey } ?: return null
        pending = null
        return result
    }

    @Synchronized
    fun clear() {
        pending = null
    }
}

internal val alarmTerminalResultStore = AlarmTerminalResultStore()
