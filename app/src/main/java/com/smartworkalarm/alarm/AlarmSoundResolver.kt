package com.smartworkalarm.alarm

object AlarmSoundResolver {
    const val BUNDLED_FALLBACK = "android.resource://com.smartworkalarm/raw/digital_pulse"

    fun candidates(selected: String?, defaultAlarm: String?, defaultNotification: String?): List<String> =
        listOfNotNull(selected, defaultAlarm, defaultNotification, BUNDLED_FALLBACK)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
}
