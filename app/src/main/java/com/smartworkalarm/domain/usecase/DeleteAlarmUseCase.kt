package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import javax.inject.Inject

class DeleteAlarmUseCase @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
) {
    suspend fun execute(id: Long) {
        scheduler.cancel(id)
        repository.delete(id)
    }
}
