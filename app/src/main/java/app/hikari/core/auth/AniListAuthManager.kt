package app.hikari.core.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import app.hikari.BuildConfig
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListAuthManager @Inject constructor(
    private val tokenStore: SecureTokenStore,
) {
    fun startLogin(context: Context): AuthStartResult {
        val clientId = BuildConfig.ANILIST_CLIENT_ID
        if (clientId.isBlank()) return AuthStartResult.MissingClientId

        val state = tokenStore.createAndSaveOAuthState()
        val uri = Uri.Builder()
            .scheme("https")
            .authority("anilist.co")
            .appendPath("api")
            .appendPath("v2")
            .appendPath("oauth")
            .appendPath("authorize")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("state", state)
            .build()

        CustomTabsIntent.Builder().build().launchUrl(context, uri)
        return AuthStartResult.Started
    }

    fun handleCallback(intent: Intent): AuthCallbackResult? {
        val data = intent.data ?: return null
        if (data.scheme != "https" || data.host != REDIRECT_HOST || data.path != REDIRECT_PATH) return null

        val fragment = data.fragment ?: run {
            tokenStore.clearOAuthState()
            return AuthCallbackResult.Error("AniList did not return an access token.")
        }
        val values = fragment.split('&')
            .mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size == 2) pieces[0] to Uri.decode(pieces[1]) else null
            }
            .toMap()

        val expectedState = tokenStore.oauthState()
        val returnedState = values["state"]
        tokenStore.clearOAuthState()
        if (expectedState == null || returnedState == null || !constantTimeEquals(expectedState, returnedState)) {
            return AuthCallbackResult.Error("Invalid AniList OAuth state.")
        }

        values["error"]?.let { error ->
            return AuthCallbackResult.Error(values["error_description"] ?: error)
        }

        val token = values["access_token"]
        if (token.isNullOrBlank()) return AuthCallbackResult.Error("AniList did not return an access token.")
        tokenStore.saveAccessToken(token)
        return AuthCallbackResult.Success
    }

    fun logout() = tokenStore.clear()

    companion object {
        const val REDIRECT_URI = "https://urlittledollstacy-web.github.io/oauth"
        private const val REDIRECT_HOST = "urlittledollstacy-web.github.io"
        private const val REDIRECT_PATH = "/oauth"

        private fun constantTimeEquals(first: String, second: String): Boolean {
            return MessageDigest.isEqual(first.toByteArray(Charsets.UTF_8), second.toByteArray(Charsets.UTF_8))
        }
    }
}

enum class AuthStartResult { Started, MissingClientId }

sealed interface AuthCallbackResult {
    data object Success : AuthCallbackResult
    data class Error(val message: String) : AuthCallbackResult
}
