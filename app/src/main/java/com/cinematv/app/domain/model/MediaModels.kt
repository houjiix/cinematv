package com.cinematv.app.domain.model

enum class MediaType { MOVIE, SERIES }

enum class ProviderCategory {
    PERSONAL_MEDIA_SERVER,
    OFFICIAL_STREAMING_SERVICE,
    PUBLIC_DOMAIN,
    AUTHORIZED_MEDIA_SOURCE
}

enum class SubtitlePreference(val code: String, val label: String) {
    ARABIC("ar", "Arabic"),
    ENGLISH("en", "English"),
    ASK("ask", "Ask Every Time")
}

enum class SubtitleFormat(val extension: String, val mimeType: String, val supportedByMedia3: Boolean) {
    SRT("srt", "application/x-subrip", true),
    WEBVTT("vtt", "text/vtt", true),
    ASS("ass", "text/x-ssa", true),
    SSA("ssa", "text/x-ssa", true),
    UNSUPPORTED("unknown", "application/octet-stream", false);

    companion object {
        fun fromFilenameOrMime(nameOrMime: String): SubtitleFormat {
            val lower = nameOrMime.lowercase()
            return when {
                lower.endsWith(".srt") || lower.contains("subrip") -> SRT
                lower.endsWith(".vtt") || lower.contains("vtt") -> WEBVTT
                lower.endsWith(".ass") -> ASS
                lower.endsWith(".ssa") || lower.contains("ssa") -> SSA
                else -> UNSUPPORTED
            }
        }
    }
}

data class SubtitleTrack(
    val id: String,
    val languageCode: String,
    val label: String,
    val format: SubtitleFormat,
    val url: String? = null,
    val inlineContent: String? = null,
    val isEmbedded: Boolean = false,
    val isRtl: Boolean = languageCode.startsWith("ar", ignoreCase = true)
)

data class SubtitleStyle(
    val fontSizeSp: Float = 22f,
    val textColorHex: Long = 0xFFFFFFFF,
    val backgroundOpacity: Float = 0.72f,
    val edgeOutlineEnabled: Boolean = true,
    val bottomPaddingFraction: Float = 0.08f,
    val syncOffsetMs: Long = 0L
)

data class AudioTrackInfo(
    val id: String,
    val languageCode: String,
    val label: String,
    val channels: String = "2.0"
)

data class StreamSource(
    val url: String,
    val mimeType: String, // application/x-mpegURL (HLS), application/dash+xml (DASH), video/mp4
    val qualityLabel: String = "Auto",
    val headers: Map<String, String> = emptyMap()
)

data class Episode(
    val id: String,
    val seriesId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val arabicTitle: String? = null,
    val synopsis: String,
    val runtimeMinutes: Int,
    val thumbnailUrl: String,
    val streamSources: List<StreamSource>,
    val subtitles: List<SubtitleTrack>,
    val audioTracks: List<AudioTrackInfo> = emptyList()
)

data class Season(
    val seasonNumber: Int,
    val title: String,
    val episodes: List<Episode>
)

data class MediaItem(
    val id: String,
    val providerId: String,
    val type: MediaType,
    val title: String,
    val originalTitle: String,
    val arabicTitle: String? = null,
    val synopsis: String,
    val releaseYear: Int,
    val genres: List<String>,
    val runtimeMinutes: Int,
    val rating: Double? = null,
    val posterUrl: String,
    val backdropUrl: String,
    val audioLanguages: List<String>,
    val subtitles: List<SubtitleTrack>,
    val streamSources: List<StreamSource> = emptyList(),
    val seasons: List<Season> = emptyList(),
    val cast: List<String> = emptyList(),
    val externalDeepLinkUri: String? = null
)

data class HomeSection(
    val id: String,
    val title: String,
    val items: List<MediaItem>
)

data class WatchProgress(
    val mediaId: String,
    val episodeId: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val updatedAtEpochMs: Long
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

data class ProviderHealth(
    val providerId: String,
    val providerName: String,
    val category: ProviderCategory,
    val isConfigured: Boolean,
    val isReachable: Boolean,
    val statusMessage: String
)

data class AppSettings(
    val appLanguage: String = "en",
    val subtitlePreference: SubtitlePreference = SubtitlePreference.ARABIC,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val preferredAudioLanguage: String = "en",
    val preferredVideoQuality: String = "Auto",
    val defaultPlaybackSpeed: Float = 1.0f,
    val autoplayNextEpisode: Boolean = true,
    val watchedThresholdPercent: Int = 90,
    val darkTheme: Boolean = true,
    val jellyfinServerUrl: String = "",
    val jellyfinUsername: String = ""
)
