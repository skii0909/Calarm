package com.smartworkalarm.alarm

import android.app.AlarmManager
import android.content.Context
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.prealarm.PreAlarmContract
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@Suppress("DEPRECATION")
class AlarmSchedulerImplTest {
    private val now = Instant.parse("2026-08-26T00:00:00Z")
    private val occurrenceAt = Instant.parse("2026-08-26T00:30:00Z")

    @Test
    fun `regular occurrence schedules and cancels the real and pre-alert pair`() {
        val (scheduler, alarmManager) = scheduler()

        assertEquals(ScheduleResult.Scheduled, scheduler.schedule(41, occurrenceAt))

        val scheduled = shadowOf(alarmManager).scheduledAlarms
        assertEquals(
            listOf(
                Instant.parse("2026-08-26T00:20:00Z").toEpochMilli(),
                occurrenceAt.toEpochMilli(),
            ),
            scheduled.map { it.triggerAtMs }.sorted(),
        )
        assertEquals(
            setOf(AlarmSchedulerImpl.ACTION_ALARM, PreAlarmContract.ACTION_PRE_ALARM),
            scheduled.map { shadowOf(it.operation).savedIntent.action }.toSet(),
        )

        scheduler.cancel(41)

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun `shift occurrence schedules and cancels the real and pre-alert pair`() {
        val (scheduler, alarmManager) = scheduler()

        assertEquals(ScheduleResult.Scheduled, scheduler.scheduleShift(73, occurrenceAt))

        val scheduled = shadowOf(alarmManager).scheduledAlarms
        assertEquals(
            listOf(
                Instant.parse("2026-08-26T00:20:00Z").toEpochMilli(),
                occurrenceAt.toEpochMilli(),
            ),
            scheduled.map { it.triggerAtMs }.sorted(),
        )
        assertEquals(
            setOf(AlarmSchedulerImpl.ACTION_SHIFT_ALARM, PreAlarmContract.ACTION_PRE_ALARM),
            scheduled.map { shadowOf(it.operation).savedIntent.action }.toSet(),
        )

        scheduler.cancelShift(73)

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    private fun scheduler(): Pair<AlarmSchedulerImpl, AlarmManager> {
        val context = RuntimeEnvironment.getApplication().applicationContext
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowOf(alarmManager).scheduledAlarms.toList().forEach { scheduled ->
            scheduled.operation?.let(alarmManager::cancel)
        }
        return AlarmSchedulerImpl(
            context = context,
            alarmManager = alarmManager,
            permission = ExactAlarmPermission(alarmManager),
            auditStore = NoOpAuditStore,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        ) to alarmManager
    }

    private object NoOpAuditStore : AlarmScheduleAuditStore {
        override fun recordScheduled(key: String, triggerAt: Instant) = Unit
        override fun recordCancelled(key: String) = Unit
        override fun read() = AlarmScheduleAudit(null, null, 0)
    }
}
