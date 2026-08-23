package com.smartworkalarm.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AlarmEntity::class, AlarmPreviewEntity::class], version = 4, exportSchema = true)
abstract class WorkAlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun alarmPreviewDao(): AlarmPreviewDao
}
