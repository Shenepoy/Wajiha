package com.wajiha.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.wajiha.state.PlatformLogoColorStyle
import org.jetbrains.compose.resources.ExperimentalResourceApi
import wajiha.composeapp.generated.resources.Res

/**
 * Bundled Dan Patrick clear logos keyed by platform id / shortName.
 * Files live under `files/platform_logos/{variant}/{id}.png`.
 */
object PlatformConsoleIcons {
    private val aliases: Map<String, String> =
        mapOf(
            "ps1" to "psx",
            "ps" to "psx",
            "playstation" to "psx",
            "playstation2" to "ps2",
            "playstation3" to "ps3",
            "psvita" to "vita",
            "pspmini" to "pspminis",
            "megadrive" to "genesis",
            "md" to "genesis",
            "gen" to "genesis",
            "msu-md" to "genesismsu",
            "mastersystem" to "master",
            "sms" to "master",
            "sg-1000" to "sg1000",
            "gameandwatch" to "gw",
            "pcengine" to "tg16",
            "pce" to "tg16",
            "tgfx" to "tg16",
            "tg-cd" to "tgcd",
            "pcecd" to "tgcd",
            "sgfx" to "supergrafx",
            "wonderswan" to "ws",
            "wonderswancolor" to "wsc",
            "atarijaguar" to "jaguar",
            "atarilynx" to "lynx",
            "cdimono1" to "cdi",
            "colecovision" to "coleco",
            "n3ds" to "3ds",
            "nintendo3ds" to "3ds",
            "nintendoswitch" to "switch",
            "nsw" to "switch",
            "gamecube" to "gc",
            "ngc" to "gc",
            "bsx" to "satellaview",
            "ss" to "saturn",
            "dc" to "dreamcast",
            "gg" to "gamegear",
            "neogeoaes" to "neogeo",
            "ng" to "neogeo",
            "32x" to "sega32x",
            "fbn" to "fbneo",
        )

    /** Ids that have at least one bundled logo file (light_color set). */
    private val knownIds: Set<String> =
        setOf(
            "3do",
            "3ds",
            "amiga",
            "amiga1200",
            "amiga600",
            "amigacd32",
            "amstradcpc",
            "android",
            "apple2",
            "appleii",
            "arcade",
            "arcadia",
            "astrocde",
            "atari2600",
            "atari5200",
            "atari7800",
            "atarist",
            "atomiswave",
            "bbcmicro",
            "c64",
            "cdi",
            "cdtv",
            "channelf",
            "coleco",
            "cpc",
            "cps",
            "cps1",
            "cps2",
            "cps3",
            "daphne",
            "dos",
            "dreamcast",
            "famicom",
            "fba",
            "fbneo",
            "fds",
            "fmtowns",
            "g7400",
            "gamate",
            "gamecom",
            "gamegear",
            "gb",
            "gba",
            "gbc",
            "gc",
            "genesis",
            "genesismsu",
            "gmaster",
            "gw",
            "gx4000",
            "intellivision",
            "ios",
            "jaguar",
            "jaguarcd",
            "lynx",
            "macintosh",
            "mame",
            "mark3",
            "master",
            "megacd",
            "megaduck",
            "model2",
            "model3",
            "msx",
            "msx1",
            "msx2",
            "msxturbor",
            "n64",
            "n64dd",
            "naomi",
            "naomi2",
            "nds",
            "ndsi",
            "neogeo",
            "neogeocd",
            "nes",
            "ngage",
            "ngp",
            "ngpc",
            "odyssey2",
            "openbor",
            "pc",
            "pcenginecd",
            "pcfx",
            "pet",
            "pico8",
            "plus4",
            "pocketstation",
            "pokemini",
            "ps2",
            "ps3",
            "psp",
            "pspminis",
            "psx",
            "pv1000",
            "satellaview",
            "saturn",
            "scummvm",
            "scv",
            "sega32x",
            "sega32xjp",
            "sega32xna",
            "segacd",
            "sfc",
            "sg1000",
            "sgb",
            "snes",
            "snesmsu1",
            "stv",
            "sufami",
            "supergrafx",
            "supervision",
            "supracan",
            "switch",
            "tg16",
            "tgcd",
            "tic80",
            "vectrex",
            "vic20",
            "videopac",
            "virtualboy",
            "vita",
            "vpinball",
            "wii",
            "wiiu",
            "wiiware",
            "windows",
            "windows3x",
            "windows9x",
            "ws",
            "wsc",
            "x68000",
            "xbox",
            "xbox360",
            "xcloud",
            "zxspectrum",
        )

    fun resolveFileId(platformId: String): String? {
        val key = platformId.lowercase().trim()
        if (key.isEmpty()) return null
        val canonical = aliases[key] ?: key
        return canonical.takeIf { it in knownIds }
    }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadBytes(
        platformId: String,
        variantKey: String,
    ): ByteArray? {
        val fileId = resolveFileId(platformId) ?: return null
        val path = "files/platform_logos/$variantKey/$fileId.png"
        return runCatching { Res.readBytes(path) }.getOrNull()
            // tic80 and similar may miss some variants — fall back to light_color
            ?: if (variantKey != "light_color") {
                runCatching { Res.readBytes("files/platform_logos/light_color/$fileId.png") }.getOrNull()
            } else {
                null
            }
    }
}

@Composable
fun PlatformConsoleIcon(
    platformId: String?,
    contentDescription: String?,
    variantKey: String,
    modifier: Modifier = Modifier,
) {
    if (platformId.isNullOrBlank()) return
    var bytes by remember(platformId, variantKey) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(platformId, variantKey) {
        bytes = PlatformConsoleIcons.loadBytes(platformId, variantKey)
    }
    val model = bytes ?: return
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize(),
    )
}

/** Convenience: resolve [PlatformLogoColorStyle] + theme into a resource key. */
fun platformLogoVariantKey(
    style: PlatformLogoColorStyle,
    darkTheme: Boolean,
): String = PlatformLogoColorStyle.resolveResourceKey(style, darkTheme)
