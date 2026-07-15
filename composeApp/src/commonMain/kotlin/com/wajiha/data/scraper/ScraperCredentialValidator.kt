package com.wajiha.data.scraper

import com.wajiha.data.WajihaJson
import com.wajiha.di.ScreenScraperDevCredentials
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

sealed class CredentialTestResult {
    data class Success(
        val message: String,
    ) : CredentialTestResult()

    data class Failure(
        val message: String,
    ) : CredentialTestResult()
}

/** Validates scraper source credentials via lightweight API probes. */
class ScraperCredentialValidator(
    private val http: HttpClient,
    private val devCreds: ScreenScraperDevCredentials,
) {
    suspend fun testScreenScraper(settings: ScraperSettings): CredentialTestResult {
        if (settings.screenScraperUser.isBlank() || settings.screenScraperPassword.isBlank()) {
            return CredentialTestResult.Failure("Username and password required")
        }
        if (resolveScreenScraperDevCredentials(settings, devCreds) == null) {
            return CredentialTestResult.Failure(
                "Developer ID required by ScreenScraper API — add Dev ID/password " +
                    "(or gradle wajiha.screenscraper.devid). User login alone is not enough.",
            )
        }
        return try {
            val body =
                http
                    .get("https://api.screenscraper.fr/api2/ssuserInfos.php") {
                        screenScraperParams(settings, devCreds)
                    }.body<String>()
            if (body.contains("\"error\"", ignoreCase = true)) {
                val lower = body.lowercase()
                val message =
                    when {
                        "développeur" in lower ||
                            "developpeur" in lower ||
                            "developer" in lower -> {
                            "Developer credentials rejected — check Dev ID/password"
                        }

                        "login" in lower || "password" in lower || "user" in lower -> {
                            "Invalid username/password"
                        }

                        else -> {
                            "Invalid credentials or API error"
                        }
                    }
                CredentialTestResult.Failure(message)
            } else {
                val pseudo =
                    runCatching {
                        WajihaJson.Lenient
                            .decodeFromString<SsUserEnvelope>(body)
                            .response
                            ?.ssuser
                            ?.pseudo
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
            val body =
                http
                    .get("https://www.steamgriddb.com/api/v2/user") {
                        steamGridDbAuth(settings)
                    }.body<String>()
            val username =
                runCatching {
                    WajihaJson.Lenient
                        .decodeFromString<SgdbUserEnvelope>(body)
                        .data
                        ?.username
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
            http
                .get("$base/api/roms") {
                    rommAuth(settings)
                    parameter("limit", 1)
                }.body<String>()
            CredentialTestResult.Success("Connected to RomM server")
        } catch (e: Exception) {
            CredentialTestResult.Failure(e.message ?: "Connection failed")
        }
    }

    @Serializable
    private data class SsUserEnvelope(
        val response: SsUserResponse? = null,
    )

    @Serializable
    private data class SsUserResponse(
        val ssuser: SsUser? = null,
    )

    @Serializable
    private data class SsUser(
        val pseudo: String? = null,
    )

    @Serializable
    private data class SgdbUserEnvelope(
        val data: SgdbUser? = null,
    )

    @Serializable
    private data class SgdbUser(
        val username: String? = null,
    )
}
