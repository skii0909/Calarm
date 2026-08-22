package com.smartworkalarm

import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectConfigurationTest {
    @Test
    fun `application class is available`() {
        assertTrue(
            Class.forName("com.smartworkalarm.WorkAlarmApplication")
                .isAssignableFrom(WorkAlarmApplication::class.java),
        )
    }
}
