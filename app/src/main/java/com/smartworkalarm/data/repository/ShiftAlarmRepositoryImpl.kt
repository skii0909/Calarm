package com.smartworkalarm.data.repository

import com.smartworkalarm.data.local.ShiftAlarmDao
import com.smartworkalarm.data.local.ShiftGroupWithTypes
import com.smartworkalarm.data.local.toDomain
import com.smartworkalarm.data.local.toEntity
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ShiftAlarmRepositoryImpl @Inject constructor(
    private val dao: ShiftAlarmDao,
) : ShiftAlarmRepository {
    override fun observeGroupsWithTypes(): Flow<List<ShiftAlarmRepository.GroupWithTypes>> =
        dao.observeGroupsWithTypes().map { groups ->
            groups.map(ShiftGroupWithTypes::toDomain).filterNot { it.group.isDraft }
        }

    override suspend fun getGroupWithTypes(groupId: Long): ShiftAlarmRepository.GroupWithTypes? =
        dao.getGroupWithTypes(groupId)?.toDomain()

    override suspend fun getShiftType(id: Long): ShiftTypeAlarm? = dao.getShiftType(id)?.toDomain()

    override suspend fun getEnabledShiftTypes(): List<ShiftTypeAlarm> =
        dao.getEnabledGroupsWithTypes().flatMap { group ->
            if (group.group.enabled && !group.group.isDraft) {
                group.shiftTypes.filter { it.enabled }.map { it.toDomain() }
            } else {
                emptyList()
            }
        }

    override suspend fun upsertGroup(group: ShiftAlarmGroup): Long = dao.upsertGroup(group.toEntity())

    override suspend fun upsertShiftType(shiftType: ShiftTypeAlarm): Long = dao.upsertShiftType(shiftType.toEntity())

    override suspend fun deleteGroup(groupId: Long) = dao.deleteGroup(groupId)

    override suspend fun deleteShiftType(shiftTypeId: Long) = dao.deleteShiftTypeAndClearEmergency(shiftTypeId)
}

private fun ShiftGroupWithTypes.toDomain() = ShiftAlarmRepository.GroupWithTypes(
    group = group.toDomain(),
    shiftTypes = shiftTypes.map { it.toDomain() },
)
