package com.cinematv.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.cinematv.app.domain.model.MediaItem
import com.cinematv.app.ui.theme.CinemaColors
import com.cinematv.app.ui.theme.CinemaSpacing

/**
 * Reusable poster card supporting both touch input on phones/tablets and crisp D-pad
 * focus scaling + gold border highlights on Android TV / Google TV.
 */
@Composable
fun CinemaPosterCard(
    item: MediaItem,
    isTvDevice: Boolean,
    progressFraction: Float? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused && isTvDevice) 1.07f else 1f, label = "cardScale")
    val cardWidth = if (isTvDevice) 176.dp else 140.dp

    Column(
        modifier = modifier
            .width(cardWidth)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .semantics { contentDescription = item.title }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) CinemaColors.GoldAccent else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            SubcomposeAsyncImage(
                model = item.posterUrl.ifBlank { item.backdropUrl },
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = CinemaColors.GoldAccent
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(CinemaColors.SlateSurface, CinemaColors.Obsidian)
                                )
                            )
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = CinemaColors.GoldAccent,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CinemaColors.CrispWhite,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            )

            if (progressFraction != null && progressFraction > 0.01f) {
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = CinemaColors.GoldAccent,
                    trackColor = Color.Black.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!item.arabicTitle.isNullOrBlank()) {
            Text(
                text = item.arabicTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaColors.GoldAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val subtitleMeta = buildString {
            if (item.releaseYear > 0) append(item.releaseYear)
            if (item.runtimeMinutes > 0) {
                if (isNotEmpty()) append(" · ")
                append("${item.runtimeMinutes}m")
            }
            if (item.subtitles.any { it.languageCode.startsWith("ar", ignoreCase = true) }) {
                if (isNotEmpty()) append(" · ")
                append("AR Sub")
            }
        }
        if (subtitleMeta.isNotEmpty()) {
            Text(
                text = subtitleMeta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CinemaHeroBanner(
    item: MediaItem,
    isTvDevice: Boolean,
    onPlayClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTvDevice) 380.dp else 300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CinemaColors.Charcoal)
    ) {
        SubcomposeAsyncImage(
            model = item.backdropUrl.ifBlank { item.posterUrl },
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            CinemaColors.Obsidian.copy(alpha = 0.94f),
                            CinemaColors.Obsidian.copy(alpha = 0.62f),
                            Color.Transparent
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(if (isTvDevice) CinemaSpacing.xl else CinemaSpacing.lg)
                .fillMaxWidth(if (isTvDevice) 0.62f else 0.92f)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.displaySmall,
                color = CinemaColors.CrispWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.arabicTitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.arabicTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = CinemaColors.GoldAccent
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.synopsis,
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaColors.CrispWhite.copy(alpha = 0.85f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CinemaColors.GoldAccent,
                        contentColor = CinemaColors.Obsidian
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play Now", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDetailsClick,
                    border = BorderStroke(1.dp, CinemaColors.CrispWhite.copy(alpha = 0.5f))
                ) {
                    Text("Details", color = CinemaColors.CrispWhite)
                }
            }
        }
    }
}

@Composable
fun CinemaErrorState(
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, CinemaColors.CrimsonAction.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CinemaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CinemaColors.CrimsonAction)
                Text("Unable to load content", style = MaterialTheme.typography.titleMedium)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onRetry != null) {
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry")
                }
            }
        }
    }
}

/**
 * Clickable Instagram contact card for @houjix_ with full Android TV D-pad focus support.
 * Opens the Instagram app when installed and falls back cleanly to the system web browser.
 */
@Composable
fun InstagramContactCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) CinemaColors.GoldAccent else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { openInstagramProfile(context) }
            .semantics { contentDescription = "Open Instagram @houjix_" }
    ) {
        Row(
            modifier = Modifier.padding(CinemaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Contact Developer on Instagram", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "@houjix_ · instagram.com/houjix_",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaColors.GoldAccent
                )
            }
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open Instagram profile",
                tint = CinemaColors.GoldAccent
            )
        }
    }
}

fun openInstagramProfile(context: Context) {
    val profileUrl = "https://www.instagram.com/houjix_/"
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)).apply {
        setPackage("com.instagram.android")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(appIntent)
    } catch (_: ActivityNotFoundException) {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(webIntent) }
    }
}
