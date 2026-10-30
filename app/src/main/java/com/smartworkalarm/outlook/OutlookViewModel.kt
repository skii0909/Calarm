package com.smartworkalarm.outlook

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OutlookUiState(
    val connection: OutlookState = OutlookState(loaded = false),
    val configured: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val requiresReconnect: Boolean get() = connection.issue in setOf(OutlookIssue.REAUTH_REQUIRED, OutlookIssue.ACCESS_DENIED)
    val canConnect: Boolean get() = configured && connection.loaded && !busy && (connection.account == null || requiresReconnect)
    val canRefresh: Boolean get() = configured && connection.loaded && !busy && connection.account != null && !requiresReconnect
}

@HiltViewModel
class OutlookViewModel @Inject constructor(
    private val store: OutlookStore,
    private val controller: OutlookController,
    configuration: OutlookConfiguration,
) : ViewModel() {
    private data class Operation(val busy: Boolean = false, val message: String? = null)
    private val operation = MutableStateFlow(Operation())
    val state = combine(store.state, operation) { connection, operation ->
        OutlookUiState(connection, configuration.isConfigured, operation.busy, operation.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutlookUiState(configured = configuration.isConfigured))

    init { viewModelScope.launch {
        try { store.awaitLoaded() } catch (error: Exception) {
            if (error is CancellationException) throw error
            operation.value = Operation(message = outlookIssueMessage(OutlookIssue.STORAGE))
        }
    } }

    fun connect(activity: Activity) = act { controller.connect(activity); Unit }
    fun refresh() = act { controller.refresh(); Unit }
    fun disconnect() = act { controller.disconnect() }
    fun select(key: OutlookCalendarKey, selected: Boolean) = act { controller.setSelected(key, selected) }

    private fun act(action: suspend () -> Unit) {
        if (operation.value.busy) return
        operation.value = Operation(busy = true)
        viewModelScope.launch {
            try { action(); operation.value = Operation() }
            catch (error: Exception) {
                if (error is CancellationException) { operation.value = Operation(); throw error }
                operation.value = Operation(message = outlookIssueMessage((error as? OutlookException)?.issue ?: OutlookIssue.NETWORK))
            }
        }
    }
}

fun outlookIssueMessage(issue: OutlookIssue): String = when (issue) {
    OutlookIssue.SETUP_REQUIRED -> "Microsoft 로그인 설정이 아직 완료되지 않았습니다. 연결을 지원하는 업데이트가 필요합니다."
    OutlookIssue.REAUTH_REQUIRED -> "Microsoft 로그인이 만료되었거나 연결 권한이 변경되었습니다. 다시 로그인해 주세요."
    OutlookIssue.ACCESS_DENIED -> "캘린더 읽기 권한을 확인해 주세요. 회사·학교 계정은 조직 관리자의 승인이 필요할 수 있습니다."
    OutlookIssue.NETWORK -> "Outlook 일정을 갱신하지 못했습니다. 인터넷 연결을 확인한 뒤 다시 시도해 주세요."
    OutlookIssue.THROTTLED -> "Microsoft에서 잠시 후 다시 연결하도록 요청했습니다. 자동으로 재시도합니다."
    OutlookIssue.INVALID_DATA -> "일정 정보를 완전히 읽지 못했습니다. 다시 시도해 주세요."
    OutlookIssue.STORAGE -> "Outlook 일정 저장소를 읽거나 저장하지 못했습니다. 저장 공간을 확인하고 앱을 다시 실행해 주세요."
}
