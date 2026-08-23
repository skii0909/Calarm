package com.smartworkalarm.domain.shift

object ExactShiftTitleMatcher {
    fun matches(title: String?, aliases: List<String>): Boolean {
        val normalizedTitle = title?.trim() ?: return false

        return aliases.any { alias ->
            normalizedTitle.equals(alias.trim(), ignoreCase = true)
        }
    }
}
