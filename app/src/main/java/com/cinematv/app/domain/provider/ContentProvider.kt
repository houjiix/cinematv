package com.cinematv.app.domain.provider

import com.cinematv.app.domain.model.Episode
import com.cinematv.app.domain.model.HomeSection
import com.cinematv.app.domain.model.MediaItem
import com.cinematv.app.domain.model.ProviderCategory
import com.cinematv.app.domain.model.ProviderHealth
import com.cinematv.app.domain.model.Season
import com.cinematv.app.domain.model.StreamSource
import com.cinematv.app.domain.model.SubtitleTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Modular contract implemented independently by each authorized media provider
 * (Jellyfin, Public-Domain Catalog, Authorized HLS/DASH Feeds, Official App Deep-Link Directory).
 * Adding a new provider requires implementing this interface and registering it in ProviderRegistry.
 */
interface ContentProvider {
    val id: String
    val name: String
    val category: ProviderCategory

    suspend fun checkHealth(): ProviderHealth
    suspend fun getHomeSections(): Result<List<HomeSection>>
    suspend fun search(query: String): Result<List<MediaItem>>
    suspend fun getMovieDetails(mediaId: String): Result<MediaItem?>
    suspend fun getSeriesDetails(seriesId: String): Result<MediaItem?>
    suspend fun getSeasons(seriesId: String): Result<List<Season>>
    suspend fun getEpisodes(seriesId: String, seasonNumber: Int): Result<List<Episode>>
    suspend fun resolvePlayback(mediaId: String, episodeId: String? = null): Result<List<StreamSource>>
    suspend fun getSubtitles(mediaId: String, episodeId: String? = null): Result<List<SubtitleTrack>>
    suspend fun reportProgress(mediaId: String, episodeId: String?, positionMs: Long, isCompleted: Boolean): Result<Unit> = Result.success(Unit)
}

data class AggregatedSearchResult(
    val items: List<MediaItem>,
    val providerErrors: Map<String, String>
)

class ProviderRegistry(
    private val providers: List<ContentProvider>
) {
    fun allProviders(): List<ContentProvider> = providers

    fun findProvider(providerId: String): ContentProvider? =
        providers.firstOrNull { it.id == providerId }

    suspend fun checkAllHealth(): List<ProviderHealth> = coroutineScope {
        providers.map { provider -> async { provider.checkHealth() } }.awaitAll()
    }

    suspend fun aggregateHomeSections(): Pair<List<HomeSection>, Map<String, String>> = coroutineScope {
        val results = providers.map { provider ->
            async { provider.name to provider.getHomeSections() }
        }.awaitAll()

        val sections = mutableListOf<HomeSection>()
        val errors = mutableMapOf<String, String>()
        results.forEach { (name, result) ->
            result.fold(
                onSuccess = { sections.addAll(it) },
                onFailure = { errors[name] = it.message ?: "Provider unavailable" }
            )
        }
        sections to errors
    }

    suspend fun searchAll(query: String): AggregatedSearchResult = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@coroutineScope AggregatedSearchResult(emptyList(), emptyMap())
        }

        val results = providers.map { provider ->
            async { provider.name to provider.search(trimmed) }
        }.awaitAll()

        val items = mutableListOf<MediaItem>()
        val errors = mutableMapOf<String, String>()
        results.forEach { (name, result) ->
            result.fold(
                onSuccess = { items.addAll(it) },
                onFailure = { errors[name] = it.message ?: "Search failed" }
            )
        }
        AggregatedSearchResult(items.distinctBy { "${it.providerId}:${it.id}" }, errors)
    }
}
