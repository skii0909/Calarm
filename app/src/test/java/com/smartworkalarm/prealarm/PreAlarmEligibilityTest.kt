package com.smartworkalarm.prealarm

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreAlarmEligibilityTest {
    private val key = AlarmOccurrenceKey(
        AlarmKind.SHIFT,
        73L,
        Instant.parse("2026-08-26T00:30:00Z"),
    )
    private val shiftType = ShiftTypeAlarm(
        id = key.id,
        groupId = 10L,
        name = "주간",
        exactTitles = listOf("주간"),
        hour = 7,
        minute = 0,
        repeatDaysMask = 127,
        enabled = true,
        ringtoneUri = null,
        gradualVolume = false,
        snoozeMinutes = 5,
        skipOnPublicHoliday = false,
    )

    @Test
    fun `shift pre-alert is ineligible when owning group is disabled`() = runTest {
        val eligibility = eligibility(group(enabled = false))

        assertNull(eligibility.alarmNameIfEligible(key))
    }

    @Test
    fun `shift pre-alert is ineligible while owning group is a draft`() = runTest {
        val eligibility = eligibility(group(enabled = true, isDraft = true))

        assertNull(eligibility.alarmNameIfEligible(key))
    }

    @Test
    fun `enabled shift type in enabled completed group exposes its name`() = runTest {
        val eligibility = eligibility(group(enabled = true, isDraft = false))

        assertEquals("주간", eligibility.alarmNameIfEligible(key))
    }

    private fun eligibility(group: ShiftAlarmGroup) = PreAlarmEligibility(
        alarmRepository = EmptyAlarmRepository,
        shiftAlarmRepository = ShiftRepository(group, shiftType),
    )

    private fun group(enabled: Boolean, isDraft: Boolean = false) = ShiftAlarmGroup(
        id = 10L,
        name = "교대",
        enabled = enabled,
        emergencyShiftTypeId = shiftType.id,
        isDraft = isDraft,
    )

    private object EmptyAlarmRepository : AlarmRepository {
        override fun observeAll(): Flow<List<Alarm>> = flowOf(emptyList())
        override suspend fun getById(id: Long): Alarm? = null
        override suspend fun getEnabled(): List<Alarm> = emptyList()
        override suspend fun upsert(alarm: Alarm): Long = alarm.id
        override suspend fun delete(id: Long) = Unit
        override suspend fun setEnabled(id: Long, enabled: Boolean) = Unit
    }

    private class ShiftRepository(
        private val group: ShiftAlarmGroup,
        private val shiftType: ShiftTypeAlarm,
    ) : ShiftAlarmRepository {
        override fun observeGroupsWithTypes(): Flow<List<ShiftAlarmRepository.GroupWithTypes>> =
            flowOf(listOf(ShiftAlarmRepository.GroupWithTypes(group, listOf(shiftType))))

        override suspend fun getGroupWithTypes(groupId: Long): ShiftAlarmRepository.GroupWithTypes? =
            ShiftAlarmRepository.GroupWithTypes(group, listOf(shiftType)).takeIf { group.id == groupId }

        override suspend fun getShiftType(id: Long): ShiftTypeAlarm? = shiftType.takeIf { it.id == id }
        override suspend fun getEnabledShiftTypes(): List<ShiftTypeAlarm> = listOf(shiftType)
        override suspend fun upsertGroup(group: ShiftAlarmGroup): Long = group.id
        override suspend fun upsertShiftType(shiftType: ShiftTypeAlarm): Long = shiftType.id
        override suspend fun deleteGroup(groupId: Long) = Unit
        override suspend fun deleteShiftType(shiftTypeId: Long) = Unit
    }
}
