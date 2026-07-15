package com.wajiha.data.scraper

import com.wajiha.di.ScreenScraperDevCredentials
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.util.encodeBase64

fun HttpRequestBuilder.rommAuth(settings: ScraperSettings) {
    if (settings.rommUsername.isNotBlank()) {
        val token = "${settings.rommUsername}:${settings.rommPassword}".encodeBase64()
        header("Authorization", "Basic $token")
    }
}

fun HttpRequestBuilder.steamGridDbAuth(settings: ScraperSettings) {
    header("Authorization", "Bearer ${settings.steamGridDbApiKey}")
}

/**
 * Effective ScreenScraper developer pair: Settings first, then build-time.
 * Returns null when neither side has both values — callers must omit params.
 */
fun resolveScreenScraperDevCredentials(
    settings: ScraperSettings,
    buildDevId: String = "",
    buildDevPassword: String = "",
): Pair<String, String>? {
    val fromSettingsId = settings.screenScraperDevId.trim()
    val fromSettingsPass = settings.screenScraperDevPassword
    if (fromSettingsId.isNotBlank() && fromSettingsPass.isNotBlank()) {
        return fromSettingsId to fromSettingsPass
    }
    val fromBuildId = buildDevId.trim()
    if (fromBuildId.isNotBlank() && buildDevPassword.isNotBlank()) {
        return fromBuildId to buildDevPassword
    }
    return null
}

fun resolveScreenScraperDevCredentials(
    settings: ScraperSettings,
    buildCreds: ScreenScraperDevCredentials,
): Pair<String, String>? = resolveScreenScraperDevCredentials(settings, buildCreds.devId, buildCreds.devPassword)

fun HttpRequestBuilder.screenScraperParams(
    settings: ScraperSettings,
    buildDevId: String = "",
    buildDevPassword: String = "",
) {
    // Never send empty devid=/devpassword= — SS rejects oddly. Omit when not provided.
    val dev = resolveScreenScraperDevCredentials(settings, buildDevId, buildDevPassword)
    if (dev != null) {
        parameter("devid", dev.first)
        parameter("devpassword", dev.second)
    }
    parameter("softname", "wajiha")
    parameter("output", "json")
    // User account — only when provided (Settings login).
    if (settings.screenScraperUser.isNotBlank()) {
        parameter("ssid", settings.screenScraperUser)
    }
    if (settings.screenScraperPassword.isNotBlank()) {
        parameter("sspassword", settings.screenScraperPassword)
    }
}

fun HttpRequestBuilder.screenScraperParams(
    settings: ScraperSettings,
    buildCreds: ScreenScraperDevCredentials,
) = screenScraperParams(settings, buildCreds.devId, buildCreds.devPassword)
