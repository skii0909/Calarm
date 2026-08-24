package com.smartworkalarm.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequiredPermissionActionPolicyTest {
    @Test
    fun `a missing runtime permission is requested before it has local history`() {
        assertEquals(
            RequiredPermissionAction.REQUEST_RUNTIME,
            requiredPermissionAction(
                permission = RequiredPermission.CALENDAR,
                isMissing = true,
                wasRequested = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun `a denied runtime permission is requested again only while rationale is available`() {
        assertEquals(
            RequiredPermissionAction.REQUEST_RUNTIME,
            requiredPermissionAction(
                permission = RequiredPermission.NOTIFICATIONS,
                isMissing = true,
                wasRequested = true,
                shouldShowRationale = true,
            ),
        )
        assertEquals(
            RequiredPermissionAction.OPEN_APP_DETAILS,
            requiredPermissionAction(
                permission = RequiredPermission.NOTIFICATIONS,
                isMissing = true,
                wasRequested = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun `granted runtime permissions and special permissions route to settings`() {
        assertEquals(
            RequiredPermissionAction.OPEN_APP_DETAILS,
            requiredPermissionAction(
                permission = RequiredPermission.CALENDAR,
                isMissing = false,
                wasRequested = true,
                shouldShowRationale = false,
            ),
        )
        assertEquals(
            RequiredPermissionAction.OPEN_SPECIAL_SETTINGS,
            requiredPermissionAction(
                permission = RequiredPermission.EXACT_ALARM,
                isMissing = true,
                wasRequested = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun `special settings launch failure falls back to app details`() {
        val launched = mutableListOf<String>()

        launchPermissionSettings(
            primary = "special",
            appDetails = "app-details",
        ) { target ->
            launched += target
            if (target == "special") throw IllegalStateException("unavailable")
        }

        assertEquals(listOf("special", "app-details"), launched)
    }
}

class RequiredPermissionsPromptSessionStateTest {
    private val missing = listOf(RequiredPermission.CALENDAR)

    @Test
    fun `later remains dismissed for the retained activity session`() {
        val dismissed = RequiredPermissionsPromptSessionState()
            .dismiss()
            .afterPermissionsRefresh(missing)

        assertFalse(dismissed.shouldShow(missing))
    }

    @Test
    fun `a genuinely new session prompts again and a completed cycle resets dismissal`() {
        val newSession = RequiredPermissionsPromptSessionState()
        assertTrue(newSession.shouldShow(missing))

        val reset = newSession
            .dismiss()
            .afterPermissionsRefresh(emptyList())

        assertTrue(reset.shouldShow(missing))
    }
}
