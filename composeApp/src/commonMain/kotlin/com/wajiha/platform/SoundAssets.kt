package com.wajiha.platform

import org.jetbrains.compose.resources.ExperimentalResourceApi
import wajiha.composeapp.generated.resources.Res

/** Public accessor for bundled sound assets (Res is module-internal). */
object SoundAssets {
    @OptIn(ExperimentalResourceApi::class)
    suspend fun read(fileName: String): ByteArray = Res.readBytes("files/sounds/$fileName")
}
