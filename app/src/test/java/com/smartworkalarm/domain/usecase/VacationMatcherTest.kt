package com.smartworkalarm.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VacationMatcherTest {
    @Test
    fun `vacation keyword matches title variants`() {
        listOf("휴가", "여름휴가", " 가족 휴가 ").forEach {
            assertTrue(it, VacationMatcher.isVacationTitle(it))
        }
    }

    @Test
    fun `non vacation title does not match`() {
        listOf<String?>(null, "", "회의", "연차", "출장").forEach {
            assertFalse(it, VacationMatcher.isVacationTitle(it))
        }
    }
}
