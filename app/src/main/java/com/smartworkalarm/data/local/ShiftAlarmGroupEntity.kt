package com.smartworkalarm.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_alarm_groups")
data class ShiftAlarmGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    @ColumnInfo(name = "emergency_shift_type_id") val emergencyShiftTypeId: Long? = null,
)
