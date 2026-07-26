package com.wajiha.domain

/** Canonical emulator package lists shared by catalog, probes, and launcher. */
object EmulatorPackages {
    val retroArch: Set<String> =
        setOf(
            "com.retroarch",
            "com.retroarch.aarch64",
            "com.retroarch.ra32",
            "com.retroarch.plus",
        )

    /** Emulators that resolve sibling tracks via real filesystem paths. */
    val needsRealPath: Set<String> =
        setOf(
            "com.github.stenzek.duckstation",
        )

    val standalone: Set<String> =
        setOf(
            "org.ppsspp.ppsspp",
            "org.ppsspp.ppssppgold",
            "org.dolphinemu.dolphinemu",
            "org.citra.citra_emu",
            "org.yuzu.yuzu_emu",
            "org.duckstation.android",
            "com.github.stenzek.duckstation",
            "xyz.aethersx2.android",
            "xyz.aethersx2.tturnip",
            "xyz.aethersx2.cturnip",
            "xyz.aethersx2.custom",
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
            "com.gameloop.global",
        )

    val all: Set<String> = retroArch + standalone

    fun isRetroArch(packageName: String): Boolean = packageName in retroArch || packageName.startsWith("com.retroarch")
}
