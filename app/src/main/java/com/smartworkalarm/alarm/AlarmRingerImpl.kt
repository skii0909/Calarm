package com.smartworkalarm.alarm

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

class AlarmRingerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val attemptTokenFactory: RingingAttemptTokenFactory,
) : AlarmRinger {
    override fun start(
        alarmId: Long,
        alarmName: String,
        ringtoneUri: String?,
        gradualVolume: Boolean,
        snoozeEnabled: Boolean,
        snoozeMinutes: Int,
    ) {
        startService(
            alarmId,
            alarmName,
            ringtoneUri,
            gradualVolume,
            snoozeEnabled,
            snoozeMinutes,
            isShiftAlarm = false,
            calendarWarning = false,
            scheduledOccurrenceAt = null,
        )
    }

    override fun startAt(
        alarmId: Long,
        alarmName: String,
        ringtoneUri: String?,
        gradualVolume: Boolean,
        snoozeEnabled: Boolean,
        snoozeMinutes: Int,
        scheduledOccurrenceAt: Instant,
    ) {
        startService(
            alarmId,
            alarmName,
            ringtoneUri,
            gradualVolume,
            snoozeEnabled,
            snoozeMinutes,
            isShiftAlarm = false,
            calendarWarning = false,
            scheduledOccurrenceAt = scheduledOccurrenceAt,
        )
    }

    override fun startShift(
        shiftTypeId: Long,
        alarmName: String,
        ringtoneUri: String?,
        gradualVolume: Boolean,
        snoozeEnabled: Boolean,
        snoozeMinutes: Int,
        calendarWarning: Boolean,
        scheduledOccurrenceAt: Instant,
    ) {
        startService(
            shiftTypeId,
            alarmName,
            ringtoneUri,
            gradualVolume,
            snoozeEnabled,
            snoozeMinutes,
            isShiftAlarm = true,
            calendarWarning,
            scheduledOccurrenceAt,
        )
    }

    private fun startService(
        alarmId: Long,
        alarmName: String,
        ringtoneUri: String?,
        gradualVolume: Boolean,
        snoozeEnabled: Boolean,
        snoozeMinutes: Int,
        isShiftAlarm: Boolean,
        calendarWarning: Boolean,
        scheduledOccurrenceAt: Instant?,
    ) {
        val sessionKey = RingingSessionKey.create(
            isShiftAlarm = isShiftAlarm,
            alarmId = alarmId,
            attemptToken = attemptTokenFactory.next(),
        )
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmRingingService::class.java).apply {
                action = AlarmRingingService.ACTION_START
                putRingingSessionKey(sessionKey)
                putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmRingingService.EXTRA_ALARM_NAME, alarmName)
                putExtra(AlarmRingingService.EXTRA_RINGTONE_URI, ringtoneUri)
                putExtra(AlarmRingingService.EXTRA_GRADUAL_VOLUME, gradualVolume)
                putExtra(AlarmRingingService.EXTRA_SNOOZE_ENABLED, snoozeEnabled)
                putExtra(AlarmRingingService.EXTRA_SNOOZE_MINUTES, snoozeMinutes.coerceIn(5, 30))
                putExtra(AlarmRingingService.EXTRA_IS_SHIFT_ALARM, isShiftAlarm)
                putExtra(AlarmRingingService.EXTRA_CALENDAR_WARNING, calendarWarning)
                scheduledOccurrenceAt?.let {
                    putExtra(AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT, it.toEpochMilli())
                }
            },
        )
    }
}
