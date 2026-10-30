package com.smartworkalarm.domain.holiday

import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

data class PublicHoliday(val date: LocalDate, val name: String)

class KoreanPublicHolidayProvider @Inject constructor() {
    private val holidays: Map<LocalDate, PublicHoliday> = buildHolidays()

    fun find(date: LocalDate): PublicHoliday? =
        if (date.year in FIRST_YEAR..LAST_YEAR) holidays[date] else null

    private fun buildHolidays(): Map<LocalDate, PublicHoliday> {
        val result = linkedMapOf<LocalDate, String>()
        for (year in FIRST_YEAR..LAST_YEAR) {
            add(result, LocalDate.of(year, 1, 1), "신정")
            add(result, LocalDate.of(year, 3, 1), "삼일절")
            // Both became national public holidays in 2026 (Act 21543 / Decree 36290).
            add(result, LocalDate.of(year, 5, 1), "노동절")
            add(result, LocalDate.of(year, 5, 5), "어린이날")
            add(result, LocalDate.of(year, 6, 6), "현충일")
            add(result, LocalDate.of(year, 7, 17), "제헌절")
            add(result, LocalDate.of(year, 8, 15), "광복절")
            add(result, LocalDate.of(year, 10, 3), "개천절")
            add(result, LocalDate.of(year, 10, 9), "한글날")
            add(result, LocalDate.of(year, 12, 25), "기독탄신일")
            addThreeDayHoliday(result, SEOLLAL.getValue(year), "설날")
            addThreeDayHoliday(result, CHUSEOK.getValue(year), "추석")
            add(result, BUDDHA.getValue(year), "부처님오신날")
        }
        add(result, LocalDate.of(2026, 6, 3), "전국동시지방선거일")
        add(result, LocalDate.of(2028, 4, 12), "국회의원선거일")

        addSingleDaySubstitutes(result)
        addThreeDaySubstitute(result, SEOLLAL, "설날")
        addThreeDaySubstitute(result, CHUSEOK, "추석")
        return result.mapValues { (date, name) -> PublicHoliday(date, name) }
    }

    private fun addSingleDaySubstitutes(result: MutableMap<LocalDate, String>) {
        for (year in FIRST_YEAR..LAST_YEAR) {
            val eligible = listOf(
                LocalDate.of(year, 3, 1) to "삼일절",
                LocalDate.of(year, 5, 1) to "노동절",
                LocalDate.of(year, 5, 5) to "어린이날",
                BUDDHA.getValue(year) to "부처님오신날",
                LocalDate.of(year, 7, 17) to "제헌절",
                LocalDate.of(year, 8, 15) to "광복절",
                LocalDate.of(year, 10, 3) to "개천절",
                LocalDate.of(year, 10, 9) to "한글날",
                LocalDate.of(year, 12, 25) to "기독탄신일",
            )
            eligible.forEach { (date, name) ->
                val overlaps = result[date]?.contains('·') == true
                if (date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) || overlaps) {
                    add(result, nextFreeWeekday(date.plusDays(1), result), "$name 대체공휴일")
                }
            }
        }
    }

    private fun addThreeDaySubstitute(
        result: MutableMap<LocalDate, String>,
        centers: Map<Int, LocalDate>,
        name: String,
    ) {
        centers.values.forEach { center ->
            val days = listOf(center.minusDays(1), center, center.plusDays(1))
            if (days.any { it.dayOfWeek == DayOfWeek.SUNDAY }) {
                add(result, nextFreeWeekday(center.plusDays(2), result), "$name 대체공휴일")
            }
        }
    }

    private fun nextFreeWeekday(start: LocalDate, result: Map<LocalDate, String>): LocalDate {
        var date = start
        while (date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) || result.containsKey(date)) {
            date = date.plusDays(1)
        }
        return date
    }

    private fun addThreeDayHoliday(result: MutableMap<LocalDate, String>, center: LocalDate, name: String) {
        add(result, center.minusDays(1), "$name 연휴")
        add(result, center, name)
        add(result, center.plusDays(1), "$name 연휴")
    }

    private fun add(result: MutableMap<LocalDate, String>, date: LocalDate, name: String) {
        result[date] = result[date]?.let { existing ->
            if (existing.split('·').contains(name)) existing else "$existing·$name"
        } ?: name
    }

    companion object {
        const val FIRST_YEAR = 2026
        const val LAST_YEAR = 2031
        private val SEOLLAL = mapOf(
            2026 to LocalDate.of(2026, 2, 17), 2027 to LocalDate.of(2027, 2, 7),
            2028 to LocalDate.of(2028, 1, 27), 2029 to LocalDate.of(2029, 2, 13),
            2030 to LocalDate.of(2030, 2, 3), 2031 to LocalDate.of(2031, 1, 23),
        )
        private val CHUSEOK = mapOf(
            2026 to LocalDate.of(2026, 9, 25), 2027 to LocalDate.of(2027, 9, 15),
            2028 to LocalDate.of(2028, 10, 3), 2029 to LocalDate.of(2029, 9, 22),
            2030 to LocalDate.of(2030, 9, 12), 2031 to LocalDate.of(2031, 10, 1),
        )
        private val BUDDHA = mapOf(
            2026 to LocalDate.of(2026, 5, 24), 2027 to LocalDate.of(2027, 5, 13),
            2028 to LocalDate.of(2028, 5, 2), 2029 to LocalDate.of(2029, 5, 20),
            2030 to LocalDate.of(2030, 5, 9), 2031 to LocalDate.of(2031, 5, 28),
        )
    }
}
