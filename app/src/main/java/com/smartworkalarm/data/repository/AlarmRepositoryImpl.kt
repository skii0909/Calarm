package com.smartworkalarm.data.repository

import com.smartworkalarm.data.local.AlarmDao
import com.smartworkalarm.data.local.toDomain
import com.smartworkalarm.data.local.toEntity
import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.repository.AlarmRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlarmRepositoryImpl @Inject constructor(
    private val dao: AlarmDao,
) : AlarmRepository {
    override fun observeAll(): Flow<List<Alarm>> = dao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun getById(id: Long): Alarm? = dao.getById(id)?.toDomain()

    override suspend fun getEnabled(): List<Alarm> = dao.getEnabled().map { it.toDomain() }

    override suspend fun upsert(alarm: Alarm): Long {
        val createdAt = if (alarm.id == 0L) {
            System.currentTimeMillis()
        } else {
            dao.getById(alarm.id)?.createdAt ?: System.currentTimeMillis()
        }
        val rowId = dao.upsert(alarm.toEntity(createdAt))
        return if (alarm.id == 0L) rowId else alarm.id
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)
}
