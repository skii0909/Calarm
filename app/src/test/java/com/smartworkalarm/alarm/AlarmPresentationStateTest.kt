package com.smartworkalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmPresentationStateTest {
    @Test
    fun `a second intent payload replaces every display and action field`() {
        val state = AlarmPresentationState()
        state.replace(
            FakeAlarmPayload(
                longs = mapOf(
                    AlarmSchedulerImpl.EXTRA_ALARM_ID to 11L,
                    AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT to 1_111L,
                ),
                booleans = mapOf(
                    AlarmRingingService.EXTRA_IS_SHIFT_ALARM to false,
                    AlarmRingingService.EXTRA_CALENDAR_WARNING to false,
                    AlarmRingingService.EXTRA_SNOOZE_ENABLED to true,
                ),
                strings = mapOf(
                    AlarmNotificationFactory.EXTRA_ALARM_NAME to "첫 알람",
                    AlarmRingingService.EXTRA_RINGING_SESSION_KEY to "v2:regular:11:attempt-a",
                ),
                ints = mapOf(AlarmRingingService.EXTRA_SNOOZE_MINUTES to 5),
            ),
        )

        state.replace(
            FakeAlarmPayload(
                longs = mapOf(
                    AlarmSchedulerImpl.EXTRA_ALARM_ID to 22L,
                    AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT to 2_222L,
                ),
                booleans = mapOf(
                    AlarmRingingService.EXTRA_IS_SHIFT_ALARM to true,
                    AlarmRingingService.EXTRA_CALENDAR_WARNING to true,
                    AlarmRingingService.EXTRA_SNOOZE_ENABLED to false,
                ),
                strings = mapOf(
                    AlarmNotificationFactory.EXTRA_ALARM_NAME to "두 번째 근무 알람",
                    AlarmRingingService.EXTRA_RINGING_SESSION_KEY to "v2:shift:22:attempt-b",
                ),
                ints = mapOf(AlarmRingingService.EXTRA_SNOOZE_MINUTES to 17),
            ),
        )

        assertEquals(
            AlarmPresentation(
                sessionKey = RingingSessionKey.create(
                    isShiftAlarm = true,
                    alarmId = 22L,
                    attemptToken = "attempt-b",
                ),
                alarmId = 22L,
                isShiftAlarm = true,
                alarmName = "두 번째 근무 알람",
                calendarWarning = true,
                snoozeEnabled = false,
                snoozeMinutes = 17,
                scheduledOccurrenceAt = 2_222L,
            ),
            state.current,
        )
    }

    @Test
    fun `legacy payload cannot reconstruct an actionable session key`() {
        val presentation = parseAlarmPresentation(
            FakeAlarmPayload(
                longs = mapOf(
                    AlarmSchedulerImpl.EXTRA_ALARM_ID to 31L,
                    AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT to 9_999L,
                ),
                booleans = mapOf(AlarmRingingService.EXTRA_IS_SHIFT_ALARM to true),
            ),
        )

        assertNull(presentation.sessionKey)
        assertEquals(9_999L, presentation.scheduledOccurrenceAt)
    }
}

private class FakeAlarmPayload(
    private val longs: Map<String, Long> = emptyMap(),
    private val booleans: Map<String, Boolean> = emptyMap(),
    private val strings: Map<String, String> = emptyMap(),
    private val ints: Map<String, Int> = emptyMap(),
) : AlarmPayload {
    override fun getLong(key: String, defaultValue: Long): Long = longs[key] ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = booleans[key] ?: defaultValue

    override fun getString(key: String): String? = strings[key]

    override fun getInt(key: String, defaultValue: Int): Int = ints[key] ?: defaultValue
}
