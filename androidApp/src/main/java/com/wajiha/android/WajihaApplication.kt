package com.wajiha.android

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.wajiha.android.display.DisplayCoordinator
import com.wajiha.android.launch.GameLauncher
import com.wajiha.android.launch.PlaySessionTracker
import com.wajiha.android.library.RomFolderManager
import com.wajiha.android.monitor.ForegroundAppMonitor
import com.wajiha.android.platform.AndroidAppActions
import com.wajiha.android.platform.AndroidLibraryActions
import com.wajiha.android.system.SystemController
import com.wajiha.data.config.ConfigInstaller
import com.wajiha.data.db.WajihaDatabase
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.data.db.buildWajihaDatabase
import com.wajiha.data.db.databaseBuilder
import com.wajiha.data.scraper.ImageProcessor
import com.wajiha.data.scraper.LocalMediaFiles
import com.wajiha.data.scraper.MediaStorage
import com.wajiha.di.ScreenScraperDevCredentials
import com.wajiha.di.initKoin
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.platform.AndroidImageProcessor
import com.wajiha.platform.AndroidMediaStorage
import com.wajiha.platform.AppActions
import com.wajiha.platform.ContentRomHasher
import com.wajiha.platform.EsDeLocalMediaFiles
import com.wajiha.platform.LibraryActions
import com.wajiha.platform.RomHasher
import com.wajiha.platform.RomScanner
import com.wajiha.platform.SafRomScanner
import com.wajiha.platform.SystemControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

class WajihaApplication : Application(), SingletonImageLoader.Factory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Tuned Coil pipeline: bounded memory cache + persistent disk cache. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.15).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        val androidModule = module {
            single<WajihaDatabase> {
                buildWajihaDatabase(databaseBuilder(this@WajihaApplication))
            }
            single<DataStore<Preferences>> {
                PreferenceDataStoreFactory.create {
                    this@WajihaApplication.preferencesDataStoreFile("wajiha_settings")
                }
            }
            single<RomScanner> { SafRomScanner(this@WajihaApplication) }
            single<RomHasher> { ContentRomHasher(this@WajihaApplication) }
            single { RomFolderManager(this@WajihaApplication, get()) }
            single { PlaySessionTracker(get(), get()) }
            single { GameLauncher(this@WajihaApplication, get(), get(), get(), get()) }
            single { DisplayCoordinator(this@WajihaApplication, get()) }
            single(named("applicationScope")) { appScope }
            single {
                com.wajiha.android.input.GamepadKeyRouter(
                    store = get(),
                    settingsRepository = get(),
                    appActions = get(),
                    scope = get(named("applicationScope"))
                )
            }
            single { ForegroundAppMonitor(this@WajihaApplication, get(), get(), get()) }
            single { AndroidAppActions(this@WajihaApplication, get(), get(), get()) } binds
                arrayOf(AppActions::class)
            single { AndroidLibraryActions(this@WajihaApplication, get()) } binds
                arrayOf(LibraryActions::class)
            single<MediaStorage> { AndroidMediaStorage(this@WajihaApplication) }
            single<LocalMediaFiles> { EsDeLocalMediaFiles() }
            single<ImageProcessor> { AndroidImageProcessor() }
            single { SystemController(this@WajihaApplication) } binds
                arrayOf(SystemControls::class)
            single {
                ScreenScraperDevCredentials(
                    devId = BuildConfig.SCREENSCRAPER_DEV_ID,
                    devPassword = BuildConfig.SCREENSCRAPER_DEV_PASSWORD
                )
            }
        }
        initKoin(platformModules = listOf(androidModule)) {
            androidLogger()
            androidContext(this@WajihaApplication)
        }

        seedDefaultsIfNeeded()
        mirrorSettings()
    }

    /** Keeps host-side flags (sounds, detection, night mode) in sync with persisted settings. */
    private fun mirrorSettings() {
        appScope.launch {
            val koin = GlobalContext.get()
            val appActions = koin.get<AndroidAppActions>()
            val monitor = koin.get<ForegroundAppMonitor>()
            koin.get<SettingsRepository>().settings.collect { settings ->
                appActions.soundsEnabled = settings.soundsEnabled
                monitor.detectionEnabled = settings.detectManualLaunches
                // Force the same night mode on both Thor displays so Compose
                // "system" and any residual platform chrome stay in lockstep.
                val nightMode = when (settings.theme) {
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
                    AppCompatDelegate.setDefaultNightMode(nightMode)
                }
            }
        }
    }

    /** First-run: install the bundled platform/emulator starter set. */
    private fun seedDefaultsIfNeeded() {
        appScope.launch {
            val koin = GlobalContext.get()
            val platformRepository = koin.get<PlatformRepository>()
            if (platformRepository.all().isEmpty()) {
                runCatching { koin.get<ConfigInstaller>().installBundledDefaults() }
            }
        }
    }
}
