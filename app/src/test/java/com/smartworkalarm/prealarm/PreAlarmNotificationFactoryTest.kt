package com.smartworkalarm.prealarm

import android.app.NotificationManager
import android.content.Context
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PreAlarmNotificationFactoryTest {
    private val context = RuntimeEnvironment.getApplication().applicationContext
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val factory = PreAlarmNotificationFactory(context, notificationManager)

    @After
    fun tearDown() {
        notificationManager.cancelAll()
    }

    @Test
    fun `cancellation by alarm identity removes the current occurrence notification`() {
        val obsolete = occurrence(AlarmKind.REGULAR, 41L, "2026-08-26T00:30:00Z")
        val current = occurrence(AlarmKind.REGULAR, 41L, "2026-08-27T00:30:00Z")
        factory.post(current, "출근")
        assertEquals(1, shadowOf(notificationManager).size())

        factory.cancel(obsolete)

        assertEquals(0, shadowOf(notificationManager).size())
    }

    @Test
    fun `new occurrence replaces the old notification without rewriting its stale action`() {
        val first = occurrence(AlarmKind.REGULAR, 41L, "2026-08-26T00:30:00Z")
        val second = occurrence(AlarmKind.REGULAR, 41L, "2026-08-27T00:30:00Z")
        factory.post(first, "출근")
        val firstSkipIntent = shadowOf(
            shadowOf(notificationManager).allNotifications.single().actions[0].actionIntent,
        ).savedIntent

        factory.post(second, "출근")

        val notifications = shadowOf(notificationManager).allNotifications
        assertEquals(1, notifications.size)
        val secondSkipIntent = shadowOf(notifications.single().actions[0].actionIntent).savedIntent
        assertNotEquals(firstSkipIntent.data, secondSkipIntent.data)
        assertEquals(
            first.scheduledAt.toEpochMilli(),
            firstSkipIntent.getLongExtra(PreAlarmContract.EXTRA_SCHEDULED_OCCURRENCE_AT, -1L),
        )
        assertEquals(
            second.scheduledAt.toEpochMilli(),
            secondSkipIntent.getLongExtra(PreAlarmContract.EXTRA_SCHEDULED_OCCURRENCE_AT, -1L),
        )
    }

    @Test
    fun `cancelling regular alarm leaves same-id shift notification intact`() {
        val regular = occurrence(AlarmKind.REGULAR, 41L, "2026-08-26T00:30:00Z")
        val shift = occurrence(AlarmKind.SHIFT, 41L, "2026-08-26T00:30:00Z")
        factory.post(regular, "출근")
        factory.post(shift, "주간")

        factory.cancel(regular.copy(scheduledAt = regular.scheduledAt.minusSeconds(60)))

        val remaining = shadowOf(notificationManager).allNotifications
        assertEquals(1, remaining.size)
        assertEquals("주간이 10분 뒤 울립니다", remaining.single().extras.getString("android.title"))
    }

    private fun occurrence(kind: AlarmKind, id: Long, scheduledAt: String) =
        AlarmOccurrenceKey(kind, id, Instant.parse(scheduledAt))
}
