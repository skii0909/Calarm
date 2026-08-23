package com.smartworkalarm.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_preview_states")
data class ShiftPreviewEntity(
    @PrimaryKey @ColumnInfo(name = "group_id") val groupId: Long,
    @ColumnInfo(name = "state_key") val stateKey: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
