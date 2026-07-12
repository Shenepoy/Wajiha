package com.wajiha.di

import com.wajiha.data.config.ConfigInstaller
import com.wajiha.data.db.WajihaDatabase
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.domain.repository.CollectionRepository
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.domain.repository.SessionRepository
import com.wajiha.domain.scan.LibraryScanner
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Shared data bindings. The platform host must provide a [WajihaDatabase]
 * single (built via `databaseBuilder(context)` + `buildWajihaDatabase`).
 */
val dataModule: Module =
    module {
        single { get<WajihaDatabase>().platformDao() }
        single { get<WajihaDatabase>().emulatorDao() }
        single { get<WajihaDatabase>().gameDao() }
        single { get<WajihaDatabase>().romFolderDao() }
        single { get<WajihaDatabase>().gameMediaDao() }
        single { get<WajihaDatabase>().playSessionDao() }
        single { get<WajihaDatabase>().collectionDao() }

        single { PlatformRepository(get(), get()) }
        single { GameRepository(get(), get(), get()) }
        single { SessionRepository(get()) }
        single { CollectionRepository(get()) }
        single { ConfigInstaller(get()) }
        single { SettingsRepository(get()) }

        @OptIn(ExperimentalTime::class)
        single {
            LibraryScanner(
                gameRepository = get(),
                platformRepository = get(),
                romScanner = get(),
                romHasher = get(),
                settingsRepository = get(),
                now = { Clock.System.now().toEpochMilliseconds() },
            )
        }
    }
