package com.smartworkalarm.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarKeywordMatcherTest {
    @Test
    fun `normalization trims removes empties and duplicate keywords`() {
        assertEquals(
            listOf("휴가", "연차", "병가"),
            CalendarKeywordMatcher.normalize(" 휴가, 연차,휴가, , 병가 "),
        )
    }

    @Test
    fun `keyword matches at start middle and end of title`() {
        val keywords = listOf("휴가")
        assertTrue(CalendarKeywordMatcher.matches("휴가 - 제주도", keywords))
        assertTrue(CalendarKeywordMatcher.matches("8월 휴가 일정", keywords))
        assertTrue(CalendarKeywordMatcher.matches("여름휴가", keywords))
    }

    @Test
    fun `english matching ignores case and empty keywords do not match`() {
        assertTrue(CalendarKeywordMatcher.matches("BUSINESS Trip", listOf("trip")))
        assertFalse(CalendarKeywordMatcher.matches("휴가", emptyList()))
        assertFalse(CalendarKeywordMatcher.matches(null, listOf("휴가")))
    }
}
