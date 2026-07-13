package com.wajiha.data.scraper

import com.wajiha.data.WajihaJson
import com.wajiha.log.logClockMs
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/** Typed HTTP GET + JSON decode result. */
sealed class HttpJsonResult<out T> {
    data class Ok<T>(
        val value: T,
        val status: Int,
    ) : HttpJsonResult<T>()

    data class Failed(
        val failure: ScrapeFailure,
    ) : HttpJsonResult<Nothing>()
}

fun logScraperHttp(
    urlString: String,
    status: Int?,
    ok: Boolean,
    error: String? = null,
    elapsedMs: Long? = null,
    bytes: Int? = null,
) {
    ScrapeApiLog.record(
        method = "GET",
        url = urlString,
        status = status,
        ok = ok,
        elapsedMs = elapsedMs,
        bytes = bytes,
        detail = error,
    )
}

suspend inline fun <reified T> HttpClient.getJsonResult(
    urlString: String,
    json: Json = WajihaJson.Default,
    crossinline block: HttpRequestBuilder.() -> Unit = {},
): HttpJsonResult<T> {
    val started = logClockMs()
    return try {
        val response: HttpResponse =
            get {
                url(urlString)
                block()
            }
        val loggedUrl = response.request.url.toString()
        val status = response.status.value
        val body = response.body<String>()
        val elapsed = logClockMs() - started
        if (!response.status.isSuccess()) {
            logScraperHttp(
                loggedUrl,
                status,
                ok = false,
                error = body.take(160),
                elapsedMs = elapsed,
                bytes = body.length,
            )
            HttpJsonResult.Failed(classifyHttpStatus(status, body.take(200)))
        } else {
            try {
                val decoded = json.decodeFromString<T>(body)
                logScraperHttp(
                    loggedUrl,
                    status,
                    ok = true,
                    elapsedMs = elapsed,
                    bytes = body.length,
                )
                HttpJsonResult.Ok(decoded, status)
            } catch (e: Exception) {
                logScraperHttp(
                    loggedUrl,
                    status,
                    ok = false,
                    error = e.message,
                    elapsedMs = elapsed,
                    bytes = body.length,
                )
                HttpJsonResult.Failed(
                    ScrapeFailure(ScrapeFailureKind.Parse, e.message ?: "Parse failed", status),
                )
            }
        }
    } catch (e: Exception) {
        logScraperHttp(
            urlString,
            status = null,
            ok = false,
            error = e.message,
            elapsedMs = logClockMs() - started,
        )
        HttpJsonResult.Failed(classifyThrowable(e))
    }
}

/** Raw string GET with status classification (for APIs that need custom parsing). */
suspend inline fun HttpClient.getStringResult(
    urlString: String,
    crossinline block: HttpRequestBuilder.() -> Unit = {},
): HttpJsonResult<String> {
    val started = logClockMs()
    return try {
        val response: HttpResponse =
            get {
                url(urlString)
                block()
            }
        val loggedUrl = response.request.url.toString()
        val status = response.status.value
        val body = response.body<String>()
        val elapsed = logClockMs() - started
        if (!response.status.isSuccess()) {
            logScraperHttp(
                loggedUrl,
                status,
                ok = false,
                error = body.take(160),
                elapsedMs = elapsed,
                bytes = body.length,
            )
            HttpJsonResult.Failed(classifyHttpStatus(status, body.take(200)))
        } else {
            logScraperHttp(
                loggedUrl,
                status,
                ok = true,
                elapsedMs = elapsed,
                bytes = body.length,
            )
            HttpJsonResult.Ok(body, status)
        }
    } catch (e: Exception) {
        logScraperHttp(
            urlString,
            status = null,
            ok = false,
            error = e.message,
            elapsedMs = logClockMs() - started,
        )
        HttpJsonResult.Failed(classifyThrowable(e))
    }
}
