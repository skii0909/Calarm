package com.smartworkalarm.prealarm

import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.shift.EvaluateShiftGroupDayUseCase
import com.smartworkalarm.domain.shift.ShiftGroupDayStatus
import java.time.ZoneId
import javax.inject.Inject

class PreAlarmEligibility @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val shiftAlarmRepository: ShiftAlarmRepository,
    private val calendarEventSource: CalendarEventSource,
    private val evaluateShiftGroupDay: EvaluateShiftGroupDayUseCase,
    private val zoneId: ZoneId,
) {
    suspend fun alarmNameIfEligible(key: AlarmOccurrenceKey): String? = when (key.kind) {
        AlarmKind.REGULAR -> alarmRepository.getById(key.id)
            ?.takeIf { it.enabled }
            ?.name

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
