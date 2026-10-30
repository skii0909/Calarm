package com.smartworkalarm.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartworkalarm.outlook.OutlookCalendarKey
import com.smartworkalarm.outlook.OutlookIssue
import com.smartworkalarm.outlook.OutlookSnapshotPolicy
import com.smartworkalarm.outlook.OutlookUiState
import com.smartworkalarm.outlook.outlookIssueMessage
import com.smartworkalarm.presentation.components.NightPulseSection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun OutlookConnectionCard(
    state: OutlookUiState,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onSelect: (OutlookCalendarKey, Boolean) -> Unit,
) {
    var confirmDisconnect by remember { mutableStateOf(false) }
    val connection = state.connection
    val account = connection.account
    NightPulseSection(title = "Outlook 캘린더", subtitle = "Microsoft 계정으로 로그인하고 알람에 사용할 캘린더를 선택합니다.") {
        if (!state.configured) {
            Text(outlookIssueMessage(OutlookIssue.SETUP_REQUIRED), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!connection.loaded || state.busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(if (state.busy) "연결 정보를 갱신하는 중입니다." else "연결 정보를 불러오는 중입니다.")
            }
        }
        if (account == null || state.requiresReconnect) {
            Button(onClick = onConnect, enabled = state.canConnect, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.requiresReconnect) "Microsoft 다시 로그인" else "Microsoft 로그인")
            }
        }
        if (account != null) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("MICROSOFT ACCOUNT", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(account.username.ifBlank { "Microsoft 계정" }, style = MaterialTheme.typography.titleMedium)
                    val fresh = OutlookSnapshotPolicy.isFresh(connection, Instant.now())
                    Text(when {
                        state.requiresReconnect -> "다시 로그인 필요"
                        !connection.catalogLoaded -> "캘린더 목록을 불러와 주세요."
                        connection.selectedIds.isEmpty() -> "알람에 사용할 캘린더를 선택해 주세요."
                        fresh -> "${connection.selectedIds.size}개 캘린더 연결됨"
                        else -> "일정 갱신 필요"
                    }, color = if (fresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    connection.syncedAt?.let { time ->
                        Text("마지막 갱신 ${DateTimeFormatter.ofPattern("MM.dd HH:mm").withZone(ZoneId.systemDefault()).format(time)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            connection.calendars.forEach { calendar ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = calendar.id in connection.selectedIds,
                        onCheckedChange = { onSelect(OutlookCalendarKey(account.id, calendar.id), it) },
                        enabled = !state.busy && state.configured,
                        modifier = Modifier.semantics { contentDescription = "Outlook ${calendar.name} 선택" },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(calendar.name)
                        if (calendar.isDefault) Text("기본 캘린더", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onRefresh, enabled = state.canRefresh) { Text("지금 새로고침") }
                TextButton(onClick = { confirmDisconnect = true }, enabled = !state.busy) { Text("연결 해제") }
            }
            Text("서버에서 변경한 일정은 동기화 후 반영됩니다. 마지막 갱신 후 6시간이 지나면 캘린더 오류 시 알람 설정을 따릅니다.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        (state.message ?: connection.issue?.let(::outlookIssueMessage))?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmDisconnect) AlertDialog(
        onDismissRequest = { confirmDisconnect = false },
        title = { Text("Outlook 연결을 해제할까요?") },
        text = { Text("Outlook 일정과 선택 정보가 이 기기에서 삭제됩니다. 저장한 알람과 Google 연결은 유지됩니다.") },
        confirmButton = { TextButton(onClick = { confirmDisconnect = false; onDisconnect() }) { Text("연결 해제") } },
        dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("취소") } },
    )
}
