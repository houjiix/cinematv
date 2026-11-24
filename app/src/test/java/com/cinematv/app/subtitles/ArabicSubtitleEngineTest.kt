package com.cinematv.app.subtitles

import com.cinematv.app.domain.model.SubtitleFormat
import com.cinematv.app.domain.model.SubtitlePreference
import com.cinematv.app.domain.model.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicSubtitleEngineTest {

    @Test
    fun parsesArabicSrtWithConnectedShapingAndBidiIsolates() {
        val srt = """
            1
            00:00:01,000 --> 00:00:05,500
            مرحباً بكم في CINEMA TV — دقة 4K بنسبة ١٠٠٪!

            2
            00:00:06,000 --> 00:00:10,000
            السطر الأول بالعربية،
            والسطر الثاني مع علامة استفهام؟
        """.trimIndent()

        val result = ArabicSubtitleEngine.parse(srt, SubtitleFormat.SRT)
        assertTrue(result is SubtitleParseResult.Success)
        val cues = (result as SubtitleParseResult.Success).cues
        assertEquals(2, cues.size)
        assertTrue(cues[0].isRtl)
        // Wrapped in Unicode RLI (\u2067) ... PDI (\u2069) without reversing Arabic letters
        assertTrue(cues[0].formattedText.startsWith("\u2067"))
        assertTrue(cues[0].formattedText.endsWith("\u2069"))
        assertTrue(cues[0].formattedText.contains("مرحباً بكم في CINEMA TV"))
        assertTrue(cues[0].formattedText.contains("١٠٠٪!"))
        // Multi-line preserves both lines isolated individually
        val lines = cues[1].formattedText.split("\n")
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("السطر الأول بالعربية،"))
        assertTrue(lines[1].contains("علامة استفهام؟"))
    }

    @Test
    fun parsesArabicWebVttAndAssFormats() {
        val vtt = """
            WEBVTT

            00:01.000 --> 00:04.000
            ترجمة WebVTT باللغة العربية.
        """.trimIndent()
        val vttResult = ArabicSubtitleEngine.parse(vtt, SubtitleFormat.WEBVTT)
        assertTrue(vttResult is SubtitleParseResult.Success)
        assertEquals(1, (vttResult as SubtitleParseResult.Success).cues.size)

        val ass = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:02.00,0:00:06.50,Default,,0,0,0,,{\i1}ترجمة ASS مع النص العربي\Nوالسطر الثاني
        """.trimIndent()
        val assResult = ArabicSubtitleEngine.parse(ass, SubtitleFormat.ASS)
        assertTrue(assResult is SubtitleParseResult.Success)
        val cue = (assResult as SubtitleParseResult.Success).cues.first()
        assertEquals(2000L, cue.startMs)
        assertEquals(6500L, cue.endMs)
        assertTrue(cue.formattedText.contains("ترجمة ASS مع النص العربي"))
        assertTrue(cue.formattedText.contains("\n"))
    }

    @Test
    fun appliesSubtitleSynchronizationOffsetAccurately() {
        val srt = """
            1
            00:00:10,000 --> 00:00:15,000
            ترجمة متزامنة بدقة.
        """.trimIndent()
        val cues = (ArabicSubtitleEngine.parse(srt, SubtitleFormat.SRT) as SubtitleParseResult.Success).cues

        assertNotNull(ArabicSubtitleEngine.findActiveCue(cues, positionMs = 12_000L, syncOffsetMs = 0L))
        // Delay subtitle by +3000ms -> should appear at 13s..18s, not at 11s
        assertNull(ArabicSubtitleEngine.findActiveCue(cues, positionMs = 11_000L, syncOffsetMs = 3_000L))
        assertNotNull(ArabicSubtitleEngine.findActiveCue(cues, positionMs = 16_000L, syncOffsetMs = 3_000L))
    }

    @Test
    fun selectsArabicByDefaultAndFallsBackToEnglishWhenUnavailable() {
        val enOnly = listOf(
            SubtitleTrack("en1", "en", "English", SubtitleFormat.SRT)
        )
        val arAndEn = listOf(
            SubtitleTrack("en1", "en", "English", SubtitleFormat.SRT),
            SubtitleTrack("ar1", "ar", "العربية", SubtitleFormat.SRT)
        )

        assertEquals("ar1", ArabicSubtitleEngine.selectPreferredTrack(arAndEn, SubtitlePreference.ARABIC)?.id)
        assertEquals("en1", ArabicSubtitleEngine.selectPreferredTrack(enOnly, SubtitlePreference.ARABIC)?.id)
        assertNull(ArabicSubtitleEngine.selectPreferredTrack(arAndEn, SubtitlePreference.ASK))
    }

    @Test
    fun reportsUnsupportedSubtitleFormatsExplicitly() {
        val result = ArabicSubtitleEngine.parse("binary", SubtitleFormat.UNSUPPORTED)
        assertTrue(result is SubtitleParseResult.UnsupportedFormat)
    }
}
