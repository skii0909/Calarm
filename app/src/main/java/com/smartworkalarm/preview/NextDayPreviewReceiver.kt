package com.smartworkalarm.preview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NextDayPreviewReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: NextDayPreviewScheduler

    override fun onReceive(context: Context, intent: Intent?) {
        scheduler.scheduleNext()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<NextDayPreviewWorker>().build(),
        )
    }

    companion object { const val UNIQUE_WORK = "evaluate-next-day-alarms" }
}
