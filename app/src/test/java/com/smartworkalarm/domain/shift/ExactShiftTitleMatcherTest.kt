package com.smartworkalarm.domain.shift

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactShiftTitleMatcherTest {
    @Test
    fun `exact matcher ignores surrounding whitespace but rejects partial title`() {
        assertTrue(ExactShiftTitleMatcher.matches("  야간  ", listOf("야간")))
        assertFalse(ExactShiftTitleMatcher.matches("야간 근무 후 회식", listOf("야간")))
    }

    @Test
    fun `exact matcher ignores English case`() {
        assertTrue(ExactShiftTitleMatcher.matches("  NIGHT SHIFT  ", listOf("night shift")))
    }

    @Test
    fun `normalized match trims and case folds the matched calendar title`() {
        assertEquals("night", ExactShiftTitleMatcher.normalizedMatch("  NIGHT  ", listOf("night")))
        assertEquals(null, ExactShiftTitleMatcher.normalizedMatch("night handoff", listOf("night")))
    }

    @Test
    fun `exact matcher rejects null title and empty aliases`() {
        assertFalse(ExactShiftTitleMatcher.matches(null, listOf("야간")))
        assertFalse(ExactShiftTitleMatcher.matches("야간", emptyList()))
    }
}
