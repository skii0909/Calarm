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
