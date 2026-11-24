package com.cinematv.app.provider

import com.cinematv.app.data.local.security.SecureCredentialStore
import com.cinematv.app.data.remote.jellyfin.JellyfinUrlValidator
import com.cinematv.app.data.remote.publicdomain.PublicDomainProvider
import com.cinematv.app.domain.provider.ProviderRegistry
import com.cinematv.app.playback.CinemaPlayerManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAndValidationTest {

    @Test
    fun validatesAndNormalizesJellyfinServerUrls() {
        val ok = JellyfinUrlValidator.normalizeAndValidate("https://media.example.com/ ")
        assertTrue(ok.isSuccess)
        assertEquals("https://media.example.com", ok.getOrNull())

        assertTrue(JellyfinUrlValidator.normalizeAndValidate("").isFailure)
        assertTrue(JellyfinUrlValidator.normalizeAndValidate("ftp://invalid.local").isFailure)
        assertTrue(JellyfinUrlValidator.normalizeAndValidate("not a url").isFailure)
    }

    @Test
    fun marksWatchedOnlyAtConfiguredCompletionThreshold() {
        assertFalse(CinemaPlayerManager.isWatched(positionMs = 89_000L, durationMs = 100_000L, thresholdPercent = 90))
        assertTrue(CinemaPlayerManager.isWatched(positionMs = 90_000L, durationMs = 100_000L, thresholdPercent = 90))
        assertFalse(CinemaPlayerManager.isWatched(positionMs = 10_000L, durationMs = 0L, thresholdPercent = 90))
    }

    @Test
    fun redactsSensitiveTokensBeforeLogging() {
        val raw = """X-Emby-Authorization: Token="super_secret_token", Api-Key=abc12345"""
        val sanitized = SecureCredentialStore.redactForLogging(raw)
        assertFalse(sanitized.contains("super_secret_token"))
        assertFalse(sanitized.contains("abc12345"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun searchesPublicDomainCatalogInBothArabicAndEnglish() = runTest {
        val registry = ProviderRegistry(listOf(PublicDomainProvider()))
        val arabicResult = registry.searchAll("سينتل")
        assertTrue(arabicResult.items.any { it.title == "Sintel" })

        val englishResult = registry.searchAll("Tears of Steel")
        assertTrue(englishResult.items.any { it.id == "pd_tears_of_steel" })

        val emptyResult = registry.searchAll("   ")
        assertTrue(emptyResult.items.isEmpty())
    }
}
