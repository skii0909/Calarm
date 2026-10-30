package com.smartworkalarm.outlook

import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GraphEventParserTest {
    @Test fun `UTC response for Korea all day preserves original September nineteenth`() {
        val parsed = GraphEventParser.parse("calendar", event("2026-09-18T15:00:00.0000000", "2026-09-19T15:00:00.0000000", "Asia/Seoul", true))!!
        assertEquals(LocalDate.parse("2026-09-19"), parsed.allDayStart)
        assertEquals(LocalDate.parse("2026-09-20"), parsed.allDayEnd)
    }

    @Test fun `Windows timezone handles daylight saving transition on all day calendar`() {
        val parsed = GraphEventParser.parse("calendar", event("2026-11-01T07:00:00", "2026-11-02T08:00:00", "Pacific Standard Time", true))!!
        assertEquals(LocalDate.parse("2026-11-01"), parsed.allDayStart)
        assertEquals(LocalDate.parse("2026-11-02"), parsed.allDayEnd)
    }

    @Test fun `timed recurrence occurrence preserves absolute instant and subject`() {
        val parsed = GraphEventParser.parse("calendar", event("2026-09-19T00:00:00", "2026-09-19T01:00:00", "Korea Standard Time", false))!!
        assertEquals(Instant.parse("2026-09-19T00:00:00Z"), parsed.start)
        assertNull(parsed.allDayStart)
        assertEquals("야간", parsed.title)
    }

    @Test fun `cancelled occurrences and malformed date ranges do not become active events`() {
        assertNull(GraphEventParser.parse("calendar", JSONObject().put("isCancelled", true)))
        assertThrows(OutlookException::class.java) {
            GraphEventParser.parse("calendar", event("2026-09-19T02:00:00", "2026-09-19T01:00:00", "UTC", false))
        }
    }

    @Test fun `unrecognized original timezone fails rather than shifting all day date`() {
        assertThrows(OutlookException::class.java) {
            GraphEventParser.parse("calendar", event("2026-09-19T00:00:00", "2026-09-20T00:00:00", "not/a/zone", true))
        }
    }

    private fun event(start: String, end: String, originalZone: String, allDay: Boolean) = JSONObject().apply {
        put("id", "opaque/occurrence=="); put("subject", "야간"); put("isCancelled", false); put("isAllDay", allDay)
        put("originalStartTimeZone", originalZone); put("originalEndTimeZone", originalZone)
        put("start", JSONObject().put("dateTime", start).put("timeZone", "UTC"))
        put("end", JSONObject().put("dateTime", end).put("timeZone", "UTC"))
    }
}
