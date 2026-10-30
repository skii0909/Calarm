package com.smartworkalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class OptionalAlarmStartupTest {
    @Test fun `failed preview setup does not prevent remaining startup`() {
        var subsequentStartup = false
        initializeOptionalAlarmStartup(
            { throw SecurityException("exact alarm permission changed") },
            { subsequentStartup = true },
        )
        assertEquals(true, subsequentStartup)
    }
}
