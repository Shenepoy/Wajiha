package com.wajiha.di

import com.wajiha.data.ra.NoopRaHashLibraryStore
import com.wajiha.data.ra.RaClient
import com.wajiha.data.ra.RaHashLibraryStore
import com.wajiha.data.ra.RaRepository
import com.wajiha.data.scraper.BatchProgressStore
import com.wajiha.data.scraper.BatchScraper
import com.wajiha.data.scraper.ImageProcessor
import com.wajiha.data.scraper.NoopImageProcessor
import com.wajiha.data.scraper.ScrapeEngine
import com.wajiha.data.scraper.ScrapeMatchTool
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
                    // ScreenScraper jeuInfos can stall >10s on name lookups without CRC;
                    // Ktor's default socket idle timeout is often 10s and aborts early.
                    requestTimeoutMillis = 90_000
                    connectTimeoutMillis = 20_000
                    socketTimeoutMillis = 90_000
                }
            }
        }

        single { ScraperSettingsRepository(get()) }

        single {
            val creds = getOrNull<ScreenScraperDevCredentials>() ?: ScreenScraperDevCredentials()
            ScraperCredentialValidator(get(), creds)
        }

        single {
            RaClient(
                http = get(),
                hashLibraryStore = getOrNull<RaHashLibraryStore>() ?: NoopRaHashLibraryStore,
            )
        }
        single { RaRepository(get(), get(), get(), get()) }

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

        single { ScrapeMatchTool(get()) }

        single {
            ScrapeEngine(
                sources = get(),
                gameRepository = get(),
                platformRepository = get(),
                mediaStorage = get(),
                http = get(),
                imageProcessor = getOrNull<ImageProcessor>() ?: NoopImageProcessor,
                matchTool = get(),
            )
        }

        single { BatchProgressStore(get()) }
        single { BatchScraper(get(), get(), get(), get()) }
    }
