package com.smartworkalarm.presentation.alarmedit

import com.smartworkalarm.domain.model.Alarm

interface AlarmSaver {
    suspend fun save(alarm: Alarm): Result<Long>
}
