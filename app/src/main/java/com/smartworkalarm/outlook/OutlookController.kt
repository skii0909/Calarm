package com.smartworkalarm.outlook

import android.app.Activity
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface OutlookSyncScheduler {
    fun ensureScheduled()
    fun requestSync()
    fun cancel()
}

class OutlookController(
    private val store: OutlookStore,
    private val auth: OutlookAuth,
    private val sync: OutlookSyncCoordinator,
    private val scheduler: OutlookSyncScheduler,
    private val onChanged: suspend () -> Unit,
) {
    private val authentication = Mutex()

    suspend fun connect(activity: Activity): Boolean {
        val attempt = UUID.randomUUID().toString()
        store.update { it.copy(generation = attempt) }
        val applied = authentication.withLock {
            if (store.state.value.generation != attempt) return@withLock false
            val account = auth.signIn(activity) ?: return@withLock false
            var accepted = false
            store.update { current ->
                if (current.generation != attempt) current else {
                    accepted = true
                    if (current.account?.id == account.id) current.copy(account = account, issue = null, retryAt = null)
                    else OutlookState(generation = attempt, account = account)
                }
            }
            accepted
        }
        if (applied) {
            scheduler.ensureScheduled()
            onChanged()
            refresh()
        }
        return applied
    }

    suspend fun refresh(): OutlookSyncResult {
        val state = store.awaitLoaded()
        if (state.account == null) return OutlookSyncResult.NOT_CONNECTED
        scheduler.ensureScheduled()
        val result = sync.sync()
        if (result == OutlookSyncResult.RETRY || result == OutlookSyncResult.BUSY || result == OutlookSyncResult.SUPERSEDED) scheduler.requestSync()
        onChanged()
        return result
    }

    suspend fun setSelected(key: OutlookCalendarKey, selected: Boolean) {
        var changed = false
        store.update { current ->
            if (current.account?.id != key.accountId || current.calendars.none { it.id == key.calendarId }) current else {
                val ids = if (selected) current.selectedIds + key.calendarId else current.selectedIds - key.calendarId
                if (ids == current.selectedIds) current else {
                    changed = true
                    current.copy(generation = UUID.randomUUID().toString(), selectedIds = ids, syncedAt = null,
                        coverageStart = null, coverageEnd = null, events = emptyList())
                }
            }
        }
        if (changed) { onChanged(); scheduler.requestSync() }
    }

    suspend fun disconnect() {
        store.update { OutlookState() }
        scheduler.cancel()
        onChanged()
        authentication.withLock { auth.signOut() }
    }
}
