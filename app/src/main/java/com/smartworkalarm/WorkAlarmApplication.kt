package com.smartworkalarm

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.smartworkalarm.prealarm.PreAlarmNotificationFactory
import com.smartworkalarm.preview.NextDayPreviewScheduler

@HiltAndroidApp
class WorkAlarmApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var previewScheduler: NextDayPreviewScheduler
    @Inject lateinit var preAlarmNotificationFactory: PreAlarmNotificationFactory

    override fun onCreate() {
        super.onCreate()
        initializePreAlarmChannelSafely(preAlarmNotificationFactory::ensureChannel)
        previewScheduler.scheduleNext()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}

internal inline fun initializePreAlarmChannelSafely(initialize: () -> Unit) {
    runCatching(initialize)
}
