package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.repository.AlarmRepository
import javax.inject.Inject

class GetAlarmsUseCase @Inject constructor(repository: AlarmRepository) {
    val alarms = repository.observeAll()
}
