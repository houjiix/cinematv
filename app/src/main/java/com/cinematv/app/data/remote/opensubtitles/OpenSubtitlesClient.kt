package com.cinematv.app.data.remote.opensubtitles

import com.cinematv.app.data.local.security.SecureCredentialStore
import com.cinematv.app.domain.model.SubtitleFormat
import com.cinematv.app.domain.model.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

@Serializable
data class OpenSubtitlesSearchResponse(
    val data: List<OpenSubtitleEntry> = emptyList()
)

@Serializable
data class OpenSubtitleEntry(
    val id: String = "",
    val attributes: OpenSubtitleAttributes = OpenSubtitleAttributes()
)

@Serializable
data class OpenSubtitleAttributes(
    val language: String = "ar",
    val release: String = "",
    @SerialName("download_count") val downloadCount: Int = 0,
    val files: List<OpenSubtitleFile> = emptyList()
)

@Serializable
data class OpenSubtitleFile(
    @SerialName("file_id") val fileId: Int = 0,
    @SerialName("file_name") val fileName: String = ""
)

@Serializable
data class OpenSubtitlesDownloadResponse(
    val link: String = "",
    @SerialName("file_name") val fileName: String = "",
    val remaining: Int = 0
)

/**
 * Integrates OpenSubtitles official REST API v1 respecting API-key authentication,
 * User-Agent header requirements, and rate limits (HTTP 429).
 * Prioritizes Arabic ('ar') subtitles first, with English ('en') as secondary fallback.
 */
class OpenSubtitlesClient(
    private val httpClient: OkHttpClient,
    private val credentialStore: SecureCredentialStore
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val baseUrl = "https://api.opensubtitles.com/api/v1"

    suspend fun searchSubtitles(
        query: String,
        languages: String = "ar,en",
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Result<List<SubtitleTrack>> = withContext(Dispatchers.IO) {
        val apiKey = credentialStore.getOpenSubtitlesApiKey()
            ?: return@withContext Result.failure(
                IllegalStateException("OpenSubtitles API key is not configured. Add your official API key in Settings.")
            )
        if (query.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        runCatching {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val urlBuilder = StringBuilder("$baseUrl/subtitles?query=$encodedQuery&languages=$languages")
            if (seasonNumber != null) urlBuilder.append("&season_number=$seasonNumber")
            if (episodeNumber != null) urlBuilder.append("&episode_number=$episodeNumber")

            val req = Request.Builder()
                .url(urlBuilder.toString())
                .header("Api-Key", apiKey)
                .header("User-Agent", "CinemaTV v1.0.0")
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(req).execute().use { res ->
                when (res.code) {
                    401 -> error("Invalid OpenSubtitles API key.")
                    429 -> error("OpenSubtitles rate limit exceeded. Please wait before retrying.")
                    !in 200..299 -> error("OpenSubtitles search failed (HTTP ${res.code}).")
                }
                val parsed = json.decodeFromString<OpenSubtitlesSearchResponse>(res.body?.string().orEmpty())
                parsed.data
                    .mapNotNull { entry ->
                        val file = entry.attributes.files.firstOrNull() ?: return@mapNotNull null
                        val format = SubtitleFormat.fromFilenameOrMime(file.fileName)
                        SubtitleTrack(
                            id = "os_${file.fileId}",
                            languageCode = entry.attributes.language,
                            label = "${entry.attributes.language.uppercase()} — ${entry.attributes.release.ifBlank { file.fileName }}",
                            format = if (format == SubtitleFormat.UNSUPPORTED) SubtitleFormat.SRT else format,
                            url = "opensubtitles://file/${file.fileId}",
                            isEmbedded = false
                        )
                    }
                    // Prioritize Arabic first, then English
                    .sortedWith(
                        compareBy<SubtitleTrack> {
                            when {
                                it.languageCode.startsWith("ar", ignoreCase = true) -> 0
                                it.languageCode.startsWith("en", ignoreCase = true) -> 1
                                else -> 2
                            }
                        }
                    )
            }
        }
    }

    suspend fun requestDownloadLink(fileId: Int): Result<OpenSubtitlesDownloadResponse> = withContext(Dispatchers.IO) {
        val apiKey = credentialStore.getOpenSubtitlesApiKey()
            ?: return@withContext Result.failure(IllegalStateException("OpenSubtitles API key not configured."))

        runCatching {
            val payload = """{"file_id":$fileId}"""
            val req = Request.Builder()
                .url("$baseUrl/download")
                .header("Api-Key", apiKey)
                .header("User-Agent", "CinemaTV v1.0.0")
                .header("Accept", "application/json")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { res ->
                if (res.code == 429) error("OpenSubtitles daily download quota reached.")
                if (!res.isSuccessful) error("Failed to request subtitle download link (HTTP ${res.code}).")
                json.decodeFromString<OpenSubtitlesDownloadResponse>(res.body?.string().orEmpty())
            }
        }
    }
}
