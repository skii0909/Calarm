package com.smartworkalarm.presentation.settings

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smartworkalarm.permissions.RequiredPermission
import com.smartworkalarm.permissions.AlarmDiagnosticKind

data class PermissionUiState(
    val permission: RequiredPermission,
    val granted: Boolean,
)

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    missingRequiredPermissions: List<RequiredPermission>,
    onPermissionAction: (RequiredPermission) -> Unit,
    onBatteryOptimizationAction: () -> Unit,
    onCalendarConnectionChanged: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()
    val permissions = permissionUiStates(Build.VERSION.SDK_INT, missingRequiredPermissions)
    val snackbarHostState = remember { SnackbarHostState() }

    fun refresh() {
        viewModel.refresh()
        onCalendarConnectionChanged()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        SettingsScreen(
            state = state,
            permissions = permissions,
            onPermissionAction = onPermissionAction,
            onDiagnosticAction = { kind ->
                when (kind) {
                    AlarmDiagnosticKind.NOTIFICATIONS -> onPermissionAction(RequiredPermission.NOTIFICATIONS)
                    AlarmDiagnosticKind.EXACT_ALARM -> onPermissionAction(RequiredPermission.EXACT_ALARM)
                    AlarmDiagnosticKind.FULL_SCREEN -> onPermissionAction(RequiredPermission.FULL_SCREEN_ALARM)
                    AlarmDiagnosticKind.BATTERY -> onBatteryOptimizationAction()
                    AlarmDiagnosticKind.SCHEDULE -> Unit
                }
            },
            onSetCalendarSelected = { calendarId, selected ->
                viewModel.setCalendarSelected(calendarId, selected)
                onCalendarConnectionChanged()
            },
            onSetAccountSelected = { accountKey, selected ->
                viewModel.setAccountSelected(accountKey, selected)
                onCalendarConnectionChanged()
            },
            onBack = onBack,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

internal fun permissionUiStates(
    sdkInt: Int,
    missing: List<RequiredPermission>,
): List<PermissionUiState> = buildList {
    add(PermissionUiState(RequiredPermission.CALENDAR, RequiredPermission.CALENDAR !in missing))
    if (sdkInt >= 33) {
        add(PermissionUiState(RequiredPermission.NOTIFICATIONS, RequiredPermission.NOTIFICATIONS !in missing))
    }
    if (sdkInt >= 31) {
        add(PermissionUiState(RequiredPermission.EXACT_ALARM, RequiredPermission.EXACT_ALARM !in missing))
    }
    if (sdkInt >= 34) {
        add(PermissionUiState(RequiredPermission.FULL_SCREEN_ALARM, RequiredPermission.FULL_SCREEN_ALARM !in missing))
    }
}
