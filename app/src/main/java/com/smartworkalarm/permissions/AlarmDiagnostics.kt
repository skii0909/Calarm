package com.smartworkalarm.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import com.smartworkalarm.alarm.AlarmExecutionJournal
import com.smartworkalarm.alarm.AlarmScheduleAudit
import com.smartworkalarm.alarm.AlarmScheduleAuditStore
import java.time.Clock
import java.time.Instant
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

enum class DiagnosticStatus {
    OK,
    ACTION_REQUIRED,
    NOT_APPLICABLE,
    UNKNOWN,
}

enum class AlarmDiagnosticKind {
    NOTIFICATIONS,
    EXACT_ALARM,
    FULL_SCREEN,
    BATTERY,
    SCHEDULE,
    EXECUTION_HISTORY,
}

data class AlarmDiagnosticItem(
    val kind: AlarmDiagnosticKind,
    val status: DiagnosticStatus,
    val summary: String,
)

fun interface AlarmDiagnosticsSource {
    fun read(): List<AlarmDiagnosticItem>
}

object EmptyAlarmDiagnosticsSource : AlarmDiagnosticsSource {
    override fun read(): List<AlarmDiagnosticItem> = emptyList()
}

class AndroidAlarmDiagnosticsSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val auditStore: AlarmScheduleAuditStore,
    private val clock: Clock,
    private val executionJournal: AlarmExecutionJournal,
) : AlarmDiagnosticsSource {
    override fun read(): List<AlarmDiagnosticItem> {
        val sdk = Build.VERSION.SDK_INT
        val notificationsGranted = sdk < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val exactGranted = if (Build.VERSION.SDK_INT >= 31) canScheduleExactAlarms() else true
        val fullScreenGranted = if (Build.VERSION.SDK_INT >= 34) canUseFullScreenIntent() else true
        val batteryIgnored = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
        return evaluateAlarmDiagnostics(
            sdkInt = sdk,
            notificationsGranted = notificationsGranted,
            exactAlarmGranted = exactGranted,
            fullScreenAlarmGranted = fullScreenGranted,
            batteryOptimizationIgnored = batteryIgnored,
            audit = auditStore.read(),
            now = clock.instant(),
        ) + AlarmDiagnosticItem(
            AlarmDiagnosticKind.EXECUTION_HISTORY,
            DiagnosticStatus.NOT_APPLICABLE,
            executionJournal.readSummary(),
        )
    }

    @RequiresApi(31)
    private fun canScheduleExactAlarms(): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    @RequiresApi(34)
    private fun canUseFullScreenIntent(): Boolean =
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}

internal fun evaluateAlarmDiagnostics(
    sdkInt: Int,
    notificationsGranted: Boolean,
    exactAlarmGranted: Boolean,
    fullScreenAlarmGranted: Boolean,
    batteryOptimizationIgnored: Boolean,
    audit: AlarmScheduleAudit,
    now: Instant? = null,
): List<AlarmDiagnosticItem> = listOf(
    permissionDiagnostic(
        AlarmDiagnosticKind.NOTIFICATIONS,
        sdkInt < 33,
        notificationsGranted,
        "알람 알림을 표시할 수 있습니다.",
        "알림 권한을 허용해 주세요.",
    ),
    permissionDiagnostic(
        AlarmDiagnosticKind.EXACT_ALARM,
        sdkInt < 31,
        exactAlarmGranted,
        "정확한 시각에 알람을 예약할 수 있습니다.",
        "정확한 알람 권한을 허용해 주세요.",
    ),
    permissionDiagnostic(
        AlarmDiagnosticKind.FULL_SCREEN,
        sdkInt < 34,
        fullScreenAlarmGranted,
        "잠금화면 위에 알람 화면을 표시할 수 있습니다.",
        "전체화면 알람 권한을 허용해 주세요.",
    ),
    AlarmDiagnosticItem(
        kind = AlarmDiagnosticKind.BATTERY,
        status = if (batteryOptimizationIgnored) DiagnosticStatus.OK else DiagnosticStatus.ACTION_REQUIRED,
        summary = if (batteryOptimizationIgnored) {
            "배터리 제한 없이 실행할 수 있습니다."
        } else {
            "배터리 절전으로 알람이 지연될 수 있습니다."
        },
    ),
    scheduleDiagnostic(audit, now),
)

private fun permissionDiagnostic(
    kind: AlarmDiagnosticKind,
    notApplicable: Boolean,
    granted: Boolean,
    okSummary: String,
    missingSummary: String,
): AlarmDiagnosticItem = when {
    notApplicable -> AlarmDiagnosticItem(kind, DiagnosticStatus.NOT_APPLICABLE, "이 Android 버전에서는 별도 설정이 필요하지 않습니다.")
    granted -> AlarmDiagnosticItem(kind, DiagnosticStatus.OK, okSummary)
    else -> AlarmDiagnosticItem(kind, DiagnosticStatus.ACTION_REQUIRED, missingSummary)
}

private fun scheduleDiagnostic(audit: AlarmScheduleAudit, now: Instant?): AlarmDiagnosticItem {
    if (
        audit.activeAlarmCount <= 0 ||
        audit.nextTriggerAt == null ||
        audit.lastSuccessfulScheduleAt == null ||
        (now != null && audit.nextTriggerAt.isBefore(now))
    ) {
        return AlarmDiagnosticItem(
            AlarmDiagnosticKind.SCHEDULE,
            DiagnosticStatus.UNKNOWN,
            "확인할 수 있는 활성 알람 예약 기록이 없습니다.",
        )
    }
    return AlarmDiagnosticItem(
        AlarmDiagnosticKind.SCHEDULE,
        DiagnosticStatus.OK,
        "활성 예약 ${audit.activeAlarmCount}개 · 다음 알람 ${audit.nextTriggerAt}",
    )
}
