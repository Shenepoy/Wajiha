package com.wajiha.platform

import com.wajiha.data.scraper.LocalMediaFiles
import com.wajiha.data.scraper.MediaType
import java.io.File

/**
 * ES-DE style local media layout:
 * `<root>/<platform shortname>/<media dir>/<rom base name>.<ext>`
 */
class EsDeLocalMediaFiles : LocalMediaFiles {
    private val dirNames =
        mapOf(
            MediaType.Boxart to listOf("covers", "boxart", "box2dfront"),
            MediaType.Logo to listOf("marquees", "wheels", "logos"),
            MediaType.Hero to listOf("miximages", "heroes"),
            MediaType.Screenshot to listOf("screenshots", "snaps"),
            MediaType.Fanart to listOf("fanart", "backgrounds"),
            MediaType.Video to listOf("videos"),
            MediaType.Icon to listOf("icons"),
            MediaType.Banner to listOf("banners", "titlescreens"),
            MediaType.Music to listOf("music", "musictracks", "soundtrack"),
        )

    private val imageExts = listOf("png", "jpg", "jpeg", "webp")
    private val videoExts = listOf("mp4", "webm", "mkv")
    private val musicExts = listOf("mp3", "ogg", "flac", "wav", "m4a")

    override fun find(
        mediaRoot: String,
        platformShortName: String,
        romBaseName: String,
        type: MediaType,
    ): String? {
        val platformDir = File(mediaRoot, platformShortName)
        if (!platformDir.isDirectory) return null
        val extensions =
            when (type) {
                MediaType.Video -> videoExts
                MediaType.Music -> musicExts
                else -> imageExts
            }
        for (dirName in dirNames[type].orEmpty()) {
            val dir = File(platformDir, dirName)
            if (!dir.isDirectory) continue
            for (ext in extensions) {
                val file = File(dir, "$romBaseName.$ext")
                if (file.isFile) return file.absolutePath
            }
        }
        return null
    }
}
