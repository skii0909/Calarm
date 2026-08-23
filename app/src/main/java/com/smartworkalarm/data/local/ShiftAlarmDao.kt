package com.smartworkalarm.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ShiftGroupWithTypes(
    @Embedded val group: ShiftAlarmGroupEntity,
    @Relation(parentColumn = "id", entityColumn = "group_id")
    val shiftTypes: List<ShiftTypeAlarmEntity>,
)

@Dao
interface ShiftAlarmDao {
    @Transaction
    @Query("SELECT * FROM shift_alarm_groups ORDER BY id")
    fun observeGroupsWithTypes(): Flow<List<ShiftGroupWithTypes>>

    @Transaction
    @Query("SELECT * FROM shift_alarm_groups WHERE id = :groupId")
    suspend fun getGroupWithTypes(groupId: Long): ShiftGroupWithTypes?

    @Upsert
    suspend fun upsertGroup(entity: ShiftAlarmGroupEntity): Long

    @Upsert
    suspend fun upsertShiftType(entity: ShiftTypeAlarmEntity): Long

    @Transaction
    suspend fun deleteGroup(groupId: Long) {
        deleteShiftTypesForGroup(groupId)
        deleteGroupRow(groupId)
    }

    @Query("DELETE FROM shift_type_alarms WHERE group_id = :groupId")
    suspend fun deleteShiftTypesForGroup(groupId: Long)

    @Query("DELETE FROM shift_alarm_groups WHERE id = :groupId")
    suspend fun deleteGroupRow(groupId: Long)

    @Query("DELETE FROM shift_type_alarms WHERE id = :shiftTypeId")
    suspend fun deleteShiftType(shiftTypeId: Long)
}
