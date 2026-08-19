package com.smartworkalarm.alarm

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AlarmRingerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AlarmRinger {
    override fun start(alarmId: Long, alarmName: String) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmRingingService::class.java).apply {
                action = AlarmRingingService.ACTION_START
                putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmRingingService.EXTRA_ALARM_NAME, alarmName)
            },
        )
    }
}
