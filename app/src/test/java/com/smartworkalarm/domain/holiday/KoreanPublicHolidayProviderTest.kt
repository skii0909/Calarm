package com.smartworkalarm.domain.holiday

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KoreanPublicHolidayProviderTest {
    private val provider = KoreanPublicHolidayProvider()

    @Test fun `official 2026 substitute holidays are included`() {
        assertEquals("광복절 대체공휴일", provider.find(LocalDate.of(2026, 8, 17))?.name)
        assertEquals("부처님오신날 대체공휴일", provider.find(LocalDate.of(2026, 5, 25))?.name)
    }

    @Test fun `lunar and projected holidays through 2031 are included`() {
        assertEquals("설날", provider.find(LocalDate.of(2029, 2, 13))?.name)
        assertEquals("추석", provider.find(LocalDate.of(2031, 10, 1))?.name)
        assertEquals("신정", provider.find(LocalDate.of(2031, 1, 1))?.name)
    }

    @Test fun `non holiday and dates after supported range return null`() {
        assertNull(provider.find(LocalDate.of(2026, 8, 18)))
        assertNull(provider.find(LocalDate.of(2032, 1, 1)))
    }
}
