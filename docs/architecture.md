# Wajiha architecture

This document explains how the pieces fit together and where to make changes. For product context and setup, see the [README](../README.md).

## Module split

Two Gradle modules matter (the iOS targets are scaffolding):

- **`composeApp`** — Kotlin Multiplatform. All UI (Compose Multiplatform), domain logic, database, scrapers, and state live here in `commonMain`. Platform-specific pieces are expressed as plain interfaces in `com.wajiha.platform` (not `expect/actual`, so hosts can bind them with DI): `AppActions`, `LibraryActions`, `SystemControls`, `RomScanner`, `RomHasher`, plus scraper-side `MediaStorage` and `LocalMediaFiles`.
- **`androidApp`** — the Android host. Activities, services, workers, and the Android implementations of every interface above. It wires everything together with Koin in `WajihaApplication`.

Both the primary and secondary activities run in **one process**, so dual-screen coordination is plain shared `StateFlow` — no IPC (this is the key simplification over NeoStation's two Flutter engines).

## Dependency injection

Koin. Shared modules in `com.wajiha.di`:

- `dataModule` — DAOs, repositories, `ConfigInstaller`, `SettingsRepository`, `LibraryScanner`
- `scraperModule` — `HttpClient` (Ktor), `ScraperSettingsRepository`, the six `ScraperSource`s, `ScrapeEngine` (with the host's `ImageProcessor` when bound), `BatchProgressStore`, `BatchScraper`, `RaClient`, `RaRepository`
- `stateModule` — `DualScreenStore`
- `uiModule` — `HomeViewModel`, `SettingsViewModel`, `ScraperViewModel`, `RaViewModel`

The Android host adds `androidModule` (in `WajihaApplication`) providing the database builder, DataStore, SAF scanner/hasher, launcher stack, display coordinator, foreground monitor, system controller, media storage, and the `ScreenScraperDevCredentials` from BuildConfig.

## Data layer

Room KMP (`com.wajiha.data.db`), bundled SQLite driver. Tables:

| Table | Purpose |
|---|---|
| `platforms` | Systems ("snes", "psx"). Stable string ids so imports merge. Holds RA console id, ScreenScraper id, libretro thumbnail name, boxart ratio. |
| `emulators` | Per-platform launch configs: candidate packages, activity, action, route type (uri/path), am-start fallback, extras JSON, flags, `keepSafUri`, RetroArch core. |
| `games` | One row per ROM. SAF uri (unique), hashes (lazy), favorite/hidden, play stats, per-game emulator override, scraped metadata fields, `raGameId`, `scrapedAt`. |
| `rom_folders` | SAF tree URIs per platform with scan settings. |
| `game_media` | One row per (game, media type): source, local path, remote url. |
| `play_sessions` | Start/end/duration; `origin` distinguishes launcher vs detected sessions. |
| `collections` + `collection_games` | User collections. |

Indices: `games(uri)` unique, `games(platformId, displayName)`, hash columns, `game_media(gameId, type)`, session times.

Settings are two DataStore blobs: `SettingsRepository` (typed keys for app/dual-screen options) and `ScraperSettingsRepository` (a single JSON-serialized `ScraperSettings`).

## Platform/emulator configs

`com.wajiha.data.config`:

- `UnifiedConfig.kt` — Wajiha's internal `PlatformConfig`/`EmulatorConfig` schema.
- `DaijishouImporter` — parses Daijishō platform JSON (119 bundled fixtures under `composeResources/files/platforms/daijishou/`), extracting extensions from player regexes, RA console ids, libretro names from `LIBRETRO:` scraper sources, and am-start arguments.
- `IisuImporter` — parses iiSU's 173-console `emuladores_default.json` (bundled).
- `ConfigMerger` — merges both, Daijishō as canonical, iiSU filling gaps.
- `AmStartArgumentsParser` — maps `-n/-a/-e/--es/--ez/-d/--activity-*` strings to structured intents.
- `ConfigInstaller` — installs the bundled starter set on first run (`WajihaApplication.seedDefaultsIfNeeded`) and imports user-provided files.

## Scanning & hashing

`LibraryScanner` (common) walks folders through the `RomScanner` interface (`SafRomScanner` on Android uses `DocumentsContract` tree traversal), matches per-platform extension lists, filters multi-disc track files when a `.cue/.gdi/.m3u` master exists, diffs against the DB, and cleans display names. Hashing (CRC32 streaming + MD5 via `ContentRomHasher`) is lazy: `computeMissingHashes` batches run after scans, capped by file size. `LibraryScanWorker` runs the whole thing in WorkManager.

## Launching

`GameLauncher` resolves game → emulator (per-game override → platform default → first installed candidate) and builds a `LaunchSpec` with placeholder substitution (`{file.path}`, `{file.uri}`, `%ROM%`). `EmulatorLauncher` (port of NeoStation's) then:

1. Grants URI permissions synchronously (`grantUriPermission` + ClipData) *before* `startActivity` — avoids the first-launch race
2. Rewraps single-file ROMs through FileProvider; keeps SAF URIs for multi-file formats and grants sibling tracks
3. Applies RetroArch defaults (LIBRETRO core path + CONFIGFILE per variant package)
4. Optionally kills emulator background processes first

`PlaySessionTracker` opens a session on launch and closes it when `MainActivity.onResume` fires (returning from the game), updating play counts.

## Dual-screen engine

`DualScreenStore` is the single source of truth. States: `SingleDisplay`, `DualBrowsing`, `GameRunning`, `AppOnSecondary`, `BlackoutSecondary`. Secondary modes: `GameGrid`, `NowPlaying`, `AppDock`, `RunningApps`, `QuickSettings`, `Achievements`, `Clock`, `Off`.

- `DisplayCoordinator` listens to `DisplayManager` and feeds `onDisplaysChanged` — this is what collapses to the single-display combined layout.
- `SecondaryHomeActivity` (`SECONDARY_HOME` intent category) renders `SecondaryApp()`, which switches on the current mode. Android launches it on the second display automatically when Wajiha is the default home.
- `ForegroundAppMonitor` polls `UsageStatsManager.queryEvents` every 2 s (or gets instant events from the opt-in `GameDetectAccessibilityService`), matches packages against known emulator lists + `CATEGORY_GAME` apps, and pushes `NowPlayingState` — this is how manually launched games appear on the bottom screen. It also maintains the Running Apps list (kill / move-to-display).
- `KeepAliveService` (foreground, `specialUse`) keeps the process alive while an external game is up.
- Options mirrored into the store synchronously (blackout-on-launch, preferred game mode) so state transitions don't need async reads.

## Scraping

`com.wajiha.data.scraper`:

- `ScraperSource` — the plugin interface: `isConfigured`, `lookup` (hash-first auto match), `search` (manual, by name). Implementations are side-effect free; they return `ScrapeCandidate`s (metadata + `MediaCandidate` URLs).
- `ScrapeEngine` — does the side effects. For one game: resolve effective settings via `ScraperSettings.forPlatform` (per-platform overrides of sources / priorities / regions; unset fields inherit globals), query every configured source once, apply metadata from the first source in `metadataPriority` that matched (merge, don't overwrite, unless it's a forced manual match), then for each media type walk `mediaPriority[type]`, pick the best-region candidate, download through a 4-permit semaphore, store via `MediaStorage`, upsert `game_media`. RA game ids are captured even when RA isn't the metadata source. Downloads run through `ImageProcessor` (Android: `AndroidImageProcessor`, BitmapFactory sample-decode) which enforces `maxImageResolution` — oversized images are downscaled and re-encoded (PNG when alpha matters, JPEG q88 otherwise).
- `BatchScraper` — sequential driver with `StateFlow` progress, pause (suspends between games), and error report; honors `skipAlreadyScraped`. Progress is checkpointed to `BatchProgressStore` (DataStore JSON) after every game: a snapshot still marked `running` means the process died mid-run, so the next worker run carries the counts forward (already-scraped games are excluded by the filter). User cancels clear the resumable flag (`BatchScraper.requestCancel` before `WorkManager.cancel`); `restoreIfIdle()` seeds the UI with the last snapshot after an app restart.
- `ScrapeWorker` (androidApp) — hosts the batch as a `dataSync` foreground WorkManager job with a progress notification; wifi-only maps to a network constraint at enqueue.

Sources and their quirks:

| Source | Auth | Match | Notes |
|---|---|---|---|
| `screenscraper` | user account (+ optional dev creds) | crc/md5 + filename, then name search | region/language chains applied to names/synopsis/dates/media |
| `steamgriddb` | API key | name autocomplete | fetches grids/heroes/logos/icons per candidate |
| `libretro` | none | filename convention | needs `platforms.libretroName`; HEAD-checks thumbnail URLs |
| `ra` | username + web API key | md5 | responses use PascalCase keys (`ID`, `Title`, ...) — keep the `@SerialName`s |
| `romm` | server URL + basic auth | name search | lenient JSON (numbers as strings) |
| `local` | media folder path | ES-DE layout lookup | returns `file://` URLs; engine "downloads" them like any other |

## RetroAchievements

`RaClient` wraps the RA Web API (`API_GetUserProfile`, `API_GetGameInfoByHash`, `API_GetGameInfoAndUserProgress`). `RaRepository` handles credential checks, persists hash-links to `games.raGameId`, and caches per-game progress in memory. `RaViewModel` follows `DualScreenStore.nowPlaying` so the `AchievementsPanel` secondary mode auto-loads the running game's badge list.

## System control

`SystemControls` (common interface) ↔ `SystemController` (Android): brightness + screen timeout via WRITE_SETTINGS, media volume, battery via sticky broadcast, torch via CameraManager, wifi/bt settings intents, and permission/role helpers (usage access, write settings, notifications — routed through `MainActivity`'s runtime-permission launcher on 33+ — all-files access, default-home detection + `ACTION_HOME_SETTINGS`). `QuickSettingsPanel` renders it; `OnboardingScreen` uses the same helpers with live permission polling. `BootReceiver` just warms the process. `MainActivity` swallows BACK, as a home screen should.

## Performance decisions

- R8 + resource shrink on release; rules in `androidApp/proguard-rules.pro` keep serializers, Room generated code, Ktor engine discovery, and reflection-instantiated workers/services.
- `baseline-prof.txt` precompiles the app's own classes (a launcher runs nearly all of them at startup); `profileinstaller` ships it.
- Coil: 15 % memory cache, 256 MB disk cache, crossfade — configured once in `WajihaApplication` (`SingletonImageLoader.Factory`).
- Koin singles are lazy; nothing network-touching is created until a screen needs it. First-run config seeding runs off the main thread.
- Lazy grids use stable keys; hidden games filtered in SQL; hashes computed in bounded batches.

## Adding things

- **A scraper source**: implement `ScraperSource`, add it to the list in `scraperModule`, add its id to `ScraperSettings.enabledSources`/priority defaults, and surface credentials in `ScraperScreen`'s Sources tab.
- **A secondary-screen mode**: add to `SecondaryMode`, render it in `SecondaryApp`, and (optionally) list it in the Settings `ChoiceRow` for the game-running mode.
- **An emulator/platform**: prefer editing/adding a Daijishō-format JSON and importing it; the DB schema stores everything the launcher needs per emulator.
- **A permission in onboarding**: extend `PermissionStates` + `SystemController.permissionStates()`, add a `PermissionRow`.
