package com.cinematv.app.data.local.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores Jellyfin authentication tokens, TMDB API keys, and OpenSubtitles credentials
 * using Android Keystore-backed AES-256-GCM EncryptedSharedPreferences.
 * Credentials are NEVER written to plaintext files or logged.
 */
class SecureCredentialStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveJellyfinSession(accessToken: String, userId: String, serverId: String) {
        prefs.edit()
            .putString(KEY_JELLYFIN_TOKEN, accessToken)
            .putString(KEY_JELLYFIN_USER_ID, userId)
            .putString(KEY_JELLYFIN_SERVER_ID, serverId)
            .apply()
    }

    fun getJellyfinAccessToken(): String? = prefs.getString(KEY_JELLYFIN_TOKEN, null)
    fun getJellyfinUserId(): String? = prefs.getString(KEY_JELLYFIN_USER_ID, null)
    fun getJellyfinServerId(): String? = prefs.getString(KEY_JELLYFIN_SERVER_ID, null)

    fun clearJellyfinSession() {
        prefs.edit()
            .remove(KEY_JELLYFIN_TOKEN)
            .remove(KEY_JELLYFIN_USER_ID)
            .remove(KEY_JELLYFIN_SERVER_ID)
            .apply()
    }

    fun saveTmdbApiKey(apiKey: String) {
        prefs.edit().putString(KEY_TMDB_API_KEY, apiKey.trim()).apply()
    }

    fun getTmdbApiKey(): String? = prefs.getString(KEY_TMDB_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun saveOpenSubtitlesApiKey(apiKey: String) {
        prefs.edit().putString(KEY_OPENSUBTITLES_API_KEY, apiKey.trim()).apply()
    }

    fun getOpenSubtitlesApiKey(): String? = prefs.getString(KEY_OPENSUBTITLES_API_KEY, null)?.takeIf { it.isNotBlank() }

    companion object {
        private const val FILE_NAME = "cinema_tv_secure_credentials"
        private const val KEY_JELLYFIN_TOKEN = "jf_token"
        private const val KEY_JELLYFIN_USER_ID = "jf_user_id"
        private const val KEY_JELLYFIN_SERVER_ID = "jf_server_id"
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
        private const val KEY_OPENSUBTITLES_API_KEY = "os_api_key"

        /**
         * Redacts sensitive headers and tokens prior to structured logging.
         */
        fun redactForLogging(raw: String): String =
            raw.replace(Regex("(Token|Api-Key|api_key|Password)=[^,\\s&\"]+", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
    }
}
