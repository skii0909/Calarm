package com.smartworkalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ProjectConfigurationTest {
    @Test
    fun `application label is Calarm`() {
        assertEquals("Calarm", RuntimeEnvironment.getApplication().getString(R.string.app_name))
    }

    @Test
    fun `application class is available`() {
        assertTrue(
            Class.forName("com.smartworkalarm.WorkAlarmApplication")
                .isAssignableFrom(WorkAlarmApplication::class.java),
        )
    }

    @Test
    fun `application uses adaptive launcher and round icons`() {
        val application = RuntimeEnvironment.getApplication()
        val resources = application.resources
        val launcherIcon = resources.getIdentifier("ic_launcher", "mipmap", application.packageName)
        val roundIcon = resources.getIdentifier("ic_launcher_round", "mipmap", application.packageName)

        assertTrue("Adaptive launcher icon must exist", launcherIcon != 0)
        assertTrue("Round launcher icon must exist", roundIcon != 0)
        assertEquals(launcherIcon, application.applicationInfo.icon)
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
