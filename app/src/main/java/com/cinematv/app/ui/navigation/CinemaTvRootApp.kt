package com.cinematv.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.cinematv.app.domain.model.MediaItem
import com.cinematv.app.domain.model.MediaType
import com.cinematv.app.ui.screens.CatalogScreen
import com.cinematv.app.ui.screens.ContinueWatchingScreen
import com.cinematv.app.ui.screens.DetailScreen
import com.cinematv.app.ui.screens.HomeScreen
import com.cinematv.app.ui.screens.MyListScreen
import com.cinematv.app.ui.screens.PlayerScreen
import com.cinematv.app.ui.screens.SearchScreen
import com.cinematv.app.ui.screens.SettingsScreen
import com.cinematv.app.ui.viewmodel.CinemaViewModel

enum class CinemaTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    MOVIES("Movies", Icons.Default.Movie),
    SERIES("Series", Icons.Default.Tv),
    SEARCH("Search", Icons.Default.Search),
    MY_LIST("My List", Icons.Default.Bookmark),
    CONTINUE("Continue", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings)
}

private data class ActivePlayback(
    val item: MediaItem,
    val episodeId: String?,
    val resumeMs: Long
)

@Composable
fun CinemaTvRootApp(
    isTvDevice: Boolean,
    viewModel: CinemaViewModel
) {
    val homeState by viewModel.homeState.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val userList by viewModel.userList.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val jellyfinWizard by viewModel.jellyfinWizard.collectAsState()

    var currentTab by remember { mutableStateOf(CinemaTab.HOME) }
    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    var activePlayback by remember { mutableStateOf<ActivePlayback?>(null) }

    val allItems = remember(homeState.sections) {
        homeState.sections.flatMap { it.items }.distinctBy { "${it.providerId}:${it.id}" }
    }

    // Fullscreen Video Player takes over entire viewport on both TV and Phone
    val playback = activePlayback
    if (playback != null) {
        PlayerScreen(
            item = playback.item,
            episodeId = playback.episodeId,
            settings = settings,
            resumePositionMs = playback.resumeMs,
            onProgressSaved = { pos, dur ->
                viewModel.saveWatchProgress(playback.item, playback.episodeId, pos, dur)
            },
            onUpdateSyncOffset = { offset ->
                viewModel.updateSubtitleStyle { it.copy(syncOffsetMs = offset) }
            },
            onBack = { activePlayback = null }
        )
        return
    }

    val openPlayback: (MediaItem, String?, Boolean) -> Unit = { item, epId, restart ->
        val key = "${item.id}::${epId ?: "movie"}"
        val resume = if (restart) 0L else continueWatching.firstOrNull { it.compositeKey == key }?.positionMs ?: 0L
        activePlayback = ActivePlayback(item, epId, resume)
    }

    val screenContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier = modifier.fillMaxSize()) {
            val detail = selectedItem
            if (detail != null) {
                val listEntry = userList.firstOrNull { it.mediaId == detail.id }
                val progressEntry = continueWatching.firstOrNull { it.mediaId == detail.id }
                DetailScreen(
                    item = detail,
                    isWatchlist = listEntry?.isWatchlist == true,
                    isFavorite = listEntry?.isFavorite == true,
                    resumePositionMs = progressEntry?.positionMs ?: 0L,
                    onPlay = { epId, restart -> openPlayback(detail, epId, restart) },
                    onToggleWatchlist = { viewModel.toggleWatchlist(detail) },
                    onToggleFavorite = { viewModel.toggleFavorite(detail) },
                    onBack = { selectedItem = null }
                )
            } else {
                when (currentTab) {
                    CinemaTab.HOME -> HomeScreen(
                        uiState = homeState,
                        continueWatching = continueWatching,
                        isTvDevice = isTvDevice,
                        onSelectMedia = { selectedItem = it },
                        onPlayMedia = { item, epId -> openPlayback(item, epId, false) },
                        onRetry = { viewModel.refreshCatalog() }
                    )
                    CinemaTab.MOVIES -> CatalogScreen(
                        title = "Movies",
                        items = allItems.filter { it.type == MediaType.MOVIE },
                        isTvDevice = isTvDevice,
                        onSelectMedia = { selectedItem = it }
                    )
                    CinemaTab.SERIES -> CatalogScreen(
                        title = "TV Series",
                        items = allItems.filter { it.type == MediaType.SERIES },
                        isTvDevice = isTvDevice,
                        onSelectMedia = { selectedItem = it }
                    )
                    CinemaTab.SEARCH -> SearchScreen(
                        uiState = searchState,
                        recentSearches = recentSearches,
                        isTvDevice = isTvDevice,
                        onQueryChange = viewModel::updateSearchQuery,
                        onClearHistory = viewModel::clearSearchHistory,
                        onSelectMedia = { selectedItem = it }
                    )
                    CinemaTab.MY_LIST -> MyListScreen(
                        allItems = allItems,
                        userList = userList,
                        isTvDevice = isTvDevice,
                        onSelectMedia = { selectedItem = it }
                    )
                    CinemaTab.CONTINUE -> ContinueWatchingScreen(
                        allItems = allItems,
                        progressList = continueWatching,
                        isTvDevice = isTvDevice,
                        onPlayMedia = { item, epId -> openPlayback(item, epId, false) }
                    )
                    CinemaTab.SETTINGS -> SettingsScreen(
                        settings = settings,
                        jellyfinState = jellyfinWizard,
                        isTvDevice = isTvDevice,
                        onSubtitlePrefChange = viewModel::setSubtitlePreference,
                        onSubtitleStyleChange = viewModel::updateSubtitleStyle,
                        onDarkThemeChange = viewModel::setDarkTheme,
                        onAutoplayChange = viewModel::setAutoplayNext,
                        onConnectJellyfin = viewModel::connectJellyfin,
                        onDisconnectJellyfin = viewModel::disconnectJellyfin,
                        onSaveApiKeys = viewModel::saveApiKeys,
                        onClearHistory = viewModel::clearWatchHistory
                    )
                }
            }
        }
    }

    if (isTvDevice) {
        // Android TV Mode: Persistent focusable left NavigationRail for effortless D-pad navigation
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                CinemaTab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = selectedItem == null && currentTab == tab,
                        onClick = { selectedItem = null; currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
            screenContent(Modifier.weight(1f))
        }
    } else {
        // Android Phone / Tablet Mode: Dedicated mobile Scaffold with touch-friendly Bottom Navigation
        val mobileTabs = listOf(CinemaTab.HOME, CinemaTab.MOVIES, CinemaTab.SERIES, CinemaTab.SEARCH, CinemaTab.SETTINGS)
        Scaffold(
            bottomBar = {
                NavigationBar {
                    mobileTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedItem == null && currentTab == tab,
                            onClick = { selectedItem = null; currentTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            screenContent(Modifier.padding(innerPadding))
        }
    }
}
