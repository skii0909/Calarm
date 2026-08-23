package com.smartworkalarm.domain.repository

import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import kotlinx.coroutines.flow.Flow

interface ShiftAlarmRepository {
    data class GroupWithTypes(
        val group: ShiftAlarmGroup,
        val shiftTypes: List<ShiftTypeAlarm>,
    )

    fun observeGroupsWithTypes(): Flow<List<GroupWithTypes>>
    suspend fun getGroupWithTypes(groupId: Long): GroupWithTypes?
    suspend fun getShiftType(id: Long): ShiftTypeAlarm?
    suspend fun getEnabledShiftTypes(): List<ShiftTypeAlarm>
    suspend fun upsertGroup(group: ShiftAlarmGroup): Long
    suspend fun upsertShiftType(shiftType: ShiftTypeAlarm): Long
    suspend fun deleteGroup(groupId: Long)
    suspend fun deleteShiftType(shiftTypeId: Long)
}
