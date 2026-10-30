package com.smartworkalarm.alarm

import android.content.Context
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AlarmExecutionJournalTest {
    private val context = RuntimeEnvironment.getApplication().applicationContext
    private val clock = Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val preferences get() = context.getSharedPreferences("alarm_execution_journal", Context.MODE_PRIVATE)
    @Before fun clear() { preferences.edit().clear().commit() }

    @Test fun `records persist newest first and describe sound as request only`() {
        AlarmExecutionJournal(context, clock).apply {
            record(false, 11, "RECEIVED")
            record(true, 12, "Ringing")
        }
        val summary = AlarmExecutionJournal(context, clock).readSummary()
        val rows = summary.lines()
        assertTrue(rows[0].contains("교대 #12"))
        assertTrue(rows[0].contains("소리 실행 요청"))
        assertTrue(rows[1].contains("일반 #11"))
        assertTrue(rows[1].contains("알람 신호 수신"))
    }
    @Test fun `only newest twenty records retained`() {
        val journal = AlarmExecutionJournal(context, clock)
        repeat(25) { journal.record(false, it.toLong(), "RECEIVED") }
        val rows = journal.readSummary().lines()
        assertEquals(20, rows.size)
        assertTrue(rows.first().contains("#24"))
        assertTrue(rows.last().contains("#5"))
    }
    @Test fun `corrupt persistence does not block later records`() {
        preferences.edit().putString("records", "bad\nnot|a|valid|record").commit()
        val journal = AlarmExecutionJournal(context, clock)
        assertTrue(journal.readSummary().contains("아직 없습니다"))
        journal.record(false, 1, "FAILED")
        assertTrue(journal.readSummary().contains("실행 처리 실패"))
    }
    @Test fun `arbitrary event titles or tokens cannot enter persistent status`() {
        val journal = AlarmExecutionJournal(context, clock)
        journal.record(false, 1, "secret-event-title")
        assertFalse(preferences.getString("records", "")!!.contains("secret-event-title"))
        assertTrue(journal.readSummary().contains("확인할 수 없는 상태"))
    }
}
