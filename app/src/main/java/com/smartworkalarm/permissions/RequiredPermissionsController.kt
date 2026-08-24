package com.smartworkalarm.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
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

        RequiredPermission.EXACT_ALARM -> resolvedOrAppDetails(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri),
        )

        RequiredPermission.FULL_SCREEN_ALARM -> resolvedOrAppDetails(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri),
        )
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun resolvedOrAppDetails(intent: Intent): Intent =
        if (intent.resolveActivity(context.packageManager) != null) intent else appDetailsIntent()

    private fun appDetailsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        packageUri,
    )

    private val packageUri: Uri
        get() = Uri.parse("package:${context.packageName}")
}
