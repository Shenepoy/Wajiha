package com.wajiha.platform

import android.content.Context
import android.net.Uri
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContentRomHasher(private val context: Context) : RomHasher {

    override suspend fun crc32(uri: String, maxBytes: Long): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val crc = CRC32()
                var total = 0L
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) return@withContext null
                        crc.update(buffer, 0, read)
                    }
                } ?: return@withContext null
                crc.value.toString(16).padStart(8, '0')
            }.getOrNull()
        }

    override suspend fun md5(uri: String, maxBytes: Long): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val digest = MessageDigest.getInstance("MD5")
                var total = 0L
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) return@withContext null
                        digest.update(buffer, 0, read)
                    }
                } ?: return@withContext null
                digest.digest().joinToString("") { b -> "%02x".format(b) }
            }.getOrNull()
        }

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
    }
}
