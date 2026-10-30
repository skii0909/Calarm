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

data class AlarmPreviewEvaluation(
    val changes: List<AlarmPreviewChange> = emptyList(),
    val calendarReadFailed: Boolean = false,
    val pendingSnapshots: List<AlarmPreviewEntity> = emptyList(),
)

class EvaluateNextDayAlarmsUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val events: CalendarEventSource,
    private val previews: AlarmPreviewDao,
    private val compare: CompareAlarmPreviewStateUseCase,
    private val clock: Clock,
    private val holidays: KoreanPublicHolidayProvider = KoreanPublicHolidayProvider(),
    private val actualSkips: ActualAlarmSkipStore = NoActualAlarmSkipStore,
) {
    suspend fun execute(targetDate: LocalDate, zoneId: ZoneId): List<AlarmPreviewChange> =
        executeWithStatus(targetDate, zoneId).changes

    suspend fun executeWithStatus(targetDate: LocalDate, zoneId: ZoneId): AlarmPreviewEvaluation {
        val candidates = alarms.getEnabled().filter { it.repeatsOn(targetDate.dayOfWeek) }
        if (candidates.isEmpty()) return AlarmPreviewEvaluation()
        val localHoliday = holidays.find(targetDate)?.name
        val requiresCalendar = candidates.any { alarm ->
            !(alarm.skipOnPublicHoliday && localHoliday != null) &&
                (alarm.skipOnPublicHoliday || (alarm.skipOnCalendarMatch && alarm.calendarKeywords.isNotEmpty()))
        }
        val dayEventsResult = if (requiresCalendar) {
            events.getEventsOverlapping(
                targetDate.atStartOfDay(zoneId).toInstant(),
                targetDate.plusDays(1).atStartOfDay(zoneId).toInstant(),
            )
        } else Result.success(emptyList())
        val changes = mutableListOf<AlarmPreviewChange>()
        val snapshots = mutableListOf<AlarmPreviewEntity>()
        candidates.forEach { alarm ->
            val knownHoliday = localHoliday.takeIf { alarm.skipOnPublicHoliday }
            val needsCalendar = alarm.skipOnPublicHoliday ||
                (alarm.skipOnCalendarMatch && alarm.calendarKeywords.isNotEmpty())
            // Failed calendar access must not hide a known holiday or an independent alarm.
            if (knownHoliday == null && needsCalendar && dayEventsResult.isFailure) return@forEach
            val dayEvents = dayEventsResult.getOrDefault(emptyList())
            val calendarHoliday = if (alarm.skipOnPublicHoliday) {
                dayEvents.firstOrNull { CalendarHolidayMatcher.matches(it) }?.title
            } else null
            val matchedKeyword = if (alarm.skipOnCalendarMatch) {
                alarm.calendarKeywords.firstOrNull { keyword ->
                    dayEvents.any { CalendarKeywordMatcher.matches(it.title, listOf(keyword), alarm.calendarKeywordExactMatch) }
                }
            } else null
            val holidayName = knownHoliday ?: calendarHoliday
            val current = if (matchedKeyword == null && holidayName == null) PreviewDecision.RING else PreviewDecision.SKIP
            val snapshot = previews.get(alarm.id)
            val snapshotDate = snapshot?.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val actualSkip = actualSkips.latestSkippedDate(alarm.id)
            val previous = if (current == PreviewDecision.RING && actualSkip != null && actualSkip.isBefore(targetDate) &&
                (snapshotDate == null || !actualSkip.isBefore(snapshotDate))) {
                PreviewDecision.SKIP
            } else snapshot?.decision?.let(PreviewDecision::valueOf)
            compare.execute(previous, current)?.let {
                changes.add(AlarmPreviewChange(alarm, it, matchedKeyword, holidayName))
            }
            snapshots.add(AlarmPreviewEntity(alarm.id, current.name, targetDate.toString(), clock.millis()))
        }
        return AlarmPreviewEvaluation(changes, dayEventsResult.isFailure, snapshots)
    }

    // Only acknowledge after delivery. Evaluation and retries leave the last delivered state intact.
    suspend fun acknowledge(evaluation: AlarmPreviewEvaluation) {
        evaluation.pendingSnapshots.forEach { previews.upsert(it) }
    }
}
