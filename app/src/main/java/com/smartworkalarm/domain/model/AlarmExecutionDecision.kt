package com.smartworkalarm.domain.model

enum class AlarmExecutionDecision {
    RING,
    RING_WITH_CALENDAR_WARNING,
    SKIP_FOR_VACATION,
    DO_NOT_RING,
}
