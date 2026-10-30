package com.smartworkalarm.preview

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.LocalDate
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
    override suspend fun doWork(): Result {
        val targetDate = inputData.getString(TARGET_DATE)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: return Result.success()
        if (!PreviewDeliveryWindow.isCurrent(targetDate, clock.instant(), zoneId)) return Result.success()
        return runCatching {
            val alarmEvaluation = evaluate.executeWithStatus(targetDate, zoneId)
            val shiftEvaluation = evaluateShiftGroups.executeWithStatus(targetDate.minusDays(1), targetDate, zoneId)
            // The query itself may cross midnight; stale work must not post or acknowledge.
            if (PreviewDeliveryWindow.isCurrent(targetDate, clock.instant(), zoneId) &&
                notifications.post(alarmEvaluation, shiftEvaluation, targetDate, clock.instant(), zoneId)) {
                evaluate.acknowledge(alarmEvaluation)
            }
        }.fold({ Result.success() }, { Result.retry() })
    }

    companion object { const val TARGET_DATE = "preview_target_date" }
}
