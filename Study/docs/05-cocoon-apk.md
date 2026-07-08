# Cocoon APK analysis

**File:** `Study/cocoon-beta-2-2.apk` (~142 MB)  
**Extracted:** `Study/_extracted/cocoon/`  
**Package:** `rip.moth.cocoonshell`  
**Version:** b2.2 (versionCode 1) — **beta**  
**SDK:** min 24, target 36  
**Stack (inferred):** Kotlin, Jetpack Compose, Room, ExoPlayer/Media3, Discord Social SDK, rcheevos (RetroAchievements native)

## Role in ecosystem

**Cocoon** is a feature-rich **Android shell / game launcher** emphasizing:

- Customizable **widget grid** and **smart folders**
- **Theme store**, wallpapers, sound packs
- **Discord** rich presence and voice-adjacent services
- **RetroAchievements** via `rcheevos` native lib + `RetroAchievementsHelper`
- **External display** activity for dual-screen devices
- **ES-DE** migration path
- Emulator focus tracking (`cocoonHasWindowFocus`, foreground game sessions)

Larger APK size driven by Discord native libs (`libdiscord_partner_sdk.so`, `libdiscord_social_sdk.so`) per ABI.

## Manifest highlights

### Application

- `android:name="rip.moth.cocoonshell.CocoonApp"`
- FileProvider: `rip.moth.cocoonshell.fileprovider`

### Main entry

`rip.moth.cocoonshell.MainActivity`:

- `LAUNCHER` + `HOME`
- Shortcut pin/install intents

### Other activities

| Activity | Purpose |
|----------|---------|
| `ExternalDisplayActivity` | Secondary display UI; manifest category **`SECONDARY_HOME`** |
| `SettingsActivity`, `FirstTimeSetupActivity` | Onboarding |
| `ScrapeActivity` | Metadata scraping UI |
| `ThemeStoreActivity`, `ThemePickerActivity`, `WallpaperActivity` | Theming |
| `ESDEMigrationActivity` | Import from ES-DE |
| `NowPlayingSettingsActivity`, `HeroSettingsActivity`, etc. | UX tuning |
| `com.discord.socialsdk.AuthenticationActivity` | Discord OAuth |

### Services

| Service | Purpose |
|---------|---------|
| `CocoonInputMethodService` | Custom IME / input injection |
| `FlutterkeyOverlayService` | Overlay (Flutterkey integration) |
| `CocoonInputInjector` | Input routing to emulators |
| `com.discord.socialsdk.ForegroundService` | Discord SDK background |

### Receivers

- `ShortcutReceiver` — `INSTALL_SHORTCUT`

### Permissions (notable)

- `MANAGE_EXTERNAL_STORAGE`, legacy storage R/W
- `KILL_BACKGROUND_PROCESSES`, `REORDER_TASKS`, `PACKAGE_USAGE_STATS`
- `SYSTEM_ALERT_WINDOW`
- `RECORD_AUDIO`, foreground service types for microphone + media playback
- `BLUETOOTH_CONNECT` (API 31+)

## Native libraries

| Library | Role |
|---------|------|
| `librcheevos.so` | RetroAchievements client |
| `libdiscord_*` | Discord Social SDK |
| `libc++_shared.so` | NDK runtime |

## Package structure (DEX)

```
rip.moth.cocoonshell/
├── CocoonApp
├── MainActivity
├── ExternalDisplayActivity
├── ShortcutReceiver
├── data/
│   ├── local/CocoonDatabase
│   ├── model/ Game, Folder, Platform, Player, Widget, WidgetColumn, RomFolder
│   └── api/ RetroAchievementsHelper, RCHash
├── domain/scraping/ ScrapeConfig
├── service/ CocoonInputInjector
├── ui/
│   ├── activity/* (settings, scrape, themes)
│   ├── keyboard/CocoonInputMethodService
│   └── theme/ SmartFolderAssets, ThemeColorScheme
└── utils/ DiscordRichPresenceManager, LayoutBackup
```

## Room schema (from DEX CREATE TABLE)

### `platforms`

Console id, name, shortname, filename regex, scraper IDs, RA console IDs, aspect ratios, revision.

### `players`

| Column | Notes |
|--------|-------|
| `intentPackage`, `intentActivity`, `intentAction` | Structured intent |
| `amStartArguments` | Daijishō-style string (parallel path) |
| `killPackageProcesses` | Force-stop emulator |
| `platformId` FK | |

### `games`

Rich metadata: URIs, scraper URLs, hashes (CRC/MD5/SHA1), RA ids, `folderId`, `launchOnExternalDisplay`, `companionAppPackage`, play stats, `gridPosition`.

### `folders`

Hierarchical library with **smart folders** (`smartFolderType`, `smartFolderQuery`), view modes, hero/logo URLs, `forceChildrenToBottomScreen`.

### `widgets` + `widget_columns`

Home screen **widget grid**:

- Types: app widgets, internal widget types (`WidgetType`, `ScreenType`)
- Grid position: column span, row span, opacity
- `providerPackage` / `providerClass` for Android AppWidgetHost
- Linked to `folderId` and `screenType` (main vs external display)

### `grid_positions`

Maps screen positions to folder/game items.

### `rom_folders`

SAF URI scan roots per platform.

### `game_sessions`

Play session tracking with `emulatorPackage`, duration.

## Emulator / foreground tracking

Log strings indicate sophisticated **focus negotiation** between Cocoon and emulators:

- `cocoonHasWindowFocus` — whether shell still has focus
- `FG_DECIDE` / `FG_RESULT` — foreground decision when returning from emulator
- `onCocoonGainedForeground` / `onCocoonLostForeground`
- `suppressSessionPaused` — play time tracking while emulator confirmed foreground
- `PACKAGE_USAGE_STATS` likely used to detect foreground app

**Wajiha relevance:** Play-time and "return from game" UX should mirror this state machine, not only `onResume()`.

## Discord integration

- `DiscordRichPresenceManager`
- Native SDK with foreground service + audio permissions
- Optional companion app packages on games (`companionAppPackage`)

## Dual display

- `ExternalDisplayActivity` (dedicated activity with `SECONDARY_HOME`, like RetroHrai / iiSU)
- `ScreenType` on widgets/folders — content can target external display
- `launchOnExternalDisplay` flag per game
- `forceChildrenToBottomScreen` on folders

## Theming

- Theme store, custom themes, smart folder assets, icon overlays
- `LayoutBackup` — export/import grid + folders

## Beta caveats

- versionCode `1` — unstable API
- Large dependency footprint (Discord)
- No public source; behavior may change

## Suggested Wajiha takeaways

1. **Widget grid model** (`widgets`, `widget_columns`, `grid_positions`) for a customizable HOME
2. **Smart folders** query model for dynamic collections
3. **Game session + focus tracking** for accurate play time
4. **ExternalDisplayActivity** pattern as alternative to RetroHrai/NeoStation approaches
5. **Room schema** for `games`/`players`/`platforms` is a concrete DB blueprint

Do **not** bundle Discord SDK unless product requires it — permissions and APK size are significant.
