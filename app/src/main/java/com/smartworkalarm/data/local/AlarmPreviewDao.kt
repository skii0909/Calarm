package com.smartworkalarm.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AlarmPreviewDao {
    @Query("SELECT * FROM alarm_preview_states WHERE alarm_id = :alarmId")
    suspend fun get(alarmId: Long): AlarmPreviewEntity?

    @Upsert
    suspend fun upsert(entity: AlarmPreviewEntity)

    @Query("DELETE FROM alarm_preview_states WHERE alarm_id = :alarmId")
    suspend fun delete(alarmId: Long)
}
