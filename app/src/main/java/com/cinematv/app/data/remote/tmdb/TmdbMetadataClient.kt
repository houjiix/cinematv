package com.cinematv.app.data.remote.tmdb

import com.cinematv.app.data.local.security.SecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

@Serializable
data class TmdbSearchResponse(
    val results: List<TmdbMetadataDto> = emptyList()
)

@Serializable
data class TmdbMetadataDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null
)

data class EnrichedMetadata(
    val tmdbId: Int,
    val arabicTitle: String?,
    val englishTitle: String?,
    val originalTitle: String?,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double?
)

/**
 * TMDB Metadata Enrichment Client (strictly catalog metadata & artwork — never a video playback provider).
 * Supports bilingual Arabic (ar-SA) and English (en-US) metadata lookups when user configures an API key.
 * Attribution: This product uses the TMDB API but is not endorsed or certified by TMDB.
 */
class TmdbMetadataClient(
    private val httpClient: OkHttpClient,
    private val credentialStore: SecureCredentialStore
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val apiBase = "https://api.themoviedb.org/3"
    private val imageBase = "https://image.tmdb.org/t/p"

    suspend fun enrichTitle(query: String, isSeries: Boolean): Result<EnrichedMetadata?> = withContext(Dispatchers.IO) {
        val apiKey = credentialStore.getTmdbApiKey()
            ?: return@withContext Result.failure(IllegalStateException("TMDB API key is not configured."))

        runCatching {
            val endpoint = if (isSeries) "search/tv" else "search/movie"
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")

            val arHit = fetchFirst("$apiBase/$endpoint?api_key=$apiKey&language=ar-SA&query=$encoded")
            val enHit = fetchFirst("$apiBase/$endpoint?api_key=$apiKey&language=en-US&query=$encoded")
            val primary = arHit ?: enHit ?: return@runCatching null

            EnrichedMetadata(
                tmdbId = primary.id,
                arabicTitle = arHit?.title ?: arHit?.name,
                englishTitle = enHit?.title ?: enHit?.name,
                originalTitle = primary.originalTitle ?: primary.originalName,
                overview = primary.overview.ifBlank { enHit?.overview.orEmpty() },
                posterUrl = primary.posterPath?.let { "$imageBase/w500$it" },
                backdropUrl = primary.backdropPath?.let { "$imageBase/w1280$it" },
                rating = primary.voteAverage
            )
        }
    }

    private fun fetchFirst(url: String): TmdbMetadataDto? {
        val req = Request.Builder().url(url).get().build()
        return httpClient.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            json.decodeFromString<TmdbSearchResponse>(res.body?.string().orEmpty()).results.firstOrNull()
        }
    }
}
