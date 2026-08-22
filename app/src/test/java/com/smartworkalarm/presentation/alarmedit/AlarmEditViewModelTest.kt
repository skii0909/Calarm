package com.smartworkalarm.presentation.alarmedit

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.RepeatDay
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.domain.usecase.DeleteAlarmUseCase
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `saving requires at least one repeat day`() = runTest(dispatcher) {
        val viewModel = viewModelWith(FakeSaver())
        viewModel.onNameChanged("출근")
        viewModel.onTimeChanged(7, 0)

        viewModel.save()

        assertEquals("반복 요일을 하나 이상 선택하세요", viewModel.state.value.repeatDaysError)
    }

    @Test
    fun `vacation switch is preserved in saved alarm`() = runTest(dispatcher) {
        val saver = FakeSaver()
        val viewModel = viewModelWith(saver)
        viewModel.onNameChanged("출근")
        viewModel.onTimeChanged(7, 0)
        viewModel.toggleDay(RepeatDay.MONDAY)
        viewModel.onSkipOnCalendarMatchChanged(true)

        viewModel.save()
        advanceUntilIdle()

        assertTrue(saver.saved.single().skipOnCalendarMatch)
    }

    private class FakeSaver : AlarmSaver {
        val saved = mutableListOf<Alarm>()
        override suspend fun save(alarm: Alarm): Result<Long> {
            saved += alarm
            return Result.success(1)
        }
    }

    private fun viewModelWith(saver: AlarmSaver): AlarmEditViewModel {
        val repository = object : AlarmRepository {
            override fun observeAll() = kotlinx.coroutines.flow.emptyFlow<List<Alarm>>()
            override suspend fun getById(id: Long) = null
            override suspend fun getEnabled() = emptyList<Alarm>()
            override suspend fun upsert(alarm: Alarm) = 0L
            override suspend fun delete(id: Long) = Unit
            override suspend fun setEnabled(id: Long, enabled: Boolean) = Unit
        }
        val scheduler = object : AlarmScheduler {
            override fun schedule(alarmId: Long, triggerAt: Instant) = ScheduleResult.Scheduled
            override fun cancel(alarmId: Long) = Unit
            override fun scheduleSnooze(alarmId: Long, triggerAt: Instant) = ScheduleResult.Scheduled
        }
        return AlarmEditViewModel(saver, repository, DeleteAlarmUseCase(repository, scheduler))
    }
}
