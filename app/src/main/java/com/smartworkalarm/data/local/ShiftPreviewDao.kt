package com.smartworkalarm.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ShiftPreviewDao {
    @Query("SELECT * FROM shift_preview_states WHERE group_id = :groupId")
    suspend fun get(groupId: Long): ShiftPreviewEntity?

    @Upsert
    suspend fun upsert(entity: ShiftPreviewEntity)
}
