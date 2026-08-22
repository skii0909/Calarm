package com.smartworkalarm.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm_preview_states")
data class AlarmPreviewEntity(
    @PrimaryKey @ColumnInfo(name = "alarm_id") val alarmId: Long,
    val decision: String,
    @ColumnInfo(name = "target_date") val targetDate: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
