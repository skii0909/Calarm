package com.smartworkalarm.presentation.alarmedit

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.RepeatDay
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.domain.usecase.DeleteAlarmUseCase
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `holiday volume and snooze settings are preserved and snooze is bounded`() = runTest(dispatcher) {
        val saver = FakeSaver()
        val viewModel = viewModelWith(saver)
        viewModel.onNameChanged("출근")
        viewModel.toggleDay(RepeatDay.MONDAY)
        viewModel.onSkipOnPublicHolidayChanged(true)
        viewModel.onGradualVolumeChanged(true)
        viewModel.onSnoozeMinutesChanged(40)

        assertEquals(30, viewModel.state.value.snoozeMinutes)
        viewModel.onSnoozeMinutesChanged(12)
        viewModel.save()
        advanceUntilIdle()

        val saved = saver.saved.single()
        assertTrue(saved.skipOnPublicHoliday)
        assertTrue(saved.gradualVolume)
        assertEquals(12, saved.snoozeMinutes)
    }

    @Test
    fun `new alarm defaults snooze to enabled`() = runTest(dispatcher) {
        val viewModel = viewModelWith(FakeSaver())

        assertTrue(viewModel.state.value.snoozeEnabled)
    }

    @Test
    fun `new and freshly loaded alarms are pristine`() = runTest(dispatcher) {
        val existing = Alarm(
            id = 7,
            name = "출근",
            hour = 7,
            minute = 0,
            repeatDaysMask = RepeatDay.MONDAY.bit,
            enabled = true,
            skipOnCalendarMatch = true,
        )
        val newAlarm = viewModelWith(FakeSaver())
        val loadedAlarm = viewModelWith(FakeSaver(), existing)

        loadedAlarm.load(existing.id)
        advanceUntilIdle()

        assertFalse(newAlarm.state.value.hasUnsavedChanges)
        assertFalse(loadedAlarm.state.value.hasUnsavedChanges)
    }

    @Test
    fun `changing and reverting an alarm field updates dirty state from baseline`() = runTest(dispatcher) {
        val existing = Alarm(
            id = 7,
            name = "출근",
            hour = 7,
            minute = 0,
            repeatDaysMask = RepeatDay.MONDAY.bit,
            enabled = true,
            skipOnCalendarMatch = true,
        )
        val viewModel = viewModelWith(FakeSaver(), existing)
        viewModel.load(existing.id)
        advanceUntilIdle()

        viewModel.onNameChanged("조기 출근")
        assertTrue(viewModel.state.value.hasUnsavedChanges)

        viewModel.onNameChanged("출근")
        assertFalse(viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun `alarm validation failure stays unsaved and dirty`() = runTest(dispatcher) {
        val viewModel = viewModelWith(FakeSaver())
        viewModel.onNameChanged("출근")

        viewModel.save()

        assertFalse(viewModel.state.value.saved)
        assertTrue(viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun `successful alarm save and delete clear dirty state`() = runTest(dispatcher) {
        val saver = FakeSaver()
        val viewModel = viewModelWith(saver)
        viewModel.onNameChanged("출근")
        viewModel.toggleDay(RepeatDay.MONDAY)

        viewModel.save()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.hasUnsavedChanges)

        val existing = Alarm(
            id = 7,
            name = "출근",
            hour = 7,
            minute = 0,
            repeatDaysMask = RepeatDay.MONDAY.bit,
            enabled = true,
            skipOnCalendarMatch = true,
        )
        val deletingViewModel = viewModelWith(FakeSaver(), existing)
        deletingViewModel.load(existing.id)
        advanceUntilIdle()
        deletingViewModel.onNameChanged("조기 출근")

        deletingViewModel.delete()
        advanceUntilIdle()

        assertFalse(deletingViewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun `editing while alarm save is in flight stays dirty and does not exit`() = runTest(dispatcher) {
        val saveCompletion = CompletableDeferred<Unit>()
        val saver = FakeSaver(saveCompletion)
        val viewModel = viewModelWith(saver)
        viewModel.onNameChanged("저장할 이름")
        viewModel.toggleDay(RepeatDay.MONDAY)

        viewModel.save()
        runCurrent()
        assertEquals("저장할 이름", saver.saved.single().name)

        viewModel.onNameChanged("저장 중 변경")
        saveCompletion.complete(Unit)
        advanceUntilIdle()

        assertEquals("저장 중 변경", viewModel.state.value.name)
        assertEquals(1L, viewModel.state.value.id)
        assertFalse(viewModel.state.value.saved)
        assertTrue(viewModel.state.value.hasUnsavedChanges)
        viewModel.onNameChanged("저장할 이름")
        assertFalse(viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun `disabling snooze preserves selected minutes on save`() = runTest(dispatcher) {
        val saver = FakeSaver()
        val viewModel = viewModelWith(saver)
        viewModel.onNameChanged("출근")
        viewModel.toggleDay(RepeatDay.MONDAY)
        viewModel.onSnoozeMinutesChanged(19)
        viewModel.onSnoozeEnabledChanged(false)

        viewModel.save()
        advanceUntilIdle()

        assertFalse(saver.saved.single().snoozeEnabled)
        assertEquals(19, saver.saved.single().snoozeMinutes)
    }

    @Test
    fun `saving an existing alarm preserves disabled snooze after an unrelated edit`() = runTest(dispatcher) {
        val existing = Alarm(
            id = 7,
            name = "출근",
            hour = 7,
            minute = 0,
            repeatDaysMask = RepeatDay.MONDAY.bit,
            enabled = true,
            skipOnCalendarMatch = true,
            snoozeEnabled = false,
        )
        val saver = FakeSaver()
        val viewModel = viewModelWith(saver, existing)

        viewModel.load(existing.id)
        advanceUntilIdle()
        viewModel.onNameChanged("조기 출근")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(false, saver.saved.single().snoozeEnabled)
    }

    private class FakeSaver(
        private val completion: CompletableDeferred<Unit>? = null,
    ) : AlarmSaver {
        val saved = mutableListOf<Alarm>()
        override suspend fun save(alarm: Alarm): Result<Long> {
            saved += alarm
            completion?.await()
            return Result.success(1)
        }
    }

    private fun viewModelWith(saver: AlarmSaver, existing: Alarm? = null): AlarmEditViewModel {
        val repository = object : AlarmRepository {
            override fun observeAll() = kotlinx.coroutines.flow.emptyFlow<List<Alarm>>()
            override suspend fun getById(id: Long) = existing?.takeIf { it.id == id }
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
