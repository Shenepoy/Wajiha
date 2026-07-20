package com.wajiha.data.scraper

import com.wajiha.data.scraper.sources.ScreenScraperSource
import com.wajiha.di.ScreenScraperDevCredentials
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ScreenScraperApiTest {
    @Test
    fun lookup_sendsRequiredParametersAndParsesGameMedia() =
        runTest {
            val http =
                mockClient { request ->
                    assertEquals("/api2/jeuInfos.php", request.url.encodedPath)
                    assertEquals("dev-id", request.url.parameters["devid"])
                    assertEquals("dev-password", request.url.parameters["devpassword"])
                    assertEquals("wajiha", request.url.parameters["softname"])
                    assertEquals("user", request.url.parameters["ssid"])
                    assertEquals("user-password", request.url.parameters["sspassword"])
                    assertEquals("4", request.url.parameters["systemeid"])
                    assertEquals("ABCD1234", request.url.parameters["crc"])
                    assertEquals("Sonic The Hedgehog.zip", request.url.parameters["romnom"])
                    GAME_RESPONSE
                }
            val source = ScreenScraperSource(http, "dev-id", "dev-password")

            val result = source.lookupResult(query(), settings())

            val hit = assertIs<SourceLookupOutcome.Hit>(result)
            assertEquals("Sonic the Hedgehog", hit.candidate.name)
            assertEquals(MatchConfidence.Hash, hit.candidate.matchConfidence)
            assertEquals("SEGA", hit.candidate.metadata?.developer)
            assertEquals("https://cdn.example/box.png", hit.candidate.thumbnailUrl)
            assertEquals(
                MediaType.Boxart,
                hit.candidate.media
                    .single()
                    .type,
            )
        }

    @Test
    fun lookup_classifiesPlainTextCredentialError() =
        runTest {
            val source =
                ScreenScraperSource(
                    mockClient { "Erreur : Authentification développeur incorrecte" },
                    "dev-id",
                    "bad-password",
                )

            val result = source.lookupResult(query(), settings())

            val failed = assertIs<SourceLookupOutcome.Failed>(result)
            assertEquals(ScrapeFailureKind.Auth, failed.failure.kind)
            assertEquals("ScreenScraper developer credentials rejected", failed.failure.message)
        }

    @Test
    fun credentialProbe_rejectsUnparseableSuccessfulResponse() =
        runTest {
            val validator =
                ScraperCredentialValidator(
                    mockClient { "<html>maintenance</html>" },
                    ScreenScraperDevCredentials("dev-id", "dev-password"),
                )

            val result = validator.testScreenScraper(settings())

            val failure = assertIs<CredentialTestResult.Failure>(result)
            assertEquals("Unexpected response from ScreenScraper", failure.message)
        }

    @Test
    fun apiError_recognizesJsonAndPlainTextForms() {
        assertEquals(
            "ScreenScraper username or password rejected",
            screenScraperApiError("""{"response":{"error":"Erreur de login utilisateur"}}"""),
        )
        assertEquals(
            "ScreenScraper quota exceeded",
            screenScraperApiError("Erreur : Quota journalier dépassé"),
        )
        assertNull(screenScraperApiError(GAME_RESPONSE))
    }

    private fun settings() =
        ScraperSettings(
            screenScraperUser = "user",
            screenScraperPassword = "user-password",
        )

    private fun query() =
        ScrapeQuery(
            gameId = 1,
            displayName = "Sonic The Hedgehog",
            fileName = "Sonic The Hedgehog.zip",
            fileSize = 1_048_576,
            crc32 = "ABCD1234",
            md5 = null,
            platformId = "snes",
            platformName = "Super Nintendo",
            screenScraperId = 4,
            raConsoleId = null,
            libretroName = null,
        )

    private fun mockClient(response: (io.ktor.client.request.HttpRequestData) -> String): HttpClient =
        HttpClient(
            MockEngine { request ->
                respond(
                    content = response(request),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

    private companion object {
        val GAME_RESPONSE =
            """
            {
              "response": {
                "jeu": {
                  "id": "1",
                  "noms": [{"region": "us", "text": "Sonic the Hedgehog"}],
                  "developpeur": {"text": "SEGA"},
                  "medias": [{
                    "type": "box-2D",
                    "url": "https://cdn.example/box.png",
                    "region": "us",
                    "format": "png"
                  }]
                }
              }
            }
            """.trimIndent()
    }
}
