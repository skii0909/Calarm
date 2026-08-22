package com.smartworkalarm.alarm

import android.app.AlarmManager
import android.os.Build
import javax.inject.Inject

class ExactAlarmPermission @Inject constructor(
    private val alarmManager: AlarmManager,
) {
    fun isGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        alarmManager.canScheduleExactAlarms()
}
