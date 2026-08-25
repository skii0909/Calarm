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

    fun startAt(
        alarmId: Long,
        alarmName: String,
        ringtoneUri: String?,
        gradualVolume: Boolean,
        snoozeEnabled: Boolean,
        snoozeMinutes: Int,
        scheduledOccurrenceAt: Instant,
    ) = start(alarmId, alarmName, ringtoneUri, gradualVolume, snoozeEnabled, snoozeMinutes)

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
