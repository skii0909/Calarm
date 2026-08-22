package com.smartworkalarm

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.smartworkalarm.preview.NextDayPreviewScheduler

@HiltAndroidApp
class WorkAlarmApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var previewScheduler: NextDayPreviewScheduler

    override fun onCreate() {
        super.onCreate()
        previewScheduler.scheduleNext()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
