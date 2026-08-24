package com.smartworkalarm.di

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import com.smartworkalarm.data.calendar.CalendarContractEventSource
import com.smartworkalarm.data.calendar.CalendarContractConnectionRepository
import com.smartworkalarm.data.calendar.SharedPreferencesCalendarSelectionRepository
import com.smartworkalarm.domain.repository.CalendarConnectionRepository
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.CalendarSelectionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarBindings {
    @Binds
    abstract fun bindCalendarConnectionRepository(
        implementation: CalendarContractConnectionRepository,
    ): CalendarConnectionRepository

    @Binds
    abstract fun bindCalendarEventSource(
        implementation: CalendarContractEventSource,
    ): CalendarEventSource

    @Binds
    abstract fun bindCalendarSelectionRepository(
        implementation: SharedPreferencesCalendarSelectionRepository,
    ): CalendarSelectionRepository
}

@Module
@InstallIn(SingletonComponent::class)
object CalendarModule {
    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    fun provideCalendarSelectionPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("calendar_selection", Context.MODE_PRIVATE)
}
