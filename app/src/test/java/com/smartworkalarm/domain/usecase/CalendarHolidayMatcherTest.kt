package com.smartworkalarm.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarHolidayMatcherTest {
    @Test fun `ordinary event mentioning a holiday is not a national holiday`() {
        listOf("한글날 행사 준비", "공휴일 근무 회의", "추석 선물 주문", "학생회 선거일", "미국 공휴일").forEach {
            assertFalse(it, CalendarHolidayMatcher.matches(it))
        }
    }

    @Test fun `canonical holiday names tolerate whitespace`() {
        listOf(" 대한민국 임시공휴일 ", "대체공휴일", "개천절 대체공휴일", "부처님 오신 날", "제헌절", "노동절").forEach {
            assertTrue(it, CalendarHolidayMatcher.matches(it))
        }
    }

    @Test fun `holidays without substitute entitlement do not gain one from a title`() {
        listOf("신정 대체공휴일", "현충일 대체공휴일", "대통령선거일 대체공휴일").forEach {
            assertFalse(it, CalendarHolidayMatcher.matches(it))
        }
    }
}
