package com.smartworkalarm.domain.repository

import com.smartworkalarm.domain.model.CalendarConnectionState

interface CalendarConnectionRepository {
    fun getState(): CalendarConnectionState
}
