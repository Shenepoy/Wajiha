# Cocoon APK analysis

**Primary file:** `Study/cocoon-3.apk` (~155 MB)  
**Extracted:** `Study/_extracted/cocoon-3/`  
**Prior beta:** `Study/cocoon-beta-2-2.apk` → `_extracted/cocoon/` (see [Delta vs b2.2](#delta-vs-b22))

| Field | Value |
|-------|-------|
| Package | `rip.moth.cocoonshell` |
| Version | **3.0** (versionCode still `1`) |
| SDK | min 24, target/compile 36 |
| Kotlin | **2.2.21** (beta was 2.0.21), Gradle 8.13 |
| Compose | runtime **1.11.0**, Material3 **1.4.0** |
| Room | **2.8.4** (migrations through **34→35**) |
| DEX | **5** multidex (~279k method refs); beta was single `classes.dex` |

**Stack (inferred):** Kotlin, Jetpack Compose, Room, DataStore, Ktor CIO client, AndroidX Window, Discord Social SDK, rcheevos, JavaSteam (bundled), LaunchBox offline DB, HowLongToBeat cache.

## Role in ecosystem

**Cocoon** is a feature-rich **Android shell / game launcher** emphasizing:

- Customizable **widget grid** with zoom-aware placement (`grid_layout` / `grid_placement` / `grid_membership`)
- **Smart folders**, theme store (“Silk Pod”), wallpapers, sound packs, **jingles**
- **Liquid Glass** UI (blur / `RenderEffect` backdrop materials)
- **Dock style** + **app icon packs** (`appfilter` / `THEME_ICONPACK`)
- **Discord** rich presence + friend play log
- **RetroAchievements** via `rcheevos` + `RetroAchievementsHelper`
- **External display** (`ExternalDisplayActivity` + `SECONDARY_HOME`)
- **Pods** deep links: Silk / Picnic / Log / Leaflet / Flutter / Settings
- **ES-DE** migration, **OnboardingV2** (zoom-aware home layout)
- Robust **pending game session** recovery across process death

APK size still dominated by Discord native libs (`libdiscord_partner_sdk.so` ~8.2 MB arm64) plus large bundled fonts/sounds/video under `res/`.

## Manifest highlights

### Application

- `android:name="rip.moth.cocoonshell.CocoonApp"`
- FileProvider: `rip.moth.cocoonshell.fileprovider`
- `localeConfig` present (Language settings activity)
- `usesCleartextTraffic="true"`, legacy external storage flags retained

### Main entry

`MainActivity`: `LAUNCHER` + `HOME` + shortcut pin/install.

### Activities (3.0)

| Activity | Purpose |
|----------|---------|
| `ExternalDisplayActivity` | Secondary display; category **`SECONDARY_HOME`** |
| `SettingsActivity` | Settings hub |
| `LiquidGlassDemoActivity` / `GlassOptionsActivity` | Glass / blur materials |
| `DockStyleActivity` / `AppIconStyleActivity` | Dock + icon pack styling |
| `JingleReposActivity` | Per-game jingle repos (e.g. `inssekt/cocoon-jingle-repo`) |
| `HeroSettingsActivity` / `NowPlayingSettingsActivity` | Hero / now-playing UX |
| `SingleScreenSettingsActivity` | Single-display mode tuning |
| `GameSessionSettingsActivity` / `GridSettingsActivity` | Sessions + grid |
| `AnimationStyleActivity` / `SoundsSettingsActivity` / `LanguageSettingsActivity` | Polish |
| `ScrapeActivity` / `WallpaperActivity` / `ThemePickerActivity` | Metadata + look |
| `ThemeStoreActivity` (**Silk Pod**) | Theme marketplace |
| `PicnicPodActivity` | Screenshot / session highlight gallery |
| `LogPodActivity` | Play-history / daily log UI |
| `ESDEMigrationActivity` | ES-DE import |
| `onboarding.OnboardingV2Activity` | First-run (replaces primary role of `FirstTimeSetupActivity`) |
| `com.discord.socialsdk.AuthenticationActivity` | Discord OAuth |

### Services / receivers

| Component | Purpose |
|-----------|---------|
| `CocoonInputMethodService` | Custom IME |
| `FlutterkeyOverlayService` | Overlay (Flutterkey) |
| `CocoonNotificationListenerService` | **New** — system notification ingest → `cocoon_notifications` / conversation cache |
| `CocoonInputInjector` | Still present in DEX (input routing) |
| `com.discord.socialsdk.ForegroundService` | Discord SDK |
| `ShortcutReceiver` | `INSTALL_SHORTCUT` |

### Permissions (notable)

Same core shell permissions as beta, plus continued:

- `MANAGE_EXTERNAL_STORAGE`, legacy R/W
- `KILL_BACKGROUND_PROCESSES`, `REORDER_TASKS`, `PACKAGE_USAGE_STATS`
- `SYSTEM_ALERT_WINDOW`, `WRITE_SETTINGS`, `BIND_APPWIDGET`
- `ACCESS_NOTIFICATION_POLICY`, `POST_NOTIFICATIONS`
- `RECORD_AUDIO` + FGS microphone / media playback (Discord)
- `BLUETOOTH_CONNECT` (API 31+)

Notification listener uses `BIND_NOTIFICATION_LISTENER_SERVICE` on the service (user must grant in system settings).

## Native libraries (arm64)

| Library | Role |
|---------|------|
| `libdiscord_partner_sdk.so` (~8.2 MB) | Discord Social SDK core |
| `libdiscord_social_sdk.so` | Discord social glue |
| `librcheevos.so` | RetroAchievements client |
| `libandroidx.graphics.path.so` | Path / graphics |
| `libdatastore_shared_counter.so` | DataStore |
| `libc++_shared.so`, `libz.so` | NDK / zlib |

## Package structure (DEX)

```
rip.moth.cocoonshell/
├── CocoonApp, MainActivity, ExternalDisplayActivity, ShortcutReceiver
├── backup/          # Major 3.0 surface — category backup/restore coordinator
├── data/
│   ├── local/       # CocoonDatabase (+ LaunchboxDatabase), many DAOs
│   ├── model/       # Game, Folder, Widget, PendingGameSession, Picnic*, Notifications…
│   └── api/         # RetroAchievementsHelper, RCHash
├── domain/scraping, domain/assets
├── service/         # Input injector, Flutterkey overlay, NotificationListener
├── ui/
│   ├── activity/*   # Settings, pods, glass, dock, onboarding
│   ├── keyboard/CocoonInputMethodService
│   └── theme/       # ThemeColorScheme, SmartFolderAssets, IconOverlayAssets…
└── utils/ DiscordRichPresenceManager
```

## Room schema (3.0)

Room migrations exist from `1_2` through **`34_35`** (schema version **35**). Identity hashes seen in DEX: `1d2ad2ab…`, `d864d927…`.

### Core library (evolved from beta)

#### `platforms` / `players` / `rom_folders`

Unchanged conceptual model: platform regexes + scraper IDs; players with `intentPackage` / `amStartArguments` / `killPackageProcesses`; SAF scan roots.

#### `folders`

Smart folders (`smartFolderType`, `smartFolderQuery`), view modes, hero/logo, `forceChildrenToBottomScreen`.  
**New:** logo placement override fields (`logoPlacementOverridden`, `logoAnchor`, `logoScaleX/Y`).

#### `games`

Beta fields retained, plus:

| Column group | Notes |
|--------------|-------|
| LaunchBox / IGDB | `launchboxDatabaseId`, `launchboxVideoUrl`, `igdbId` |
| HLTB | `timeToBeatHastily/Normally/Completely` |
| Art provenance | `boxArtSource`, `logoSource`, `heroSource`, `metadataLocks` |
| Extra local art | `screenshotGameplayLocal`, `screenshotTitleLocal` |
| Logo placement | same override fields as folders |
| Dual display | `launchOnExternalDisplay`, `companionAppPackage` |
| Audio | `jingleLocal` |

#### `game_sessions`

Play sessions + `achievementUnlocksJson`.

### Grid system (expanded)

Beta had only `grid_positions` + `widgets` / `widget_columns`.

**3.0:**

| Table | Role |
|-------|------|
| `grid_positions` | Legacy position map by `screenType` |
| `grid_layout` | Per-`screenType` + **`zoomLevel`** column count |
| `grid_placement` | Item col/row/span at a zoom level |
| `grid_membership` | Which items belong to a screen |
| `widgets` | Widget instances (`screenType`, spans, AppWidgetHost provider, opacity) |

`widget_columns` removed from schema (column layout folded into widget row fields / new grid tables). Migration helpers reference `widgets_new`.

### New tables in 3.0

| Table | Role |
|-------|------|
| `pending_game_sessions` | Durable in-flight play sessions (`sessionToken`, `state`, pause, `ownerProcessEpoch`, `recoverySource`, `showOnExternalDisplay`) |
| `picnic_screenshot_records` | Session-linked screenshots for Picnic Pod |
| `cocoon_notifications` | Ingested Android notifications (channels, reply, conversation flags) |
| `android_conversation_messages` | Cached conversation threads from notification listener |
| `discord_friend_play_log` | Friends’ recent game activity |
| `retro_achievement_game_cache` | RA payload cache per game |
| `hltb_entries` | HowLongToBeat name / SteamAppId cache |
| `launchbox_games` (+ images, alternate names) | Offline LaunchBox metadata DB (separate `LaunchboxDatabase`) |

## Pods & deep links

| URI | Surface |
|-----|---------|
| `cocoon://pod/silk` | Theme store |
| `cocoon://pod/picnic` | Screenshot / highlight gallery |
| `cocoon://pod/log` | Play log |
| `cocoon://pod/leaflet` | Leaflet pod |
| `cocoon://pod/flutter` | Flutterkey-related |
| `cocoon://pod/settings` | Settings |
| `cocoon://pods` | Pod index |

## Liquid Glass / dock / icons

- `CocoonGlassMaterial`, `glassBlur`, backdrop draw path, Android `RenderEffect`
- Exported demo: `LiquidGlassDemoActivity`
- Dock: `DockStyle`, `dockAppIcons`, `dockIconShape`
- Icons: `IconPack`, `appfilter.xml`, Nova-style `com.fede.launcher.THEME_ICONPACK`
- Settings preview webps under `assets/settings_previews/` (icon style, battery, cursor, scrape, corner hints, fullscreen folder)

## Backup system (major 3.0)

Large `rip.moth.cocoonshell.backup` package with category handlers:

- Unified **BackupCoordinator** / **RestoreCoordinator**
- Categories: appearance, layout/fixed grid, metadata, play history, picnic, sources & players
- Legacy adapters (`LegacyLayoutBackupAdapter`, `LegacyPicnicCategoryHandler`, …) for older Cocoon backups
- Preference file backup + pending restore plans

## Emulator / foreground tracking

Still sophisticated focus negotiation (log tags):

- `cocoonHasWindowFocus`
- `FG_DECIDE` / `FG_DETECT` / `FG_RESULT` (usage-stats + event-pair winners)
- `onCocoonGainedForeground` / `onCocoonLostForeground`
- `suppressSessionPaused`

**3.0 addition:** `pending_game_sessions` persists session state across process death / stale FG decisions, with `recoverySource` and external-display flag.

**Wajiha relevance:** Play-time and “return from game” UX need a durable pending-session model, not only `onResume()`.

## Dual display

- `ExternalDisplayActivity` + `SECONDARY_HOME` (unchanged pattern)
- `ScreenType` on widgets/grid tables — content per display
- `launchOnExternalDisplay` on games; `showOnExternalDisplay` on pending sessions
- `forceChildrenToBottomScreen` on folders
- `allowExternalDisplayRefreshControl` preference strings
- `SingleScreenSettingsActivity` for non-dual devices

## Scraping / metadata

- ScreenScraper via `ScrapeActivity`
- SteamGridDB IDs on games
- **LaunchBox** offline DB + images
- **IGDB** id + skip path for live artwork
- **HowLongToBeat** cache table + game time-to-beat columns
- Art source locks (`metadataLocks`, per-field source columns)

## Discord

- `DiscordRichPresenceManager`
- Native SDK + foreground service + audio permissions
- **New:** `discord_friend_play_log` table

## Delta vs b2.2

| Area | b2.2 | 3.0 |
|------|------|-----|
| Version name | b2.2 | **3.0** |
| Kotlin | 2.0.21 | **2.2.21** |
| DEX | 1 | **5** |
| APK size | ~136 MB | ~155 MB |
| Grid | `grid_positions` + `widget_columns` | zoom layout/placement/membership; columns table gone |
| Sessions | `game_sessions` only | + **`pending_game_sessions`** |
| Notifications | — | Listener service + Room tables |
| Picnic / Log pods | — | Activities + screenshot records |
| Liquid Glass / Dock / Icon style | — | Dedicated activities |
| Backup | `LayoutBackup` util-scale | Full **category backup** package |
| Onboarding | `FirstTimeSetupActivity` | **`OnboardingV2Activity`** (+ zoom picker) |
| Metadata | ScreenScraper / RA / SGDB | + LaunchBox DB, IGDB, HLTB |
| Room migrations | fewer | through **35** |

## Suggested Wajiha takeaways

1. **Zoom-aware grid model** (`grid_layout` / `grid_placement` / `grid_membership`) — better than flat `grid_positions` alone
2. **Pending session durability** for accurate play time across FG churn and process death
3. **`ExternalDisplayActivity` + ScreenType** still the clearest Compose dual-HOME pattern alongside iiSU
4. **Category backup/restore** design if Wajiha ever exports layout/metadata/play history
5. **Smart folders** + art provenance locks as library UX patterns
6. Treat Discord / JavaSteam / LaunchBox offline DB as optional product choices — they dominate size and complexity

Do **not** bundle Discord SDK or JavaSteam unless product requires them.

## Deep dive (enums & state machines)

Resolved via `apkanalyzer dex packages` field listings (not guessed from noisy strings).

### `PendingGameSessionState`

`RUNNING` → `PAUSED` → `FINALIZING` → `FINALIZED`

DAO surface (`PendingGameSessionDao`):

| Method | Role |
|--------|------|
| `upsert` | Create / refresh row |
| `markRunning` | Bind running + `ownerProcessEpoch` |
| `updatePauseState` | Set/clear `pauseStartedAtMs` |
| `markFinalized` | Write `finalizedAtMs` + `recoverySource` |
| `finalizeAllOpenForGame` | Close all open tokens for a game |
| `getOpenSessions` / `getLatestOpenSessionForGame` | Live queries |
| `getRecoverableSessions(ownerProcessEpoch)` | Rows where `finalizedAtMs IS NULL AND ownerProcessEpoch != ?` |

FG decision tags (`CocoonSession`): `FG_DECIDE` / `FG_DETECT` / `FG_RESULT` (event-pair winner → usage-stats fallback → cached), `GAINED_FG` / `LOST_FG`, screen-on **grace** (`COCOON_FOCUS_GAIN: IGNORED`, `GRACE_EXPIRED`), `suppressSessionPaused` cleared when emulator confirmed foreground.

### `Widget.ScreenType`

`HOME` | `EXTERNAL` | `FOLDER` | `SETTINGS`

Folder-scoped layouts also use string keys like `folder_%` in `grid_layout` (columns nulled for non-paged folder layouts).

### `Widget.WidgetType` (internal + host)

| Group | Types |
|-------|-------|
| System | `CLOCK`, `DATE`, `BATTERY`, `WEATHER`, `STORAGE_INFO`, `SYSTEM_INFO` |
| Library | `CONTINUE_PLAYING`, `LAST_PLAYED`, `RECENT_GAMES`, `RANDOM_GAME`, `PLAYTIME`, `RETROACHIEVEMENTS` |
| Social / media | `DISCORD_FRIENDS`, `DISCORD_STATUS`, `NOW_PLAYING`, `MUSIC_PLAYER`, `ALBUM_ART`, `PICNIC` |
| Chrome | `BLANK`, `SPACER`, `DIVIDER`, `IMAGE` |
| Shortcuts | `APP_SHORTCUT`, `FOLDER_SHORTCUT`, `SETTINGS_SHORTCUT` |
| Host | `ANDROID_WIDGET` (AppWidgetHost via `providerPackage`/`providerClass`) |

Log tags: `CocoonGridV3`, `CocoonGridV3-DRAG`, `CocoonGridV3-EDIT`, `GridPositionRepo`, glass downsample notes in shader comments.

### `GridPosition.ItemType`

`GAME` | `FOLDER` | `WIDGET` | `SETTINGS` | `CREATE_FOLDER` | `EMPTY`

### Smart folders

`SmartFolderType` / `SmartFolderKind`:  
`FAVORITES`, `RECENT`, `MOST_PLAYED`, `NEWLY_ADDED`, `UNPLAYED`, `BY_PLATFORM`, `BY_GENRE`, `BY_DEVELOPER`, `CUSTOM`, `SUBFOLDER`  
Presets: `PRESET_FAVORITES`, `PRESET_RECENT`, `PRESET_UNPLAYED`.

`Folder.ViewMode`: `GRID` | `LIST` | `COVER` | `DETAIL`

### Scraping

`ScrapeSource`: `SCREENSCRAPER`, `STEAMGRIDDB`, `LAUNCHBOX`, `IGDB`, `HLTB`  
(`supportsArt` / `supportsMetadata` flags per source — ScreenScraper rejected for folders; folders use SteamGridDB.)

`ScrapeMode`: `MISSING_BASICS` | `MISSING_SELECTED` | `FORCE_ALL`

External data feeds:

- LaunchBox: `https://gamesdb.launchbox-app.com/Metadata.zip` + `images.launchbox-app.com`
- HLTB: GitHub CSV (`julianxhokaxhiu/hltb-scraper`) + Cocoon mirror repo `inssekt/cocoon-hltb`
- Jingles: `inssekt/cocoon-jingle-repo`

Metadata lock keys: `TITLE`, `SUMMARY`, `DEVELOPER`, `PUBLISHER`, `GENRE`, `RATING`, `RELEASE_DATE`, `TIME_TO_BEAT`.

### Notifications

`NotificationChannel`: `SCRAPING` | `SOCIAL` | `ACHIEVEMENT` | `ERROR` | `SYSTEM_APP`  
Ingest via `CocoonNotificationListenerService` → Room + optional conversation reply cache.

### Backup categories

`APPEARANCE` | `LAYOUT` | `METADATA` | `PLAY_HISTORY` | `PICNIC` | `SOURCES_AND_PLAYERS`

Phases: create `PREPARING` → `EXPORTING` → `FINALIZING`; restore `PRE_SCAN` / `POST_SCAN`.  
Sources: `UNIFIED` | `LEGACY_LAYOUT` | `LEGACY_PLAYTIME`.

### Dual-display routing (refined)

- Widget/grid `ScreenType.EXTERNAL` for secondary home content
- Launch ExternalDisplay only when companion/docked display exists; else skip
- Per-game `launchOnExternalDisplay` + pending session `showOnExternalDisplay`
- Folder `forceChildrenToBottomScreen`
- `SCREEN_TOP` / `SCREEN_BOTTOM` appear as UI/sound identifiers (not `Widget.ScreenType` values)
- Shell input: `CocoonShellBridge` can suppress `CocoonInputInjector` (`inject SKIPPED (shell bridge active)`)

### Glass / grid rendering

`CocoonGlassMaterial` + backdrop path; grid tiles may **downsample** blur radius (`CocoonGridV3 downsample`) so full-res widget blur radii stay correct on downsampled tiles.

## Wajiha cross-check

Wajiha already has in-memory pause/resume in `PlaySessionTracker` (`syncTopDisplayForeground`). Cocoon 3.0’s edge is **Room-backed pending sessions** with `ownerProcessEpoch` recovery and FG event-pair / usage-stats arbitration — the piece most worth studying next if play-time accuracy across process death matters.

## Dissection notes

```bash
mkdir -p Study/_extracted/cocoon-3
unzip -qo Study/cocoon-3.apk -d Study/_extracted/cocoon-3
apkanalyzer manifest print Study/cocoon-3.apk
apkanalyzer dex packages Study/cocoon-3.apk | rg 'PendingGameSessionState|Widget\$WidgetType|BackupCategory'
strings Study/_extracted/cocoon-3/classes*.dex | rg 'CREATE TABLE IF NOT EXISTS'
```

Closed source — infer architecture from manifest + DEX strings + assets; do not treat decompiled output as reusable code.
