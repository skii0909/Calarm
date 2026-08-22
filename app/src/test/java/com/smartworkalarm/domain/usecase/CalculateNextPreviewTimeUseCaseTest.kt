package com.smartworkalarm.domain.usecase

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateNextPreviewTimeUseCaseTest {
    private val calculate = CalculateNextPreviewTimeUseCase()
    private val zone = ZoneId.of("Asia/Seoul")

    @Test fun `before 22 uses today and at 22 uses tomorrow`() {
        assertEquals(
            Instant.parse("2026-08-22T13:00:00Z"),
            calculate.execute(Instant.parse("2026-08-22T12:59:00Z"), zone),
        )
        assertEquals(
            Instant.parse("2026-08-23T13:00:00Z"),
            calculate.execute(Instant.parse("2026-08-22T13:00:00Z"), zone),
        )
    }
}
