package com.smartworkalarm

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.smartworkalarm.permissions.RequiredPermission
import com.smartworkalarm.permissions.RequiredPermissionsController
import com.smartworkalarm.presentation.navigation.WorkAlarmNavHost
import com.smartworkalarm.presentation.permissions.RequiredPermissionsPrompt
import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var requiredPermissions: RequiredPermissionsController
    private var missingPermissions by mutableStateOf(emptyList<RequiredPermission>())
    private var permissionsPromptDismissed by mutableStateOf(false)

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
            WorkAlarmTheme {
                WorkAlarmNavHost(requiredPermissionsMissing = missingPermissions.isNotEmpty())
                if (!permissionsPromptDismissed) {
                    RequiredPermissionsPrompt(
                        missing = missingPermissions,
                        onRequestRuntime = { permission ->
                            when (permission) {
                                RequiredPermission.CALENDAR -> calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                                RequiredPermission.NOTIFICATIONS -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else -> Unit
                            }
                        },
                        onOpenSettings = { permission ->
                            requiredPermissions.settingsIntent(permission)?.let(::startActivity)
                        },
                        onLater = { permissionsPromptDismissed = true },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::requiredPermissions.isInitialized) refreshRequiredPermissions()
    }

    private fun refreshRequiredPermissions() {
        missingPermissions = requiredPermissions.missing()
        if (missingPermissions.isEmpty()) permissionsPromptDismissed = false
    }
}
