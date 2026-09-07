package app.hikari.core.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import app.hikari.BuildConfig
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListAuthManager @Inject constructor(
    private val tokenStore: SecureTokenStore,
) {
    fun startLogin(context: Context): AuthStartResult {
        val clientId = BuildConfig.ANILIST_CLIENT_ID
        if (clientId.isBlank()) return AuthStartResult.MissingClientId

        val state = randomState()
        tokenStore.saveOAuthState(state)
        val uri = Uri.Builder()
            .scheme("https")
            .authority("anilist.co")
            .appendPath("api")
            .appendPath("v2")
            .appendPath("oauth")
            .appendPath("authorize")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("state", state)
            .build()

        CustomTabsIntent.Builder().build().launchUrl(context, uri)
        return AuthStartResult.Started
    }

    fun handleCallback(intent: Intent): AuthCallbackResult? {
        val data = intent.data ?: return null
        if (data.scheme != "hikari" || data.host != "oauth") return null

        val fragment = data.fragment ?: return AuthCallbackResult.Error("AniList did not return an access token.")
        val values = fragment.split('&')
            .mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size == 2) pieces[0] to Uri.decode(pieces[1]) else null
            }
            .toMap()

        val expectedState = tokenStore.oauthState()
        val returnedState = values["state"]
        if (expectedState.isNullOrBlank() || returnedState != expectedState) {
            tokenStore.clearOAuthState()
            return AuthCallbackResult.Error("AniList login could not be verified. Please try again.")
        }

        tokenStore.clearOAuthState()
        values["error"]?.let { error ->
            return AuthCallbackResult.Error(values["error_description"] ?: error)
        }

        val token = values["access_token"]
        if (token.isNullOrBlank()) return AuthCallbackResult.Error("AniList did not return an access token.")
        tokenStore.saveAccessToken(token)
        return AuthCallbackResult.Success
    }

    fun logout() = tokenStore.clear()

    private fun randomState(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        const val REDIRECT_URI = "hikari://oauth"
    }
}

enum class AuthStartResult { Started, MissingClientId }

sealed interface AuthCallbackResult {
    data object Success : AuthCallbackResult
    data class Error(val message: String) : AuthCallbackResult
}
