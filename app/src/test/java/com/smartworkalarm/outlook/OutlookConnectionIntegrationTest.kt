package com.smartworkalarm.outlook

import com.smartworkalarm.domain.model.CalendarConnectionState
import com.smartworkalarm.permissions.RequiredPermission
import com.smartworkalarm.permissions.alarmRequiredPermissions
import com.smartworkalarm.presentation.calendar.calendarConnectionSummary
import com.smartworkalarm.presentation.calendar.shouldShowCalendarConnectionPrompt
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class OutlookConnectionIntegrationTest {
    @Test fun `fresh Outlook connection suppresses missing-calendar prompt without local permission`() {
        val now = Instant.now()
        val outlook = OutlookState(account = OutlookAccount("a", "me@example.com"), selectedIds = setOf("id"),
            syncedAt = now, coverageStart = now.minusSeconds(86400), coverageEnd = now.plusSeconds(86400))
        val state = CalendarConnectionState(false, emptyList(), outlook = outlook)
        assertTrue(state.isConnected)
        assertEquals("연결된 캘린더 1개", calendarConnectionSummary(state))
        assertFalse(shouldShowCalendarConnectionPrompt(state, false))
    }

    @Test fun `calendar permission is optional while actual alarm permissions are still requested`() {
        assertEquals(listOf(RequiredPermission.NOTIFICATIONS, RequiredPermission.EXACT_ALARM),
            alarmRequiredPermissions(listOf(RequiredPermission.CALENDAR, RequiredPermission.NOTIFICATIONS, RequiredPermission.EXACT_ALARM)))
        assertTrue(alarmRequiredPermissions(listOf(RequiredPermission.CALENDAR)).isEmpty())
    }
}
