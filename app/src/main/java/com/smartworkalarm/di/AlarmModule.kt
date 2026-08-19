package com.smartworkalarm.di

import android.app.AlarmManager
import android.content.Context
import com.smartworkalarm.alarm.AlarmSchedulerImpl
import com.smartworkalarm.domain.repository.AlarmScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmBindings {
    @Binds
    abstract fun bindAlarmScheduler(implementation: AlarmSchedulerImpl): AlarmScheduler
}

@Module
@InstallIn(SingletonComponent::class)
object AlarmModule {
    @Provides
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    @Provides
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    fun provideClock(zoneId: ZoneId): Clock = Clock.system(zoneId)
}
