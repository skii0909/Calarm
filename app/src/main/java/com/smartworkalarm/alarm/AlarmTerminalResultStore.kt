package com.smartworkalarm.alarm

internal data class AlarmTerminalServiceResult(
    val requestId: String,
    val sessionKey: RingingSessionKey,
    val result: TerminalActionResult,
    val message: String?,
)

internal class AlarmTerminalResultStore {
    private val pendingByRequestId = LinkedHashMap<String, AlarmTerminalServiceResult>()

    @Synchronized
    fun publish(result: AlarmTerminalServiceResult) {
        pendingByRequestId.putIfAbsent(result.requestId, result)
        while (pendingByRequestId.size > MAX_PENDING_RESULTS) {
            pendingByRequestId.remove(pendingByRequestId.keys.first())
        }
    }

    @Synchronized
    fun take(requestId: String): AlarmTerminalServiceResult? =
        pendingByRequestId.remove(requestId)

    @Synchronized
    fun clear() {
        pendingByRequestId.clear()
    }

    private companion object {
        const val MAX_PENDING_RESULTS = 64
    }
}

internal class AlarmTerminalResultRouter(
    private val controller: AlarmTerminalActionController,
    private val store: AlarmTerminalResultStore,
) {
    fun acceptBroadcast(result: AlarmTerminalServiceResult): AlarmTerminalUiEffect {
        if (
            result.requestId != controller.state.inFlightRequestId ||
            result.sessionKey != controller.state.sessionKey
        ) {
            return AlarmTerminalUiEffect.IGNORE
        }
        store.take(result.requestId)
        return controller.acceptServiceResult(result)
    }

    fun acceptStoredResult(): AlarmTerminalUiEffect {
        val requestId = controller.state.inFlightRequestId ?: return AlarmTerminalUiEffect.IGNORE
        val result = store.take(requestId) ?: return AlarmTerminalUiEffect.IGNORE
        return controller.acceptServiceResult(result)
    }
}

internal val alarmTerminalResultStore = AlarmTerminalResultStore()
