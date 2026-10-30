package com.smartworkalarm.prealarm

import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.shift.EvaluateShiftGroupDayUseCase
import com.smartworkalarm.domain.shift.ShiftGroupDayStatus
import com.smartworkalarm.domain.model.AlarmExecutionDecision
import com.smartworkalarm.domain.usecase.ShouldRingAlarmUseCase
import java.time.ZoneId
import javax.inject.Inject

class PreAlarmEligibility @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val shiftAlarmRepository: ShiftAlarmRepository,
    private val calendarEventSource: CalendarEventSource,
    private val shouldRingAlarm: ShouldRingAlarmUseCase,
    private val evaluateShiftGroupDay: EvaluateShiftGroupDayUseCase,
    private val zoneId: ZoneId,
) {
    suspend fun alarmNameIfEligible(key: AlarmOccurrenceKey): String? = when (key.kind) {
        AlarmKind.REGULAR -> {
            val alarm = alarmRepository.getById(key.id)
                ?.takeIf { it.enabled }
                ?: return null
            val date = key.scheduledAt.atZone(zoneId).toLocalDate()
            when (shouldRingAlarm.execute(alarm, date, zoneId)) {
                AlarmExecutionDecision.RING,
                AlarmExecutionDecision.RING_WITH_CALENDAR_WARNING,
                -> alarm.name

                AlarmExecutionDecision.SKIP_FOR_VACATION,
                AlarmExecutionDecision.DO_NOT_RING,
                -> null
            }
        }

        AlarmKind.SHIFT -> {
            val shiftType = shiftAlarmRepository.getShiftType(key.id)
                ?.takeIf { it.enabled }
                ?: return null
            val aggregate = shiftAlarmRepository.getGroupWithTypes(shiftType.groupId)
                ?: return null
            if (aggregate.group.isDraft) return null

            val date = key.scheduledAt.atZone(zoneId).toLocalDate()
            val events = calendarEventSource.getEventsOverlapping(
                date.atStartOfDay(zoneId).toInstant(),
                date.plusDays(1).atStartOfDay(zoneId).toInstant(),
            )
            when (val status = evaluateShiftGroupDay.execute(aggregate, date, events)) {
                is ShiftGroupDayStatus.Matched -> status.shiftType.name.takeIf { status.shiftType.id == key.id }
                is ShiftGroupDayStatus.ReadFailure -> status.emergencyType?.name.takeIf {
                    status.emergencyType?.id == key.id
                }
                else -> null
            }
        }
    }
}
