package com.smartworkalarm.preview

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class NextDayPreviewNotificationFactoryTest {
    private val context = RuntimeEnvironment.getApplication().applicationContext
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val factory = NextDayPreviewNotificationFactory(context, manager)
    private val target = LocalDate.of(2026, 10, 6)
    private val now = Instant.parse("2026-10-05T13:00:00Z")
    private val zone = ZoneId.of("Asia/Seoul")
    private val warning = AlarmPreviewEvaluation(calendarReadFailed = true)

    @Test fun `blocked channel does not acknowledge delivery`() {
        manager.createNotificationChannel(NotificationChannel(
            NextDayPreviewNotificationFactory.CHANNEL_ID, "Preview", NotificationManager.IMPORTANCE_NONE))
        assertFalse(factory.post(warning, ShiftPreviewEvaluation(), target, now, zone))
        assertEquals(0, shadowOf(manager).size())
    }
    @Test fun `disabled app notifications do not acknowledge delivery`() {
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(factory.post(warning, ShiftPreviewEvaluation(), target, now, zone))
        assertEquals(0, shadowOf(manager).size())
    }
    @Test fun `delivered notification identifies the date and expires at midnight`() {
        assertTrue(factory.post(warning, ShiftPreviewEvaluation(), target, now, zone))
        val notification = shadowOf(manager).allNotifications.single()
        assertTrue(notification.extras.getString("android.title")!!.contains("10월 6일"))
        assertEquals(7_200_000L, notification.timeoutAfter)
    }
}
