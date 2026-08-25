package com.smartworkalarm.alarm

import java.time.Instant

interface AlarmRinger {
    fun start(
        alarmId: Long,
        alarmName: String,
        ringtoneUri: String?,
        gradualVolume: Boolean,
        snoozeEnabled: Boolean,
        snoozeMinutes: Int,
    )

    fun startShift(
        shiftTypeId: Long,
        alarmName: String,
        ringtoneUri: String?,
        gradualVolume: Boolean,
        snoozeEnabled: Boolean,
        snoozeMinutes: Int,
        calendarWarning: Boolean,
        scheduledOccurrenceAt: Instant,
    ) = start(shiftTypeId, alarmName, ringtoneUri, gradualVolume, snoozeEnabled, snoozeMinutes)
}
