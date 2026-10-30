package com.smartworkalarm.outlook

import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

internal object OutlookStateJson {
    fun encode(state: OutlookState): String = JSONObject().apply {
        put("generation", state.generation)
        state.account?.let { put("account", JSONObject().put("id", it.id).put("username", it.username)) }
        put("catalogLoaded", state.catalogLoaded)
        put("calendars", JSONArray().apply {
            state.calendars.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("default", it.isDefault).put("owner", it.ownerAddress)) }
        })
        put("selected", JSONArray(state.selectedIds.toList()))
        put("events", JSONArray().apply {
            state.events.forEach { event ->
                put(JSONObject().apply {
                    put("calendarId", event.calendarId); put("id", event.id); put("title", event.title)
                    put("start", event.start.toString()); put("end", event.end.toString())
                    put("allDayStart", event.allDayStart?.toString()); put("allDayEnd", event.allDayEnd?.toString())
                })
            }
        })
        put("coverageStart", state.coverageStart?.toString()); put("coverageEnd", state.coverageEnd?.toString())
        put("syncedAt", state.syncedAt?.toString()); put("issue", state.issue?.name)
        put("retryAt", state.retryAt?.toString())
    }.toString()

    fun decode(value: String): OutlookState {
        val json = JSONObject(value)
        return OutlookState(
            generation = json.getString("generation"),
            account = json.optJSONObject("account")?.let { OutlookAccount(it.getString("id"), it.getString("username")) },
            catalogLoaded = json.getBoolean("catalogLoaded"),
            calendars = json.getJSONArray("calendars").objects().map {
                OutlookCalendar(it.getString("id"), it.getString("name"), it.getBoolean("default"), it.textOrNull("owner"))
            },
            selectedIds = json.getJSONArray("selected").let { ids -> (0 until ids.length()).map { ids.getString(it) }.toSet() },
            events = json.getJSONArray("events").objects().map {
                OutlookEvent(it.getString("calendarId"), it.getString("id"), it.textOrNull("title"),
                    Instant.parse(it.getString("start")), Instant.parse(it.getString("end")),
                    it.textOrNull("allDayStart")?.let(LocalDate::parse), it.textOrNull("allDayEnd")?.let(LocalDate::parse))
            },
            coverageStart = json.textOrNull("coverageStart")?.let(Instant::parse),
            coverageEnd = json.textOrNull("coverageEnd")?.let(Instant::parse),
            syncedAt = json.textOrNull("syncedAt")?.let(Instant::parse),
            issue = json.textOrNull("issue")?.let(OutlookIssue::valueOf),
            retryAt = json.textOrNull("retryAt")?.let(Instant::parse),
        )
    }
}

internal fun JSONObject.textOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
