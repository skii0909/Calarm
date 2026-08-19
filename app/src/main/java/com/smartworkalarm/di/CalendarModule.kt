package com.smartworkalarm.di

import android.content.ContentResolver
import android.content.Context
import com.smartworkalarm.data.calendar.CalendarContractEventSource
import com.smartworkalarm.domain.repository.CalendarEventSource
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
    abstract fun bindCalendarEventSource(
        implementation: CalendarContractEventSource,
    ): CalendarEventSource
}

@Module
@InstallIn(SingletonComponent::class)
object CalendarModule {
    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver
}
