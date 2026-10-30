package com.smartworkalarm.outlook

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import org.json.JSONObject

internal object GraphEventParser {
    fun parse(calendarId: String, json: JSONObject): OutlookEvent? = try {
        if (json.optBoolean("isCancelled", false)) null else {
            val startValue = json.getJSONObject("start")
            val endValue = json.getJSONObject("end")
            val start = instant(startValue)
            val end = instant(endValue)
            require(end > start)
            val allDay = json.getBoolean("isAllDay")
            val originalStartZone = json.textOrNull("originalStartTimeZone") ?: startValue.getString("timeZone")
            val originalEndZone = json.textOrNull("originalEndTimeZone") ?: endValue.getString("timeZone")
            val dayStart = if (allDay) start.atZone(zone(originalStartZone)).toLocalDate() else null
            val dayEnd = if (allDay) end.atZone(zone(originalEndZone)).toLocalDate() else null
            if (allDay) require(dayEnd!! > dayStart!!)
            OutlookEvent(calendarId, json.getString("id"), json.textOrNull("subject"), start, end, dayStart, dayEnd)
        }
    } catch (_: Exception) { throw OutlookException(OutlookIssue.INVALID_DATA) }

    private fun instant(value: JSONObject): Instant {
        val text = value.getString("dateTime")
        return runCatching { OffsetDateTime.parse(text).toInstant() }.getOrElse {
            LocalDateTime.parse(text).atZone(zone(value.getString("timeZone"))).toInstant()
        }
    }

    private fun zone(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrElse {
        val mapped = android.icu.util.TimeZone.getIDForWindowsID(id, "001")
            ?: throw OutlookException(OutlookIssue.INVALID_DATA)
        ZoneId.of(mapped)
    }
}
