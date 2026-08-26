package com.smartworkalarm.prealarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PreAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var shiftAlarmRepository: ShiftAlarmRepository
    @Inject lateinit var skipStore: OccurrenceSkipStore
    @Inject lateinit var notificationFactory: PreAlarmNotificationFactory

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.occurrenceKeyOrNull() ?: return
        when (intent.action) {
            PreAlarmContract.ACTION_PRE_ALARM -> postIfEnabled(key)
            PreAlarmContract.ACTION_SKIP_OCCURRENCE -> {
                skipStore.mark(key)
                notificationFactory.cancel(key)
            }
            PreAlarmContract.ACTION_KEEP_OCCURRENCE -> notificationFactory.cancel(key)
        }
    }

    private fun postIfEnabled(key: AlarmOccurrenceKey) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val name = when (key.kind) {
                    AlarmKind.REGULAR -> alarmRepository.getById(key.id)
                        ?.takeIf { it.enabled }
                        ?.name
                    AlarmKind.SHIFT -> shiftAlarmRepository.getShiftType(key.id)
                        ?.takeIf { it.enabled }
                        ?.name
                }
                if (name != null) notificationFactory.post(key, name)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private fun Intent.occurrenceKeyOrNull(): AlarmOccurrenceKey? {
    val kind = getStringExtra(PreAlarmContract.EXTRA_KIND)
        ?.let { runCatching { AlarmKind.valueOf(it) }.getOrNull() }
        ?: return null
    val id = getLongExtra(PreAlarmContract.EXTRA_ID, -1L)
    val scheduledAt = getLongExtra(
        PreAlarmContract.EXTRA_SCHEDULED_OCCURRENCE_AT,
        Long.MIN_VALUE,
    )
    if (id < 0L || scheduledAt == Long.MIN_VALUE) return null
    return AlarmOccurrenceKey(kind, id, Instant.ofEpochMilli(scheduledAt))
}
