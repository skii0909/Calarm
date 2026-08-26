package com.smartworkalarm.prealarm

import java.time.Instant

object PreAlarmContract {
    const val ACTION_PRE_ALARM = "com.smartworkalarm.ACTION_PRE_ALARM"
    const val ACTION_SKIP_OCCURRENCE = "com.smartworkalarm.ACTION_SKIP_OCCURRENCE"
    const val ACTION_KEEP_OCCURRENCE = "com.smartworkalarm.ACTION_KEEP_OCCURRENCE"
    const val EXTRA_KIND = "pre_alarm_kind"
    const val EXTRA_ID = "pre_alarm_id"
    const val EXTRA_SCHEDULED_OCCURRENCE_AT = "pre_alarm_scheduled_occurrence_at"
}

fun preAlarmTriggerAt(now: Instant, occurrenceAt: Instant): Instant? {
    if (occurrenceAt.isBefore(now)) return null
    return maxOf(now, occurrenceAt.minusSeconds(PRE_ALARM_LEAD_SECONDS))
}

private const val PRE_ALARM_LEAD_SECONDS = 10 * 60L
