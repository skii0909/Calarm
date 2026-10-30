package com.smartworkalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@AndroidEntryPoint
class AlarmBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: AlarmExecutionCoordinator
    @Inject lateinit var shiftCoordinator: ShiftAlarmExecutionCoordinator
    @Inject lateinit var clock: Clock
    @Inject lateinit var journal: AlarmExecutionJournal

    override fun onReceive(context: Context, intent: Intent) {
        val isShift = intent.action == AlarmSchedulerImpl.ACTION_SHIFT_ALARM
        val alarmId = intent.getLongExtra(
            if (isShift) AlarmSchedulerImpl.EXTRA_SHIFT_TYPE_ID else AlarmSchedulerImpl.EXTRA_ALARM_ID,
            -1L,
        )
        if (alarmId < 0 || (!isShift && intent.action != AlarmSchedulerImpl.ACTION_ALARM)) return
        val pendingResult = goAsync()
        val wakeLock = runCatching {
            context.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Calarm:alarm-delivery")
                .apply { acquire(30_000L) }
        }.getOrNull()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                journal.record(isShift, alarmId, "RECEIVED")
                withTimeout(25_000L) {
                    val now = clock.instant()
                    val scheduledOccurrenceAt = intent.getLongExtra(
                        AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT,
                        Long.MIN_VALUE,
                    ).takeIf { it != Long.MIN_VALUE }?.let(Instant::ofEpochMilli) ?: now
                    val outcome = if (isShift) {
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
                    journal.record(isShift, alarmId, outcome.name)
                }
            } catch (error: TimeoutCancellationException) {
                journal.record(isShift, alarmId, "TIMED_OUT")
                Log.e("CalarmDelivery", "Alarm delivery timed out: $alarmId", error)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                journal.record(isShift, alarmId, "FAILED")
                Log.e("CalarmDelivery", "Alarm delivery failed: $alarmId", error)
            } finally {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                pendingResult.finish()
            }
        }
    }
}
