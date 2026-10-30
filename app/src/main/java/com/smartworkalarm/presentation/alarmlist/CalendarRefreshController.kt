package com.smartworkalarm.presentation.alarmlist

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.smartworkalarm.data.calendar.CalendarContractConnectionRepository
import com.smartworkalarm.data.calendar.awaitCalendarProvider
import com.smartworkalarm.outlook.OutlookController
import com.smartworkalarm.outlook.OutlookStore
import com.smartworkalarm.outlook.OutlookSyncResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

data class CalendarRefreshState(val refreshing: Boolean = false, val revision: Long = 0, val message: String = "")

@Singleton
class CalendarRefreshController internal constructor(
    private val googleRefresh: suspend () -> String?,
    private val outlookRefresh: suspend () -> String?,
    val changes: Flow<Unit>,
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        google: CalendarContractConnectionRepository,
        outlook: OutlookController,
        store: OutlookStore,
    ) : this(
        googleRefresh = {
            awaitCalendarProvider {
                val connection = google.getState()
                check(connection.calendarQuerySucceeded) { "Calendar catalog unavailable" }
                val accounts = connection.calendars.filter { it.selected && it.accountName.isNotBlank() && it.accountType.isNotBlank() }
                    .map { Account(it.accountName, it.accountType) }.distinct()
                accounts.forEach { account ->
                    ContentResolver.requestSync(account, CalendarContract.AUTHORITY, Bundle().apply {
                        putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                        putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                    })
                }
                // Android owns remote sync completion; late provider changes trigger another forecast.
                if (accounts.isEmpty()) null else "Google 동기화 요청 · 기기 일정으로 확인"
            }
        },
        outlookRefresh = {
            when (outlook.refresh()) {
                OutlookSyncResult.NOT_CONNECTED -> null
                OutlookSyncResult.SUCCESS -> "Outlook 동기화 완료"
                OutlookSyncResult.BUSY -> "Outlook 동기화 진행 중"
                else -> "Outlook 동기화 확인 필요 · 저장된 일정으로 확인"
            }
        },
        changes = merge(providerChanges(context.contentResolver), store.state.drop(1).map { Unit }),
    )

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(CalendarRefreshState())
    val state: StateFlow<CalendarRefreshState> = mutableState.asStateFlow()

    suspend fun refresh() {
        if (!mutex.tryLock()) return
        mutableState.value = mutableState.value.copy(refreshing = true, message = "캘린더 동기화 중…")
        val notes = mutableListOf<String>()
        try {
            sync("Google", 5_000, googleRefresh)?.let(notes::add)
            sync("Outlook", 20_000, outlookRefresh)?.let(notes::add)
        } finally {
            mutableState.value = CalendarRefreshState(false, mutableState.value.revision + 1, notes.joinToString("\n"))
            mutex.unlock()
        }
    }

    private suspend fun sync(name: String, timeout: Long, action: suspend () -> String?): String? = try {
        // Wrap nullable success so an unconnected account is not mistaken for a timeout.
        val completed = withTimeoutOrNull(timeout) { listOf(action()) }
        if (completed == null) "$name 동기화 시간 초과 · 저장된 일정으로 확인" else completed.first()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { "$name 동기화 실패 · 저장된 일정으로 확인" }
}

private fun providerChanges(resolver: ContentResolver): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { trySend(Unit) }
    }
    val registered = runCatching {
        resolver.registerContentObserver(Uri.parse("content://${CalendarContract.AUTHORITY}"), true, observer)
    }.isSuccess
    awaitClose { if (registered) runCatching { resolver.unregisterContentObserver(observer) } }
}
