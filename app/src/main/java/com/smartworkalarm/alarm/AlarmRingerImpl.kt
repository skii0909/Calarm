package com.smartworkalarm.alarm

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AlarmRingerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AlarmRinger {
    override fun start(alarmId: Long, alarmName: String, ringtoneUri: String?, gradualVolume: Boolean, snoozeMinutes: Int) {
        startService(alarmId, alarmName, ringtoneUri, gradualVolume, snoozeMinutes, isShiftAlarm = false, calendarWarning = false)
    }

    override fun startShift(
        shiftTypeId: Long,
        alarmName: String,
        ringtoneUri: String?,
        gradualVolume: Boolean,
        snoozeMinutes: Int,
        calendarWarning: Boolean,
    ) {
        startService(shiftTypeId, alarmName, ringtoneUri, gradualVolume, snoozeMinutes, isShiftAlarm = true, calendarWarning)
    }

    private fun startService(
        alarmId: Long,
        alarmName: String,
        ringtoneUri: String?,
        gradualVolume: Boolean,
        snoozeMinutes: Int,
        isShiftAlarm: Boolean,
        calendarWarning: Boolean,
    ) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmRingingService::class.java).apply {
                action = AlarmRingingService.ACTION_START
                putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmRingingService.EXTRA_ALARM_NAME, alarmName)
                putExtra(AlarmRingingService.EXTRA_RINGTONE_URI, ringtoneUri)
                putExtra(AlarmRingingService.EXTRA_GRADUAL_VOLUME, gradualVolume)
                putExtra(AlarmRingingService.EXTRA_SNOOZE_MINUTES, snoozeMinutes.coerceIn(5, 30))
                putExtra(AlarmRingingService.EXTRA_IS_SHIFT_ALARM, isShiftAlarm)
                putExtra(AlarmRingingService.EXTRA_CALENDAR_WARNING, calendarWarning)
            },
        )
    }
}
