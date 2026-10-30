package com.smartworkalarm.outlook

import org.junit.Assert.*
import org.junit.Test

class OutlookConfigurationTest {
    @Test fun `missing identifiers never enable an unusable login flow`() {
        assertFalse(OutlookConfiguration("", "aaoXrHFrYrhShsQEbGTUANF+6Ek=", "com.smartworkalarm").isConfigured)
        assertFalse(OutlookConfiguration("not-an-id", "hash", "com.smartworkalarm").isConfigured)
    }

    @Test fun `registered signature is escaped only in OAuth URI`() {
        val config = OutlookConfiguration("12345678-1234-4234-9234-123456789abc", "aaoXrHFrYrhShsQEbGTUANF+6Ek=", "com.smartworkalarm")
        assertTrue(config.isConfigured)
        assertEquals("msauth://com.smartworkalarm/aaoXrHFrYrhShsQEbGTUANF%2B6Ek%3D", config.redirectUri)
    }
}
