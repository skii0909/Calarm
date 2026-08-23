package com.smartworkalarm.alarm

interface AlarmRinger {
    fun start(alarmId: Long, alarmName: String, ringtoneUri: String?, gradualVolume: Boolean, snoozeMinutes: Int)

    fun startShift(
        shiftTypeId: Long,
        alarmName: String,
        ringtoneUri: String?,
        gradualVolume: Boolean,
        snoozeMinutes: Int,
        calendarWarning: Boolean,
    ) = start(shiftTypeId, alarmName, ringtoneUri, gradualVolume, snoozeMinutes)
}
