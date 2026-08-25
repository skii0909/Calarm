package com.smartworkalarm.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

class RequiredPermissionsController(
    private val context: Context,
) {
    fun missing(): List<RequiredPermission> = RequiredPermission.missingFor(
        sdkInt = Build.VERSION.SDK_INT,
        calendarGranted = isGranted(Manifest.permission.READ_CALENDAR),
        notificationsGranted = isGranted(Manifest.permission.POST_NOTIFICATIONS),
        exactAlarmGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        fullScreenAlarmGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
    )

    fun settingsIntent(permission: RequiredPermission): Intent? = when (permission) {
        RequiredPermission.CALENDAR,
        RequiredPermission.NOTIFICATIONS,
        -> null

        RequiredPermission.EXACT_ALARM -> Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            packageUri,
        )

        RequiredPermission.FULL_SCREEN_ALARM -> Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            packageUri,
        )
    }

    fun appDetailsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        packageUri,
    )

    fun batteryOptimizationIntent(): Intent = Intent(
        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
    )

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private val packageUri: Uri
        get() = Uri.parse("package:${context.packageName}")
}

internal fun <T> launchPermissionSettings(
    primary: T?,
    appDetails: T,
    launch: (T) -> Unit,
) {
    if (primary == null) {
        launch(appDetails)
        return
    }
    try {
        launch(primary)
    } catch (_: ActivityNotFoundException) {
        launch(appDetails)
    } catch (_: RuntimeException) {
        launch(appDetails)
    }
}
