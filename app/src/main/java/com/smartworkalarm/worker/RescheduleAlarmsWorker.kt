package com.smartworkalarm.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import com.smartworkalarm.preview.NextDayPreviewScheduler

@HiltWorker
class RescheduleAlarmsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val useCase: RescheduleAlarmsUseCase,
    private val clock: Clock,
    private val previewScheduler: NextDayPreviewScheduler,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        useCase.execute(clock.instant())
        previewScheduler.scheduleNext()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )
}
