package com.smartworkalarm.domain.usecase

object CalendarHolidayMatcher {
    private val holidayMarkers = listOf(
        "공휴일",
        "선거일",
        "설날",
        "추석",
        "부처님오신날",
        "어린이날",
        "현충일",
        "광복절",
        "개천절",
        "한글날",
        "성탄절",
    )

    fun matches(title: String?): Boolean = title?.let { value ->
        holidayMarkers.any { marker -> value.contains(marker, ignoreCase = true) }
    } ?: false
}
