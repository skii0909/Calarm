package com.smartworkalarm.preview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NextDayPreviewReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: NextDayPreviewScheduler

    override fun onReceive(context: Context, intent: Intent?) {
        val targetDate = intent?.getStringExtra(NextDayPreviewWorker.TARGET_DATE)
        // Rearming failure must not prevent this evening's already delivered check.
        runCatching { scheduler.scheduleNext() }
        if (targetDate == null) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<NextDayPreviewWorker>()
                .setInputData(workDataOf(NextDayPreviewWorker.TARGET_DATE to targetDate))
                .build(),
        )
    }

    companion object { const val UNIQUE_WORK = "evaluate-next-day-alarms" }
}
