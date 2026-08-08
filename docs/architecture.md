# Wajiha architecture

This document explains how the pieces fit together and where to make changes. For product context and setup, see the [README](../README.md). For CI, release signing, and test layers, see [ci.md](ci.md).

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
- `uiModule` — `HomeViewModel`, `SettingsViewModel`, `ScraperViewModel`, `RaViewModel`, `GameDetailViewModel`, `PlatformSettingsViewModel`

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
| `collections` + `collection_games` | User collections (**schema + repository exist; UI pending**). |

Indices: `games(uri)` unique, `games(platformId, displayName)`, hash columns, `game_media(gameId, type)`, session times.

Settings are two DataStore blobs: `SettingsRepository` (typed keys for app/dual-screen options) and `ScraperSettingsRepository` (a single JSON-serialized `ScraperSettings`). Notable typed prefs:

- `HeroLayoutBundle` — independent `HeroLayout` for primary vs secondary (`HeroDisplaySlot`); presets + custom element geometry in `HeroLayout.kt` / `HeroLayoutFitter`
- `GameGridPreferences` — art / rows / tile size / titles / chrome, separate primary vs secondary keys (edited for whichever display hosts Settings)
- Home dock, icon pack package, icon shape (`IconAppearancePreferences`)

## Platform/emulator configs

`com.wajiha.data.config`:

- `UnifiedConfig.kt` — Wajiha's internal `PlatformConfig`/`EmulatorConfig` schema.
- `DaijishouImporter` — parses Daijishō platform JSON (119 bundled fixtures under `composeResources/files/platforms/daijishou/`), extracting extensions from player regexes, RA console ids, libretro names from `LIBRETRO:` scraper sources, and am-start arguments.
- `IisuImporter` — parses iiSU's 173-console `iisu_consoles.json` (bundled under `composeResources/files/platforms/`).
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

`PlaySessionTracker` opens one Room row per active emulator package (multi-session). Rows close when `ForegroundAppMonitor` confirms a game ended, or when `MainActivity.onResume` runs with no active sessions in `DualScreenStore` (dual-display: bottom launcher stays foreground while gaming, so sessions are not closed on resume). See [sessions.md](sessions.md).

## Gamepad input

Full gamepad navigation lives in `composeApp/src/commonMain/kotlin/com/wajiha/input/` and `ui/components/gamepad/`. Android key routing is in `androidApp/.../input/GamepadKeyRouter.kt`. See [gamepad.md](gamepad.md).

## ROM reconciliation (Tier 3)

When **Settings → detect external games** is enabled, `ExternalGameResolver` probes emulator data (RetroArch `content_history.lpl`, AetherSX2 `playtime.dat` / `settings.ini`) to match manually launched ROMs to library games. Probes live in `androidApp/.../detect/probes/`.

## Dual-screen engine

`DualScreenStore` is the single source of truth. States: `SingleDisplay`, `DualBrowsing`, `GameRunning`, `AppOnSecondary`, `BlackoutSecondary`. User-facing secondary modes while a game runs: `NowPlaying`, `QuickSettings`, `RunningApps`, `Clock`, `Off` (blackout). Also: `GameGrid` (library), `AppDock` (Apps drawer on secondary). `Achievements` remains in the enum for legacy prefs and remaps to `NowPlaying`.

- `DisplayCoordinator` listens to `DisplayManager` and feeds `onDisplaysChanged` — this is what collapses to the single-display combined layout.
- `SecondaryHomeActivity` (`SECONDARY_HOME` intent category) renders `SecondaryApp()`, which switches on the current mode. Android launches it on the second display automatically when Wajiha is the default home.
- `ForegroundAppMonitor` polls `UsageStatsManager.queryEvents` every **2 s idle / 750 ms when sessions are active** (or gets instant events from the opt-in `GameDetectAccessibilityService`), matches packages against known emulator lists + `CATEGORY_GAME` apps, and maintains multi-session state in `DualScreenStore.sessionCache`. See [sessions.md](sessions.md) and [debug.md](debug.md).
- `KeepAliveService` (foreground, `specialUse`) keeps the process alive while an external game is up.
- Options mirrored into the store synchronously (blackout-on-launch, preferred game mode) so state transitions don't need async reads.

### Hero layout

Per-display layouts live in `HeroLayoutBundle` (Settings → Screens → Hero layout). `HeroCanvas` renders elements (`Backdrop`, `Cover`, `Logo`, …) with `HeroLayoutFitter`. **Customize layout** opens `HeroLayoutEditor` as a Settings fullscreen drill-in; the draft and selection sit on `DualScreenStore` until save via `SettingsRepository.updateHeroLayoutSlot`. Presets: Classic, Cover focus, Logo focus, Minimal, Text only, Empty, Custom.

## Scraping

`com.wajiha.data.scraper`:

- `ScraperSource` — the plugin interface: `isConfigured`, `lookupResult` (typed `SourceLookupOutcome`: Hit / Miss / Failed), and `search` (manual, by name). Implementations classify auth / network / rate-limit / HTTP failures instead of swallowing them to null. Each source owns call budgets / circuit cool-downs via `SourceHealthBudget`.
- `ScrapeMatchTool` — searches configured sources, ranks media by match confidence (Hash > Name > Autocomplete), community score, and author prefer/blacklist, then auto-picks (`MatchMode.Auto`) or returns ordered options for Review.
- `ScrapeEngine` — side effects only. `scrapeGame` / Review gather paths call `ScrapeMatchTool`, then download and persist. Metadata still follows `metadataPriority`. Media downloads that all fail yield `Partial`. Downloads run through `ImageProcessor` (Android: `AndroidImageProcessor`) which enforces `maxImageResolution`.
- `BatchScraper` — sequential driver with `StateFlow` progress (matched / partial / noMatch / errorCount + capped `issues` list), pause (status “Paused”), cancel, preflight (`preflightMessage`), and `prepareRetryFailed` for Error+Partial games. Progress is checkpointed to `BatchProgressStore` after every game; user cancels clear the resumable flag; `restoreIfIdle()` seeds the UI after restart.
- `ScrapeWorker` (androidApp) — hosts the batch as a `dataSync` foreground WorkManager job; notifications mirror split counts and issue lines. Enqueue is blocked when work is already active or preflight fails (`LibraryActions.startScrape` / `retryFailedScrape` return a reason string).

Sources and their quirks (see also [external-apis.md](external-apis.md)):

| Source | Auth | Match | Notes |
|---|---|---|---|
| `screenscraper` | user account (+ optional dev creds) | hash+systeme → cleaned name → search → `gameid` hydrate | region/language chains; `romtype` / `systemeid` required without CRC |
| `steamgriddb` | API key | autocomplete only; lazy art for chosen id | parses `score` + `author`; grids→boxart **and** square (1:1 dims), heroes, logos, icons |
| `libretro` | none | filename convention | needs `platforms.libretroName`; HEAD-checks thumbnail URLs |
| `ra` | username + web API key | md5 | responses use PascalCase keys (`ID`, `Title`, ...) — keep the `@SerialName`s |
| `romm` | server URL + basic auth | name search | lenient JSON (numbers as strings) |
| `local` | media folder path | ES-DE layout lookup | returns `file://` URLs; engine "downloads" them like any other |

## RetroAchievements

`RaClient` wraps the RA Web API (`API_GetUserProfile`, `API_GetGameList` with hashes, `API_GetGame`, `API_GetGameInfoAndUserProgress`). Auth uses `y` (API key) plus `z` (username) like the official client; progress/`u` prefers a stored ULID after login. Hash libraries are disk-cached aggressively (GetGameList guideline); HTTP is paced. There is no public Web `GetGameInfoByHash` — hash→game uses GetGameList (title fallback when ROM MD5 ≠ RA hash). `RaRepository` persists hash/title-links to `games.raGameId` and caches progress briefly in memory. Scraper source `ra` and game-detail / progress UI consume the same client; the old Achievements secondary tab was removed (legacy enum → Now Playing).

## Settings UI composition

Settings uses folder chrome (`WajihaFolderSettingChrome` + `FolderTabRow`): Library, Scraper, Screens, Appearance, System. Dense sections open as fullscreen drill-ins (`WajihaSettingFullscreenPage` — Back + title, no folder tabs) — e.g. Scraper hub pages, Screens → Dual display / Now Running / Hero layout / editor, Appearance → Focus ring / Glyphs. Prefer shared `Wajiha*Setting` / `GamepadSettingRow` primitives; see `.cursor/rules/wajiha-ui-library.mdc`.

## System control

`SystemControls` (common interface) ↔ `SystemController` (Android): brightness + screen timeout via WRITE_SETTINGS, media volume, battery via sticky broadcast, torch via CameraManager, wifi/bt settings intents, and permission/role helpers (usage access, write settings, notifications — routed through `MainActivity`'s runtime-permission launcher on 33+ — all-files access, default-home detection + `ACTION_HOME_SETTINGS`). `QuickSettingsPanel` renders it; `OnboardingScreen` uses the same helpers with live permission polling. `BootReceiver` just warms the process. `MainActivity` swallows BACK, as a home screen should.

## Performance decisions

- R8 + resource shrink on release; rules in `androidApp/proguard-rules.pro` keep serializers, Room generated code, Ktor engine discovery, and reflection-instantiated workers/services.
- `baseline-prof.txt` precompiles the app's own classes (a launcher runs nearly all of them at startup); `profileinstaller` ships it.
- Coil: 15 % memory cache, 256 MB disk cache, crossfade — configured once in `WajihaApplication` (`SingletonImageLoader.Factory`).
- Koin singles are lazy; nothing network-touching is created until a screen needs it. First-run config seeding runs off the main thread.
- Lazy grids use stable keys; hidden games filtered in SQL; hashes computed in bounded batches.

## Adding things

- **A scraper source**: implement `ScraperSource`, add it to the list in `scraperModule`, add its id to `ScraperSettings.enabledSources`/priority defaults, and surface credentials under Scraper → Accounts / Sources.
- **A secondary-screen mode**: add to `SecondaryMode`, render it in `SecondaryApp`, and (optionally) list it in Settings → Screens → Dual display preferred-mode choices.
- **An emulator/platform**: prefer editing/adding a Daijishō-format JSON and importing it; the DB schema stores everything the launcher needs per emulator.
- **A permission in onboarding**: extend `PermissionStates` + `SystemController.permissionStates()`, add a `PermissionRow`.
