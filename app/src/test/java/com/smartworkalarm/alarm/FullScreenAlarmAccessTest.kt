package com.smartworkalarm.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenAlarmAccessTest {
    @Test
    fun `Android 14 이상에서 전체 화면 알람 권한이 없으면 안내가 필요하다`() {
        assertTrue(FullScreenAlarmAccess.requiresSettingsPrompt(sdkInt = 34, canUseFullScreenIntent = false))
    }

    @Test
    fun `전체 화면 알람을 사용할 수 있으면 안내하지 않는다`() {
        assertFalse(FullScreenAlarmAccess.requiresSettingsPrompt(sdkInt = 36, canUseFullScreenIntent = true))
    }

    @Test
    fun `Android 13 이하는 별도 전체 화면 알람 설정이 필요하지 않다`() {
        assertFalse(FullScreenAlarmAccess.requiresSettingsPrompt(sdkInt = 33, canUseFullScreenIntent = false))
    }
}
