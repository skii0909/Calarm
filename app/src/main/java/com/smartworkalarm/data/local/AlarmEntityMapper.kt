package com.smartworkalarm.data.local

import com.smartworkalarm.domain.model.Alarm

fun Alarm.toEntity(createdAt: Long) = AlarmEntity(
    id = id,
    name = name,
    hour = hour,
    minute = minute,
    repeatDaysMask = repeatDaysMask,
    enabled = enabled,
    skipOnCalendarMatch = skipOnCalendarMatch,
    calendarKeywords = calendarKeywords.joinToString(","),
    ringtoneUri = ringtoneUri,
    skipOnPublicHoliday = skipOnPublicHoliday,
    gradualVolume = gradualVolume,
    snoozeEnabled = snoozeEnabled,
    snoozeMinutes = snoozeMinutes.coerceIn(5, 30),
    createdAt = createdAt,
)

fun AlarmEntity.toDomain() = Alarm(
    id = id,
    name = name,
    hour = hour,
    minute = minute,
    repeatDaysMask = repeatDaysMask,
    enabled = enabled,
    skipOnCalendarMatch = skipOnCalendarMatch,
    calendarKeywords = calendarKeywords.split(',').map(String::trim).filter(String::isNotEmpty),
    ringtoneUri = ringtoneUri,
    skipOnPublicHoliday = skipOnPublicHoliday,
    gradualVolume = gradualVolume,
    snoozeEnabled = snoozeEnabled,
    snoozeMinutes = snoozeMinutes.coerceIn(5, 30),
)
