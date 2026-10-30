package com.smartworkalarm.outlook

import com.smartworkalarm.prealarm.PreAlarmEligibility
import com.smartworkalarm.prealarm.PreAlarmNotificationFactory
import com.smartworkalarm.preview.NextDayPreviewScheduler
import com.smartworkalarm.worker.RescheduleAlarmsUseCase
import java.time.Clock
import javax.inject.Inject

class OutlookAlarmReconciler @Inject constructor(
    private val notifications: PreAlarmNotificationFactory,
    private val eligibility: PreAlarmEligibility,
    private val alarms: RescheduleAlarmsUseCase,
    private val preview: NextDayPreviewScheduler,
    private val clock: Clock,
) {
    suspend fun execute() {
        notifications.reconcileEligibility(clock.instant()) { eligibility.alarmNameIfEligible(it) != null }
        alarms.execute(clock.instant())
        preview.scheduleNext()
    }
}
