package com.cinematv.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cinematv.app.data.local.db.SearchHistoryEntity
import com.cinematv.app.data.local.db.UserListEntity
import com.cinematv.app.data.local.db.WatchProgressEntity
import com.cinematv.app.domain.model.MediaItem
import com.cinematv.app.domain.model.MediaType
import com.cinematv.app.ui.components.CinemaErrorState
import com.cinematv.app.ui.components.CinemaHeroBanner
import com.cinematv.app.ui.components.CinemaPosterCard
import com.cinematv.app.ui.theme.CinemaColors
import com.cinematv.app.ui.theme.CinemaSpacing
import com.cinematv.app.ui.viewmodel.HomeUiState
import com.cinematv.app.ui.viewmodel.SearchUiState

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    continueWatching: List<WatchProgressEntity>,
    isTvDevice: Boolean,
    onSelectMedia: (MediaItem) -> Unit,
    onPlayMedia: (MediaItem, String?) -> Unit,
    onRetry: () -> Unit
) {
    val allItems = remember(uiState.sections) { uiState.sections.flatMap { it.items } }
    val heroItem = allItems.firstOrNull()
    val progressByMedia = remember(continueWatching) {
        continueWatching.associateBy { it.mediaId }
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CinemaColors.GoldAccent)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = if (isTvDevice) CinemaSpacing.tvOverscanHorizontal else CinemaSpacing.md,
            vertical = CinemaSpacing.md
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (heroItem != null) {
            item {
                CinemaHeroBanner(
                    item = heroItem,
                    isTvDevice = isTvDevice,
                    onPlayClick = { onPlayMedia(heroItem, null) },
                    onDetailsClick = { onSelectMedia(heroItem) }
                )
            }
        }

        if (uiState.errorMessage != null) {
            item { CinemaErrorState(uiState.errorMessage, onRetry = onRetry) }
        }

        if (continueWatching.isNotEmpty()) {
            item {
                Text("Continue Watching", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(continueWatching, key = { it.compositeKey }) { progress ->
                        val item = allItems.firstOrNull { it.id == progress.mediaId }
                        if (item != null) {
                            val frac = if (progress.durationMs > 0) progress.positionMs.toFloat() / progress.durationMs else 0f
                            CinemaPosterCard(
                                item = item,
                                isTvDevice = isTvDevice,
                                progressFraction = frac,
                                onClick = { onPlayMedia(item, progress.episodeId) }
                            )
                        }
                    }
                }
            }
        }

        items(uiState.sections, key = { it.id }) { section ->
            if (section.items.isNotEmpty()) {
                Column {
                    Text(section.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(section.items, key = { "${section.id}_${it.id}" }) { media ->
                            val p = progressByMedia[media.id]
                            val frac = if (p != null && p.durationMs > 0) p.positionMs.toFloat() / p.durationMs else null
                            CinemaPosterCard(
                                item = media,
                                isTvDevice = isTvDevice,
                                progressFraction = frac,
                                onClick = { onSelectMedia(media) }
                            )
                        }
                    }
                }
            }
        }

        if (uiState.providerHealth.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Configured Content Providers", style = MaterialTheme.typography.titleMedium)
                    uiState.providerHealth.forEach { health ->
                        Text(
                            text = "${health.providerName} · ${health.statusMessage}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CatalogScreen(
    title: String,
    items: List<MediaItem>,
    isTvDevice: Boolean,
    onSelectMedia: (MediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (isTvDevice) CinemaSpacing.tvOverscanHorizontal else CinemaSpacing.md,
                vertical = CinemaSpacing.md
            )
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (items.isEmpty()) {
            Text(
                "No titles found from your active providers. Connect Jellyfin or add an authorized stream in Settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTvDevice) 180.dp else 148.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items, key = { "${it.providerId}_${it.id}" }) { media ->
                    CinemaPosterCard(
                        item = media,
                        isTvDevice = isTvDevice,
                        onClick = { onSelectMedia(media) }
                    )
                }
            }
        }
    }
}

@Composable
fun DetailScreen(
    item: MediaItem,
    isWatchlist: Boolean,
    isFavorite: Boolean,
    resumePositionMs: Long,
    onPlay: (episodeId: String?, restart: Boolean) -> Unit,
    onToggleWatchlist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit
) {
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CinemaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Back", style = MaterialTheme.typography.titleMedium)
            }
        }

        item {
            Text(item.title, style = MaterialTheme.typography.displaySmall)
            if (!item.arabicTitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(item.arabicTitle, style = MaterialTheme.typography.titleLarge, color = CinemaColors.GoldAccent)
            }
            Spacer(Modifier.height(8.dp))
            val meta = listOfNotNull(
                item.releaseYear.takeIf { it > 0 }?.toString(),
                item.runtimeMinutes.takeIf { it > 0 }?.let { "${it}m" },
                item.rating?.let { "★ %.1f".format(it) },
                item.genres.joinToString(" / ").takeIf { it.isNotBlank() },
                "Audio: ${item.audioLanguages.joinToString(", ")}",
                "Subtitles: ${item.subtitles.joinToString(", ") { it.languageCode.uppercase() }.ifBlank { "None" }}"
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(item.synopsis, style = MaterialTheme.typography.bodyLarge)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val defaultEpisodeId = item.seasons.firstOrNull()?.episodes?.firstOrNull()?.id
                Button(
                    onClick = { onPlay(defaultEpisodeId, false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CinemaColors.GoldAccent,
                        contentColor = CinemaColors.Obsidian
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (resumePositionMs > 0) "Resume" else "Play", fontWeight = FontWeight.Bold)
                }

                if (resumePositionMs > 0) {
                    OutlinedButton(onClick = { onPlay(defaultEpisodeId, true) }) {
                        Icon(Icons.Default.Replay, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Restart")
                    }
                }

                OutlinedButton(onClick = onToggleWatchlist) {
                    Icon(if (isWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isWatchlist) "In My List" else "My List")
                }

                OutlinedButton(onClick = onToggleFavorite) {
                    Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null)
                }
            }
        }

        if (item.type == MediaType.SERIES && item.seasons.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.seasons.forEachIndexed { idx, season ->
                        Button(
                            onClick = { selectedSeasonIndex = idx },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedSeasonIndex == idx) CinemaColors.GoldAccent else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedSeasonIndex == idx) CinemaColors.Obsidian else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(season.title)
                        }
                    }
                }
            }

            val episodes = item.seasons.getOrNull(selectedSeasonIndex)?.episodes.orEmpty()
            items(episodes, key = { it.id }) { ep ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusable()
                        .clickable { onPlay(ep.id, false) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "S${ep.seasonNumber} E${ep.episodeNumber} · ${ep.title}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            ep.arabicTitle?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = CinemaColors.GoldAccent)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                ep.synopsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play episode", tint = CinemaColors.GoldAccent)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    recentSearches: List<SearchHistoryEntity>,
    isTvDevice: Boolean,
    onQueryChange: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSelectMedia: (MediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isTvDevice) CinemaSpacing.tvOverscanHorizontal else CinemaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Search Catalog", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = uiState.query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search movies, series, Arabic titles, genres...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (uiState.query.isBlank() && recentSearches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Searches", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onClearHistory) { Text("Clear History") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recentSearches, key = { it.query }) { history ->
                    OutlinedButton(onClick = { onQueryChange(history.query) }) {
                        Text(history.query)
                    }
                }
            }
        }

        if (uiState.isSearching) {
            CircularProgressIndicator(color = CinemaColors.GoldAccent)
        } else if (uiState.query.isNotBlank() && uiState.results.isEmpty()) {
            Text(
                "No matches found for \"${uiState.query}\". Try an English title, Arabic title, or genre.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (uiState.results.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTvDevice) 180.dp else 148.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.results, key = { "${it.providerId}_${it.id}" }) { media ->
                    CinemaPosterCard(media, isTvDevice = isTvDevice, onClick = { onSelectMedia(media) })
                }
            }
        }
    }
}

@Composable
fun MyListScreen(
    allItems: List<MediaItem>,
    userList: List<UserListEntity>,
    isTvDevice: Boolean,
    onSelectMedia: (MediaItem) -> Unit
) {
    val savedIds = userList.map { it.mediaId }.toSet()
    val items = allItems.filter { it.id in savedIds }
    CatalogScreen("My List & Favorites", items, isTvDevice, onSelectMedia)
}

@Composable
fun ContinueWatchingScreen(
    allItems: List<MediaItem>,
    progressList: List<WatchProgressEntity>,
    isTvDevice: Boolean,
    onPlayMedia: (MediaItem, String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isTvDevice) CinemaSpacing.tvOverscanHorizontal else CinemaSpacing.md)
    ) {
        Text("Continue Watching", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        if (progressList.isEmpty()) {
            Text(
                "Your in-progress movies and episodes will appear here automatically.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTvDevice) 180.dp else 148.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(progressList, key = { it.compositeKey }) { progress ->
                    val media = allItems.firstOrNull { it.id == progress.mediaId }
                    if (media != null) {
                        val frac = if (progress.durationMs > 0) progress.positionMs.toFloat() / progress.durationMs else 0f
                        CinemaPosterCard(
                            item = media,
                            isTvDevice = isTvDevice,
                            progressFraction = frac,
                            onClick = { onPlayMedia(media, progress.episodeId) }
                        )
                    }
                }
            }
        }
    }
}
