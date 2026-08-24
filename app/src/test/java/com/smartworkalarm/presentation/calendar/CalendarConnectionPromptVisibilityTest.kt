package com.smartworkalarm.presentation.calendar

import com.smartworkalarm.domain.model.CalendarConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarConnectionPromptVisibilityTest {
    private val disconnectedCalendar = CalendarConnectionState(
        permissionGranted = true,
        calendars = emptyList(),
    )

    @Test
    fun `필수 권한이 하나라도 누락이면 캘린더 연결 프롬프트를 표시하지 않는다`() {
        assertFalse(
            shouldShowCalendarConnectionPrompt(
                state = disconnectedCalendar,
                requiredPermissionsMissing = true,
            ),
        )
    }

    @Test
    fun `필수 권한이 모두 충족된 미연결 캘린더는 연결 프롬프트를 표시한다`() {
        assertTrue(
            shouldShowCalendarConnectionPrompt(
                state = disconnectedCalendar,
                requiredPermissionsMissing = false,
            ),
        )
    }
}
