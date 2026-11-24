package com.cinematv.app.data.remote.jellyfin

import com.cinematv.app.data.local.prefs.SettingsRepository
import com.cinematv.app.data.local.security.SecureCredentialStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URLEncoder
import java.util.UUID

@Serializable
data class JellyfinPublicInfo(
    @SerialName("ServerName") val serverName: String = "",
    @SerialName("Version") val version: String = "",
    @SerialName("Id") val id: String = ""
)

@Serializable
data class JellyfinAuthRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val password: String
)

@Serializable
data class JellyfinAuthResponse(
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("ServerId") val serverId: String = "",
    @SerialName("User") val user: JellyfinUserDto
)

@Serializable
data class JellyfinUserDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String
)

@Serializable
data class JellyfinItemsResponse(
    @SerialName("Items") val items: List<JellyfinItemDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0
)

@Serializable
data class JellyfinMediaStreamDto(
    @SerialName("Index") val index: Int = 0,
    @SerialName("Type") val type: String = "",
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("IsExternal") val isExternal: Boolean = false
)

@Serializable
data class JellyfinMediaSourceDto(
    @SerialName("Id") val id: String = "",
    @SerialName("MediaStreams") val mediaStreams: List<JellyfinMediaStreamDto> = emptyList()
)

@Serializable
data class JellyfinItemDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("OriginalTitle") val originalTitle: String? = null,
    @SerialName("Type") val type: String = "Movie",
    @SerialName("Overview") val overview: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("MediaSources") val mediaSources: List<JellyfinMediaSourceDto> = emptyList()
)

object JellyfinUrlValidator {
    fun normalizeAndValidate(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Server URL is required."))
        }
        val uri = runCatching { URI(trimmed) }.getOrElse {
            return Result.failure(IllegalArgumentException("Invalid URL syntax."))
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return Result.failure(IllegalArgumentException("Server URL must start with https:// or http://."))
        }
        if (uri.host.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Server URL must include a valid hostname or IP address."))
        }
        return Result.success(trimmed)
    }
}

class JellyfinProvider(
    private val httpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val credentialStore: SecureCredentialStore
) : ContentProvider {

    override val id: String = "jellyfin"
    override val name: String = "Jellyfin Media Server"
    override val category: ProviderCategory = ProviderCategory.PERSONAL_MEDIA_SERVER

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val deviceId: String = UUID.nameUUIDFromBytes("com.cinematv.app.android".toByteArray()).toString()

    private fun authHeader(token: String? = null): String {
        val base = """MediaBrowser Client="CINEMA TV", Device="Android", DeviceId="$deviceId", Version="1.0.0""""
        return if (!token.isNullOrBlank()) """$base, Token="$token"""" else base
    }

    suspend fun verifyServerConnection(rawUrl: String): Result<JellyfinPublicInfo> = withContext(Dispatchers.IO) {
        val normalized = JellyfinUrlValidator.normalizeAndValidate(rawUrl).getOrElse {
            return@withContext Result.failure(it)
        }
        runCatching {
            val request = Request.Builder()
                .url("$normalized/System/Info/Public")
                .header("X-Emby-Authorization", authHeader())
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Server responded with HTTP ${response.code}.")
                }
                val body = response.body?.string().orEmpty()
                json.decodeFromString<JellyfinPublicInfo>(body)
            }
        }
    }

    suspend fun login(rawUrl: String, username: String, password: String): Result<JellyfinAuthResponse> =
        withContext(Dispatchers.IO) {
            val normalized = JellyfinUrlValidator.normalizeAndValidate(rawUrl).getOrElse {
                return@withContext Result.failure(it)
            }
            if (username.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Username cannot be empty."))
            }
            runCatching {
                val payload = json.encodeToString(
                    JellyfinAuthRequest.serializer(),
                    JellyfinAuthRequest(username.trim(), password)
                )
                val request = Request.Builder()
                    .url("$normalized/Users/AuthenticateByName")
                    .header("X-Emby-Authorization", authHeader())
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    when (response.code) {
                        401, 403 -> error("Invalid Jellyfin username or password.")
                        !in 200..299 -> error("Authentication failed with HTTP ${response.code}.")
                    }
                    val body = response.body?.string().orEmpty()
                    val auth = json.decodeFromString<JellyfinAuthResponse>(body)
                    credentialStore.saveJellyfinSession(auth.accessToken, auth.user.id, auth.serverId)
                    settingsRepository.updateSettings {
                        it.copy(jellyfinServerUrl = normalized, jellyfinUsername = auth.user.name)
                    }
                    auth
                }
            }
        }

    suspend fun disconnect() {
        credentialStore.clearJellyfinSession()
        settingsRepository.updateSettings { it.copy(jellyfinUsername = "") }
    }

    override suspend fun checkHealth(): ProviderHealth {
        val url = settingsRepository.settingsFlow.first().jellyfinServerUrl
        val token = credentialStore.getJellyfinAccessToken()
        if (url.isBlank() || token.isNullOrBlank()) {
            return ProviderHealth(
                providerId = id,
                providerName = name,
                category = category,
                isConfigured = false,
                isReachable = false,
                statusMessage = "Not configured — open Settings to connect your Jellyfin server."
            )
        }
        return verifyServerConnection(url).fold(
            onSuccess = { info ->
                ProviderHealth(
                    providerId = id,
                    providerName = info.serverName.ifBlank { name },
                    category = category,
                    isConfigured = true,
                    isReachable = true,
                    statusMessage = "Connected (Jellyfin ${info.version})"
                )
            },
            onFailure = { err ->
                ProviderHealth(
                    providerId = id,
                    providerName = name,
                    category = category,
                    isConfigured = true,
                    isReachable = false,
                    statusMessage = err.message ?: "Server unreachable"
                )
            }
        )
    }

    private suspend fun activeCredentials(): Triple<String, String, String>? {
        val url = settingsRepository.settingsFlow.first().jellyfinServerUrl
        val token = credentialStore.getJellyfinAccessToken()
        val userId = credentialStore.getJellyfinUserId()
        if (url.isBlank() || token.isNullOrBlank() || userId.isNullOrBlank()) return null
        return Triple(url.trimEnd('/'), userId, token)
    }

    override suspend fun getHomeSections(): Result<List<HomeSection>> = withContext(Dispatchers.IO) {
        val (baseUrl, userId, token) = activeCredentials() ?: return@withContext Result.success(emptyList())
        runCatching {
            val movies = fetchItems(baseUrl, userId, token, "IncludeItemTypes=Movie&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Limit=20&Fields=Overview,Genres,OriginalTitle,MediaSources")
            val series = fetchItems(baseUrl, userId, token, "IncludeItemTypes=Series&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Limit=20&Fields=Overview,Genres,OriginalTitle")
            buildList {
                if (movies.isNotEmpty()) add(HomeSection("jf_movies", "Jellyfin — Recently Added Movies", movies))
                if (series.isNotEmpty()) add(HomeSection("jf_series", "Jellyfin — Recently Added Series", series))
            }
        }
    }

    override suspend fun search(query: String): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        val (baseUrl, userId, token) = activeCredentials() ?: return@withContext Result.success(emptyList())
        runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            fetchItems(baseUrl, userId, token, "SearchTerm=$encoded&IncludeItemTypes=Movie,Series&Recursive=true&Limit=30&Fields=Overview,Genres,OriginalTitle,MediaSources")
        }
    }

    override suspend fun getMovieDetails(mediaId: String): Result<MediaItem?> = withContext(Dispatchers.IO) {
        val (baseUrl, userId, token) = activeCredentials() ?: return@withContext Result.success(null)
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/Users/$userId/Items/$mediaId")
                .header("X-Emby-Authorization", authHeader(token))
                .get()
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (res.code == 401) error("Jellyfin session expired. Please sign in again.")
                if (!res.isSuccessful) error("Could not load movie details (HTTP ${res.code}).")
                val dto = json.decodeFromString<JellyfinItemDto>(res.body?.string().orEmpty())
                dto.toMediaItem(baseUrl, token)
            }
        }
    }

    override suspend fun getSeriesDetails(seriesId: String): Result<MediaItem?> = getMovieDetails(seriesId).map { item ->
        item?.copy(seasons = getSeasons(seriesId).getOrDefault(emptyList()))
    }

    override suspend fun getSeasons(seriesId: String): Result<List<Season>> = withContext(Dispatchers.IO) {
        val (baseUrl, userId, token) = activeCredentials() ?: return@withContext Result.success(emptyList())
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/Shows/$seriesId/Seasons?userId=$userId")
                .header("X-Emby-Authorization", authHeader(token))
                .get()
                .build()
            val seasonDtos = httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("Failed to load seasons (HTTP ${res.code}).")
                json.decodeFromString<JellyfinItemsResponse>(res.body?.string().orEmpty()).items
            }
            seasonDtos.map { seasonDto ->
                val seasonNum = seasonDto.indexNumber ?: 1
                Season(
                    seasonNumber = seasonNum,
                    title = seasonDto.name,
                    episodes = getEpisodes(seriesId, seasonNum).getOrDefault(emptyList())
                )
            }
        }
    }

    override suspend fun getEpisodes(seriesId: String, seasonNumber: Int): Result<List<Episode>> = withContext(Dispatchers.IO) {
        val (baseUrl, userId, token) = activeCredentials() ?: return@withContext Result.success(emptyList())
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/Shows/$seriesId/Episodes?userId=$userId&Fields=Overview,MediaSources")
                .header("X-Emby-Authorization", authHeader(token))
                .get()
                .build()
            val items = httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("Failed to load episodes (HTTP ${res.code}).")
                json.decodeFromString<JellyfinItemsResponse>(res.body?.string().orEmpty()).items
            }
            items.filter { (it.parentIndexNumber ?: 1) == seasonNumber }.map { dto ->
                Episode(
                    id = dto.id,
                    seriesId = seriesId,
                    seasonNumber = seasonNumber,
                    episodeNumber = dto.indexNumber ?: 1,
                    title = dto.name,
                    synopsis = dto.overview.orEmpty(),
                    runtimeMinutes = ((dto.runTimeTicks ?: 0L) / 600_000_000L).toInt().coerceAtLeast(1),
                    thumbnailUrl = "$baseUrl/Items/${dto.id}/Images/Primary?api_key=$token",
                    streamSources = buildPlaybackSources(baseUrl, dto.id, token),
                    subtitles = extractSubtitles(baseUrl, dto, token)
                )
            }
        }
    }

    override suspend fun resolvePlayback(mediaId: String, episodeId: String?): Result<List<StreamSource>> {
        val (baseUrl, _, token) = activeCredentials()
            ?: return Result.failure(IllegalStateException("Jellyfin is not connected."))
        val targetId = episodeId ?: mediaId
        return Result.success(buildPlaybackSources(baseUrl, targetId, token))
    }

    override suspend fun getSubtitles(mediaId: String, episodeId: String?): Result<List<SubtitleTrack>> {
        val targetId = episodeId ?: mediaId
        return getMovieDetails(targetId).map { it?.subtitles.orEmpty() }
    }

    override suspend fun reportProgress(mediaId: String, episodeId: String?, positionMs: Long, isCompleted: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            val (baseUrl, _, token) = activeCredentials() ?: return@withContext Result.success(Unit)
            val targetId = episodeId ?: mediaId
            val ticks = positionMs * 10_000L
            runCatching {
                val endpoint = if (isCompleted) "$baseUrl/Sessions/Playing/Stopped" else "$baseUrl/Sessions/Playing/Progress"
                val payload = """{"ItemId":"$targetId","PositionTicks":$ticks,"IsPaused":false}"""
                val req = Request.Builder()
                    .url(endpoint)
                    .header("X-Emby-Authorization", authHeader(token))
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().use { Unit }
            }
        }

    private fun fetchItems(baseUrl: String, userId: String, token: String, query: String): List<MediaItem> {
        val req = Request.Builder()
            .url("$baseUrl/Users/$userId/Items?$query")
            .header("X-Emby-Authorization", authHeader(token))
            .get()
            .build()
        return httpClient.newCall(req).execute().use { res ->
            if (res.code == 401) error("Jellyfin authentication expired. Reconnect in Settings.")
            if (!res.isSuccessful) error("Jellyfin request failed (HTTP ${res.code}).")
            val parsed = json.decodeFromString<JellyfinItemsResponse>(res.body?.string().orEmpty())
            parsed.items.map { it.toMediaItem(baseUrl, token) }
        }
    }

    private fun buildPlaybackSources(baseUrl: String, itemId: String, token: String): List<StreamSource> = listOf(
        StreamSource(
            url = "$baseUrl/Videos/$itemId/master.m3u8?api_key=$token&MediaSourceId=$itemId",
            mimeType = "application/x-mpegURL",
            qualityLabel = "HLS Adaptive",
            headers = mapOf("X-Emby-Authorization" to authHeader(token))
        ),
        StreamSource(
            url = "$baseUrl/Videos/$itemId/stream?static=true&api_key=$token",
            mimeType = "video/mp4",
            qualityLabel = "Direct Stream",
            headers = mapOf("X-Emby-Authorization" to authHeader(token))
        )
    )

    private fun extractSubtitles(baseUrl: String, dto: JellyfinItemDto, token: String): List<SubtitleTrack> {
        val source = dto.mediaSources.firstOrNull() ?: return emptyList()
        return source.mediaStreams
            .filter { it.type.equals("Subtitle", ignoreCase = true) }
            .map { stream ->
                val codec = stream.codec.orEmpty().lowercase()
                val format = when {
                    codec.contains("srt") || codec.contains("subrip") -> SubtitleFormat.SRT
                    codec.contains("vtt") -> SubtitleFormat.WEBVTT
                    codec.contains("ass") -> SubtitleFormat.ASS
                    codec.contains("ssa") -> SubtitleFormat.SSA
                    else -> SubtitleFormat.SRT
                }
                val lang = stream.language ?: "und"
                SubtitleTrack(
                    id = "jf_${dto.id}_${stream.index}",
                    languageCode = lang,
                    label = stream.displayTitle ?: lang.uppercase(),
                    format = format,
                    url = "$baseUrl/Videos/${dto.id}/${source.id}/Subtitles/${stream.index}/0/Stream.${format.extension}?api_key=$token",
                    isEmbedded = !stream.isExternal
                )
            }
    }

    private fun JellyfinItemDto.toMediaItem(baseUrl: String, token: String): MediaItem {
        val audioLangs = mediaSources.firstOrNull()?.mediaStreams
            ?.filter { it.type.equals("Audio", ignoreCase = true) }
            ?.mapNotNull { it.language?.uppercase() }
            ?.distinct()
            .orEmpty()
            .ifEmpty { listOf("EN") }

        return MediaItem(
            id = id,
            providerId = "jellyfin",
            type = if (type.equals("Series", ignoreCase = true)) MediaType.SERIES else MediaType.MOVIE,
            title = name,
            originalTitle = originalTitle ?: name,
            arabicTitle = null,
            synopsis = overview.orEmpty(),
            releaseYear = productionYear ?: 0,
            genres = genres,
            runtimeMinutes = ((runTimeTicks ?: 0L) / 600_000_000L).toInt(),
            rating = communityRating,
            posterUrl = "$baseUrl/Items/$id/Images/Primary?api_key=$token",
            backdropUrl = "$baseUrl/Items/$id/Images/Backdrop?api_key=$token",
            audioLanguages = audioLangs,
            subtitles = extractSubtitles(baseUrl, this, token),
            streamSources = if (type.equals("Movie", ignoreCase = true)) buildPlaybackSources(baseUrl, id, token) else emptyList()
        )
    }
}
