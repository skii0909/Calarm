package com.smartworkalarm.domain.repository

import com.smartworkalarm.domain.model.Alarm
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    fun observeAll(): Flow<List<Alarm>>
    suspend fun getById(id: Long): Alarm?
    suspend fun getEnabled(): List<Alarm>
    suspend fun upsert(alarm: Alarm): Long
    suspend fun delete(id: Long)
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
