package com.wajiha.platform

import android.content.Context
import com.wajiha.data.scraper.MediaStorage
import com.wajiha.data.scraper.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stores downloaded media under `files/media/<gameId>/<type>_<stamp>.<ext>`.
 *
 * Filenames are unique per save so Coil (and any path-keyed cache) picks up
 * replacements instead of keeping a stale bitmap for `boxart.png`.
 */
class AndroidMediaStorage(
    context: Context,
) : MediaStorage {
    private val root = File(context.filesDir, "media")

    override suspend fun save(
        gameId: Long,
        type: MediaType,
        extension: String,
        bytes: ByteArray,
    ): String =
        withContext(Dispatchers.IO) {
            val dir = File(root, gameId.toString()).apply { mkdirs() }
            val ext = extension.trimStart('.').ifBlank { "png" }
            deleteFilesForType(dir, type)
            val file = File(dir, "${type.dbName}_${System.currentTimeMillis()}.$ext")
            file.writeBytes(bytes)
            file.absolutePath
        }

    override suspend fun delete(path: String) {
        withContext(Dispatchers.IO) { File(path).delete() }
    }

    private fun deleteFilesForType(
        dir: File,
        type: MediaType,
    ) {
        val legacyPrefix = "${type.dbName}."
        val stampedPrefix = "${type.dbName}_"
        dir
            .listFiles()
            ?.filter { f ->
                f.name.startsWith(legacyPrefix) || f.name.startsWith(stampedPrefix)
            }?.forEach { it.delete() }
    }
}
