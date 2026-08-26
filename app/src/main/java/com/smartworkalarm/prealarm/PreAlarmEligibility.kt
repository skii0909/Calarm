package com.smartworkalarm.prealarm

import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import javax.inject.Inject

class PreAlarmEligibility @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val shiftAlarmRepository: ShiftAlarmRepository,
) {
    suspend fun alarmNameIfEligible(key: AlarmOccurrenceKey): String? = when (key.kind) {
        AlarmKind.REGULAR -> alarmRepository.getById(key.id)
            ?.takeIf { it.enabled }
            ?.name

        AlarmKind.SHIFT -> {
            val shiftType = shiftAlarmRepository.getShiftType(key.id)
                ?.takeIf { it.enabled }
                ?: return null
            val group = shiftAlarmRepository.getGroupWithTypes(shiftType.groupId)?.group
                ?: return null
            shiftType.name.takeIf { group.enabled && !group.isDraft }
        }
    }
}
