package com.cinematv.app.data.remote.authorized

import com.cinematv.app.data.local.db.AuthorizedStreamDao
import com.cinematv.app.domain.model.Episode
import com.cinematv.app.domain.model.HomeSection
import com.cinematv.app.domain.model.MediaItem
import com.cinematv.app.domain.model.MediaType
import com.cinematv.app.domain.model.ProviderCategory
import com.cinematv.app.domain.model.ProviderHealth
import com.cinematv.app.domain.model.Season
import com.cinematv.app.domain.model.StreamSource
import com.cinematv.app.domain.model.SubtitleFormat
import com.cinematv.app.domain.model.SubtitleTrack
import com.cinematv.app.domain.provider.ContentProvider

/**
 * Provider for user-owned licensed HLS (.m3u8), MPEG-DASH (.mpd), and MP4 streams stored in Room.
 */
class AuthorizedStreamProvider(
    private val dao: AuthorizedStreamDao
) : ContentProvider {

    override val id: String = "authorized_streams"
    override val name: String = "My Authorized Streams"
    override val category: ProviderCategory = ProviderCategory.AUTHORIZED_MEDIA_SOURCE

    private suspend fun loadAll(): List<MediaItem> = dao.getAllOnce().map { entity ->
        val subs = entity.subtitleUrl?.takeIf { it.isNotBlank() }?.let { url ->
            listOf(
                SubtitleTrack(
                    id = "${entity.id}_sub",
                    languageCode = "ar",
                    label = "External Subtitle",
                    format = SubtitleFormat.fromFilenameOrMime(url),
                    url = url
                )
            )
        }.orEmpty()

        MediaItem(
            id = entity.id,
            providerId = id,
            type = MediaType.MOVIE,
            title = entity.title,
            originalTitle = entity.title,
            arabicTitle = entity.arabicTitle,
            synopsis = "User-configured authorized stream (${entity.mimeType}).",
            releaseYear = 2026,
            genres = listOf("Authorized Stream"),
            runtimeMinutes = 0,
            posterUrl = "",
            backdropUrl = "",
            audioLanguages = listOf("AUTO"),
            subtitles = subs,
            streamSources = listOf(StreamSource(entity.streamUrl, entity.mimeType, "User Stream"))
        )
    }

    override suspend fun checkHealth(): ProviderHealth {
        val count = dao.getAllOnce().size
        return ProviderHealth(
            providerId = id,
            providerName = name,
            category = category,
            isConfigured = count > 0,
            isReachable = true,
            statusMessage = if (count > 0) "$count authorized feed(s) saved." else "No custom feeds added yet."
        )
    }

    override suspend fun getHomeSections(): Result<List<HomeSection>> {
        val items = loadAll()
        return Result.success(
            if (items.isEmpty()) emptyList() else listOf(HomeSection("auth_streams", "Authorized Feeds", items))
        )
    }

    override suspend fun search(query: String): Result<List<MediaItem>> {
        val q = query.trim().lowercase()
        return Result.success(
            loadAll().filter {
                it.title.lowercase().contains(q) || it.arabicTitle?.lowercase()?.contains(q) == true
            }
        )
    }

    override suspend fun getMovieDetails(mediaId: String): Result<MediaItem?> =
        Result.success(loadAll().firstOrNull { it.id == mediaId })

    override suspend fun getSeriesDetails(seriesId: String): Result<MediaItem?> = Result.success(null)
    override suspend fun getSeasons(seriesId: String): Result<List<Season>> = Result.success(emptyList())
    override suspend fun getEpisodes(seriesId: String, seasonNumber: Int): Result<List<Episode>> = Result.success(emptyList())

    override suspend fun resolvePlayback(mediaId: String, episodeId: String?): Result<List<StreamSource>> {
        val item = loadAll().firstOrNull { it.id == mediaId }
            ?: return Result.failure(NoSuchElementException("Stream not found."))
        return Result.success(item.streamSources)
    }

    override suspend fun getSubtitles(mediaId: String, episodeId: String?): Result<List<SubtitleTrack>> =
        Result.success(loadAll().firstOrNull { it.id == mediaId }?.subtitles.orEmpty())
}

/**
 * Official Streaming Service Deep-Link Directory.
 * Commercial streaming services do not expose unauthorized playback APIs; this provider
 * transparently hands off to the official Android / Android TV application or web portal.
 */
data class OfficialServiceLink(
    val id: String,
    val name: String,
    val packageName: String,
    val webUrl: String,
    val note: String
)

object OfficialStreamingDirectory {
    val services: List<OfficialServiceLink> = listOf(
        OfficialServiceLink("netflix", "Netflix", "com.netflix.mediaclient", "https://www.netflix.com", "Opens official Netflix app"),
        OfficialServiceLink("disney", "Disney+", "com.disney.disneyplus", "https://www.disneyplus.com", "Opens official Disney+ app"),
        OfficialServiceLink("prime", "Prime Video", "com.amazon.avod.thirdpartyclient", "https://www.primevideo.com", "Opens official Prime Video app"),
        OfficialServiceLink("shahid", "Shahid", "net.mbc.shahid", "https://shahid.mbc.net", "Opens official Shahid Arabic app")
    )
}
