package com.cinematv.app.di

import android.content.Context
import com.cinematv.app.data.local.db.CinemaDatabase
import com.cinematv.app.data.local.prefs.SettingsRepository
import com.cinematv.app.data.local.security.SecureCredentialStore
import com.cinematv.app.data.remote.authorized.AuthorizedStreamProvider
import com.cinematv.app.data.remote.jellyfin.JellyfinProvider
import com.cinematv.app.data.remote.opensubtitles.OpenSubtitlesClient
import com.cinematv.app.data.remote.publicdomain.PublicDomainProvider
import com.cinematv.app.data.remote.tmdb.TmdbMetadataClient
import com.cinematv.app.domain.provider.ProviderRegistry
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val database: CinemaDatabase = CinemaDatabase.build(appContext)
    val settingsRepository: SettingsRepository = SettingsRepository(appContext)
    val credentialStore: SecureCredentialStore = SecureCredentialStore(appContext)

    val jellyfinProvider: JellyfinProvider = JellyfinProvider(
        httpClient = httpClient,
        settingsRepository = settingsRepository,
        credentialStore = credentialStore
    )
    val publicDomainProvider: PublicDomainProvider = PublicDomainProvider()
    val authorizedStreamProvider: AuthorizedStreamProvider = AuthorizedStreamProvider(
        dao = database.authorizedStreamDao()
    )

    val providerRegistry: ProviderRegistry = ProviderRegistry(
        providers = listOf(jellyfinProvider, publicDomainProvider, authorizedStreamProvider)
    )

    val openSubtitlesClient: OpenSubtitlesClient = OpenSubtitlesClient(httpClient, credentialStore)
    val tmdbMetadataClient: TmdbMetadataClient = TmdbMetadataClient(httpClient, credentialStore)
}
