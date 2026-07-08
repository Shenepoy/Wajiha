package com.wajiha.domain

/**
 * Known gaming, emulator, and streaming packages used for foreground detection
 * and the Now Playing overlay. Emulator packages from the DB are merged at
 * runtime; this set covers common apps not always present in bundled configs.
 */
object GamingAppCatalog {

    /** Streaming and cloud-gaming clients. */
    val defaultStreamingPackages: Set<String> = setOf(
        "com.netflix.mediaclient",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.gaming",
        "tv.twitch.android.app",
        "com.valvesoftware.steamlink",
        "com.nvidia.geforcenow",
        "com.microsoft.xboxone.smartglass",
        "com.microsoft.xcloud",
        "com.amazon.avod.thirdpartyclient",
        "com.plexapp.android",
        "com.crunchyroll.crunchyroid",
        "com.disney.disneyplus",
        "com.hbo.hbonow",
        "com.spotify.music"
    )

    /** Common standalone emulators and RetroArch variants. */
    val defaultEmulatorPackages: Set<String> = setOf(
        "com.retroarch",
        "com.retroarch.aarch64",
        "com.retroarch.ra32",
        "org.ppsspp.ppsspp",
        "org.ppsspp.ppssppgold",
        "org.dolphinemu.dolphinemu",
        "org.citra.citra_emu",
        "org.yuzu.yuzu_emu",
        "org.duckstation.android",
        "org.easyrpg.player",
        "com.drastic",
        "com.explusalpha.NeoEmu",
        "com.explusalpha.Snes9xPlus",
        "com.explusalpha.GbaEmu",
        "com.explusalpha.MdEmu",
        "com.explusalpha.MsxEmu",
        "com.explusalpha.NgpEmu",
        "com.explusalpha.PceEmu",
        "info.cemu.cemu",
        "com.limelight",
        "com.limelight.noir",
        "com.moonlight_stream",
        "com.parsec.client",
        "com.bluestacks.appmart",
        "com.gameloop.global"
    )

    val defaultGamingPackages: Set<String> =
        defaultStreamingPackages + defaultEmulatorPackages

    fun isDefaultGamingPackage(packageName: String): Boolean =
        packageName in defaultGamingPackages ||
            packageName.startsWith("com.retroarch")

    fun isKnownGamingPackage(
        packageName: String,
        emulatorPackages: Set<String>,
        isPlayStoreGame: Boolean = false
    ): Boolean =
        packageName in emulatorPackages ||
            isDefaultGamingPackage(packageName) ||
            isPlayStoreGame
}
