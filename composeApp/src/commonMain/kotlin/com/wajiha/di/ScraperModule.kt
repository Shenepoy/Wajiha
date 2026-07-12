package com.wajiha.di

import com.wajiha.data.ra.RaClient
import com.wajiha.data.ra.RaRepository
import com.wajiha.data.scraper.BatchProgressStore
import com.wajiha.data.scraper.BatchScraper
import com.wajiha.data.scraper.ImageProcessor
import com.wajiha.data.scraper.NoopImageProcessor
import com.wajiha.data.scraper.ScrapeEngine
import com.wajiha.data.scraper.ScraperCredentialValidator
import com.wajiha.data.scraper.ScraperSettingsRepository
import com.wajiha.data.scraper.ScraperSource
import com.wajiha.data.scraper.sources.LibretroThumbnailsSource
import com.wajiha.data.scraper.sources.LocalMediaSource
import com.wajiha.data.scraper.sources.RetroAchievementsSource
import com.wajiha.data.scraper.sources.RommSource
import com.wajiha.data.scraper.sources.ScreenScraperSource
import com.wajiha.data.scraper.sources.SteamGridDbSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.koin.core.module.Module
import org.koin.dsl.module

/** Optional developer credentials for ScreenScraper (from build config). */
data class ScreenScraperDevCredentials(
    val devId: String = "",
    val devPassword: String = "",
)

/**
 * Scraper bindings. The platform host must additionally provide:
 * [com.wajiha.data.scraper.MediaStorage], [com.wajiha.data.scraper.LocalMediaFiles],
 * and optionally [ScreenScraperDevCredentials].
 */
val scraperModule: Module =
    module {
        single {
            HttpClient {
                expectSuccess = false
                install(HttpTimeout) {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 15_000
                }
            }
        }

        single { ScraperSettingsRepository(get()) }

        single {
            val creds = getOrNull<ScreenScraperDevCredentials>() ?: ScreenScraperDevCredentials()
            ScraperCredentialValidator(get(), creds)
        }

        single {
            val creds = getOrNull<ScreenScraperDevCredentials>() ?: ScreenScraperDevCredentials()
            listOf<ScraperSource>(
                ScreenScraperSource(get(), creds.devId, creds.devPassword),
                SteamGridDbSource(get()),
                LibretroThumbnailsSource(get()),
                RetroAchievementsSource(get()),
                RommSource(get()),
                LocalMediaSource(get()),
            )
        }

        single {
            ScrapeEngine(
                sources = get(),
                gameRepository = get(),
                platformRepository = get(),
                mediaStorage = get(),
                http = get(),
                imageProcessor = getOrNull<ImageProcessor>() ?: NoopImageProcessor,
            )
        }

        single { BatchProgressStore(get()) }
        single { BatchScraper(get(), get(), get(), get()) }

        single { RaClient(get()) }
        single { RaRepository(get(), get(), get()) }
    }
