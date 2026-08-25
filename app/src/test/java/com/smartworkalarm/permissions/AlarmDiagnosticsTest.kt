package com.smartworkalarm.permissions

import com.smartworkalarm.alarm.AlarmScheduleAudit
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmDiagnosticsTest {
    @Test
    fun `Android 13에서는 전체화면 알람 진단이 해당 없음이다`() {
        val items = evaluateAlarmDiagnostics(
            sdkInt = 33,
            notificationsGranted = true,
            exactAlarmGranted = true,
            fullScreenAlarmGranted = false,
            batteryOptimizationIgnored = true,
            audit = activeAudit(),
        )

        assertEquals(
            DiagnosticStatus.NOT_APPLICABLE,
            items.single { it.kind == AlarmDiagnosticKind.FULL_SCREEN }.status,
        )
    }

    @Test
    fun `정확한 알람 권한이 없으면 확인 필요이다`() {
        val items = evaluateAlarmDiagnostics(
            sdkInt = 36,
            notificationsGranted = true,
            exactAlarmGranted = false,
            fullScreenAlarmGranted = true,
            batteryOptimizationIgnored = true,
            audit = activeAudit(),
        )

        assertEquals(
            DiagnosticStatus.ACTION_REQUIRED,
            items.single { it.kind == AlarmDiagnosticKind.EXACT_ALARM }.status,
        )
    }

    @Test
    fun `신뢰할 예약 기록이 없으면 정상으로 추정하지 않는다`() {
        val items = evaluateAlarmDiagnostics(
            sdkInt = 36,
            notificationsGranted = true,
            exactAlarmGranted = true,
            fullScreenAlarmGranted = true,
            batteryOptimizationIgnored = true,
            audit = AlarmScheduleAudit(null, null, 0),
        )

        assertEquals(
            DiagnosticStatus.UNKNOWN,
            items.single { it.kind == AlarmDiagnosticKind.SCHEDULE }.status,
        )
    }

    @Test
    fun `다음 예약 시각이 이미 지났으면 오래된 기록을 정상으로 표시하지 않는다`() {
        val items = evaluateAlarmDiagnostics(
            sdkInt = 36,
            notificationsGranted = true,
            exactAlarmGranted = true,
            fullScreenAlarmGranted = true,
            batteryOptimizationIgnored = true,
            audit = activeAudit(),
            now = Instant.parse("2026-08-26T00:00:00Z"),
        )

        assertEquals(
            DiagnosticStatus.UNKNOWN,
            items.single { it.kind == AlarmDiagnosticKind.SCHEDULE }.status,
        )
    }

    @Test
    fun `배터리 최적화가 적용되면 확인 필요이다`() {
        val items = evaluateAlarmDiagnostics(
            sdkInt = 36,
            notificationsGranted = true,
            exactAlarmGranted = true,
            fullScreenAlarmGranted = true,
            batteryOptimizationIgnored = false,
            audit = activeAudit(),
        )

        assertEquals(
            DiagnosticStatus.ACTION_REQUIRED,
            items.single { it.kind == AlarmDiagnosticKind.BATTERY }.status,
        )
    }

    private fun activeAudit() = AlarmScheduleAudit(
        lastSuccessfulScheduleAt = Instant.parse("2026-08-25T12:00:00Z"),
        nextTriggerAt = Instant.parse("2026-08-25T22:00:00Z"),
        activeAlarmCount = 2,
    )
}
