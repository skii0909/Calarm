package com.smartworkalarm.preview

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.PreviewChangeType
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewNotificationTextTest {
    private val alarm = Alarm(1, "주말 알람", 10, 10, 64, true, true)

    @Test fun `skipped alarm message includes keyword time and name`() {
        val text = PreviewNotificationText.format(
            listOf(AlarmPreviewChange(alarm, PreviewChangeType.SKIPPED, "테스트")),
        )
        assertEquals("‘테스트’ 일정으로 오전 10:10 ‘주말 알람’이 울리지 않습니다.", text.content)
    }

    @Test fun `resumed alarm message says it will ring again`() {
        val text = PreviewNotificationText.format(
            listOf(AlarmPreviewChange(alarm, PreviewChangeType.RESUMED)),
        )
        assertEquals("일정이 종료되어 오전 10:10 ‘주말 알람’이 다시 울립니다.", text.content)
    }

    @Test fun `holiday message includes holiday name`() {
        val text = PreviewNotificationText.format(
            listOf(AlarmPreviewChange(alarm, PreviewChangeType.SKIPPED, holidayName = "광복절")),
        )
        assertEquals("광복절로 인해 오전 10:10 ‘주말 알람’이 울리지 않습니다.", text.content)
    }

    @Test fun `combined message includes holiday and calendar keyword`() {
        val text = PreviewNotificationText.format(
            listOf(AlarmPreviewChange(alarm, PreviewChangeType.SKIPPED, "휴가", "광복절")),
        )
        assertEquals("광복절 및 ‘휴가’ 일정으로 인해 오전 10:10 ‘주말 알람’이 울리지 않습니다.", text.content)
    }
}
