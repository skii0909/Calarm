package com.smartworkalarm.permissions

enum class RequiredPermission {
    CALENDAR,
    NOTIFICATIONS,
    EXACT_ALARM,
    FULL_SCREEN_ALARM,
    ;

    companion object {
        fun missingFor(
            sdkInt: Int,
            calendarGranted: Boolean,
            notificationsGranted: Boolean,
            exactAlarmGranted: Boolean,
            fullScreenAlarmGranted: Boolean,
        ): List<RequiredPermission> = buildList {
            if (!calendarGranted) add(CALENDAR)
            if (sdkInt >= 33 && !notificationsGranted) add(NOTIFICATIONS)
            if (sdkInt >= 31 && !exactAlarmGranted) add(EXACT_ALARM)
            if (sdkInt >= 34 && !fullScreenAlarmGranted) add(FULL_SCREEN_ALARM)
        }
    }
}

internal enum class RequiredPermissionAction {
    REQUEST_RUNTIME,
    OPEN_APP_DETAILS,
    OPEN_SPECIAL_SETTINGS,
}

internal fun requiredPermissionAction(
    permission: RequiredPermission,
    isMissing: Boolean,
    wasRequested: Boolean,
    shouldShowRationale: Boolean,
): RequiredPermissionAction {
    val isRuntimePermission = permission == RequiredPermission.CALENDAR ||
        permission == RequiredPermission.NOTIFICATIONS
    if (!isRuntimePermission) return RequiredPermissionAction.OPEN_SPECIAL_SETTINGS
    if (!isMissing) return RequiredPermissionAction.OPEN_APP_DETAILS
    return if (!wasRequested || shouldShowRationale) {
        RequiredPermissionAction.REQUEST_RUNTIME
    } else {
        RequiredPermissionAction.OPEN_APP_DETAILS
    }
}

internal data class RequiredPermissionsPromptSessionState(
    val dismissedForSession: Boolean = false,
) {
    fun shouldShow(missing: List<RequiredPermission>): Boolean =
        missing.isNotEmpty() && !dismissedForSession

    fun dismiss(): RequiredPermissionsPromptSessionState = copy(dismissedForSession = true)

    fun afterPermissionsRefresh(missing: List<RequiredPermission>): RequiredPermissionsPromptSessionState =
        if (missing.isEmpty()) copy(dismissedForSession = false) else this
}
