package com.smartworkalarm

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.smartworkalarm.prealarm.PreAlarmNotificationFactory
import com.smartworkalarm.preview.NextDayPreviewScheduler
import com.smartworkalarm.outlook.OutlookRuntime

@HiltAndroidApp
class WorkAlarmApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var previewScheduler: NextDayPreviewScheduler
    @Inject lateinit var preAlarmNotificationFactory: PreAlarmNotificationFactory
    @Inject lateinit var outlookRuntime: OutlookRuntime

    override fun onCreate() {
        super.onCreate()
        initializePreAlarmChannelSafely(preAlarmNotificationFactory::ensureChannel)
        initializeOptionalAlarmStartup(previewScheduler::scheduleNext, outlookRuntime::start)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}

internal inline fun initializePreAlarmChannelSafely(initialize: () -> Unit) {
    runCatching(initialize)
}

internal fun initializeOptionalAlarmStartup(vararg initializers: () -> Unit) {
    initializers.forEach { initialize -> runCatching(initialize) }
}
