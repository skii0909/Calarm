package com.smartworkalarm.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shift_type_alarms",
    indices = [Index(value = ["group_id"])],
)
data class ShiftTypeAlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "group_id") val groupId: Long,
    val name: String,
    @ColumnInfo(name = "exact_titles") val exactTitles: String,
    val hour: Int,
    val minute: Int,
    @ColumnInfo(name = "repeat_days_mask") val repeatDaysMask: Int,
    val enabled: Boolean,
    @ColumnInfo(name = "ringtone_uri") val ringtoneUri: String?,
    @ColumnInfo(name = "gradual_volume") val gradualVolume: Boolean,
    @ColumnInfo(name = "snooze_enabled", defaultValue = "1") val snoozeEnabled: Boolean = true,
    @ColumnInfo(name = "snooze_minutes") val snoozeMinutes: Int,
    @ColumnInfo(name = "skip_on_public_holiday") val skipOnPublicHoliday: Boolean,
)
