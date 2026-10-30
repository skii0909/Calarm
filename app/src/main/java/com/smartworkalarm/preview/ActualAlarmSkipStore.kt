package com.smartworkalarm.preview

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import javax.inject.Singleton

interface ActualAlarmSkipStore {
    fun recordSkipped(alarmId: Long, date: LocalDate)
    fun latestSkippedDate(alarmId: Long): LocalDate?
    fun delete(alarmId: Long)
}

object NoActualAlarmSkipStore : ActualAlarmSkipStore {
    override fun recordSkipped(alarmId: Long, date: LocalDate) = Unit
    override fun latestSkippedDate(alarmId: Long): LocalDate? = null
    override fun delete(alarmId: Long) = Unit
}

private class PreferencesActualAlarmSkipStore(context: Context) : ActualAlarmSkipStore {
    private val preferences = context.getSharedPreferences("actual_alarm_skips", Context.MODE_PRIVATE)
    override fun recordSkipped(alarmId: Long, date: LocalDate) {
        preferences.edit().putString(alarmId.toString(), date.toString()).apply()
    }
    override fun latestSkippedDate(alarmId: Long): LocalDate? =
        preferences.getString(alarmId.toString(), null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    override fun delete(alarmId: Long) {
        preferences.edit().remove(alarmId.toString()).apply()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ActualAlarmSkipModule {
    @Provides
    @Singleton
    fun provideActualAlarmSkipStore(@ApplicationContext context: Context): ActualAlarmSkipStore =
        PreferencesActualAlarmSkipStore(context)
}
