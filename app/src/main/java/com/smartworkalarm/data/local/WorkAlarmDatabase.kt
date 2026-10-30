package com.smartworkalarm.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AlarmEntity::class,
        AlarmPreviewEntity::class,
        ShiftAlarmGroupEntity::class,
        ShiftTypeAlarmEntity::class,
        ShiftPreviewEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class WorkAlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun alarmPreviewDao(): AlarmPreviewDao
    abstract fun shiftAlarmDao(): ShiftAlarmDao
    abstract fun shiftPreviewDao(): ShiftPreviewDao
}
