package com.smartworkalarm.preview

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.PreviewChangeType

data class AlarmPreviewChange(
    val alarm: Alarm,
    val type: PreviewChangeType,
    val keyword: String? = null,
)

data class PreviewNotificationContent(val title: String, val content: String, val detail: String)

object PreviewNotificationText {
    fun format(changes: List<AlarmPreviewChange>): PreviewNotificationContent {
        require(changes.isNotEmpty())
        val rows = changes.map(::row)
        return if (changes.size == 1) {
            PreviewNotificationContent("내일 알람 변경", rows.single(), rows.single())
        } else {
            PreviewNotificationContent(
                "내일 알람 ${changes.size}개 변경",
                "캘린더 일정에 따른 변경 내용을 확인하세요.",
                rows.joinToString("\n"),
            )
        }
    }

    private fun row(change: AlarmPreviewChange): String {
        val time = koreanTime(change.alarm.hour, change.alarm.minute)
        return when (change.type) {
            PreviewChangeType.SKIPPED -> "‘${change.keyword.orEmpty()}’ 일정으로 $time ‘${change.alarm.name}’이 울리지 않습니다."
            PreviewChangeType.RESUMED -> "일정이 종료되어 $time ‘${change.alarm.name}’이 다시 울립니다."
        }
    }

    private fun koreanTime(hour: Int, minute: Int): String {
        val period = if (hour < 12) "오전" else "오후"
        val displayHour = when (val value = hour % 12) { 0 -> 12; else -> value }
        return "$period %02d:%02d".format(displayHour, minute)
    }
}
