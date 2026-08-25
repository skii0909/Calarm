package com.smartworkalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AlarmBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: AlarmExecutionCoordinator
    @Inject lateinit var shiftCoordinator: ShiftAlarmExecutionCoordinator
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val isShift = intent.action == AlarmSchedulerImpl.ACTION_SHIFT_ALARM
        val alarmId = intent.getLongExtra(
            if (isShift) AlarmSchedulerImpl.EXTRA_SHIFT_TYPE_ID else AlarmSchedulerImpl.EXTRA_ALARM_ID,
            -1L,
        )
        if (alarmId < 0 || (!isShift && intent.action != AlarmSchedulerImpl.ACTION_ALARM)) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = clock.instant()
                val scheduledOccurrenceAt = intent.getLongExtra(
                    AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT,
                    Long.MIN_VALUE,
                ).takeIf { it != Long.MIN_VALUE }?.let(Instant::ofEpochMilli) ?: now
                if (isShift) {
                    shiftCoordinator.execute(
                        alarmId,
                        intent.getBooleanExtra(AlarmSchedulerImpl.EXTRA_IS_SNOOZE, false),
                        now,
                        scheduledOccurrenceAt,
                    )
                } else {
                    coordinator.execute(
                        alarmId,
                        intent.getBooleanExtra(AlarmSchedulerImpl.EXTRA_IS_SNOOZE, false),
                        now,
                        scheduledOccurrenceAt,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
