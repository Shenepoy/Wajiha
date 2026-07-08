package com.wajiha.data.scraper

import java.io.File

internal actual fun readLocalFile(path: String): ByteArray? = try {
    val file = File(path)
    if (file.isFile) file.readBytes() else null
} catch (_: Exception) {
    null
}
