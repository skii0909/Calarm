package com.smartworkalarm.alarm

import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AlarmNotificationActionsTest {
    private val context = RuntimeEnvironment.getApplication().applicationContext
    private val factory = AlarmNotificationFactory(context, context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)

    @Test fun actionsCarryTheirOwnSessionAndDoNotReplaceTheOpenAction() {
        val key = RingingSessionKey.create(false, 1, "first")
        val notification = factory.create(key, 1, "출근", snoozeMinutes = 10)
        assertEquals(listOf("알람 해제", "10분 후 다시 울림"), notification.actions.map { it.title.toString() })
        val dismiss = shadowOf(notification.actions[0].actionIntent).savedIntent
        val snooze = shadowOf(notification.actions[1].actionIntent).savedIntent
        assertEquals(AlarmRingingService.ACTION_DISMISS, dismiss.action)
        assertEquals(AlarmRingingService.ACTION_SNOOZE, snooze.action)
        assertEquals(key, dismiss.ringingSessionKey())
        assertEquals(key, snooze.ringingSessionKey())
        assertNotEquals(notification.contentIntent, notification.actions[0].actionIntent)
        assertNotEquals(notification.actions[0].actionIntent, notification.actions[1].actionIntent)
        val next = factory.create(RingingSessionKey.create(false, 1, "second"), 1, "출근")
        assertNotEquals(notification.actions[0].actionIntent, next.actions[0].actionIntent)
        assertEquals(key, shadowOf(notification.actions[0].actionIntent).savedIntent.ringingSessionKey())
    }

    @Test fun disabledSnoozeOnlyOffersDismiss() {
        val notification = factory.create(RingingSessionKey.create(true, 1, "shift"), 1, "근무", isShiftAlarm = true, snoozeEnabled = false)
        assertEquals(1, notification.actions.size)
        assertEquals("알람 해제", notification.actions[0].title.toString())
    }
}
