package com.cinematv.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cinematv.app.data.local.db.AuthorizedStreamEntity
import com.cinematv.app.data.local.db.SearchHistoryEntity
import com.cinematv.app.data.local.db.UserListEntity
import com.cinematv.app.data.local.db.WatchProgressEntity
import com.cinematv.app.di.AppContainer
import com.cinematv.app.domain.model.AppSettings
import com.cinematv.app.domain.model.HomeSection
import com.cinematv.app.domain.model.MediaItem
import com.cinematv.app.domain.model.ProviderHealth
import com.cinematv.app.domain.model.SubtitlePreference
import com.cinematv.app.domain.model.SubtitleStyle
import com.cinematv.app.playback.CinemaPlayerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class HomeUiState(
    val isLoading: Boolean = true,
    val sections: List<HomeSection> = emptyList(),
    val providerHealth: List<ProviderHealth> = emptyList(),
    val providerErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null
)

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<MediaItem> = emptyList(),
    val providerErrors: Map<String, String> = emptyMap()
)

data class JellyfinWizardState(
    val isTesting: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false
)

class CinemaViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val continueWatching: StateFlow<List<WatchProgressEntity>> = container.database.watchProgressDao()
        .observeContinueWatching()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchHistory: StateFlow<List<WatchProgressEntity>> = container.database.watchProgressDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userList: StateFlow<List<UserListEntity>> = container.database.userListDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSearches: StateFlow<List<SearchHistoryEntity>> = container.database.searchHistoryDao()
        .observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _jellyfinWizard = MutableStateFlow(JellyfinWizardState())
    val jellyfinWizard: StateFlow<JellyfinWizardState> = _jellyfinWizard.asStateFlow()

    private var searchJob: Job? = null

    init {
        refreshCatalog()
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            _homeState.value = _homeState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val health = container.providerRegistry.checkAllHealth()
                val (sections, errors) = container.providerRegistry.aggregateHomeSections()
                _homeState.value = HomeUiState(
                    isLoading = false,
                    sections = sections,
                    providerHealth = health,
                    providerErrors = errors,
                    errorMessage = if (sections.isEmpty() && errors.isNotEmpty()) errors.values.first() else null
                )
            }.onFailure { err ->
                _homeState.value = _homeState.value.copy(
                    isLoading = false,
                    errorMessage = err.message ?: "Failed to load catalog."
                )
            }
        }
    }

    fun allLoadedItems(): List<MediaItem> =
        _homeState.value.sections.flatMap { it.items }.distinctBy { "${it.providerId}:${it.id}" }

    fun findItemById(mediaId: String): MediaItem? =
        allLoadedItems().firstOrNull { it.id == mediaId }

    fun updateSearchQuery(query: String) {
        _searchState.value = _searchState.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchState.value = SearchUiState(query = "")
            return
        }
        searchJob = viewModelScope.launch {
            delay(250L)
            _searchState.value = _searchState.value.copy(isSearching = true)
            val result = container.providerRegistry.searchAll(query)
            container.database.searchHistoryDao().insert(
                SearchHistoryEntity(query.trim(), System.currentTimeMillis())
            )
            _searchState.value = SearchUiState(
                query = query,
                isSearching = false,
                results = result.items,
                providerErrors = result.providerErrors
            )
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { container.database.searchHistoryDao().clearAll() }
    }

    fun toggleWatchlist(item: MediaItem) {
        viewModelScope.launch {
            val current = userList.value.firstOrNull { it.mediaId == item.id }
            val nextWatchlist = !(current?.isWatchlist ?: false)
            val keepFavorite = current?.isFavorite ?: false
            if (!nextWatchlist && !keepFavorite) {
                container.database.userListDao().delete(item.id)
            } else {
                container.database.userListDao().upsert(
                    UserListEntity(item.id, item.providerId, nextWatchlist, keepFavorite, System.currentTimeMillis())
                )
            }
        }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            val current = userList.value.firstOrNull { it.mediaId == item.id }
            val nextFavorite = !(current?.isFavorite ?: false)
            val keepWatchlist = current?.isWatchlist ?: false
            if (!nextFavorite && !keepWatchlist) {
                container.database.userListDao().delete(item.id)
            } else {
                container.database.userListDao().upsert(
                    UserListEntity(item.id, item.providerId, keepWatchlist, nextFavorite, System.currentTimeMillis())
                )
            }
        }
    }

    fun saveWatchProgress(
        item: MediaItem,
        episodeId: String?,
        positionMs: Long,
        durationMs: Long
    ) {
        viewModelScope.launch {
            val threshold = settings.value.watchedThresholdPercent
            val completed = CinemaPlayerManager.isWatched(positionMs, durationMs, threshold)
            val key = "${item.id}::${episodeId ?: "movie"}"
            container.database.watchProgressDao().upsert(
                WatchProgressEntity(
                    compositeKey = key,
                    mediaId = item.id,
                    episodeId = episodeId,
                    title = item.title,
                    posterUrl = item.posterUrl,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isCompleted = completed,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
            container.providerRegistry.findProvider(item.providerId)
                ?.reportProgress(item.id, episodeId, positionMs, completed)
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch { container.database.watchProgressDao().clearAll() }
    }

    fun connectJellyfin(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _jellyfinWizard.value = JellyfinWizardState(isTesting = true, statusMessage = "Verifying Jellyfin server...")
            val verify = container.jellyfinProvider.verifyServerConnection(serverUrl)
            if (verify.isFailure) {
                _jellyfinWizard.value = JellyfinWizardState(
                    isTesting = false,
                    statusMessage = verify.exceptionOrNull()?.message ?: "Could not reach Jellyfin server.",
                    isError = true
                )
                return@launch
            }
            val login = container.jellyfinProvider.login(serverUrl, username, password)
            login.fold(
                onSuccess = { auth ->
                    _jellyfinWizard.value = JellyfinWizardState(
                        isTesting = false,
                        statusMessage = "Connected as ${auth.user.name}. Refreshing library...",
                        isError = false
                    )
                    refreshCatalog()
                },
                onFailure = { err ->
                    _jellyfinWizard.value = JellyfinWizardState(
                        isTesting = false,
                        statusMessage = err.message ?: "Jellyfin login failed.",
                        isError = true
                    )
                }
            )
        }
    }

    fun disconnectJellyfin() {
        viewModelScope.launch {
            container.jellyfinProvider.disconnect()
            _jellyfinWizard.value = JellyfinWizardState(statusMessage = "Disconnected from Jellyfin.")
            refreshCatalog()
        }
    }

    fun addAuthorizedStream(title: String, arabicTitle: String?, url: String, mimeType: String, subtitleUrl: String?) {
        viewModelScope.launch {
            container.database.authorizedStreamDao().upsert(
                AuthorizedStreamEntity(
                    id = "auth_${UUID.randomUUID()}",
                    title = title.trim(),
                    arabicTitle = arabicTitle?.trim()?.takeIf { it.isNotEmpty() },
                    streamUrl = url.trim(),
                    mimeType = mimeType,
                    subtitleUrl = subtitleUrl?.trim()?.takeIf { it.isNotEmpty() },
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
            refreshCatalog()
        }
    }

    fun setSubtitlePreference(pref: SubtitlePreference) {
        viewModelScope.launch {
            container.settingsRepository.updateSettings { it.copy(subtitlePreference = pref) }
        }
    }

    fun updateSubtitleStyle(transform: (SubtitleStyle) -> SubtitleStyle) {
        viewModelScope.launch {
            container.settingsRepository.updateSettings { it.copy(subtitleStyle = transform(it.subtitleStyle)) }
        }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.updateSettings { it.copy(darkTheme = dark) }
        }
    }

    fun setAutoplayNext(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.updateSettings { it.copy(autoplayNextEpisode = enabled) }
        }
    }

    fun saveApiKeys(tmdbKey: String, openSubtitlesKey: String) {
        if (tmdbKey.isNotBlank()) container.credentialStore.saveTmdbApiKey(tmdbKey)
        if (openSubtitlesKey.isNotBlank()) container.credentialStore.saveOpenSubtitlesApiKey(openSubtitlesKey)
    }
}

class CinemaViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = CinemaViewModel(container) as T
}
