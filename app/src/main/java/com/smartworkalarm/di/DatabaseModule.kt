package com.smartworkalarm.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartworkalarm.data.local.AlarmDao
import com.smartworkalarm.data.local.AlarmPreviewDao
import com.smartworkalarm.data.local.ShiftAlarmDao
import com.smartworkalarm.data.local.ShiftPreviewDao
import com.smartworkalarm.data.local.WorkAlarmDatabase
import com.smartworkalarm.data.repository.AlarmRepositoryImpl
import com.smartworkalarm.data.repository.ShiftAlarmRepositoryImpl
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindings {
    @Binds
    abstract fun bindAlarmRepository(implementation: AlarmRepositoryImpl): AlarmRepository

    @Binds
    abstract fun bindShiftAlarmRepository(implementation: ShiftAlarmRepositoryImpl): ShiftAlarmRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WorkAlarmDatabase =
        Room.databaseBuilder(context, WorkAlarmDatabase::class.java, "work-alarm.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    @Provides
    fun provideAlarmDao(database: WorkAlarmDatabase): AlarmDao = database.alarmDao()

    @Provides
    fun provideAlarmPreviewDao(database: WorkAlarmDatabase): AlarmPreviewDao = database.alarmPreviewDao()

    @Provides
    fun provideShiftAlarmDao(database: WorkAlarmDatabase): ShiftAlarmDao = database.shiftAlarmDao()

    @Provides
    fun provideShiftPreviewDao(database: WorkAlarmDatabase): ShiftPreviewDao = database.shiftPreviewDao()
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS shift_alarm_groups (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "enabled INTEGER NOT NULL, " +
                "emergency_shift_type_id INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS shift_type_alarms (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "group_id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "exact_titles TEXT NOT NULL, " +
                "hour INTEGER NOT NULL, " +
                "minute INTEGER NOT NULL, " +
                "repeat_days_mask INTEGER NOT NULL, " +
                "enabled INTEGER NOT NULL, " +
                "ringtone_uri TEXT, " +
                "gradual_volume INTEGER NOT NULL, " +
                "snooze_minutes INTEGER NOT NULL, " +
                "skip_on_public_holiday INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shift_type_alarms_group_id ON shift_type_alarms (group_id)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS shift_preview_states (" +
                "group_id INTEGER NOT NULL PRIMARY KEY, " +
                "state_key TEXT NOT NULL, " +
                "updated_at INTEGER NOT NULL)",
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS alarm_preview_states (" +
                "alarm_id INTEGER NOT NULL PRIMARY KEY, " +
                "decision TEXT NOT NULL, " +
                "target_date TEXT NOT NULL, " +
                "updated_at INTEGER NOT NULL)",
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alarms ADD COLUMN skip_on_public_holiday INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE alarms ADD COLUMN gradual_volume INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE alarms ADD COLUMN snooze_minutes INTEGER NOT NULL DEFAULT 5")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alarms ADD COLUMN calendar_keywords TEXT NOT NULL DEFAULT '휴가'")
        db.execSQL("ALTER TABLE alarms ADD COLUMN ringtone_uri TEXT DEFAULT NULL")
    }
}
