package com.cinematv.app.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cinematv.app.domain.model.AppSettings
import com.cinematv.app.domain.model.SubtitlePreference
import com.cinematv.app.domain.model.SubtitleStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "cinema_tv_settings")

class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            appLanguage = prefs[KEY_APP_LANGUAGE] ?: "en",
            subtitlePreference = prefs[KEY_SUBTITLE_PREF]
                ?.let { code -> SubtitlePreference.entries.firstOrNull { it.code == code } }
                ?: SubtitlePreference.ARABIC,
            subtitleStyle = SubtitleStyle(
                fontSizeSp = prefs[KEY_SUB_FONT_SIZE] ?: 22f,
                textColorHex = prefs[KEY_SUB_TEXT_COLOR] ?: 0xFFFFFFFF,
                backgroundOpacity = prefs[KEY_SUB_BG_OPACITY] ?: 0.72f,
                edgeOutlineEnabled = prefs[KEY_SUB_EDGE_OUTLINE] ?: true,
                bottomPaddingFraction = prefs[KEY_SUB_BOTTOM_PADDING] ?: 0.08f,
                syncOffsetMs = prefs[KEY_SUB_SYNC_OFFSET_MS] ?: 0L
            ),
            preferredAudioLanguage = prefs[KEY_AUDIO_LANG] ?: "en",
            preferredVideoQuality = prefs[KEY_VIDEO_QUALITY] ?: "Auto",
            defaultPlaybackSpeed = prefs[KEY_PLAYBACK_SPEED] ?: 1.0f,
            autoplayNextEpisode = prefs[KEY_AUTOPLAY_NEXT] ?: true,
            watchedThresholdPercent = prefs[KEY_WATCHED_THRESHOLD] ?: 90,
            darkTheme = prefs[KEY_DARK_THEME] ?: true,
            jellyfinServerUrl = prefs[KEY_JELLYFIN_URL] ?: "",
            jellyfinUsername = prefs[KEY_JELLYFIN_USER] ?: ""
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = AppSettings(
                appLanguage = prefs[KEY_APP_LANGUAGE] ?: "en",
                subtitlePreference = prefs[KEY_SUBTITLE_PREF]
                    ?.let { code -> SubtitlePreference.entries.firstOrNull { it.code == code } }
                    ?: SubtitlePreference.ARABIC,
                subtitleStyle = SubtitleStyle(
                    fontSizeSp = prefs[KEY_SUB_FONT_SIZE] ?: 22f,
                    textColorHex = prefs[KEY_SUB_TEXT_COLOR] ?: 0xFFFFFFFF,
                    backgroundOpacity = prefs[KEY_SUB_BG_OPACITY] ?: 0.72f,
                    edgeOutlineEnabled = prefs[KEY_SUB_EDGE_OUTLINE] ?: true,
                    bottomPaddingFraction = prefs[KEY_SUB_BOTTOM_PADDING] ?: 0.08f,
                    syncOffsetMs = prefs[KEY_SUB_SYNC_OFFSET_MS] ?: 0L
                ),
                preferredAudioLanguage = prefs[KEY_AUDIO_LANG] ?: "en",
                preferredVideoQuality = prefs[KEY_VIDEO_QUALITY] ?: "Auto",
                defaultPlaybackSpeed = prefs[KEY_PLAYBACK_SPEED] ?: 1.0f,
                autoplayNextEpisode = prefs[KEY_AUTOPLAY_NEXT] ?: true,
                watchedThresholdPercent = prefs[KEY_WATCHED_THRESHOLD] ?: 90,
                darkTheme = prefs[KEY_DARK_THEME] ?: true,
                jellyfinServerUrl = prefs[KEY_JELLYFIN_URL] ?: "",
                jellyfinUsername = prefs[KEY_JELLYFIN_USER] ?: ""
            )
            val updated = transform(current)
            prefs[KEY_APP_LANGUAGE] = updated.appLanguage
            prefs[KEY_SUBTITLE_PREF] = updated.subtitlePreference.code
            prefs[KEY_SUB_FONT_SIZE] = updated.subtitleStyle.fontSizeSp
            prefs[KEY_SUB_TEXT_COLOR] = updated.subtitleStyle.textColorHex
            prefs[KEY_SUB_BG_OPACITY] = updated.subtitleStyle.backgroundOpacity
            prefs[KEY_SUB_EDGE_OUTLINE] = updated.subtitleStyle.edgeOutlineEnabled
            prefs[KEY_SUB_BOTTOM_PADDING] = updated.subtitleStyle.bottomPaddingFraction
            prefs[KEY_SUB_SYNC_OFFSET_MS] = updated.subtitleStyle.syncOffsetMs
            prefs[KEY_AUDIO_LANG] = updated.preferredAudioLanguage
            prefs[KEY_VIDEO_QUALITY] = updated.preferredVideoQuality
            prefs[KEY_PLAYBACK_SPEED] = updated.defaultPlaybackSpeed
            prefs[KEY_AUTOPLAY_NEXT] = updated.autoplayNextEpisode
            prefs[KEY_WATCHED_THRESHOLD] = updated.watchedThresholdPercent
            prefs[KEY_DARK_THEME] = updated.darkTheme
            prefs[KEY_JELLYFIN_URL] = updated.jellyfinServerUrl
            prefs[KEY_JELLYFIN_USER] = updated.jellyfinUsername
        }
    }

    companion object {
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_SUBTITLE_PREF = stringPreferencesKey("subtitle_preference")
        private val KEY_SUB_FONT_SIZE = floatPreferencesKey("sub_font_size")
        private val KEY_SUB_TEXT_COLOR = longPreferencesKey("sub_text_color")
        private val KEY_SUB_BG_OPACITY = floatPreferencesKey("sub_bg_opacity")
        private val KEY_SUB_EDGE_OUTLINE = booleanPreferencesKey("sub_edge_outline")
        private val KEY_SUB_BOTTOM_PADDING = floatPreferencesKey("sub_bottom_padding")
        private val KEY_SUB_SYNC_OFFSET_MS = longPreferencesKey("sub_sync_offset_ms")
        private val KEY_AUDIO_LANG = stringPreferencesKey("audio_lang")
        private val KEY_VIDEO_QUALITY = stringPreferencesKey("video_quality")
        private val KEY_PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        private val KEY_AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
        private val KEY_WATCHED_THRESHOLD = intPreferencesKey("watched_threshold")
        private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        private val KEY_JELLYFIN_URL = stringPreferencesKey("jellyfin_url")
        private val KEY_JELLYFIN_USER = stringPreferencesKey("jellyfin_user")
    }
}
