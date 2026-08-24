package com.smartworkalarm.presentation.calendar

import com.smartworkalarm.domain.model.CalendarConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    @Test
    fun `entering each alarm edit entry resets an earlier dismissal`() {
        val listKey = calendarConnectionPromptRequestKey("alarms", "list-entry")
        val firstEditKey = calendarConnectionPromptRequestKey("alarm/{alarmId}", "edit-entry-1")
        val secondEditKey = calendarConnectionPromptRequestKey("alarm/{alarmId}", "edit-entry-2")

        assertNotEquals(firstEditKey, secondEditKey)
        assertTrue(
            shouldShowCalendarConnectionPrompt(
                state = disconnectedCalendar,
                requiredPermissionsMissing = false,
                requestKey = firstEditKey,
                dismissedRequestKey = listKey,
            ),
        )
        assertTrue(
            shouldShowCalendarConnectionPrompt(
                state = disconnectedCalendar,
                requiredPermissionsMissing = false,
                requestKey = secondEditKey,
                dismissedRequestKey = firstEditKey,
            ),
        )
    }

    @Test
    fun `shift edit entries request connection while settings itself does not`() {
        val shiftGroupKey = calendarConnectionPromptRequestKey("shift-group/{groupId}", "group-entry")
        val shiftTypeKey = calendarConnectionPromptRequestKey(
            "shift-type/{groupId}/{shiftTypeId}",
            "type-entry",
        )

        assertTrue(shiftGroupKey != null)
        assertTrue(shiftTypeKey != null)
        assertTrue(
            shouldShowCalendarConnectionPrompt(
                state = disconnectedCalendar,
                requiredPermissionsMissing = false,
                requestKey = shiftTypeKey,
                dismissedRequestKey = shiftGroupKey,
            ),
        )
        assertFalse(
            shouldShowCalendarConnectionPrompt(
                state = disconnectedCalendar,
                requiredPermissionsMissing = false,
                requestKey = calendarConnectionPromptRequestKey("settings", "settings-entry"),
                dismissedRequestKey = null,
            ),
        )
    }
}
