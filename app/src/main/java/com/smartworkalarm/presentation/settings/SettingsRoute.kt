package com.smartworkalarm.presentation.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smartworkalarm.permissions.RequiredPermission
import com.smartworkalarm.permissions.RequiredPermissionsController

data class PermissionUiState(
    val permission: RequiredPermission,
    val granted: Boolean,
)

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) { RequiredPermissionsController(context) }
    val state by viewModel.state.collectAsState()
    var permissions by remember {
        mutableStateOf(permissionUiStates(Build.VERSION.SDK_INT, controller.missing()))
    }
    val snackbarHostState = remember { SnackbarHostState() }

    fun refresh() {
        permissions = permissionUiStates(Build.VERSION.SDK_INT, controller.missing())
        viewModel.refresh()
    }

    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh() }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh() }

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
            onRequestRuntimePermission = { permission ->
                when (permission) {
                    RequiredPermission.CALENDAR -> calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                    RequiredPermission.NOTIFICATIONS -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> Unit
                }
            },
            onOpenPermissionSettings = { permission ->
                val appDetails = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                )
                val intent = controller.settingsIntent(permission) ?: appDetails
                runCatching { context.startActivity(intent) }
                    .getOrElse { context.startActivity(appDetails) }
            },
            onSetCalendarSelected = viewModel::setCalendarSelected,
            onSetAccountSelected = viewModel::setAccountSelected,
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

internal val RequiredPermission.isRuntimePermission: Boolean
    get() = this == RequiredPermission.CALENDAR || this == RequiredPermission.NOTIFICATIONS
