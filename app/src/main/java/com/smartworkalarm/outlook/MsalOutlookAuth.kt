package com.smartworkalarm.outlook

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MsalOutlookAuth(private val context: Context, private val config: OutlookConfiguration) : OutlookAuth {
    private val initialization = Mutex()
    private var application: ISingleAccountPublicClientApplication? = null

    override suspend fun account(): OutlookAccount? = guarded {
        withContext(Dispatchers.IO) { app().currentAccount.currentAccount?.toOutlookAccount() }
    }

    override suspend fun token(forceRefresh: Boolean): String = guarded {
        withContext(Dispatchers.IO) {
            val client = app()
            val account = client.currentAccount.currentAccount ?: throw OutlookException(OutlookIssue.REAUTH_REQUIRED)
            val parameters = AcquireTokenSilentParameters.Builder()
                .forAccount(account).fromAuthority(account.authority).withScopes(SCOPES)
                .forceRefresh(forceRefresh).build()
            client.acquireTokenSilent(parameters).accessToken
        }
    }

    override suspend fun signIn(activity: Activity): OutlookAccount? = guarded {
        val client = app()
        val existing = withContext(Dispatchers.IO) { client.currentAccount.currentAccount }
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : AuthenticationCallback {
                    override fun onSuccess(result: IAuthenticationResult) {
                        if (continuation.isActive) continuation.resume(result.account.toOutlookAccount())
                    }
                    override fun onError(exception: MsalException) {
                        if (continuation.isActive) continuation.resumeWithException(mapError(exception))
                    }
                    override fun onCancel() { if (continuation.isActive) continuation.resume(null) }
                }
                val parameters = SignInParameters.builder().withActivity(activity).withScopes(SCOPES).withCallback(callback).build()
                if (existing == null) client.signIn(parameters) else client.signInAgain(parameters)
            }
        }
    }

    override suspend fun signOut() = guarded {
        if (config.isConfigured) withContext(Dispatchers.IO) {
            val client = app()
            if (client.currentAccount.currentAccount != null && !client.signOut()) throw OutlookException(OutlookIssue.REAUTH_REQUIRED)
        }
        Unit
    }

    private suspend fun app(): ISingleAccountPublicClientApplication = initialization.withLock {
        application?.let { return@withLock it }
        if (!config.isConfigured) throw OutlookException(OutlookIssue.SETUP_REQUIRED)
        withContext(Dispatchers.IO) {
            if (actualSignatureHash() != config.signatureHash) throw OutlookException(OutlookIssue.SETUP_REQUIRED)
            val file = File(context.noBackupFilesDir, "outlook-msal-config.json")
            file.writeText(JSONObject().apply {
                put("client_id", config.clientId)
                put("redirect_uri", config.redirectUri)
                put("account_mode", "SINGLE")
                put("authorization_user_agent", "BROWSER")
                put("broker_redirect_uri_registered", false)
                put("authorities", JSONArray().put(JSONObject().put("type", "AAD").put("default", true)
                    .put("audience", JSONObject().put("type", "AzureADandPersonalMicrosoftAccount").put("tenant_id", "common"))))
            }.toString())
            PublicClientApplication.createSingleAccountPublicClientApplication(context, file).also { application = it }
        }
    }

    @Suppress("DEPRECATION")
    private fun actualSignatureHash(): String {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo!!.apkContentsSigners
        } else context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures!!
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(signatures.first().toByteArray()))
    }

    private suspend fun <T> guarded(block: suspend () -> T): T = try { block() } catch (error: Exception) {
        if (error is CancellationException || error is OutlookException) throw error
        throw mapError(error)
    }

    private fun mapError(error: Exception): OutlookException = OutlookException(when {
        error is MsalUiRequiredException -> OutlookIssue.REAUTH_REQUIRED
        error is MsalException && error.errorCode in setOf("access_denied", "authorization_declined", "consent_required") -> OutlookIssue.ACCESS_DENIED
        error is MsalException && error.errorCode in setOf("invalid_client", "redirect_uri_validation_error", "invalid_redirect_uri", "browser_not_installed") -> OutlookIssue.SETUP_REQUIRED
        else -> OutlookIssue.NETWORK
    })

    private fun IAccount.toOutlookAccount() = OutlookAccount("${claims?.get("tid").orEmptyValue()}:$id", username.orEmpty())
    private fun Any?.orEmptyValue() = this?.toString().orEmpty()
    private companion object { val SCOPES = listOf("https://graph.microsoft.com/Calendars.ReadBasic") }
}
