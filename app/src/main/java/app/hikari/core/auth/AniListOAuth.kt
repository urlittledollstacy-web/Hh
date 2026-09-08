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
class AniListOAuth @Inject constructor(private val tokenStore: SecureTokenStore) {
    fun launch(context: Context) {
        require(BuildConfig.ANILIST_CLIENT_ID.isNotBlank()) { "Set ANILIST_CLIENT_ID in gradle.properties before signing in." }
        val state = tokenStore.createAndSaveOAuthState()
        val authorizationUrl = Uri.Builder().scheme("https").authority("anilist.co").appendPath("api").appendPath("v2").appendPath("oauth").appendPath("authorize")
            .appendQueryParameter("client_id", BuildConfig.ANILIST_CLIENT_ID)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("redirect_uri", "hikari://oauth")
            .appendQueryParameter("state", state)
            .build()
        CustomTabsIntent.Builder().build().launchUrl(context, authorizationUrl)
    }

    /** Handles the OAuth implicit-flow callback registered in the manifest. */
    fun consumeRedirect(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        if (uri.scheme != "hikari" || uri.host != "oauth") return false

        val values = uri.fragment?.split("&")?.mapNotNull { part ->
            val pieces = part.split("=", limit = 2)
            if (pieces.size == 2) pieces[0] to Uri.decode(pieces[1]) else null
        }?.toMap() ?: return false

        val expectedState = tokenStore.oauthState()
        val returnedState = values["state"]
        tokenStore.clearOAuthState()
        if (expectedState == null || returnedState == null || !MessageDigest.isEqual(
                expectedState.toByteArray(Charsets.UTF_8),
                returnedState.toByteArray(Charsets.UTF_8),
            )
        ) return false

        val token = values["access_token"] ?: return false
        if (token.isBlank()) return false
        tokenStore.saveAccessToken(token)
        return true
    }
}
