package com.wajiha.data

import kotlinx.serialization.json.Json

object WajihaJson {
    val Default = Json { ignoreUnknownKeys = true }

    val Lenient = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val Settings = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
