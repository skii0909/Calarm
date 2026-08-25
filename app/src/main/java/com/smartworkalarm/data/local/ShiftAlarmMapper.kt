package com.smartworkalarm.data.local

import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm

fun ShiftAlarmGroup.toEntity() = ShiftAlarmGroupEntity(
    id = id,
    name = name,
    enabled = enabled,
    emergencyShiftTypeId = emergencyShiftTypeId,
    isDraft = isDraft,
)

fun ShiftAlarmGroupEntity.toDomain() = ShiftAlarmGroup(
    id = id,
    name = name,
    enabled = enabled,
    emergencyShiftTypeId = emergencyShiftTypeId,
    isDraft = isDraft,
)

fun ShiftTypeAlarm.toEntity() = ShiftTypeAlarmEntity(
    id = id,
    groupId = groupId,
    name = name,
    exactTitles = exactTitles.normalizedExactTitles().joinToString("\n"),
    hour = hour,
    minute = minute,
    repeatDaysMask = repeatDaysMask,
    enabled = enabled,
    ringtoneUri = ringtoneUri,
    gradualVolume = gradualVolume,
    snoozeEnabled = snoozeEnabled,
    snoozeMinutes = snoozeMinutes,
    skipOnPublicHoliday = skipOnPublicHoliday,
)

fun ShiftTypeAlarmEntity.toDomain() = ShiftTypeAlarm(
    id = id,
    groupId = groupId,
    name = name,
    exactTitles = exactTitles.split('\n').normalizedExactTitles(),
    hour = hour,
    minute = minute,
    repeatDaysMask = repeatDaysMask,
    enabled = enabled,
    ringtoneUri = ringtoneUri,
    gradualVolume = gradualVolume,
    snoozeEnabled = snoozeEnabled,
    snoozeMinutes = snoozeMinutes,
    skipOnPublicHoliday = skipOnPublicHoliday,
)

private fun Iterable<String>.normalizedExactTitles(): List<String> =
    asSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()
