package com.smartworkalarm

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.smartworkalarm.permissions.RequiredPermission
import com.smartworkalarm.permissions.RequiredPermissionAction
import com.smartworkalarm.permissions.RequiredPermissionsController
import com.smartworkalarm.permissions.RequiredPermissionsSessionViewModel
import com.smartworkalarm.permissions.launchPermissionSettings
import com.smartworkalarm.permissions.requiredPermissionAction
import com.smartworkalarm.presentation.navigation.WorkAlarmNavHost
import com.smartworkalarm.presentation.permissions.RequiredPermissionsPrompt
import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import dagger.hilt.android.AndroidEntryPoint
import com.smartworkalarm.permissions.alarmRequiredPermissions
import com.smartworkalarm.outlook.OutlookRuntime
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.smartworkalarm.presentation.alarmlist.CalendarRefreshController

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var outlookRuntime: OutlookRuntime
    @Inject lateinit var calendarRefresh: CalendarRefreshController
    private val permissionsSession: RequiredPermissionsSessionViewModel by viewModels()
    private lateinit var requiredPermissions: RequiredPermissionsController
    private var missingPermissions by mutableStateOf(emptyList<RequiredPermission>())
    private val requestHistory by lazy {
        getSharedPreferences(PERMISSION_REQUEST_HISTORY, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requiredPermissions = RequiredPermissionsController(this)
        refreshRequiredPermissions()
        setContent {
            val calendarPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { refreshRequiredPermissions() }
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { refreshRequiredPermissions() }

            fun handlePermissionAction(permission: RequiredPermission) {
                val manifestPermission = permission.manifestPermission
                when (
                    requiredPermissionAction(
                        permission = permission,
                        isMissing = permission in missingPermissions,
                        wasRequested = wasRequested(permission),
                        shouldShowRationale = manifestPermission?.let(::shouldShowRequestPermissionRationale) == true,
                    )
                ) {
                    RequiredPermissionAction.REQUEST_RUNTIME -> {
                        markRequested(permission)
                        when (permission) {
                            RequiredPermission.CALENDAR -> calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                            RequiredPermission.NOTIFICATIONS -> notificationPermission.launch(
                                Manifest.permission.POST_NOTIFICATIONS,
                            )
                            else -> Unit
                        }
                    }
                    RequiredPermissionAction.OPEN_APP_DETAILS -> openPermissionSettings(primary = null)
                    RequiredPermissionAction.OPEN_SPECIAL_SETTINGS -> openPermissionSettings(
                        primary = requiredPermissions.settingsIntent(permission),
                    )
                }
            }

            WorkAlarmTheme {
                WorkAlarmNavHost(
                    missingRequiredPermissions = missingPermissions,
                    onRequiredPermissionAction = ::handlePermissionAction,
                    onBatteryOptimizationAction = {
                        openPermissionSettings(requiredPermissions.batteryOptimizationIntent())
                    },
                )
                val alarmPermissions = alarmRequiredPermissions(missingPermissions)
                if (permissionsSession.state.shouldShow(alarmPermissions)) {
                    RequiredPermissionsPrompt(
                        missing = alarmPermissions,
                        onPermissionAction = ::handlePermissionAction,
                        onLater = permissionsSession::dismissPrompt,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::requiredPermissions.isInitialized) refreshRequiredPermissions()
        outlookRuntime.start()
        lifecycleScope.launch { calendarRefresh.refresh() }
        // Opening after a force-stop or interrupted restore repairs active OS reservations.
        runCatching {
            androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
                com.smartworkalarm.alarm.SystemChangeReceiver.UNIQUE_WORK_NAME,
                androidx.work.ExistingWorkPolicy.KEEP,
                androidx.work.OneTimeWorkRequestBuilder<com.smartworkalarm.worker.RescheduleAlarmsWorker>().build(),
            )
        }
    }

    private fun refreshRequiredPermissions() {
        missingPermissions = requiredPermissions.missing()
        permissionsSession.onPermissionsRefreshed(alarmRequiredPermissions(missingPermissions))
    }

    private fun openPermissionSettings(primary: android.content.Intent?) {
        launchPermissionSettings(
            primary = primary,
            appDetails = requiredPermissions.appDetailsIntent(),
            launch = ::startActivity,
        )
    }

    private fun wasRequested(permission: RequiredPermission): Boolean =
        requestHistory.getBoolean(permission.requestHistoryKey, false)

    private fun markRequested(permission: RequiredPermission) {
        requestHistory.edit().putBoolean(permission.requestHistoryKey, true).apply()
    }

    private val RequiredPermission.manifestPermission: String?
        get() = when (this) {
            RequiredPermission.CALENDAR -> Manifest.permission.READ_CALENDAR
            RequiredPermission.NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
            RequiredPermission.EXACT_ALARM,
            RequiredPermission.FULL_SCREEN_ALARM,
            -> null
        }

    private val RequiredPermission.requestHistoryKey: String
        get() = "requested_${name.lowercase()}"

    private companion object {
        const val PERMISSION_REQUEST_HISTORY = "required_permission_request_history"
    }
}
