package com.smartworkalarm.permissions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

internal class RequiredPermissionsSessionViewModel : ViewModel() {
    var state by mutableStateOf(RequiredPermissionsPromptSessionState())
        private set

    fun dismissPrompt() {
        state = state.dismiss()
    }

    fun onPermissionsRefreshed(missing: List<RequiredPermission>) {
        state = state.afterPermissionsRefresh(missing)
    }
}
