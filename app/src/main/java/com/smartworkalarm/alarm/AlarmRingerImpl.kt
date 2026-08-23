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
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmRingingService::class.java).apply {
                action = AlarmRingingService.ACTION_START
                putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmRingingService.EXTRA_ALARM_NAME, alarmName)
                putExtra(AlarmRingingService.EXTRA_RINGTONE_URI, ringtoneUri)
                putExtra(AlarmRingingService.EXTRA_GRADUAL_VOLUME, gradualVolume)
                putExtra(AlarmRingingService.EXTRA_SNOOZE_MINUTES, snoozeMinutes.coerceIn(5, 30))
            },
        )
    }
}
