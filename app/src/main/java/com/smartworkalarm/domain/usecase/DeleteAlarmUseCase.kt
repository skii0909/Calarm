package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import javax.inject.Inject
import com.smartworkalarm.preview.ActualAlarmSkipStore
import com.smartworkalarm.preview.NoActualAlarmSkipStore
import com.smartworkalarm.data.local.AlarmPreviewDao

class DeleteAlarmUseCase @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val previews: AlarmPreviewDao? = null,
    private val actualSkips: ActualAlarmSkipStore = NoActualAlarmSkipStore,
) {
    suspend fun execute(id: Long) {
        scheduler.cancel(id)
        repository.delete(id)
        previews?.delete(id)
        actualSkips.delete(id)
    }
}
