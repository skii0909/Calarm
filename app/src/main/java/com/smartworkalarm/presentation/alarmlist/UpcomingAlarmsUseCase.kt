package com.smartworkalarm.presentation.alarmlist

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.AlarmExecutionDecision
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.holiday.KoreanPublicHolidayProvider
import com.smartworkalarm.domain.shift.EvaluateShiftGroupDayUseCase
import com.smartworkalarm.domain.shift.ShiftGroupDayStatus
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import com.smartworkalarm.domain.usecase.ShouldRingAlarmUseCase
import com.smartworkalarm.domain.usecase.toAlarm
import com.smartworkalarm.prealarm.AlarmKind
import com.smartworkalarm.prealarm.AlarmOccurrenceKey
import com.smartworkalarm.prealarm.OccurrenceSkipStore
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

data class UpcomingAlarms(val items: List<NextAlarmCardItem>, val calendarReadFailed: Boolean)

/** Read-only forecast. It never cancels OS reservations or consumes a one-occurrence skip. */
class UpcomingAlarmsUseCase @Inject constructor(
    private val events: CalendarEventSource,
    private val holidays: KoreanPublicHolidayProvider,
    private val shiftEvaluator: EvaluateShiftGroupDayUseCase,
    private val skips: OccurrenceSkipStore,
) {
    suspend fun execute(
        alarms: List<Alarm>,
        groups: List<ShiftAlarmRepository.GroupWithTypes>,
        now: Instant,
        zone: ZoneId,
    ): UpcomingAlarms {
        val active = alarms.filter { it.enabled }
        val shifts = groups.filter { it.group.enabled && !it.group.isDraft }
        val today = now.atZone(zone).toLocalDate()
        val needsCalendar = active.any { it.skipOnPublicHoliday || (it.skipOnCalendarMatch && it.calendarKeywords.isNotEmpty()) } || shifts.isNotEmpty()
        val snapshot = if (needsCalendar) {
            try {
                withTimeoutOrNull(5_000) {
                    // Outlook stores 32 days; keep a margin for a still-fresh snapshot made before midnight.
                    events.getEventsOverlapping(today.atStartOfDay(zone).toInstant(), today.plusDays(30).atStartOfDay(zone).toInstant())
                } ?: Result.failure(IllegalStateException("Calendar query timed out"))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Result.failure(error) }
        } else Result.success(emptyList())
        val cached = object : CalendarEventSource {
            override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant): Result<List<CalendarEvent>> =
                snapshot.map { all -> all.filter { it.start < endExclusive && it.end > startInclusive } }
        }
        val decision = ShouldRingAlarmUseCase(cached, holidays)
        val calculator = CalculateNextOccurrenceUseCase()
        val once = active.filter { it.repeatDaysMask == 0 }.associate { it.id to calculator.execute(it, now, zone) }
        val result = mutableListOf<NextAlarmCardItem>()
        for (offset in 0L..29L) {
            val date = today.plusDays(offset)
            val start = date.atStartOfDay(zone).toInstant()
            val reference = maxOf(now, start.minusNanos(1))
            val day = mutableListOf<NextAlarmCardItem>()
            for (alarm in active) {
                val due = (if (alarm.repeatDaysMask == 0) once[alarm.id] else calculator.execute(alarm, reference, zone))
                    ?.takeIf { it.atZone(zone).toLocalDate() == date } ?: continue
                if (isSkipped(AlarmKind.REGULAR, alarm.id, due)) continue
                val status = decision.execute(alarm, date, zone)
                if (status == AlarmExecutionDecision.RING || status == AlarmExecutionDecision.RING_WITH_CALENDAR_WARNING) {
                    day += NextAlarmCardItem(alarm.id, alarm.name, due, false, status == AlarmExecutionDecision.RING_WITH_CALENDAR_WARNING)
                }
            }
            if (shifts.isNotEmpty()) {
                val dayEvents = cached.getEventsOverlapping(start, date.plusDays(1).atStartOfDay(zone).toInstant())
                for (group in shifts) {
                    val status = shiftEvaluator.execute(group, date, dayEvents)
                    val type = when (status) {
                        is ShiftGroupDayStatus.Matched -> status.shiftType
                        is ShiftGroupDayStatus.ReadFailure -> status.emergencyType
                        else -> null
                    } ?: continue
                    val due = calculator.execute(type.toAlarm(), reference, zone)
                        ?.takeIf { it.atZone(zone).toLocalDate() == date } ?: continue
                    if (!isSkipped(AlarmKind.SHIFT, type.id, due)) {
                        day += NextAlarmCardItem(type.id, type.name, due, true, status is ShiftGroupDayStatus.ReadFailure)
                    }
                }
            }
            result += day.sortedWith(compareBy<NextAlarmCardItem> { it.triggerAt }.thenBy { it.isShift }.thenBy { it.id })
            if (result.size >= 4) break
        }
        return UpcomingAlarms(result.take(4), snapshot.isFailure)
    }

    private fun isSkipped(kind: AlarmKind, id: Long, at: Instant): Boolean =
        runCatching { skips.contains(AlarmOccurrenceKey(kind, id, at)) }.getOrDefault(false)
}
