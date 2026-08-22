package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.model.PreviewChangeType
import com.smartworkalarm.domain.model.PreviewDecision
import javax.inject.Inject

class CompareAlarmPreviewStateUseCase @Inject constructor() {
    fun execute(previous: PreviewDecision?, current: PreviewDecision): PreviewChangeType? = when {
        current == PreviewDecision.UNKNOWN -> null
        current == previous -> null
        current == PreviewDecision.SKIP -> PreviewChangeType.SKIPPED
        previous == PreviewDecision.SKIP && current == PreviewDecision.RING -> PreviewChangeType.RESUMED
        else -> null
    }
}
