package com.wajiha.data.ra

import com.wajiha.data.WajihaJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object RaMediaUrls {
    const val BASE = "https://media.retroachievements.org"

    fun media(path: String): String = "$BASE$path"

    fun badge(badgeName: String): String = "$BASE/Badge/$badgeName.png"

    fun badgeLocked(badgeName: String): String = "$BASE/Badge/${badgeName}_lock.png"
}

@Serializable
internal data class RaHashEnvelope(
    @SerialName("ID") val id: Long? = null,
)

internal fun parseRaGameIdFromHashResponse(
    body: String,
    json: Json = WajihaJson.Lenient,
): Long? = runCatching { json.decodeFromString<RaHashEnvelope>(body).id?.takeIf { it > 0 } }.getOrNull()
