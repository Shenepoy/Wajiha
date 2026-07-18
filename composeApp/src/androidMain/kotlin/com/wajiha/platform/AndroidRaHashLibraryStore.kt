package com.wajiha.platform

import android.content.Context
import com.wajiha.data.WajihaJson
import com.wajiha.data.ra.RaHashLibraryDiskEntry
import com.wajiha.data.ra.RaHashLibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persists compact RA hash libraries under `files/ra/hashlib_{consoleId}.json`.
 */
class AndroidRaHashLibraryStore(
    context: Context,
) : RaHashLibraryStore {
    private val root = File(context.filesDir, "ra")

    override suspend fun load(consoleId: Int): RaHashLibraryDiskEntry? =
        withContext(Dispatchers.IO) {
            val file = fileFor(consoleId)
            if (!file.isFile) return@withContext null
            runCatching {
                WajihaJson.Lenient.decodeFromString<RaHashLibraryDiskEntry>(file.readText())
            }.getOrNull()
        }

    override suspend fun save(
        consoleId: Int,
        entry: RaHashLibraryDiskEntry,
    ) {
        withContext(Dispatchers.IO) {
            root.mkdirs()
            val file = fileFor(consoleId)
            val tmp = File(root, "hashlib_$consoleId.json.tmp")
            tmp.writeText(WajihaJson.Lenient.encodeToString(RaHashLibraryDiskEntry.serializer(), entry))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    private fun fileFor(consoleId: Int): File = File(root, "hashlib_$consoleId.json")
}
