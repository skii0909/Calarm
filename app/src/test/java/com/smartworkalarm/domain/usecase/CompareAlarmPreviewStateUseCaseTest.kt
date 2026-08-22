package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.model.PreviewChangeType
import com.smartworkalarm.domain.model.PreviewDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompareAlarmPreviewStateUseCaseTest {
    private val compare = CompareAlarmPreviewStateUseCase()

    @Test
    fun `first ring and unknown are not changes`() {
        assertNull(compare.execute(null, PreviewDecision.RING))
        assertNull(compare.execute(PreviewDecision.SKIP, PreviewDecision.UNKNOWN))
    }

    @Test
    fun `first skip and ring to skip report skipped`() {
        assertEquals(PreviewChangeType.SKIPPED, compare.execute(null, PreviewDecision.SKIP))
        assertEquals(
            PreviewChangeType.SKIPPED,
            compare.execute(PreviewDecision.RING, PreviewDecision.SKIP),
        )
    }

    @Test
    fun `skip to ring reports resumed while same states do not change`() {
        assertEquals(
            PreviewChangeType.RESUMED,
            compare.execute(PreviewDecision.SKIP, PreviewDecision.RING),
        )
        assertNull(compare.execute(PreviewDecision.SKIP, PreviewDecision.SKIP))
        assertNull(compare.execute(PreviewDecision.RING, PreviewDecision.RING))
    }
}
