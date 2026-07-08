package com.wajiha.data.scraper

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** Persists the whole [ScraperSettings] blob as JSON in DataStore. */
class ScraperSettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settings: Flow<ScraperSettings> = dataStore.data.map { prefs ->
        prefs[KEY]?.let {
            try {
                json.decodeFromString<ScraperSettings>(it)
            } catch (_: Exception) {
                ScraperSettings()
            }
        } ?: ScraperSettings()
    }

    suspend fun current(): ScraperSettings = settings.first()

    suspend fun update(transform: (ScraperSettings) -> ScraperSettings) {
        dataStore.edit { prefs ->
            val old = prefs[KEY]?.let {
                try {
                    json.decodeFromString<ScraperSettings>(it)
                } catch (_: Exception) {
                    ScraperSettings()
                }
            } ?: ScraperSettings()
            prefs[KEY] = json.encodeToString(ScraperSettings.serializer(), transform(old))
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("scraper_settings")
    }
}
