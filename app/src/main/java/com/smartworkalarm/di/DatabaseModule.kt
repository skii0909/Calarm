package com.smartworkalarm.di

import android.content.Context
import androidx.room.Room
import com.smartworkalarm.data.local.AlarmDao
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
        Room.databaseBuilder(context, WorkAlarmDatabase::class.java, "work-alarm.db").build()

    @Provides
    fun provideAlarmDao(database: WorkAlarmDatabase): AlarmDao = database.alarmDao()
}
