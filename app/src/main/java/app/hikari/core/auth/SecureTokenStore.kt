package app.hikari.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureTokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "hikari.secure.session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun accessToken(): String? = preferences.getString(TOKEN, null)
    fun saveAccessToken(token: String) = preferences.edit().putString(TOKEN, token).apply()
    fun oauthState(): String? = preferences.getString(OAUTH_STATE, null)
    fun saveOAuthState(state: String) = preferences.edit().putString(OAUTH_STATE, state).apply()
    fun clearOAuthState() = preferences.edit().remove(OAUTH_STATE).apply()
    fun clear() = preferences.edit().clear().apply()

    private companion object {
        const val TOKEN = "anilist_access_token"
        const val OAUTH_STATE = "anilist_oauth_state"
    }
}
