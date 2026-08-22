package com.smartworkalarm.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartworkalarm.data.local.AlarmDao
import com.smartworkalarm.data.local.AlarmPreviewDao
import com.smartworkalarm.data.local.WorkAlarmDatabase
import com.smartworkalarm.data.repository.AlarmRepositoryImpl
import com.smartworkalarm.domain.repository.AlarmRepository
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
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WorkAlarmDatabase =
        Room.databaseBuilder(context, WorkAlarmDatabase::class.java, "work-alarm.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideAlarmDao(database: WorkAlarmDatabase): AlarmDao = database.alarmDao()

    @Provides
    fun provideAlarmPreviewDao(database: WorkAlarmDatabase): AlarmPreviewDao = database.alarmPreviewDao()
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

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alarms ADD COLUMN calendar_keywords TEXT NOT NULL DEFAULT '휴가'")
        db.execSQL("ALTER TABLE alarms ADD COLUMN ringtone_uri TEXT DEFAULT NULL")
    }
}
