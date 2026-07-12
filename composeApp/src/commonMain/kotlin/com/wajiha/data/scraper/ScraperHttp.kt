package com.wajiha.data.scraper

import com.wajiha.data.WajihaJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/** Typed HTTP GET + JSON decode result. */
sealed class HttpJsonResult<out T> {
    data class Ok<T>(val value: T, val status: Int) : HttpJsonResult<T>()
    data class Failed(val failure: ScrapeFailure) : HttpJsonResult<Nothing>()
}

suspend inline fun <reified T> HttpClient.getJsonResult(
    urlString: String,
    json: Json = WajihaJson.Default,
    crossinline block: HttpRequestBuilder.() -> Unit = {}
): HttpJsonResult<T> = try {
    val response: HttpResponse = get {
        url(urlString)
        block()
    }
    val status = response.status.value
    val body = response.body<String>()
    if (!response.status.isSuccess()) {
        HttpJsonResult.Failed(classifyHttpStatus(status, body.take(200)))
    } else {
        try {
            HttpJsonResult.Ok(json.decodeFromString<T>(body), status)
        } catch (e: Exception) {
            HttpJsonResult.Failed(
                ScrapeFailure(ScrapeFailureKind.Parse, e.message ?: "Parse failed", status)
            )
        }
    }
} catch (e: Exception) {
    HttpJsonResult.Failed(classifyThrowable(e))
}

/** Raw string GET with status classification (for APIs that need custom parsing). */
suspend inline fun HttpClient.getStringResult(
    urlString: String,
    crossinline block: HttpRequestBuilder.() -> Unit = {}
): HttpJsonResult<String> = try {
    val response: HttpResponse = get {
        url(urlString)
        block()
    }
    val status = response.status.value
    val body = response.body<String>()
    if (!response.status.isSuccess()) {
        HttpJsonResult.Failed(classifyHttpStatus(status, body.take(200)))
    } else {
        HttpJsonResult.Ok(body, status)
    }
} catch (e: Exception) {
    HttpJsonResult.Failed(classifyThrowable(e))
}
