package com.smartworkalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AlarmBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: AlarmExecutionCoordinator
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, -1L)
        if (alarmId < 0) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                coordinator.execute(
                    alarmId,
                    intent.getBooleanExtra(AlarmSchedulerImpl.EXTRA_IS_SNOOZE, false),
                    clock.instant(),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
