package com.smartworkalarm.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.smartworkalarm.MainActivity
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.prealarm.AlarmKind
import com.smartworkalarm.prealarm.AlarmOccurrenceKey
import com.smartworkalarm.prealarm.PreAlarmContract
import com.smartworkalarm.prealarm.PreAlarmNotificationCanceller
import com.smartworkalarm.prealarm.PreAlarmNotificationFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertSeparateAlarmSemantics(scheduled, AlarmSchedulerImpl.ACTION_ALARM)

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
        assertSeparateAlarmSemantics(scheduled, AlarmSchedulerImpl.ACTION_SHIFT_ALARM)

        scheduler.cancelShift(73)

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun `immediate pre-alert uses separate semantics and leaves the real alarm clock scheduled`() {
        val (scheduler, alarmManager) = scheduler()
        val nearOccurrence = now.plusSeconds(5 * 60L)

        assertEquals(ScheduleResult.Scheduled, scheduler.schedule(41, nearOccurrence))

        val scheduled = shadowOf(alarmManager).scheduledAlarms
        assertEquals(2, scheduled.size)
        assertEquals(
            setOf(now.toEpochMilli(), nearOccurrence.toEpochMilli()),
            scheduled.map { it.triggerAtMs }.toSet(),
        )
        assertSeparateAlarmSemantics(scheduled, AlarmSchedulerImpl.ACTION_ALARM)
    }

    @Test
    fun `snooze stays exact while idle and never gains a pre-alert`() {
        val (scheduler, alarmManager) = scheduler()

        assertEquals(ScheduleResult.Scheduled, scheduler.scheduleSnooze(41, occurrenceAt))

        val scheduled = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(AlarmSchedulerImpl.ACTION_ALARM, shadowOf(scheduled.single().operation).savedIntent.action)
        assertEquals(
            AlarmBroadcastReceiver::class.java.name,
            shadowOf(scheduled.single().operation).savedIntent.component?.className,
        )
        assertTrue(scheduled.single().isAllowWhileIdle)
        assertNull(scheduled.single().alarmClockInfo)
    }

    @Test
    fun `rescheduling a regular snooze removes its data-less v1 9 identity before using the URI identity`() {
        val (scheduler, alarmManager) = scheduler()
        scheduleLegacyRegular(alarmManager, 41L, snooze = true)
        val legacyOperation = shadowOf(alarmManager).scheduledAlarms.single().operation
        assertNull(shadowOf(legacyOperation).savedIntent.data)

        assertEquals(ScheduleResult.Scheduled, scheduler.scheduleSnooze(41L, occurrenceAt))

        val regularSnoozes = shadowOf(alarmManager).scheduledAlarms.filter { scheduled ->
            shadowOf(scheduled.operation).savedIntent.action == AlarmSchedulerImpl.ACTION_ALARM
        }
        assertEquals(1, regularSnoozes.size)
        assertEquals(
            "smartworkalarm://scheduled-alarm/regular/41/snooze",
            shadowOf(regularSnoozes.single().operation).savedIntent.dataString,
        )
    }

    @Test
    fun `regular cancellation removes both data-less v1 9 occurrence and snooze identities`() {
        val (scheduler, alarmManager) = scheduler()
        scheduleLegacyRegular(alarmManager, 41L, snooze = false)
        scheduleLegacyRegular(alarmManager, 41L, snooze = true)

        scheduler.cancel(41L)

        assertTrue(
            shadowOf(alarmManager).scheduledAlarms.none { scheduled ->
                shadowOf(scheduled.operation).savedIntent.action == AlarmSchedulerImpl.ACTION_ALARM
            },
        )
    }

    @Test
    fun `shift cancellation removes both data-less v1 9 occurrence and snooze identities`() {
        val (scheduler, alarmManager) = scheduler()
        scheduleLegacyShift(alarmManager, 73L, snooze = false)
        scheduleLegacyShift(alarmManager, 73L, snooze = true)

        scheduler.cancelShift(73L)

        assertTrue(
            shadowOf(alarmManager).scheduledAlarms.none { scheduled ->
                shadowOf(scheduled.operation).savedIntent.action == AlarmSchedulerImpl.ACTION_SHIFT_ALARM
            },
        )
    }

    @Test
    fun `colliding long hash ids retain independent real and pre-alert scheduling identities`() {
        val (scheduler, alarmManager) = scheduler()
        val firstId = 1L
        val collidingId = 1L shl 32
        val laterOccurrence = occurrenceAt.plusSeconds(60 * 60L)

        scheduler.schedule(firstId, occurrenceAt)
        scheduler.schedule(collidingId, laterOccurrence)

        val scheduled = shadowOf(alarmManager).scheduledAlarms
        assertEquals(4, scheduled.size)
        assertEquals(4, scheduled.map { shadowOf(it.operation).savedIntent.data }.toSet().size)
        assertEquals(
            setOf(firstId, collidingId),
            scheduled.map { scheduledAlarmId(shadowOf(it.operation).savedIntent) }.toSet(),
        )

        scheduler.cancel(firstId)

        val remaining = shadowOf(alarmManager).scheduledAlarms
        assertEquals(2, remaining.size)
        assertTrue(remaining.all { scheduledAlarmId(shadowOf(it.operation).savedIntent) == collidingId })
    }

    @Test
    fun `cancelling an alarm clears its currently posted pre-alert notification`() {
        val (scheduler, _) = scheduler()
        val (notifications, notificationManager) = notifications()
        notifications.post(
            AlarmOccurrenceKey(AlarmKind.REGULAR, 41L, occurrenceAt),
            "출근",
        )
        assertEquals(1, shadowOf(notificationManager).size())

        scheduler.cancel(41L)

        assertEquals(0, shadowOf(notificationManager).size())
    }

    @Test
    fun `rescheduling a shift clears its currently posted pre-alert notification`() {
        val (scheduler, _) = scheduler()
        val (notifications, notificationManager) = notifications()
        notifications.post(
            AlarmOccurrenceKey(AlarmKind.SHIFT, 73L, occurrenceAt),
            "주간",
        )
        assertEquals(1, shadowOf(notificationManager).size())

        scheduler.scheduleShift(73L, occurrenceAt.plusSeconds(60 * 60L))

        assertEquals(0, shadowOf(notificationManager).size())
    }

    @Test
    fun `notification cleanup failure does not block real alarm scheduling or cancellation`() {
        val (scheduler, alarmManager) = scheduler(
            PreAlarmNotificationCanceller { _, _ -> error("notification service unavailable") },
        )

        assertEquals(ScheduleResult.Scheduled, scheduler.schedule(41L, occurrenceAt))
        assertEquals(2, shadowOf(alarmManager).scheduledAlarms.size)

        scheduler.cancel(41L)

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    private fun scheduledAlarmId(intent: android.content.Intent): Long =
        when (intent.action) {
            AlarmSchedulerImpl.ACTION_ALARM ->
                intent.getLongExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, -1L)
            PreAlarmContract.ACTION_PRE_ALARM ->
                intent.getLongExtra(PreAlarmContract.EXTRA_ID, -1L)
            else -> -1L
        }

    private fun assertSeparateAlarmSemantics(
        scheduled: List<org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm>,
        realAction: String,
    ) {
        val realAlarm = scheduled.single { shadowOf(it.operation).savedIntent.action == realAction }
        val preAlarm = scheduled.single {
            shadowOf(it.operation).savedIntent.action == PreAlarmContract.ACTION_PRE_ALARM
        }

        assertNotNull(realAlarm.alarmClockInfo)
        assertEquals(
            AlarmBroadcastReceiver::class.java.name,
            shadowOf(realAlarm.operation).savedIntent.component?.className,
        )
        val showIntent = realAlarm.alarmClockInfo!!.showIntent
        assertNotNull(showIntent)
        assertEquals(
            MainActivity::class.java.name,
            shadowOf(showIntent).savedIntent.component?.className,
        )
        assertNull(preAlarm.alarmClockInfo)
        assertTrue(preAlarm.isAllowWhileIdle)
    }

    private fun scheduler(
        preAlarmNotifications: PreAlarmNotificationCanceller? = null,
    ): Pair<AlarmSchedulerImpl, AlarmManager> {
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
            preAlarmNotifications = preAlarmNotifications ?: PreAlarmNotificationFactory(
                context,
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
            ),
        ) to alarmManager
    }

    private fun notifications(): Pair<PreAlarmNotificationFactory, NotificationManager> {
        val context = RuntimeEnvironment.getApplication().applicationContext
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        return PreAlarmNotificationFactory(context, notificationManager) to notificationManager
    }

    private fun scheduleLegacyRegular(alarmManager: AlarmManager, alarmId: Long, snooze: Boolean) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            occurrenceAt.toEpochMilli(),
            legacyPendingIntent(
                action = AlarmSchedulerImpl.ACTION_ALARM,
                requestCode = alarmId.hashCode() + if (snooze) 1_000_000 else 0,
            ),
        )
    }

    private fun scheduleLegacyShift(alarmManager: AlarmManager, shiftTypeId: Long, snooze: Boolean) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            occurrenceAt.toEpochMilli(),
            legacyPendingIntent(
                action = AlarmSchedulerImpl.ACTION_SHIFT_ALARM,
                requestCode = shiftTypeId.hashCode() + if (snooze) 3_000_000 else 2_000_000,
            ),
        )
    }

    private fun legacyPendingIntent(action: String, requestCode: Int): PendingIntent {
        val context = RuntimeEnvironment.getApplication().applicationContext
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private object NoOpAuditStore : AlarmScheduleAuditStore {
        override fun recordScheduled(key: String, triggerAt: Instant) = Unit
        override fun recordCancelled(key: String) = Unit
        override fun read() = AlarmScheduleAudit(null, null, 0)
    }
}
