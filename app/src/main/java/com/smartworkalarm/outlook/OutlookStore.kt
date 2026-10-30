package com.smartworkalarm.outlook

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface OutlookStore {
    val state: StateFlow<OutlookState>
    suspend fun awaitLoaded(): OutlookState
    suspend fun update(transform: (OutlookState) -> OutlookState): OutlookState
}

class RoomOutlookStore(private val database: OutlookDatabase) : OutlookStore {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(OutlookState(loaded = false))
    override val state = mutableState.asStateFlow()

    override suspend fun awaitLoaded(): OutlookState = mutex.withLock { load() }

    override suspend fun update(transform: (OutlookState) -> OutlookState): OutlookState = mutex.withLock {
        val old = load()
        val next = transform(old).copy(loaded = true)
        if (next != old) {
            try {
                database.snapshotDao().put(OutlookSnapshotRow(payload = OutlookStateJson.encode(next)))
                mutableState.value = next
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = old.copy(issue = OutlookIssue.STORAGE)
                throw OutlookException(OutlookIssue.STORAGE)
            }
        }
        next
    }

    private suspend fun load(): OutlookState {
        if (!mutableState.value.loaded) {
            try {
                mutableState.value = database.snapshotDao().read()?.let { OutlookStateJson.decode(it.payload) } ?: OutlookState()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = OutlookState(issue = OutlookIssue.STORAGE)
            }
        }
        if (mutableState.value.issue == OutlookIssue.STORAGE) throw OutlookException(OutlookIssue.STORAGE)
        return mutableState.value
    }
}
