package com.smartworkalarm.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AlarmEntity::class], version = 2, exportSchema = true)
abstract class WorkAlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
}
