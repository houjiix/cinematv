package com.cinematv.app.playback

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import com.cinematv.app.domain.model.StreamSource
import com.cinematv.app.domain.model.SubtitlePreference
import com.cinematv.app.domain.model.SubtitleStyle
import com.cinematv.app.domain.model.SubtitleTrack
import kotlin.math.roundToInt

/**
 * Lifecycle-safe AndroidX Media3 ExoPlayer manager supporting HLS, MPEG-DASH, MP4, and MKV
 * (when device codecs support them), hardware decoding by default, Arabic-first subtitle selection,
 * and configurable watch-completion tracking.
 */
@OptIn(UnstableApi::class)
class CinemaPlayerManager(context: Context) {

    private val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setPreferredTextLanguage("ar")
                .setPreferredTextLanguages("ar", "ara", "en", "eng")
                .setPreferredAudioLanguage("en")
        )
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .build()

    fun applyLanguagePreferences(subtitlePreference: SubtitlePreference, preferredAudioLang: String) {
        val builder = trackSelector.buildUponParameters()
            .setPreferredAudioLanguage(preferredAudioLang)

        when (subtitlePreference) {
            SubtitlePreference.ARABIC -> builder
                .setPreferredTextLanguages("ar", "ara", "en", "eng")
                .setIgnoredTextSelectionFlags(0)
            SubtitlePreference.ENGLISH -> builder
                .setPreferredTextLanguages("en", "eng", "ar", "ara")
                .setIgnoredTextSelectionFlags(0)
            SubtitlePreference.ASK -> builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        }
        trackSelector.setParameters(builder)
    }

    fun prepareMedia(
        source: StreamSource,
        externalSubtitles: List<SubtitleTrack>,
        resumePositionMs: Long = 0L,
        playbackSpeed: Float = 1.0f
    ) {
        val subtitleConfigs = externalSubtitles
            .filter { !it.url.isNullOrBlank() && it.format.supportedByMedia3 }
            .map { sub ->
                Media3Item.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType(sub.format.mimeType)
                    .setLanguage(sub.languageCode)
                    .setLabel(sub.label)
                    .setSelectionFlags(
                        if (sub.languageCode.startsWith("ar", ignoreCase = true)) C.SELECTION_FLAG_DEFAULT else 0
                    )
                    .build()
            }

        val media3Item = Media3Item.Builder()
            .setUri(source.url)
            .setMimeType(source.mimeType)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()

        exoPlayer.setMediaItem(media3Item)
        exoPlayer.setPlaybackSpeed(playbackSpeed.coerceIn(0.5f, 2.0f))
        exoPlayer.prepare()
        if (resumePositionMs > 0L) {
            exoPlayer.seekTo(resumePositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    fun selectVideoQualityMaxHeight(maxHeightPx: Int?) {
        val builder = trackSelector.buildUponParameters()
        if (maxHeightPx == null) {
            builder.clearVideoSizeConstraints()
        } else {
            builder.setMaxVideoSize(Int.MAX_VALUE, maxHeightPx)
        }
        trackSelector.setParameters(builder)
    }

    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val audioGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val target = audioGroups.getOrNull(groupIndex) ?: return
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setOverrideForType(TrackSelectionOverride(target.mediaTrackGroup, trackIndex))
        )
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed.coerceIn(0.5f, 2.0f))
    }

    fun seekRelative(deltaMs: Long) {
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, duration)
        exoPlayer.seekTo(target)
    }

    fun buildCaptionStyle(style: SubtitleStyle): CaptionStyleCompat {
        val bgAlpha = (style.backgroundOpacity.coerceIn(0f, 1f) * 255f).roundToInt()
        val bgColor = Color.argb(bgAlpha, 9, 10, 13)
        val edgeType = if (style.edgeOutlineEnabled) {
            CaptionStyleCompat.EDGE_TYPE_OUTLINE
        } else {
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        }
        return CaptionStyleCompat(
            style.textColorHex.toInt(),
            bgColor,
            Color.TRANSPARENT,
            edgeType,
            Color.BLACK,
            Typeface.SANS_SERIF
        )
    }

    fun release() {
        exoPlayer.release()
    }

    companion object {
        fun isWatched(positionMs: Long, durationMs: Long, thresholdPercent: Int): Boolean {
            if (durationMs <= 0L) return false
            val pct = (positionMs.toDouble() / durationMs.toDouble()) * 100.0
            return pct >= thresholdPercent.coerceIn(50, 99)
        }

        fun formatErrorMessage(error: PlaybackException): String = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Network connection interrupted. Check your internet or media server and retry."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "Media server refused the stream request. Your session may have expired."
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                "This device does not support decoding this video/audio codec."
            else -> error.localizedMessage ?: "Playback failed (${error.errorCodeName})."
        }
    }
}
