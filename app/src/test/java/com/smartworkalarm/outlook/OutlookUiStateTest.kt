package com.smartworkalarm.outlook

import org.junit.Assert.*
import org.junit.Test

class OutlookUiStateTest {
    @Test fun `missing configuration disables authentication without pretending to be connected`() {
        val state = OutlookUiState(OutlookState(), configured = false)
        assertFalse(state.canConnect)
        assertFalse(state.canRefresh)
    }

    @Test fun `revoked account offers reauthentication instead of silent refresh`() {
        val state = OutlookUiState(OutlookState(account = OutlookAccount("a", "me@example.com"), issue = OutlookIssue.REAUTH_REQUIRED), configured = true)
        assertTrue(state.canConnect)
        assertFalse(state.canRefresh)
        assertTrue(state.requiresReconnect)
    }

    @Test fun `running operation disables conflicting actions`() {
        val state = OutlookUiState(OutlookState(account = OutlookAccount("a", "me@example.com")), configured = true, busy = true)
        assertFalse(state.canConnect)
        assertFalse(state.canRefresh)
    }
}
