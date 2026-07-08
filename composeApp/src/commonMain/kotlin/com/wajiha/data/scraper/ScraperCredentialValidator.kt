package com.wajiha.data.scraper

import com.wajiha.di.ScreenScraperDevCredentials
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.util.encodeBase64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed class CredentialTestResult {
    data class Success(val message: String) : CredentialTestResult()
    data class Failure(val message: String) : CredentialTestResult()
}

/** Validates scraper source credentials via lightweight API probes. */
class ScraperCredentialValidator(
    private val http: HttpClient,
    private val devCreds: ScreenScraperDevCredentials
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun testScreenScraper(settings: ScraperSettings): CredentialTestResult {
        if (settings.screenScraperUser.isBlank() || settings.screenScraperPassword.isBlank()) {
            return CredentialTestResult.Failure("Username and password required")
        }
        return try {
            val body = http.get("https://api.screenscraper.fr/api2/ssuserInfos.php") {
                parameter("devid", devCreds.devId)
                parameter("devpassword", devCreds.devPassword)
                parameter("softname", "wajiha")
                parameter("output", "json")
                parameter("ssid", settings.screenScraperUser)
                parameter("sspassword", settings.screenScraperPassword)
            }.body<String>()
            if (body.contains("\"error\"", ignoreCase = true)) {
                CredentialTestResult.Failure("Invalid credentials or API error")
            } else {
                val pseudo = runCatching {
                    json.decodeFromString<SsUserEnvelope>(body).response?.ssuser?.pseudo
                }.getOrNull()
                val label = pseudo?.takeIf { it.isNotBlank() } ?: settings.screenScraperUser
                CredentialTestResult.Success("Logged in as $label")
            }
        } catch (e: Exception) {
            CredentialTestResult.Failure(e.message ?: "Connection failed")
        }
    }

    suspend fun testSteamGridDb(settings: ScraperSettings): CredentialTestResult {
        if (settings.steamGridDbApiKey.isBlank()) {
            return CredentialTestResult.Failure("API key required")
        }
        return try {
            val body = http.get("https://www.steamgriddb.com/api/v2/user") {
                header("Authorization", "Bearer ${settings.steamGridDbApiKey}")
            }.body<String>()
            val username = runCatching {
                json.decodeFromString<SgdbUserEnvelope>(body).data?.username
            }.getOrNull()
            if (username.isNullOrBlank()) {
                CredentialTestResult.Failure("Invalid API key")
            } else {
                CredentialTestResult.Success("Logged in as $username")
            }
        } catch (e: Exception) {
            CredentialTestResult.Failure(e.message ?: "Connection failed")
        }
    }

    suspend fun testRomm(settings: ScraperSettings): CredentialTestResult {
        val base = settings.rommUrl.trimEnd('/')
        if (base.isBlank()) {
            return CredentialTestResult.Failure("Server URL required")
        }
        return try {
            http.get("$base/api/roms") {
                if (settings.rommUsername.isNotBlank()) {
                    val token = "${settings.rommUsername}:${settings.rommPassword}".encodeBase64()
                    header("Authorization", "Basic $token")
                }
                parameter("limit", 1)
            }.body<String>()
            CredentialTestResult.Success("Connected to RomM server")
        } catch (e: Exception) {
            CredentialTestResult.Failure(e.message ?: "Connection failed")
        }
    }

    @Serializable
    private data class SsUserEnvelope(val response: SsUserResponse? = null)

    @Serializable
    private data class SsUserResponse(val ssuser: SsUser? = null)

    @Serializable
    private data class SsUser(val pseudo: String? = null)

    @Serializable
    private data class SgdbUserEnvelope(val data: SgdbUser? = null)

    @Serializable
    private data class SgdbUser(val username: String? = null)
}
