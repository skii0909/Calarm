package com.smartworkalarm.preview

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.ZoneId

@HiltWorker
class NextDayPreviewWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val evaluate: EvaluateNextDayAlarmsUseCase,
    private val notifications: NextDayPreviewNotificationFactory,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        val tomorrow = clock.instant().atZone(zoneId).toLocalDate().plusDays(1)
        notifications.post(evaluate.execute(tomorrow, zoneId))
    }.fold({ Result.success() }, { Result.retry() })
}
