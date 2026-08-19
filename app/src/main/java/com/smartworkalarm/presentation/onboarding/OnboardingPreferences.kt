package com.smartworkalarm.presentation.onboarding

import android.content.Context

class OnboardingPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
    fun isComplete(): Boolean = preferences.getBoolean(KEY_COMPLETE, false)
    fun complete() = preferences.edit().putBoolean(KEY_COMPLETE, true).apply()

    companion object { private const val KEY_COMPLETE = "complete" }
}
