package com.wajiha.di

import com.wajiha.state.DualScreenStore
import com.wajiha.ui.gamedetail.GameDetailViewModel
import com.wajiha.ui.home.HomeViewModel
import com.wajiha.ui.ra.RaViewModel
import com.wajiha.ui.scraper.ScraperViewModel
import com.wajiha.ui.settings.PlatformSettingsViewModel
import com.wajiha.ui.settings.SettingsViewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Shared Koin bootstrap. Platform hosts call this with their own modules
 * (Android context bindings, actual implementations, etc.).
 */
fun initKoin(platformModules: List<Module> = emptyList(), config: KoinApplication.() -> Unit = {}) {
    startKoin {
        config()
        modules(sharedModules() + platformModules)
    }
}

val stateModule: Module = module {
    single { DualScreenStore() }
}

val uiModule: Module = module {
    single { HomeViewModel(get(), get(), get(), get()) }
    single { SettingsViewModel(get(), get(), get(), get(), get()) }
    single { PlatformSettingsViewModel(get(), get(), get()) }
    single { ScraperViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { GameDetailViewModel(get(), get(), get(), get(), get(), get()) }
    single { RaViewModel(get(), get()) }
}

fun sharedModules(): List<Module> = listOf(dataModule, scraperModule, stateModule, uiModule)
