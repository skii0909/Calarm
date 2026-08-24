package com.smartworkalarm.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object FullScreenAlarmAccess {
    fun requiresSettingsPrompt(sdkInt: Int, canUseFullScreenIntent: Boolean): Boolean =
        sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !canUseFullScreenIntent

    fun canUse(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    fun settingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
        Uri.parse("package:${context.packageName}"),
    )
}
