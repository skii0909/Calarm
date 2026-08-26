package com.smartworkalarm.prealarm

import java.time.Instant

interface OccurrenceSkipStore {
    fun mark(key: AlarmOccurrenceKey)
    fun consume(key: AlarmOccurrenceKey): Boolean
    fun prune(now: Instant)
}
