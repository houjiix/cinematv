package com.cinematv.app.subtitles

import com.cinematv.app.domain.model.SubtitleFormat
import com.cinematv.app.domain.model.SubtitlePreference
import com.cinematv.app.domain.model.SubtitleTrack
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val rawText: String,
    val formattedText: String,
    val isRtl: Boolean
)

sealed class SubtitleParseResult {
    data class Success(val format: SubtitleFormat, val cues: List<SubtitleCue>) : SubtitleParseResult()
    data class UnsupportedFormat(val reason: String) : SubtitleParseResult()
    data class ParseError(val reason: String) : SubtitleParseResult()
}

/**
 * Production Arabic-first subtitle parser, Unicode BiDi isolator, synchronizer, and track selector.
 *
 * Guarantees for Arabic subtitles:
 * - Preserves connected Arabic letter shaping (never manually reverses characters).
 * - Wraps Arabic/RTL lines with Unicode Right-to-Left Isolate (RLI \u2067 ... PDI \u2069)
 *   so mixed Arabic + English sentences, Latin/Arabic numerals, and trailing punctuation
 *   render in true logical reading order on Android TextView & Media3 SubtitleView.
 * - Supports UTF-8 (with BOM stripping) and Windows-1256 fallback for legacy Arabic SRT files.
 * - Explicitly reports unsupported formats instead of silently failing.
 */
object ArabicSubtitleEngine {

    private const val RLI = '\u2067' // Right-to-Left Isolate
    private const val LRI = '\u2066' // Left-to-Right Isolate
    private const val PDI = '\u2069' // Pop Directional Isolate
    private val ARABIC_CHAR_REGEX = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val ASS_TAG_REGEX = Regex("\\{[^}]*\\}")

    fun containsArabic(text: String): Boolean = ARABIC_CHAR_REGEX.containsMatchIn(text)

    /**
     * Decodes raw subtitle bytes safely: validates UTF-8 first, falling back to Windows-1256
     * when legacy Arabic subtitle files are encountered.
     */
    fun decodeBytes(bytes: ByteArray): String {
        val utf8 = String(bytes, StandardCharsets.UTF_8).removePrefix("\uFEFF")
        return if (!utf8.contains('\uFFFD')) {
            utf8
        } else {
            runCatching { String(bytes, Charset.forName("windows-1256")) }
                .getOrDefault(utf8)
                .removePrefix("\uFEFF")
        }
    }

    /**
     * Applies Unicode BiDi isolation per line so Arabic punctuation, mixed English tokens,
     * and Arabic/Latin numerals render accurately without reversed words or displaced punctuation.
     */
    fun formatBidiText(rawText: String, forceRtl: Boolean = false): String {
        val cleaned = rawText
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace(HTML_TAG_REGEX, "")
            .replace(ASS_TAG_REGEX, "")
            .trim()

        if (cleaned.isEmpty()) return ""

        return cleaned.split("\n").joinToString("\n") { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                ""
            } else if (forceRtl || containsArabic(trimmedLine)) {
                "$RLI$trimmedLine$PDI"
            } else {
                "$LRI$trimmedLine$PDI"
            }
        }
    }

    fun parse(content: String, format: SubtitleFormat, forceRtl: Boolean = false): SubtitleParseResult {
        if (!format.supportedByMedia3 || format == SubtitleFormat.UNSUPPORTED) {
            return SubtitleParseResult.UnsupportedFormat(
                "Subtitle format '${format.extension}' is not supported by the playback engine."
            )
        }
        val normalized = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n").trim()
        if (normalized.isEmpty()) {
            return SubtitleParseResult.ParseError("Subtitle file is empty.")
        }

        return when (format) {
            SubtitleFormat.SRT -> parseSrt(normalized, forceRtl)
            SubtitleFormat.WEBVTT -> parseWebVtt(normalized, forceRtl)
            SubtitleFormat.ASS, SubtitleFormat.SSA -> parseAss(normalized, format, forceRtl)
            SubtitleFormat.UNSUPPORTED -> SubtitleParseResult.UnsupportedFormat("Unsupported format.")
        }
    }

    private fun parseSrt(content: String, forceRtl: Boolean): SubtitleParseResult {
        val blocks = content.split(Regex("\n\\s*\n"))
        val cues = mutableListOf<SubtitleCue>()
        var idx = 1

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) continue
            val timingIdx = lines.indexOfFirst { it.contains("-->") }
            if (timingIdx == -1 || timingIdx + 1 >= lines.size) continue

            val parts = lines[timingIdx].split("-->")
            if (parts.size < 2) continue
            val start = parseTimestampMs(parts[0].trim()) ?: continue
            val end = parseTimestampMs(parts[1].trim().split(" ")[0]) ?: continue
            val raw = lines.drop(timingIdx + 1).joinToString("\n")
            val isRtl = forceRtl || containsArabic(raw)
            cues.add(
                SubtitleCue(
                    index = idx++,
                    startMs = start,
                    endMs = end,
                    rawText = raw,
                    formattedText = formatBidiText(raw, isRtl),
                    isRtl = isRtl
                )
            )
        }

        return if (cues.isEmpty()) {
            SubtitleParseResult.ParseError("No valid SRT cues found.")
        } else {
            SubtitleParseResult.Success(SubtitleFormat.SRT, cues.sortedBy { it.startMs })
        }
    }

    private fun parseWebVtt(content: String, forceRtl: Boolean): SubtitleParseResult {
        val body = if (content.startsWith("WEBVTT")) {
            content.substringAfter("\n", "")
        } else {
            content
        }
        val srtResult = parseSrt(body, forceRtl)
        return when (srtResult) {
            is SubtitleParseResult.Success -> SubtitleParseResult.Success(SubtitleFormat.WEBVTT, srtResult.cues)
            is SubtitleParseResult.ParseError -> SubtitleParseResult.ParseError("No valid WebVTT cues found.")
            is SubtitleParseResult.UnsupportedFormat -> srtResult
        }
    }

    private fun parseAss(content: String, format: SubtitleFormat, forceRtl: Boolean): SubtitleParseResult {
        val cues = mutableListOf<SubtitleCue>()
        var idx = 1
        content.lines().forEach { line ->
            if (!line.startsWith("Dialogue:", ignoreCase = true)) return@forEach
            val payload = line.substringAfter(":")
            // Standard ASS format has 9 commas before Text field
            val fields = payload.split(",", limit = 10)
            if (fields.size < 10) return@forEach
            val start = parseAssTimestampMs(fields[1].trim()) ?: return@forEach
            val end = parseAssTimestampMs(fields[2].trim()) ?: return@forEach
            val raw = fields[9].trim()
            val isRtl = forceRtl || containsArabic(raw)
            cues.add(
                SubtitleCue(
                    index = idx++,
                    startMs = start,
                    endMs = end,
                    rawText = raw,
                    formattedText = formatBidiText(raw, isRtl),
                    isRtl = isRtl
                )
            )
        }
        return if (cues.isEmpty()) {
            SubtitleParseResult.ParseError("No valid ASS/SSA dialogue events found.")
        } else {
            SubtitleParseResult.Success(format, cues.sortedBy { it.startMs })
        }
    }

    fun findActiveCue(cues: List<SubtitleCue>, positionMs: Long, syncOffsetMs: Long = 0L): SubtitleCue? {
        val adjustedMs = positionMs - syncOffsetMs
        return cues.firstOrNull { adjustedMs in it.startMs..it.endMs }
    }

    /**
     * Resolves default subtitle track according to priority:
     * 1. Arabic (default) -> falls back to English if Arabic is unavailable.
     * 2. English -> falls back to Arabic if English is unavailable.
     * 3. Ask -> returns null so player prompts the user.
     */
    fun selectPreferredTrack(
        tracks: List<SubtitleTrack>,
        preference: SubtitlePreference
    ): SubtitleTrack? {
        val supported = tracks.filter { it.format.supportedByMedia3 }
        if (supported.isEmpty()) return null

        return when (preference) {
            SubtitlePreference.ASK -> null
            SubtitlePreference.ARABIC ->
                supported.firstOrNull { isArabicCode(it.languageCode) }
                    ?: supported.firstOrNull { isEnglishCode(it.languageCode) }
            SubtitlePreference.ENGLISH ->
                supported.firstOrNull { isEnglishCode(it.languageCode) }
                    ?: supported.firstOrNull { isArabicCode(it.languageCode) }
        }
    }

    fun isArabicCode(code: String): Boolean {
        val c = code.lowercase()
        return c == "ar" || c == "ara" || c == "arb" || c.startsWith("ar-") || c.startsWith("ar_")
    }

    fun isEnglishCode(code: String): Boolean {
        val c = code.lowercase()
        return c == "en" || c == "eng" || c.startsWith("en-") || c.startsWith("en_")
    }

    private fun parseTimestampMs(raw: String): Long? {
        val normalized = raw.replace(',', '.')
        val parts = normalized.split(":")
        return runCatching {
            when (parts.size) {
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val sParts = parts[2].split(".")
                    val s = sParts[0].toLong()
                    val ms = sParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
                    ((h * 3600) + (m * 60) + s) * 1000L + ms
                }
                2 -> {
                    val m = parts[0].toLong()
                    val sParts = parts[1].split(".")
                    val s = sParts[0].toLong()
                    val ms = sParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
                    ((m * 60) + s) * 1000L + ms
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun parseAssTimestampMs(raw: String): Long? {
        val parts = raw.split(":")
        if (parts.size != 3) return null
        return runCatching {
            val h = parts[0].toLong()
            val m = parts[1].toLong()
            val sParts = parts[2].split(".")
            val s = sParts[0].toLong()
            val cs = sParts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLong() ?: 0L
            ((h * 3600) + (m * 60) + s) * 1000L + (cs * 10L)
        }.getOrNull()
    }
}
