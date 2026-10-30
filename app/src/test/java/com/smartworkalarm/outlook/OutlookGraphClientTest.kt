package com.smartworkalarm.outlook

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OutlookGraphClientTest {
    private val now = Instant.parse("2026-09-19T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun `all pages are read with server nextLink unchanged`() = runTest {
        val urls = mutableListOf<String>()
        val next = "https://graph.microsoft.com/v1.0/me/calendars?%24skiptoken=opaque%2Bvalue"
        val client = OutlookGraphClient(GraphHttpTransport { url, token ->
            assertEquals("test", token)
            urls += url
            if (urls.size == 1) GraphResponse(200, """{"value":[{"id":"one","name":"Calendar","isDefaultCalendar":true}],"@odata.nextLink":"$next"}""")
            else GraphResponse(200, """{"value":[{"id":"two","name":"Other"}]}""")
        }, clock)
        assertEquals(listOf("one", "two"), client.calendars("test").map { it.id })
        assertEquals(next, urls.last())
    }

    @Test fun `untrusted nextLink is rejected before bearer token can be sent`() = runTest {
        var requests = 0
        val client = OutlookGraphClient(GraphHttpTransport { _, _ ->
            requests++
            GraphResponse(200, """{"value":[],"@odata.nextLink":"https://evil.example/steal"}""")
        }, clock)
        val result = runCatching { client.calendars("private-token") }
        assertTrue(result.isFailure)
        assertEquals(1, requests)
    }

    @Test fun `second page failure does not return first page as complete`() = runTest {
        var requests = 0
        val client = OutlookGraphClient(GraphHttpTransport { _, _ ->
            if (++requests == 1) GraphResponse(200, """{"value":[{"id":"one","name":"Calendar"}],"@odata.nextLink":"https://graph.microsoft.com/v1.0/me/calendars?page=2"}""")
            else GraphResponse(503, "")
        }, clock)
        val error = runCatching { client.calendars("test") }.exceptionOrNull() as GraphHttpException
        assertEquals(503, error.status)
    }

    @Test fun `retry after is respected and auth access failures remain distinct`() = runTest {
        for ((status, issue) in listOf(401 to OutlookIssue.REAUTH_REQUIRED, 403 to OutlookIssue.ACCESS_DENIED, 429 to OutlookIssue.THROTTLED)) {
            val client = OutlookGraphClient(GraphHttpTransport { _, _ -> GraphResponse(status, "", "120") }, clock)
            val error = runCatching { client.calendars("test") }.exceptionOrNull() as OutlookException
            assertEquals(issue, error.issue)
            if (status == 429) assertEquals(now.plusSeconds(120), error.retryAt)
        }
    }

    @Test fun `opaque calendar identifier and interval are encoded as values`() = runTest {
        var requested = ""
        val client = OutlookGraphClient(GraphHttpTransport { url, _ -> requested = url; GraphResponse(200, """{"value":[]}""") }, clock)
        client.events("test", "a/b+c=", now, now.plusSeconds(86400))
        assertTrue(requested.contains("/calendars/a%2Fb%2Bc%3D/calendarView?"))
        assertTrue(requested.contains("startDateTime=2026-09-19T00%3A00%3A00Z"))
    }
}
