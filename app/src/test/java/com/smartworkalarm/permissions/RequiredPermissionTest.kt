package com.smartworkalarm.permissions

import org.junit.Assert.assertEquals
import org.junit.Test

class RequiredPermissionTest {
    @Test
    fun `Android 14 누락 권한을 실행 가능한 순서로 반환한다`() {
        val missing = RequiredPermission.missingFor(
            sdkInt = 34,
            calendarGranted = false,
            notificationsGranted = false,
            exactAlarmGranted = false,
            fullScreenAlarmGranted = false,
        )

        assertEquals(
            listOf(
                RequiredPermission.CALENDAR,
                RequiredPermission.NOTIFICATIONS,
                RequiredPermission.EXACT_ALARM,
                RequiredPermission.FULL_SCREEN_ALARM,
            ),
            missing,
        )
    }

    @Test
    fun `Android 11에서는 캘린더 권한만 필요하다`() {
        assertEquals(
            listOf(RequiredPermission.CALENDAR),
            RequiredPermission.missingFor(
                sdkInt = 30,
                calendarGranted = false,
                notificationsGranted = false,
                exactAlarmGranted = false,
                fullScreenAlarmGranted = false,
            ),
        )
    }

    @Test
    fun `필수 권한이 모두 허용되면 빈 목록을 반환한다`() {
        assertEquals(
            emptyList<RequiredPermission>(),
            RequiredPermission.missingFor(
                sdkInt = 34,
                calendarGranted = true,
                notificationsGranted = true,
                exactAlarmGranted = true,
                fullScreenAlarmGranted = true,
            ),
        )
    }
}
