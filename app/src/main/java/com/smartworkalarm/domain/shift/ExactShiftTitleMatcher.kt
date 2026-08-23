package com.smartworkalarm.domain.shift

import java.util.Locale

object ExactShiftTitleMatcher {
    fun matches(title: String?, aliases: List<String>): Boolean = normalizedMatch(title, aliases) != null

    fun normalizedMatch(title: String?, aliases: List<String>): String? {
        val trimmedTitle = title?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return trimmedTitle.takeIf { candidate ->
            aliases.any { alias -> candidate.equals(alias.trim(), ignoreCase = true) }
        }?.lowercase(Locale.ROOT)
    }
}
