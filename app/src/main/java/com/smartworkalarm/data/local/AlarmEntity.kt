package com.smartworkalarm.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hour: Int,
    val minute: Int,
    @ColumnInfo(name = "repeat_days_mask") val repeatDaysMask: Int,
    val enabled: Boolean,
    @ColumnInfo(name = "skip_on_vacation") val skipOnCalendarMatch: Boolean,
    @ColumnInfo(name = "calendar_keywords", defaultValue = "'휴가'") val calendarKeywords: String = "휴가",
    @ColumnInfo(name = "ringtone_uri", defaultValue = "NULL") val ringtoneUri: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
