package app.hikari.core.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import app.hikari.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListOAuth @Inject constructor(private val tokenStore: SecureTokenStore) {
    fun launch(context: Context) {
        require(BuildConfig.ANILIST_CLIENT_ID.isNotBlank()) { "Set ANILIST_CLIENT_ID in gradle.properties before signing in." }
        val authorizationUrl = Uri.Builder().scheme("https").authority("anilist.co").appendPath("api").appendPath("v2").appendPath("oauth").appendPath("authorize")
            .appendQueryParameter("client_id", BuildConfig.ANILIST_CLIENT_ID)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("redirect_uri", "hikari://oauth")
            .build()
        CustomTabsIntent.Builder().build().launchUrl(context, authorizationUrl)
    }

    /** Handles the OAuth implicit-flow callback registered in the manifest. */
    fun consumeRedirect(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        if (uri.scheme != "hikari" || uri.host != "oauth") return false
        val token = uri.fragment?.split("&")?.firstOrNull { it.startsWith("access_token=") }
            ?.substringAfter("access_token=") ?: return false
        tokenStore.saveAccessToken(Uri.decode(token))
        return true
    }
}
