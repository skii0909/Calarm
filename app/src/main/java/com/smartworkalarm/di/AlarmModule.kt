package com.smartworkalarm.di

import android.app.AlarmManager
import android.content.Context
import com.smartworkalarm.alarm.AlarmSchedulerImpl
import com.smartworkalarm.alarm.AlarmScheduleAuditStore
import com.smartworkalarm.alarm.AlarmRinger
import com.smartworkalarm.alarm.AlarmRingerImpl
import com.smartworkalarm.permissions.AlarmDiagnosticsSource
import com.smartworkalarm.permissions.AndroidAlarmDiagnosticsSource
import com.smartworkalarm.alarm.SharedPreferencesAlarmScheduleAuditStore
import com.smartworkalarm.domain.usecase.SaveAlarmUseCase
import com.smartworkalarm.prealarm.OccurrenceSkipStore
import com.smartworkalarm.prealarm.SharedPreferencesOccurrenceSkipStore
import com.smartworkalarm.presentation.alarmedit.AlarmSaver
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.repository.ShiftAlarmScheduler
import com.smartworkalarm.domain.shift.ShiftDayEvaluator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmBindings {
    @Binds
    abstract fun bindAlarmScheduler(implementation: AlarmSchedulerImpl): AlarmScheduler

    @Binds
    abstract fun bindShiftAlarmScheduler(implementation: AlarmSchedulerImpl): ShiftAlarmScheduler

    @Binds
    abstract fun bindAlarmRinger(implementation: AlarmRingerImpl): AlarmRinger

    @Binds
    abstract fun bindAlarmSaver(implementation: SaveAlarmUseCase): AlarmSaver

    @Binds
    abstract fun bindAlarmDiagnosticsSource(implementation: AndroidAlarmDiagnosticsSource): AlarmDiagnosticsSource
}

@Module
@InstallIn(SingletonComponent::class)
object AlarmModule {
    @Provides
    @Singleton
    fun provideOccurrenceSkipStore(@ApplicationContext context: Context): OccurrenceSkipStore =
        SharedPreferencesOccurrenceSkipStore(
            preferences = context.getSharedPreferences("occurrence_skip_store", Context.MODE_PRIVATE),
        )

    @Provides
    @Singleton
    fun provideAlarmScheduleAuditStore(
        @ApplicationContext context: Context,
        clock: Clock,
    ): AlarmScheduleAuditStore = SharedPreferencesAlarmScheduleAuditStore(
        preferences = context.getSharedPreferences("alarm_schedule_audit", Context.MODE_PRIVATE),
        clock = clock,
    )

    @Provides
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    @Provides
    fun provideNotificationManager(@ApplicationContext context: Context): android.app.NotificationManager =
        context.getSystemService(android.app.NotificationManager::class.java)

    @Provides
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    fun provideClock(zoneId: ZoneId): Clock = Clock.system(zoneId)

    @Provides
    fun provideShiftDayEvaluator(): ShiftDayEvaluator = ShiftDayEvaluator()
}
