package com.smartworkalarm.outlook

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class GraphResponse(val status: Int, val body: String, val retryAfter: String? = null)
fun interface GraphHttpTransport { suspend fun get(url: String, token: String): GraphResponse }

class HttpsGraphTransport : GraphHttpTransport {
    override suspend fun get(url: String, token: String): GraphResponse = withContext(Dispatchers.IO) {
        validateGraphUrl(url)
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Prefer", "outlook.timezone=\"UTC\"")
            val status = connection.responseCode
            val body = if (status == 200) connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (output.length + count > 8 * 1024 * 1024) throw OutlookException(OutlookIssue.INVALID_DATA)
                    output.append(buffer, 0, count)
                }
                output.toString()
            } else ""
            GraphResponse(status, body, connection.getHeaderField("Retry-After"))
        } catch (error: Exception) {
            if (error is CancellationException || error is OutlookException) throw error
            throw OutlookException(OutlookIssue.NETWORK)
        } finally { connection.disconnect() }
    }
}

class OutlookGraphClient(private val transport: GraphHttpTransport, private val clock: Clock) : OutlookGraphGateway {
    override suspend fun calendars(token: String): List<OutlookCalendar> = pages(
        "$BASE/me/calendars?\$select=id,name,isDefaultCalendar,owner&\$top=100", token,
    ).map { value ->
        try {
            OutlookCalendar(value.getString("id"), value.getString("name"), value.optBoolean("isDefaultCalendar"),
                value.optJSONObject("owner")?.textOrNull("address"))
        } catch (_: Exception) { throw OutlookException(OutlookIssue.INVALID_DATA) }
    }

    override suspend fun events(token: String, calendarId: String, start: Instant, end: Instant): List<OutlookEvent> {
        require(start < end)
        val fields = "id,subject,start,end,isAllDay,isCancelled,originalStartTimeZone,originalEndTimeZone"
        val url = "$BASE/me/calendars/${encode(calendarId)}/calendarView?startDateTime=${encode(start.toString())}" +
            "&endDateTime=${encode(end.toString())}&\$select=$fields&\$top=500"
        return pages(url, token).mapNotNull { GraphEventParser.parse(calendarId, it) }
    }

    private suspend fun pages(first: String, token: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val visited = mutableSetOf<String>()
        var next: String? = first
        while (next != null) {
            val url = next
            validateGraphUrl(url)
            if (!visited.add(url) || visited.size > 1000) throw OutlookException(OutlookIssue.INVALID_DATA)
            val response = transport.get(url, token)
            if (response.status != 200) throw GraphHttpException(response.status, retryAt(response))
            try {
                val json = JSONObject(response.body)
                result += json.getJSONArray("value").objects()
                if (result.size > 100_000) throw OutlookException(OutlookIssue.INVALID_DATA)
                next = json.textOrNull("@odata.nextLink")
            } catch (error: Exception) {
                if (error is OutlookException) throw error
                throw OutlookException(OutlookIssue.INVALID_DATA)
            }
        }
        return result
    }

    private fun retryAt(response: GraphResponse): Instant? {
        if (response.status != 429 && response.status != 503) return null
        val value = response.retryAfter
        val seconds = value?.toLongOrNull()
        if (seconds != null) return clock.instant().plusSeconds(seconds.coerceIn(1, 7 * 86400))
        return value?.let { runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull() }
            ?: clock.instant().plusSeconds(60)
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private companion object { const val BASE = "https://graph.microsoft.com/v1.0" }
}

internal fun validateGraphUrl(value: String) {
    val uri = runCatching { URI(value) }.getOrElse { throw OutlookException(OutlookIssue.INVALID_DATA) }
    if (uri.scheme != "https" || uri.host != "graph.microsoft.com" || uri.port !in setOf(-1, 443) ||
        uri.userInfo != null || uri.fragment != null || !uri.path.startsWith("/v1.0/")) {
        throw OutlookException(OutlookIssue.INVALID_DATA)
    }
}
