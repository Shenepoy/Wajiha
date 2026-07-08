package com.wajiha.platform

import android.content.Context
import com.wajiha.data.scraper.MediaStorage
import com.wajiha.data.scraper.MediaType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores downloaded media under `files/media/<gameId>/<type>.<ext>`. */
class AndroidMediaStorage(context: Context) : MediaStorage {

    private val root = File(context.filesDir, "media")

    override suspend fun save(
        gameId: Long,
        type: MediaType,
        extension: String,
        bytes: ByteArray
    ): String = withContext(Dispatchers.IO) {
        val dir = File(root, gameId.toString()).apply { mkdirs() }
        val ext = extension.trimStart('.').ifBlank { "png" }
        val file = File(dir, "${type.dbName}.$ext")
        // Remove stale files of the same type with a different extension
        dir.listFiles()?.filter {
            it.name.startsWith("${type.dbName}.") && it.name != file.name
        }?.forEach { it.delete() }
        file.writeBytes(bytes)
        file.absolutePath
    }

    override suspend fun delete(path: String) {
        withContext(Dispatchers.IO) { File(path).delete() }
    }
}
