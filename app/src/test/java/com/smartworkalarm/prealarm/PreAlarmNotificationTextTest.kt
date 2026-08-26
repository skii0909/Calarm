package com.smartworkalarm.prealarm

import org.junit.Assert.assertEquals
import org.junit.Test

class PreAlarmNotificationTextTest {
    @Test
    fun `title names the alarm and says it will ring in ten minutes`() {
        assertEquals(
            "출근 알람이 10분 뒤 울립니다",
            PreAlarmNotificationText.forAlarm("출근 알람").title,
        )
    }

    @Test
    fun `body asks whether to turn off this occurrence`() {
        assertEquals(
            "오늘 이 알람을 끌까요?",
            PreAlarmNotificationText.forAlarm("출근 알람").body,
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
