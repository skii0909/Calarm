package com.smartworkalarm.outlook

import android.app.Activity
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID

interface OutlookAuth : OutlookTokenProvider {
    suspend fun signIn(activity: Activity): OutlookAccount?
    suspend fun signOut()
}

data class OutlookConfiguration(val clientId: String, val signatureHash: String, val packageName: String) {
    val isConfigured: Boolean get() = runCatching {
        UUID.fromString(clientId).toString().equals(clientId, ignoreCase = true) &&
            clientId != "00000000-0000-0000-0000-000000000000" && Base64.getDecoder().decode(signatureHash).size == 20
    }.getOrDefault(false)
    val redirectUri: String get() = "msauth://$packageName/${URLEncoder.encode(signatureHash, "UTF-8").replace("+", "%20")}"
}
