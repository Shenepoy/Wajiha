package com.wajiha.android.detect

import android.content.Context
import android.os.Environment
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads emulator state files under [Environment.getExternalStorageDirectory].
 * Requires all-files access on Android 11+ for most [Android/data] paths; Thor may
 * allow reads without the grant — [canRead] probes a known path to verify.
 */
class EmulatorDataReader(private val context: Context) {

    @Volatile
    private var cachedCanRead: Boolean? = null

    @Volatile
    private var lastCanReadCheckMs = 0L

    private val mtimeCache = mutableMapOf<String, Pair<Long, String?>>()

    fun canRead(): Boolean {
        val now = System.currentTimeMillis()
        val cached = cachedCanRead
        if (cached != null && now - lastCanReadCheckMs < CAN_READ_CACHE_MS) {
            return cached
        }
        val result = probeReadAccess()
        cachedCanRead = result
        lastCanReadCheckMs = now
        WajihaLog.d(WajihaTags.EXTERNAL_RESOLVE, "canRead=$result (manager=${Environment.isExternalStorageManager()})")
        return result
    }

    fun invalidateCanReadCache() {
        cachedCanRead = null
    }

    suspend fun readText(path: String): String? = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext null
        runCatching {
            val file = File(path)
            if (!file.isFile) return@runCatching null
            file.readText()
        }.getOrNull()
    }

    suspend fun readTextIfModifiedSince(path: String, sinceMs: Long): String? =
        withContext(Dispatchers.IO) {
            if (!canRead()) return@withContext null
            runCatching {
                val file = File(path)
                if (!file.isFile) return@runCatching null
                val mtime = file.lastModified()
                val cached = mtimeCache[path]
                if (cached != null && cached.first == mtime && mtime <= sinceMs) {
                    return@runCatching cached.second
                }
                if (mtime <= sinceMs && cached?.first == mtime) {
                    return@runCatching cached.second
                }
                val text = file.readText()
                mtimeCache[path] = mtime to text
                text
            }.getOrNull()
        }

    fun exists(path: String): Boolean =
        canRead() && File(path).exists()

    fun dataRootForPackage(packageName: String): String =
        "${externalRoot()}/Android/data/$packageName/files"

    fun externalRoot(): String =
        Environment.getExternalStorageDirectory().absolutePath

    fun newestFileInDir(dirPath: String, extension: String): File? {
        if (!canRead()) return null
        val dir = File(dirPath)
        if (!dir.isDirectory) return null
        return dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(extension, ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun probeReadAccess(): Boolean {
        if (Environment.isExternalStorageManager()) return true
        val probes = listOf(
            "${externalRoot()}/Android/data/com.retroarch.aarch64/files/retroarch.cfg",
            "${externalRoot()}/Android/data/xyz.aethersx2.android/files/playtime.dat",
            "${externalRoot()}/RetroArch"
        )
        return probes.any { path ->
            runCatching {
                val file = File(path)
                when {
                    file.isFile -> file.canRead() && file.length() > 0
                    file.isDirectory -> file.canRead() && !file.list().isNullOrEmpty()
                    else -> false
                }
            }.getOrDefault(false)
        }
    }

    private companion object {
        const val CAN_READ_CACHE_MS = 30_000L
    }
}
