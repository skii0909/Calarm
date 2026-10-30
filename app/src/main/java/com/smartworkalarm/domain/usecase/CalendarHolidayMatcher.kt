package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.model.CalendarEvent

object CalendarHolidayMatcher {
    private val whitespace = Regex("\\s+")
    private val holidayNames = setOf(
        "신정",
        "새해첫날",
        "삼일절",
        "3·1절",
        "3.1절",
        "설날",
        "설날연휴",
        "추석",
        "추석연휴",
        "부처님오신날",
        "노동절",
        "근로자의날",
        "어린이날",
        "현충일",
        "제헌절",
        "광복절",
        "개천절",
        "한글날",
        "성탄절",
        "기독탄신일",
        "크리스마스",
        "대통령선거일",
        "국회의원선거일",
        "전국동시지방선거일",
    )
    private val substituteNames = holidayNames - setOf(
        "신정", "새해첫날", "현충일", "대통령선거일", "국회의원선거일", "전국동시지방선거일",
    )
    private val canonicalTitles = holidayNames + setOf("임시공휴일", "대한민국임시공휴일", "대체공휴일") +
        substituteNames.flatMap { name ->
            listOf("${name}대체공휴일", "대체공휴일($name)", "대체공휴일（$name）")
        }

    // A meeting about a holiday does not make its date a national holiday.
    fun matches(event: CalendarEvent): Boolean = event.allDay && matches(event.title)

    fun matches(title: String?): Boolean = title?.let { it.replace(whitespace, "") in canonicalTitles } ?: false
}
