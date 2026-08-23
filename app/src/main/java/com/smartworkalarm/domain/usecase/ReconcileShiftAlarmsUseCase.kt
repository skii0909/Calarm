package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.repository.ShiftAlarmScheduler
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

class ReconcileShiftAlarmsUseCase @Inject constructor(
    private val repository: ShiftAlarmRepository,
    private val scheduler: ShiftAlarmScheduler,
    private val calculator: CalculateNextOccurrenceUseCase,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    suspend fun saveGroup(group: ShiftAlarmGroup): Long {
        val id = repository.upsertGroup(group)
        repository.getGroupWithTypes(if (group.id == 0L) id else group.id)?.let(::reconcile)
        return id
    }

    suspend fun saveShiftType(shiftType: ShiftTypeAlarm): Long {
        if (shiftType.id != 0L) scheduler.cancelShift(shiftType.id)
        val id = repository.upsertShiftType(shiftType)
        val saved = shiftType.copy(id = if (shiftType.id == 0L) id else shiftType.id)
        val group = repository.getGroupWithTypes(saved.groupId)?.group
        if (group?.enabled == true && saved.enabled) schedule(saved)
        return id
    }

    suspend fun deleteShiftType(groupId: Long, shiftTypeId: Long) {
        scheduler.cancelShift(shiftTypeId)
        repository.deleteShiftType(shiftTypeId)
        val remaining = repository.getGroupWithTypes(groupId) ?: return
        val complete = remaining.shiftTypes.size >= 2 &&
            remaining.group.emergencyShiftTypeId in remaining.shiftTypes.map { it.id }
        if (!complete) {
            repository.upsertGroup(remaining.group.copy(enabled = false, emergencyShiftTypeId = null))
        }
        repository.getGroupWithTypes(groupId)?.let(::reconcile)
    }

    suspend fun deleteGroup(groupId: Long) {
        repository.getGroupWithTypes(groupId)?.shiftTypes?.forEach { scheduler.cancelShift(it.id) }
        repository.deleteGroup(groupId)
    }

    private fun reconcile(aggregate: ShiftAlarmRepository.GroupWithTypes) {
        aggregate.shiftTypes.forEach { type ->
            scheduler.cancelShift(type.id)
            if (aggregate.group.enabled && type.enabled) schedule(type)
        }
    }

    private fun schedule(type: ShiftTypeAlarm) {
        calculator.execute(type.toAlarm(), clock.instant(), zoneId)?.let {
            scheduler.scheduleShift(type.id, it)
        }
    }
}

fun ShiftTypeAlarm.toAlarm() = Alarm(
    id = id,
    name = name,
    hour = hour,
    minute = minute,
    repeatDaysMask = repeatDaysMask,
    enabled = enabled,
    skipOnCalendarMatch = false,
    ringtoneUri = ringtoneUri,
    skipOnPublicHoliday = skipOnPublicHoliday,
    gradualVolume = gradualVolume,
    snoozeMinutes = snoozeMinutes,
)
