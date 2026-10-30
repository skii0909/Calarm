package com.smartworkalarm.preview

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.smartworkalarm.domain.usecase.CalculateNextPreviewTimeUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

class NextDayPreviewScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val calculate: CalculateNextPreviewTimeUseCase,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    fun scheduleNext() {
        val target = calculate.execute(clock.instant(), zoneId)
        val at = target.toEpochMilli()
        val operation = PendingIntent.getBroadcast(
            context, REQUEST_CODE, Intent(context, NextDayPreviewReceiver::class.java)
                .putExtra(NextDayPreviewWorker.TARGET_DATE, target.atZone(zoneId).toLocalDate().plusDays(1).toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    companion object { private const val REQUEST_CODE = 2200 }
}
