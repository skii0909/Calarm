package com.smartworkalarm.outlook

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WorkManagerOutlookScheduler(
    private val context: Context,
    private val store: OutlookStore,
    private val clock: Clock,
) : OutlookSyncScheduler {
    private val work get() = WorkManager.getInstance(context)
    private val constraints get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun ensureScheduled() {
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<OutlookSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }

    override fun requestSync() {
        val delay = store.state.value.retryAt?.let { Duration.between(clock.instant(), it).seconds.coerceAtLeast(0) } ?: 0
        work.enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<OutlookSyncWorker>().setConstraints(constraints)
                .setInitialDelay(delay, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }

    override fun cancel() { work.cancelUniqueWork(PERIODIC); work.cancelUniqueWork(IMMEDIATE) }
    private companion object {
        const val PERIODIC = "outlook-periodic-sync"
        const val IMMEDIATE = "outlook-requested-sync"
    }
}

@HiltWorker
class OutlookSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: OutlookSyncCoordinator,
    private val alarms: OutlookAlarmReconciler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val result = sync.sync()
        if (result == OutlookSyncResult.SUCCESS || result == OutlookSyncResult.ACTION_REQUIRED) {
            alarms.execute()
        }
        when (result) {
            OutlookSyncResult.RETRY, OutlookSyncResult.BUSY, OutlookSyncResult.SUPERSEDED ->
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            else -> Result.success()
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

class OutlookRuntime(private val store: OutlookStore, private val scheduler: OutlookSyncScheduler) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun start() { scope.launch {
        try {
            if (store.awaitLoaded().account != null) { scheduler.ensureScheduled(); scheduler.requestSync() }
        } catch (error: Exception) { if (error is CancellationException) throw error }
    } }
}
