package com.smartworkalarm.preview

import com.smartworkalarm.data.local.AlarmPreviewDao
import com.smartworkalarm.data.local.AlarmPreviewEntity
import com.smartworkalarm.domain.model.PreviewDecision
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.usecase.CalendarKeywordMatcher
import com.smartworkalarm.domain.usecase.CalendarHolidayMatcher
import com.smartworkalarm.domain.usecase.CompareAlarmPreviewStateUseCase
import com.smartworkalarm.domain.holiday.KoreanPublicHolidayProvider
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class EvaluateNextDayAlarmsUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val events: CalendarEventSource,
    private val previews: AlarmPreviewDao,
    private val compare: CompareAlarmPreviewStateUseCase,
    private val clock: Clock,
    private val holidays: KoreanPublicHolidayProvider = KoreanPublicHolidayProvider(),
) {
    suspend fun execute(targetDate: LocalDate, zoneId: ZoneId): List<AlarmPreviewChange> {
        val candidates = alarms.getEnabled().filter { it.repeatsOn(targetDate.dayOfWeek) }
        if (candidates.isEmpty()) return emptyList()
        val dayEvents = events.getEventsOverlapping(
            targetDate.atStartOfDay(zoneId).toInstant(),
            targetDate.plusDays(1).atStartOfDay(zoneId).toInstant(),
        ).getOrNull()
        return buildList {
            candidates.forEach { alarm ->
                val localHoliday = if (alarm.skipOnPublicHoliday) holidays.find(targetDate)?.name else null
                val calendarHoliday = if (alarm.skipOnPublicHoliday) {
                    dayEvents?.firstOrNull { CalendarHolidayMatcher.matches(it.title) }?.title
                } else null
                val matchedKeyword = if (alarm.skipOnCalendarMatch) {
                    alarm.calendarKeywords.firstOrNull { keyword ->
                        dayEvents?.any { CalendarKeywordMatcher.matches(it.title, listOf(keyword)) } == true
                    }
                } else null
                val holidayName = localHoliday ?: calendarHoliday
                if (dayEvents == null && holidayName == null) return@forEach
                val current = if (matchedKeyword == null && holidayName == null) PreviewDecision.RING else PreviewDecision.SKIP
                val previous = previews.get(alarm.id)?.decision?.let(PreviewDecision::valueOf)
                compare.execute(previous, current)?.let {
                    add(AlarmPreviewChange(alarm, it, matchedKeyword, holidayName))
                }
                previews.upsert(
                    AlarmPreviewEntity(alarm.id, current.name, targetDate.toString(), clock.millis()),
                )
            }
        }
    }
}
