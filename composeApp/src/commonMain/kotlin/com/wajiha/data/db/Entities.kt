package com.wajiha.data.db

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * A game system (SNES, PS1, ...). Ids are stable short names ("snes", "psx")
 * so imported configs (Daijishō / iiSU) and bundled defaults merge cleanly.
 */
@Entity(tableName = "platforms")
data class PlatformEntity(
    @PrimaryKey val id: String,
    val name: String,
    val shortName: String,
    /** Comma-separated list of ROM file extensions without dot: "sfc,smc,zip" */
    val extensions: String,
    /** RetroAchievements console id, null if unsupported */
    val raConsoleId: Int? = null,
    /** ScreenScraper systeme id, null if unknown */
    val screenScraperId: Int? = null,
    /** libretro-thumbnails system directory name, e.g. "Nintendo - Super Nintendo Entertainment System" */
    val libretroName: String? = null,
    /** Preferred boxart aspect ratio, e.g. "3:4", used by grid layout */
    val boxartAspectRatio: String? = null,
    val sortIndex: Int = 0,
    val enabled: Boolean = true,
    /** Default emulator id for this platform (FK into emulators, loose) */
    val defaultEmulatorId: String? = null,
    /** When true, ROM folders for this platform use a deeper scan walk. */
    val deepScan: Boolean = false,
)

/**
 * An emulator/player config for a platform. One platform can have many;
 * `isDefault` marks the platform-level default.
 */
@Entity(
    tableName = "emulators",
    indices = [Index("platformId")],
)
data class EmulatorEntity(
    @PrimaryKey val id: String,
    val platformId: String,
    val name: String,
    /** Comma-separated candidate packages, first installed wins */
    val packageNames: String,
    val activityName: String? = null,
    /** Intent action, defaults to VIEW when null */
    val action: String? = null,
    /** "uri" | "path" — how the ROM is passed */
    val routeType: String = "uri",
    /** Raw Daijishō-style am-start arguments (fallback path) */
    val amStartArguments: String? = null,
    /** JSON array of {key,value,type} intent extras */
    val extrasJson: String? = null,
    /** JSON array of activity flag strings: clear-top, clear-task, ... */
    val activityFlagsJson: String? = null,
    /** Keep original SAF uri (no FileProvider rewrap) — Flycast zips etc. */
    val keepSafUri: Boolean = false,
    /** Kill emulator background processes before launch (Daijishō flag) */
    val killBeforeLaunch: Boolean = false,
    /** RetroArch libretro core name when applicable */
    val libretroCore: String? = null,
    val isDefault: Boolean = false,
    /** true if user-created/edited rather than imported/bundled */
    val custom: Boolean = false,
)

@Entity(
    tableName = "games",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["platformId", "displayName"]),
        Index("crc32"),
        Index("md5"),
    ],
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SAF content uri (or file uri) of the ROM entry file */
    val uri: String,
    val platformId: String,
    val displayName: String,
    val sortName: String,
    val fileName: String,
    val fileSize: Long = 0,
    /** Lazily computed hashes (hex, lowercase); null until computed */
    val crc32: String? = null,
    val md5: String? = null,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val playCount: Int = 0,
    /** Epoch millis */
    val lastPlayedAt: Long? = null,
    val addedAt: Long = 0,
    /** Per-game emulator override (FK into emulators, loose) */
    val emulatorOverrideId: String? = null,
    /** Display id preference when launching on dual-display devices */
    val launchOnDisplay: Int? = null,
    // Scraped metadata
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val rating: Float? = null,
    /** Age classification e.g. "ESRB Teen", "PEGI 12" (not community score) */
    val ageRating: String? = null,
    val players: String? = null,
    val region: String? = null,
    /** RetroAchievements game id once hash-linked */
    val raGameId: Long? = null,
    /** Epoch millis of last successful scrape, null = never scraped */
    val scrapedAt: Long? = null,
)

@Entity(
    tableName = "rom_folders",
    indices = [Index("platformId")],
)
data class RomFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: String,
    /** SAF tree uri persisted with takePersistableUriPermission */
    val treeUri: String,
    val scanDepth: Int = 3,
    /** Comma-separated extra extensions to include beyond platform defaults */
    val extraExtensions: String? = null,
    val lastScanAt: Long? = null,
    val enabled: Boolean = true,
)

@Entity(
    tableName = "game_media",
    indices = [Index(value = ["gameId", "type"])],
)
data class GameMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    /** boxart | logo | hero | screenshot | fanart | video | icon | banner | music */
    val type: String,
    /** screenscraper | steamgriddb | libretro | ra | romm | local | manual */
    val source: String,
    val localPath: String? = null,
    val remoteUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val updatedAt: Long = 0,
)

@Entity(
    tableName = "play_sessions",
    indices = [Index("gameId"), Index("startedAt")],
)
data class PlaySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null for sessions of manually-launched unknown apps */
    val gameId: Long? = null,
    /** Foreground package that was tracked */
    val packageName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationSec: Long = 0,
    /** launcher | detected — how the session was started */
    val origin: String = "launcher",
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconPath: String? = null,
    val sortIndex: Int = 0,
)

@Entity(
    tableName = "collection_games",
    primaryKeys = ["collectionId", "gameId"],
    indices = [Index("gameId")],
)
data class CollectionGameCrossRef(
    val collectionId: Long,
    val gameId: Long,
    val position: Int = 0,
)
