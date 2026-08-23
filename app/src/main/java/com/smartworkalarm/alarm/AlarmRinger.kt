package com.smartworkalarm.alarm

interface AlarmRinger {
    fun start(alarmId: Long, alarmName: String, ringtoneUri: String?, gradualVolume: Boolean, snoozeMinutes: Int)
}
