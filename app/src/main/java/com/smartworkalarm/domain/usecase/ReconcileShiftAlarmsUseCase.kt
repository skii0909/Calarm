package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.repository.ShiftAlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class ReconcileShiftAlarmsUseCase @Inject constructor(
    private val repository: ShiftAlarmRepository,
    private val scheduler: ShiftAlarmScheduler,
    private val calculator: CalculateNextOccurrenceUseCase,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    suspend fun saveGroup(group: ShiftAlarmGroup): Long {
        val previous = if (group.id != 0L) repository.getGroupWithTypes(group.id) else null
        val id = repository.upsertGroup(group)
        val savedId = if (group.id == 0L) id else group.id
        try {
            repository.getGroupWithTypes(savedId)?.let(::reconcile)
        } catch (error: Exception) {
            restoreAfterFailure(error) {
                if (previous != null) {
                    repository.upsertGroup(previous.group)
                    reconcile(previous)
                } else {
                    val created = repository.getGroupWithTypes(savedId)
                    repository.deleteGroup(savedId)
                    attemptEvery(created?.shiftTypes.orEmpty()) { scheduler.cancelShift(it.id) }
                }
            }
            throw error
        }
        return id
    }

    suspend fun saveShiftType(shiftType: ShiftTypeAlarm): Long {
        val previous = if (shiftType.id != 0L) repository.getShiftType(shiftType.id) else null
        val group = repository.getGroupWithTypes(shiftType.groupId)?.group
        val id = repository.upsertShiftType(shiftType)
        val saved = shiftType.copy(id = if (shiftType.id == 0L) id else shiftType.id)
        try {
            reconcileType(group, saved)
        } catch (error: Exception) {
            restoreAfterFailure(error) {
                if (previous != null) {
                    repository.upsertShiftType(previous)
                    reconcileType(group, previous)
                } else {
                    repository.deleteShiftType(saved.id)
                    scheduler.cancelShift(saved.id)
                }
            }
            throw error
        }
        return id
    }

    suspend fun deleteShiftType(groupId: Long, shiftTypeId: Long) {
        repository.deleteShiftType(shiftTypeId)
        val remaining = repository.getGroupWithTypes(groupId)
        if (remaining == null) {
            scheduler.cancelShift(shiftTypeId)
            return
        }
        val complete = remaining.shiftTypes.size >= 2 &&
            remaining.group.emergencyShiftTypeId in remaining.shiftTypes.map { it.id }
        if (!complete) {
            repository.upsertGroup(remaining.group.copy(enabled = false, emergencyShiftTypeId = null))
        }
        val updated = repository.getGroupWithTypes(groupId)
        attemptEvery(listOf<() -> Unit>(
            { scheduler.cancelShift(shiftTypeId) },
            { updated?.let(::reconcile) },
        )) { it() }
    }

    suspend fun deleteGroup(groupId: Long) {
        val types = repository.getGroupWithTypes(groupId)?.shiftTypes.orEmpty()
        repository.deleteGroup(groupId)
        attemptEvery(types) { scheduler.cancelShift(it.id) }
    }

    private fun reconcile(aggregate: ShiftAlarmRepository.GroupWithTypes) {
        attemptEvery(aggregate.shiftTypes) { reconcileType(aggregate.group, it) }
    }

    private fun reconcileType(group: ShiftAlarmGroup?, type: ShiftTypeAlarm) {
        if (group?.enabled == true && !group.isDraft && type.enabled) {
            schedule(type)
            // Scheduling is committed; cleanup failure must not roll back only the DB row.
            runCatching { scheduler.cancelShiftSnooze(type.id) }
        } else scheduler.cancelShift(type.id)
    }

    private fun schedule(type: ShiftTypeAlarm) {
        calculator.execute(type.toAlarm(), clock.instant(), zoneId)?.let {
            check(scheduler.scheduleShift(type.id, it) == ScheduleResult.Scheduled) {
                "정확한 알람 권한이 없어 교대 알람을 예약하지 못했습니다. 설정에서 권한을 허용해 주세요."
            }
        }
    }

    private fun <T> attemptEvery(items: List<T>, action: (T) -> Unit) {
        var failure: Exception? = null
        items.forEach {
            try { action(it) } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (failure == null) failure = error else if (failure !== error) failure?.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private suspend fun restoreAfterFailure(error: Exception, restore: suspend () -> Unit) {
        withContext(NonCancellable) {
            try { restore() } catch (restoreError: Exception) {
                if (restoreError !== error) error.addSuppressed(restoreError)
            }
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
    snoozeEnabled = snoozeEnabled,
    snoozeMinutes = snoozeMinutes,
)
