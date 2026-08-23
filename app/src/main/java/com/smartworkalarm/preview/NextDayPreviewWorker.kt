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
    private val evaluateShiftGroups: EvaluateNextDayShiftGroupsUseCase,
    private val notifications: NextDayPreviewNotificationFactory,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        val today = clock.instant().atZone(zoneId).toLocalDate()
        val tomorrow = today.plusDays(1)
        notifications.post(
            alarmChanges = evaluate.execute(tomorrow, zoneId),
            shiftChanges = evaluateShiftGroups.execute(today, tomorrow, zoneId),
        )
    }.fold({ Result.success() }, { Result.retry() })
}
