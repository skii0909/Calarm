package com.smartworkalarm.alarm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Bounded local delivery history. Diagnostics must never interrupt alarm delivery. */
@Singleton
class AlarmExecutionJournal @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val preferences by lazy {
        context.getSharedPreferences("alarm_execution_journal", Context.MODE_PRIVATE)
    }

    @Synchronized
    fun record(isShift: Boolean, id: Long, status: String) {
        runCatching {
            val safeStatus = status.takeIf { it in labels } ?: "UNKNOWN"
            val latest = Entry(clock.millis(), isShift, id, safeStatus)
            val rows = (listOf(latest) + readEntries()).take(20)
            preferences.edit().putString("records", rows.joinToString("\n") { entry ->
                "${entry.at}|${if (entry.isShift) "S" else "R"}|${entry.id}|${entry.status}"
            }).apply()
        }
    }

    @Synchronized
    fun readSummary(): String = runCatching {
        val entries = readEntries()
        if (entries.isEmpty()) return@runCatching "알람 실행 기록이 아직 없습니다."
        val format = DateTimeFormatter.ofPattern("MM.dd HH:mm:ss").withZone(ZoneId.systemDefault())
        entries.joinToString("\n") { entry ->
            val kind = if (entry.isShift) "교대" else "일반"
            "${format.format(Instant.ofEpochMilli(entry.at))} · $kind #${entry.id} · ${labels.getValue(entry.status)}"
        }
    }.getOrDefault("알람 실행 기록을 읽을 수 없습니다.")

    private fun readEntries(): List<Entry> = runCatching {
        preferences.getString("records", "").orEmpty().lineSequence().mapNotNull { line ->
            val fields = line.split('|')
            if (fields.size != 4 || fields[1] !in setOf("S", "R") || fields[3] !in labels) {
                return@mapNotNull null
            }
            val at = fields[0].toLongOrNull() ?: return@mapNotNull null
            val id = fields[2].toLongOrNull() ?: return@mapNotNull null
            Entry(at, fields[1] == "S", id, fields[3])
        }.take(20).toList()
    }.getOrDefault(emptyList())

    private data class Entry(val at: Long, val isShift: Boolean, val id: Long, val status: String)

    private companion object {
        val labels = mapOf(
            "RECEIVED" to "알람 신호 수신",
            "Ringing" to "소리 실행 요청",
            "RingingWithCalendarWarning" to "캘린더 확인 실패 · 소리 실행 요청",
            "SkippedForVacation" to "휴일 또는 일정 조건으로 건너뜀",
            "SkippedForShift" to "교대 근무 조건으로 건너뜀",
            "SkippedByUser" to "사용자가 이번 알람 건너뜀",
            "Ignored" to "꺼졌거나 유효하지 않은 알람",
            "TIMED_OUT" to "실행 처리 시간 초과",
            "FAILED" to "실행 처리 실패",
            "UNKNOWN" to "확인할 수 없는 상태",
        )
    }
}
