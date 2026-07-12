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

fun HttpRequestBuilder.screenScraperParams(
    settings: ScraperSettings,
    devId: String,
    devPassword: String,
) {
    parameter("devid", devId)
    parameter("devpassword", devPassword)
    parameter("softname", "wajiha")
    parameter("output", "json")
    parameter("ssid", settings.screenScraperUser)
    parameter("sspassword", settings.screenScraperPassword)
}

fun HttpRequestBuilder.screenScraperParams(
    settings: ScraperSettings,
    devCreds: ScreenScraperDevCredentials,
) = screenScraperParams(settings, devCreds.devId, devCreds.devPassword)
