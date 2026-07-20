package com.wajiha.android.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.security.MessageDigest

/** Disk cache for resolved app icons (`cacheDir/app_icons`). Key includes shape + calendar day. */
class IconBitmapCache(
    context: Context,
) {
    private val dir = File(context.cacheDir, "app_icons").also { it.mkdirs() }

    fun get(
        packPackage: String,
        packageName: String,
        activityName: String,
        dpi: Int,
        packVersion: Long,
        shapeId: String,
        calendarDay: Int,
    ): ImageBitmap? {
        val file = fileFor(packPackage, packageName, activityName, dpi, packVersion, shapeId, calendarDay)
        if (!file.isFile) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)?.withoutDensity()?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun put(
        packPackage: String,
        packageName: String,
        activityName: String,
        dpi: Int,
        packVersion: Long,
        shapeId: String,
        calendarDay: Int,
        bitmap: ImageBitmap,
    ) {
        val file = fileFor(packPackage, packageName, activityName, dpi, packVersion, shapeId, calendarDay)
        try {
            file.outputStream().use { out ->
                bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (_: Exception) {
            file.delete()
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(
        packPackage: String,
        packageName: String,
        activityName: String,
        dpi: Int,
        packVersion: Long,
        shapeId: String,
        calendarDay: Int,
    ): File {
        // v8: scale opaque content to fill before shape mask (adaptive + legacy).
        val key = "v8|$packPackage|$packageName|$activityName|$dpi|$packVersion|$shapeId|$calendarDay"
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(key.toByteArray())
                .joinToString("") { b -> "%02x".format(b) }
        return File(dir, "$digest.png")
    }

    private fun Bitmap.withoutDensity(): Bitmap = apply { density = Bitmap.DENSITY_NONE }
}
