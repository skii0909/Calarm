package com.smartworkalarm.alarm

object AlarmSoundResolver {
    fun candidates(selected: String?, defaultAlarm: String?, defaultNotification: String?): List<String> =
        listOfNotNull(selected, defaultAlarm, defaultNotification)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
}
