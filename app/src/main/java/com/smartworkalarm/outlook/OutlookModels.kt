package com.smartworkalarm.outlook

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class OutlookAccount(val id: String, val username: String)
data class OutlookCalendarKey(val accountId: String, val calendarId: String)
data class OutlookCalendar(val id: String, val name: String, val isDefault: Boolean = false, val ownerAddress: String? = null)
data class OutlookEvent(
    val calendarId: String,
    val id: String,
    val title: String?,
    val start: Instant,
    val end: Instant,
    val allDayStart: LocalDate? = null,
    val allDayEnd: LocalDate? = null,
)

enum class OutlookIssue {
    SETUP_REQUIRED, REAUTH_REQUIRED, ACCESS_DENIED, NETWORK, THROTTLED, INVALID_DATA, STORAGE,
}

data class OutlookState(
    val loaded: Boolean = true,
    val generation: String = UUID.randomUUID().toString(),
    val account: OutlookAccount? = null,
    val calendars: List<OutlookCalendar> = emptyList(),
    val catalogLoaded: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val events: List<OutlookEvent> = emptyList(),
    val coverageStart: Instant? = null,
    val coverageEnd: Instant? = null,
    val syncedAt: Instant? = null,
    val issue: OutlookIssue? = null,
    val retryAt: Instant? = null,
) {
    val hasSelection: Boolean get() = account != null && selectedIds.isNotEmpty()
    fun selectedKeys(): Set<OutlookCalendarKey> = account?.let { account ->
        selectedIds.mapTo(linkedSetOf()) { OutlookCalendarKey(account.id, it) }
    }.orEmpty()
}

open class OutlookException(val issue: OutlookIssue, val retryAt: Instant? = null) : Exception(issue.name)

class GraphHttpException(val status: Int, retryAt: Instant? = null) : OutlookException(
    when (status) {
        401 -> OutlookIssue.REAUTH_REQUIRED
        403 -> OutlookIssue.ACCESS_DENIED
        429 -> OutlookIssue.THROTTLED
        in 500..599 -> OutlookIssue.NETWORK
        else -> OutlookIssue.INVALID_DATA
    }, retryAt,
)
