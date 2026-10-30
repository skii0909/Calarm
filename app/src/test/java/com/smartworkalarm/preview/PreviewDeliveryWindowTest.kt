package com.smartworkalarm.preview

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PreviewDeliveryWindowTest {
    private val zone = ZoneId.of("Asia/Seoul")
    @Test fun `scheduled target remains valid on originating evening`() {
        assertTrue(PreviewDeliveryWindow.isCurrent(LocalDate.of(2026, 10, 6),
            Instant.parse("2026-10-05T13:00:05Z"), zone))
    }
    @Test fun `previous evening work expires at midnight instead of checking another day`() {
        assertFalse(PreviewDeliveryWindow.isCurrent(LocalDate.of(2026, 10, 6),
            Instant.parse("2026-10-05T15:00:00Z"), zone))
    }
    @Test fun `work cannot run before scheduled evening`() {
        assertFalse(PreviewDeliveryWindow.isCurrent(LocalDate.of(2026, 10, 6),
            Instant.parse("2026-10-05T12:59:59Z"), zone))
    }
}
