package com.smartworkalarm.domain.usecase

object VacationMatcher {
    fun isVacationTitle(title: String?): Boolean = title?.trim()?.contains("휴가") == true
}
