package com.smartworkalarm.data.local

import com.smartworkalarm.domain.model.Alarm

fun Alarm.toEntity(createdAt: Long) = AlarmEntity(
    id = id,
    name = name,
    hour = hour,
    minute = minute,
    repeatDaysMask = repeatDaysMask,
    enabled = enabled,
    skipOnVacation = skipOnVacation,
    createdAt = createdAt,
)

fun AlarmEntity.toDomain() = Alarm(
    id = id,
    name = name,
    hour = hour,
    minute = minute,
    repeatDaysMask = repeatDaysMask,
    enabled = enabled,
    skipOnVacation = skipOnVacation,
)
