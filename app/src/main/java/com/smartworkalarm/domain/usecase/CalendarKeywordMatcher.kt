package com.smartworkalarm.domain.usecase

import java.util.Locale

object CalendarKeywordMatcher {
    fun normalize(input: String): List<String> = normalize(input.split(','))

    fun normalize(keywords: List<String>): List<String> = keywords
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter(String::isNotEmpty)
        .distinct()

    fun matches(title: String?, keywords: List<String>, exactMatch: Boolean = false): Boolean {
        val normalizedTitle = title?.trim()?.lowercase(Locale.ROOT) ?: return false
        return normalize(keywords).any { if (exactMatch) normalizedTitle == it else normalizedTitle.contains(it) }
    }
}
