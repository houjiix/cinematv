package com.cinematv.app.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.cinematv.app.domain.model.AppSettings
import com.cinematv.app.domain.model.MediaItem
import com.cinematv.app.domain.model.SubtitleTrack
import com.cinematv.app.playback.CinemaPlayerManager
import com.cinematv.app.subtitles.ArabicSubtitleEngine
import com.cinematv.app.subtitles.SubtitleCue
import com.cinematv.app.subtitles.SubtitleParseResult
import com.cinematv.app.ui.components.CinemaErrorState
import com.cinematv.app.ui.theme.CinemaColors
import kotlinx.coroutines.delay

/**
 * Fullscreen AndroidX Media3 ExoPlayer screen with Arabic RTL BiDi subtitle rendering,
 * sync offset adjustment, speed control, resume tracking, and clean lifecycle teardown.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    item: MediaItem,
    episodeId: String?,
    settings: AppSettings,
    resumePositionMs: Long,
    onProgressSaved: (positionMs: Long, durationMs: Long) -> Unit,
    onUpdateSyncOffset: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playerManager = remember { CinemaPlayerManager(context) }

    val activeSource = remember(item, episodeId) {
        if (episodeId != null) {
            item.seasons.flatMap { it.episodes }.firstOrNull { it.id == episodeId }?.streamSources?.firstOrNull()
        } else {
            item.streamSources.firstOrNull()
        }
    }

    val availableSubtitles: List<SubtitleTrack> = remember(item, episodeId) {
        if (episodeId != null) {
            item.seasons.flatMap { it.episodes }.firstOrNull { it.id == episodeId }?.subtitles.orEmpty()
        } else {
            item.subtitles
        }
    }

    var selectedSubtitle by remember(availableSubtitles, settings.subtitlePreference) {
        mutableStateOf(ArabicSubtitleEngine.selectPreferredTrack(availableSubtitles, settings.subtitlePreference))
    }

    val parsedCues: List<SubtitleCue> = remember(selectedSubtitle) {
        val content = selectedSubtitle?.inlineContent ?: return@remember emptyList()
        when (val res = ArabicSubtitleEngine.parse(content, selectedSubtitle!!.format, selectedSubtitle!!.isRtl)) {
            is SubtitleParseResult.Success -> res.cues
            else -> emptyList()
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(resumePositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(settings.defaultPlaybackSpeed) }

    DisposableEffect(activeSource) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    durationMs = playerManager.exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = CinemaPlayerManager.formatErrorMessage(error)
                isBuffering = false
            }
        }
        playerManager.exoPlayer.addListener(listener)
        playerManager.applyLanguagePreferences(settings.subtitlePreference, settings.preferredAudioLanguage)

        if (activeSource != null) {
            playerManager.prepareMedia(
                source = activeSource,
                externalSubtitles = availableSubtitles,
                resumePositionMs = resumePositionMs,
                playbackSpeed = playbackSpeed
            )
        } else {
            errorMessage = "No playable media stream is configured for this title."
            isBuffering = false
        }

        onDispose {
            onProgressSaved(playerManager.exoPlayer.currentPosition, playerManager.exoPlayer.duration.coerceAtLeast(0L))
            playerManager.exoPlayer.removeListener(listener)
            playerManager.release()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentPosMs = playerManager.exoPlayer.currentPosition
            val dur = playerManager.exoPlayer.duration
            if (dur > 0) durationMs = dur
            delay(500L)
        }
    }

    val activeCue = remember(parsedCues, currentPosMs, settings.subtitleStyle.syncOffsetMs) {
        ArabicSubtitleEngine.findActiveCue(parsedCues, currentPosMs, settings.subtitleStyle.syncOffsetMs)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = playerManager.exoPlayer
                    useController = false
                    subtitleView?.setStyle(playerManager.buildCaptionStyle(settings.subtitleStyle))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Native Unicode Arabic RTL / BiDi Subtitle Overlay
        if (activeCue != null) {
            val layoutDir = if (activeCue.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 112.dp, start = 24.dp, end = 24.dp)
                        .background(
                            Color(0xFF090A0D).copy(alpha = settings.subtitleStyle.backgroundOpacity),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = activeCue.formattedText,
                        color = Color(settings.subtitleStyle.textColorHex),
                        fontSize = settings.subtitleStyle.fontSizeSp.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = (settings.subtitleStyle.fontSizeSp * 1.35f).sp
                    )
                }
            }
        }

        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = CinemaColors.GoldAccent
            )
        }

        if (errorMessage != null) {
            Box(
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            ) {
                CinemaErrorState(
                    message = errorMessage!!,
                    onRetry = {
                        errorMessage = null
                        activeSource?.let {
                            playerManager.prepareMedia(it, availableSubtitles, currentPosMs, playbackSpeed)
                        }
                    }
                )
            }
        }

        // Top Bar
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column {
                    Text(item.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    item.arabicTitle?.let {
                        Text(it, color = CinemaColors.GoldAccent, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    val next = speeds[(speeds.indexOf(playbackSpeed) + 1) % speeds.size]
                    playbackSpeed = next
                    playerManager.setPlaybackSpeed(next)
                }) {
                    Text("${playbackSpeed}x")
                }
                IconButton(onClick = { showSubtitleMenu = !showSubtitleMenu }) {
                    Icon(Icons.Default.Subtitles, contentDescription = "Subtitles", tint = CinemaColors.GoldAccent)
                }
            }
        }

        // Subtitle Track & Sync Overlay
        if (showSubtitleMenu) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 20.dp)
                    .background(CinemaColors.Charcoal.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Subtitles (Arabic Default)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                availableSubtitles.forEach { track ->
                    Button(onClick = { selectedSubtitle = track; showSubtitleMenu = false }) {
                        Text("${track.label}${if (track.id == selectedSubtitle?.id) " ✓" else ""}")
                    }
                }
                Button(onClick = { selectedSubtitle = null; showSubtitleMenu = false }) {
                    Text("Off")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sync Offset: ${settings.subtitleStyle.syncOffsetMs} ms",
                    color = CinemaColors.MutedSteel,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onUpdateSyncOffset(settings.subtitleStyle.syncOffsetMs - 250L) }) { Text("-0.25s") }
                    Button(onClick = { onUpdateSyncOffset(0L) }) { Text("Reset") }
                    Button(onClick = { onUpdateSyncOffset(settings.subtitleStyle.syncOffsetMs + 250L) }) { Text("+0.25s") }
                }
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Slider(
                value = if (durationMs > 0) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                onValueChange = { fraction ->
                    if (durationMs > 0) playerManager.exoPlayer.seekTo((fraction * durationMs).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = CinemaColors.GoldAccent,
                    activeTrackColor = CinemaColors.GoldAccent
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playerManager.seekRelative(-10_000L) }) {
                    Icon(Icons.Default.Replay10, contentDescription = "Rewind 10 seconds", tint = Color.White)
                }
                IconButton(onClick = {
                    if (isPlaying) playerManager.exoPlayer.pause() else playerManager.exoPlayer.play()
                }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = CinemaColors.GoldAccent
                    )
                }
                IconButton(onClick = { playerManager.seekRelative(10_000L) }) {
                    Icon(Icons.Default.Forward10, contentDescription = "Skip forward 10 seconds", tint = Color.White)
                }
            }
        }
    }
}
