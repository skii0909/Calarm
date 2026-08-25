package com.smartworkalarm.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartworkalarm.permissions.RequiredPermission
import com.smartworkalarm.permissions.AlarmDiagnosticItem
import com.smartworkalarm.permissions.AlarmDiagnosticKind
import com.smartworkalarm.permissions.DiagnosticStatus
import com.smartworkalarm.presentation.components.FeaturePill
import com.smartworkalarm.presentation.components.GradientHero
import com.smartworkalarm.presentation.components.NightPulseSection

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    permissions: List<PermissionUiState>,
    onPermissionAction: (RequiredPermission) -> Unit,
    onDiagnosticAction: (AlarmDiagnosticKind) -> Unit = {},
    onSetCalendarSelected: (Long, Boolean) -> Unit,
    onSetAccountSelected: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GradientHero {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "설정과 도움말",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "권한과 캘린더를 확인하고 알람 동작을 알아보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = .88f),
                    )
                }
                TextButton(onClick = onBack) { Text("돌아가기", color = Color.White) }
            }
        }

        PermissionCard(
            permissions = permissions,
            onPermissionAction = onPermissionAction,
        )
        AlarmDiagnosticsCard(
            diagnostics = state.diagnostics,
            onAction = onDiagnosticAction,
        )
        CalendarCard(
            state = state,
            onSetCalendarSelected = onSetCalendarSelected,
            onSetAccountSelected = onSetAccountSelected,
        )
        HELP_SECTIONS.forEach { help ->
            ExpandableHelpCard(help)
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun AlarmDiagnosticsCard(
    diagnostics: List<AlarmDiagnosticItem>,
    onAction: (AlarmDiagnosticKind) -> Unit,
) {
    NightPulseSection(
        title = "알람 작동 진단",
        subtitle = "현재 휴대폰에서 알람을 방해할 수 있는 설정을 확인합니다.",
    ) {
        if (diagnostics.isEmpty()) {
            Text("진단 정보를 불러오지 못했습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        diagnostics.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.kind.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        item.status.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.status == DiagnosticStatus.ACTION_REQUIRED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    Text(
                        item.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.status == DiagnosticStatus.ACTION_REQUIRED && item.kind != AlarmDiagnosticKind.SCHEDULE) {
                    TextButton(onClick = { onAction(item.kind) }) { Text("설정하러 가기") }
                }
            }
        }
    }
}

private val AlarmDiagnosticKind.label: String
    get() = when (this) {
        AlarmDiagnosticKind.NOTIFICATIONS -> "알림 권한"
        AlarmDiagnosticKind.EXACT_ALARM -> "정확한 알람"
        AlarmDiagnosticKind.FULL_SCREEN -> "잠금화면 전체화면 알람"
        AlarmDiagnosticKind.BATTERY -> "배터리 최적화"
        AlarmDiagnosticKind.SCHEDULE -> "최근 알람 예약"
    }

private val DiagnosticStatus.label: String
    get() = when (this) {
        DiagnosticStatus.OK -> "정상"
        DiagnosticStatus.ACTION_REQUIRED -> "확인 필요"
        DiagnosticStatus.NOT_APPLICABLE -> "해당 없음"
        DiagnosticStatus.UNKNOWN -> "알 수 없음"
    }

@Composable
private fun PermissionCard(
    permissions: List<PermissionUiState>,
    onPermissionAction: (RequiredPermission) -> Unit,
) {
    NightPulseSection(
        title = "연결 및 권한",
        subtitle = "알람 실행에 필요한 권한을 한곳에서 확인합니다.",
    ) {
        if (permissions.all { it.granted }) {
            FeaturePill("알람 실행 준비 완료")
        }
        permissions.forEachIndexed { index, permission ->
            if (index > 0) HorizontalDivider()
            PermissionRow(
                permission = permission,
                onClick = { onPermissionAction(permission.permission) },
            )
        }
    }
}

@Composable
private fun PermissionRow(
    permission: PermissionUiState,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(permission.permission.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (permission.granted) "허용됨" else "필요",
                style = MaterialTheme.typography.labelMedium,
                color = if (permission.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        TextButton(onClick = onClick) {
            Text("설정")
        }
    }
}

@Composable
private fun CalendarCard(
    state: SettingsUiState,
    onSetCalendarSelected: (Long, Boolean) -> Unit,
    onSetAccountSelected: (String, Boolean) -> Unit,
) {
    val expandedAccounts = remember { mutableStateMapOf<String, Boolean>() }
    NightPulseSection(
        title = "사용 중인 캘린더",
        subtitle = "알람 판단에 사용할 Google 캘린더를 선택합니다.",
    ) {
        CatalogStatus(state.catalogStatus)
        state.accounts.forEach { account ->
            val expanded = expandedAccounts[account.key] == true
            CalendarAccount(
                account = account,
                expanded = expanded,
                onExpandedChange = { expandedAccounts[account.key] = !expanded },
                onSetCalendarSelected = onSetCalendarSelected,
                onSetAccountSelected = onSetAccountSelected,
            )
        }
        HorizontalDivider()
        Text(
            "Outlook — 준비 중",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CatalogStatus(status: CalendarCatalogStatus) {
    when (status) {
        CalendarCatalogStatus.LOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("캘린더를 불러오는 중입니다.")
        }

        CalendarCatalogStatus.PERMISSION_REQUIRED -> Text("캘린더 읽기 권한을 허용해 주세요.")
        CalendarCatalogStatus.PROVIDER_QUERY_FAILED -> Text(
            "캘린더 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
            color = MaterialTheme.colorScheme.error,
        )

        CalendarCatalogStatus.NO_GOOGLE_CALENDARS -> Text("동기화된 Google 캘린더가 없습니다.")
        CalendarCatalogStatus.ZERO_SELECTED_CALENDARS -> Text(
            "선택된 캘린더가 없어 현재 연결되지 않은 상태입니다.",
            color = MaterialTheme.colorScheme.error,
        )

        CalendarCatalogStatus.READY -> Unit
    }
}

@Composable
private fun CalendarAccount(
    account: CalendarAccountUiState,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onSetCalendarSelected: (Long, Boolean) -> Unit,
    onSetAccountSelected: (String, Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TriStateCheckbox(
                    state = when (account.selection) {
                        AccountSelection.NONE -> ToggleableState.Off
                        AccountSelection.PARTIAL -> ToggleableState.Indeterminate
                        AccountSelection.ALL -> ToggleableState.On
                    },
                    onClick = {
                        onSetAccountSelected(account.key, account.selection != AccountSelection.ALL)
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "${account.displayName} 전체 선택"
                    },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onExpandedChange)
                        .padding(vertical = 8.dp),
                ) {
                    Text(account.displayName, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${account.selectedCount}/${account.totalCount} 선택",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onExpandedChange,
                    modifier = Modifier.semantics {
                        contentDescription = "${account.displayName} ${if (expanded) "접기" else "펼치기"}"
                    },
                ) { Text(if (expanded) "−" else "+") }
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(bottom = 4.dp))
                account.calendars.forEach { calendar ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = calendar.selected,
                            onCheckedChange = { onSetCalendarSelected(calendar.id, it) },
                            modifier = Modifier.semantics {
                                contentDescription = "${calendar.displayName} ${if (calendar.selected) "선택됨" else "선택 안 됨"}"
                            },
                        )
                        Text(calendar.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private val RequiredPermission.label: String
    get() = when (this) {
        RequiredPermission.CALENDAR -> "캘린더 읽기"
        RequiredPermission.NOTIFICATIONS -> "알림"
        RequiredPermission.EXACT_ALARM -> "정확한 알람"
        RequiredPermission.FULL_SCREEN_ALARM -> "전체화면 알람"
    }
