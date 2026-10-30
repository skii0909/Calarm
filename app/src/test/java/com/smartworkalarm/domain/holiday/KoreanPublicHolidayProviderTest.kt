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

    @Test fun `2026 law includes labour and constitution holidays through supported years`() {
        for (year in 2026..2031) {
            assertEquals("노동절", provider.find(LocalDate.of(year, 5, 1))?.name)
            assertEquals("제헌절", provider.find(LocalDate.of(year, 7, 17))?.name)
        }
        assertEquals("노동절 대체공휴일", provider.find(LocalDate.of(2027, 5, 3))?.name)
        assertEquals("제헌절 대체공휴일", provider.find(LocalDate.of(2027, 7, 19))?.name)
        assertNull(provider.find(LocalDate.of(2027, 5, 4)))
        assertNull(provider.find(LocalDate.of(2027, 7, 20)))
    }

    @Test fun `holiday periods end before the following working day`() {
        assertEquals("개천절 대체공휴일", provider.find(LocalDate.of(2026, 10, 5))?.name)
        assertNull(provider.find(LocalDate.of(2026, 10, 6)))
        assertNull(provider.find(LocalDate.of(2026, 10, 7)))
        assertNull(provider.find(LocalDate.of(2026, 10, 8)))
        assertEquals("한글날", provider.find(LocalDate.of(2026, 10, 9))?.name)
        assertEquals("설날 대체공휴일", provider.find(LocalDate.of(2027, 2, 9))?.name)
        assertNull(provider.find(LocalDate.of(2027, 2, 10)))
        assertEquals("개천절 대체공휴일", provider.find(LocalDate.of(2028, 10, 5))?.name)
        assertNull(provider.find(LocalDate.of(2028, 10, 6)))
    }
}
