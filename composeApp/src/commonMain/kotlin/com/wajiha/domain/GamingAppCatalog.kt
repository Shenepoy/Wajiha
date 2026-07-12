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
    val defaultEmulatorPackages: Set<String> = EmulatorPackages.all

    val defaultGamingPackages: Set<String> =
        defaultStreamingPackages + defaultEmulatorPackages

    fun isDefaultGamingPackage(packageName: String): Boolean =
        packageName in defaultGamingPackages ||
            EmulatorPackages.isRetroArch(packageName)

    fun isKnownGamingPackage(
        packageName: String,
        emulatorPackages: Set<String>,
        isPlayStoreGame: Boolean = false
    ): Boolean =
        packageName in emulatorPackages ||
            isDefaultGamingPackage(packageName) ||
            isPlayStoreGame
}
