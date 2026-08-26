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

    @Test
    fun `pre-alarm channel creation failure does not abort application initialization`() {
        val result: Result<Unit> = runCatching {
            initializePreAlarmChannelSafely {
                throw SecurityException("Notification channel unavailable")
            }
        }

        assertTrue(result.isSuccess)
    }
}
