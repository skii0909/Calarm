package com.smartworkalarm.prealarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class PreAlarmNotificationTextTest {
    @Test
    fun `title names the alarm and shows its scheduled time`() {
        assertEquals(
            "출근 알람이 07:05에 울립니다",
            PreAlarmNotificationText.forAlarm("출근", LocalTime.of(7, 5)).title,
        )
    }

    @Test
    fun `body asks whether to turn off this occurrence`() {
        assertEquals(
            "오늘 이 알림을 끌까요?",
            PreAlarmNotificationText.forAlarm("출근", LocalTime.of(7, 5)).body,
        )
    }

    @Test
    fun `notification permission is not required before Android 13`() {
        assertEquals(true, canPostPreAlarmNotification(sdkInt = 32, permissionGranted = false))
    }

    @Test
    fun `notification permission is required from Android 13`() {
        assertEquals(false, canPostPreAlarmNotification(sdkInt = 33, permissionGranted = false))
        assertEquals(true, canPostPreAlarmNotification(sdkInt = 33, permissionGranted = true))
    }
}
