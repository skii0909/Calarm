package com.smartworkalarm.data.repository

import com.smartworkalarm.data.local.ShiftAlarmDao
import com.smartworkalarm.data.local.ShiftAlarmGroupEntity
import com.smartworkalarm.data.local.ShiftGroupWithTypes
import com.smartworkalarm.data.local.ShiftTypeAlarmEntity
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShiftAlarmRepositoryImplTest {
    @Test
    fun `deleting emergency shift type clears its group emergency reference`() = runTest {
        val dao = RecordingDao(ShiftAlarmGroupEntity(10, "교대", true, emergencyShiftTypeId = 2))
        val repository = ShiftAlarmRepositoryImpl(dao)

        repository.deleteShiftType(2)

        assertNull(dao.group.emergencyShiftTypeId)
        assertEquals(listOf(2L), dao.deletedIds)
    }

    private class RecordingDao(initial: ShiftAlarmGroupEntity) : ShiftAlarmDao {
        var group = initial
        val deletedIds = mutableListOf<Long>()

        override fun observeGroupsWithTypes(): Flow<List<ShiftGroupWithTypes>> = emptyFlow()
        override suspend fun getGroupWithTypes(groupId: Long): ShiftGroupWithTypes? = null
        override suspend fun getEnabledGroupsWithTypes(): List<ShiftGroupWithTypes> = emptyList()
        override suspend fun getShiftType(shiftTypeId: Long): ShiftTypeAlarmEntity? = null
        override suspend fun upsertGroup(entity: ShiftAlarmGroupEntity): Long = entity.id
        override suspend fun upsertShiftType(entity: ShiftTypeAlarmEntity): Long = entity.id
        override suspend fun deleteGroup(groupId: Long) = Unit
        override suspend fun deleteShiftTypesForGroup(groupId: Long) = Unit
        override suspend fun deleteGroupRow(groupId: Long) = Unit
        override suspend fun clearEmergencyShiftType(shiftTypeId: Long) {
            if (group.emergencyShiftTypeId == shiftTypeId) group = group.copy(emergencyShiftTypeId = null)
        }
        override suspend fun deleteShiftTypeRow(shiftTypeId: Long) {
            deletedIds += shiftTypeId
        }
    }
}
