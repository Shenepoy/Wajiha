# RetroHrai APK analysis

**File:** `Study/RetroHrai-v0.5.1.apk` (~28 MB)  
**Extracted:** `Study/_extracted/retrohrai/`  
**Package:** `com.retrohrai.launcher`  
**Version:** 0.5.1 (versionCode 51)  
**SDK:** min 26, target 36  
**Stack (inferred):** Kotlin, Jetpack Compose, Room, Hilt/Dagger, WorkManager, Firebase Analytics, Google Ads

## Role in ecosystem

RetroHrai is a **native Compose** retro launcher that:

- Registers as **HOME** launcher
- Implements **dual-screen** UX for clamshell Android devices
- Bundles **Daijishō-compatible platform JSON** (`assets/platforms/` — 128 JSON files, 131 directory entries including stubs/tooling)
- Ships **MAME/FBNeo gamelist** assets under `assets/gamelists/`

Closest **closed-source** counterpart to what Wajiha might look like if built purely in Compose on Android.

## Manifest highlights

### Launcher activities

| Activity | Purpose |
|----------|---------|
| `com.retrohrai.launcher.MainActivity` | Primary UI; `LAUNCHER` + `HOME` |
| `com.retrohrai.launcher.SecondaryDisplayActivity` | Secondary screen; `SECONDARY_HOME`; separate task affinity `com.retrohrai.launcher.secondary` |
| `com.retrohrai.launcher.ShortcutPinConfirmationActivity` | Pin shortcuts |

### Services

| Service | Purpose |
|---------|---------|
| `ScrapingForegroundService` | Long-running metadata scrape |
| `MirrorProjectionCaptureService` | Media projection — mirror main screen to secondary |
| `FloatingOverlayService` | System overlay UI |
| `HotkeyAccessibilityService` | Global hotkeys via accessibility |
| `MediaNotificationListenerService` | Now playing / media info on secondary |

### Notable permissions

- `SYSTEM_ALERT_WINDOW`, `WRITE_SETTINGS`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `FOREGROUND_SERVICE_DATA_SYNC`
- Large `<queries>` block listing emulators (RetroArch variants, ExPlusAlpha, DuckStation, Winlator, etc.)

Also queries `me.magnum.melondualds` (melonDS dual-screen emulator).

## Package structure (from DEX strings)

```
com.retrohrai.launcher/
├── MainActivity
├── SecondaryDisplayActivity
├── RetroHraiApp
├── navigation/
├── ui/
│   ├── home/              # HomeScreenState, gamepad actions, brightness
│   ├── dualscreen/        # DualScreenMirrorSettings, external widgets
│   └── designsystem/
├── data/
│   ├── dao/               # RomDao (large generated impl)
│   ├── entities/          # Room entities
│   ├── roms/              # CueFileReader, M3uFileReader, CrcCalculator
│   ├── scraping/
│   └── retroachievements/
├── domain/usecase/
├── managers/
│   ├── DualScreenManager
│   └── WallpaperSlot
├── launcher/
│   └── UnifiedEmulatorLauncher
└── services/
```

Key classes for Wajiha study:

- **`DualScreenManager`** — connection state, enable/disable dual mode
- **`UnifiedEmulatorLauncher`** — parses `amStartArguments`, launches emulators
- **`AmStartArgumentsParser`** — Daijishō argument string → Intent
- **`SecondaryDisplayActivity`** — large Compose UI (~28k+ lines referenced in strings)

## Room database (inferred schema)

Core tables extracted from DEX `CREATE TABLE` strings:

| Table | Purpose |
|-------|---------|
| `platforms` | Console definitions, overlays, backgrounds, scraper IDs |
| `platform_emulators` | Package + core per platform |
| `roms` | ROM files (composite key with platformId) |
| `rom_folders` | Scan paths |
| `game_media` | Covers, fanart, logos per ROM |
| `external_media_sources` | SAF trees for media |
| `platform_view_config` | Per-platform carousel/grid/list UI prefs |
| `user_collections` / `collection_games` | User collections |
| `ra_*` | RetroAchievements cache, links, achievements |
| `android_app_overrides` | Per-platform Android app enablement |

## Bundled assets

```
assets/platforms/<PlatformName>.json   # Daijishō-format platform defs
assets/gamelists/                      # MAME, FBNeo, CPS*, Atomiswave name lists
```

Use these JSON files directly when testing Wajiha's platform importer.

## Dual-screen behavior (inferred)

From class/method names:

- `isDualScreenAvailable`, `isDualScreenEnabled`, `onDualScreenEnabledChanged`
- `brightnessAffectsSecondaryDisplay` — brightness slider can drive bottom panel
- `DualScreenExternalWidgetBoardSection` — widgets on external display
- `MirrorProjectionCaptureService` + `MirrorProjectionFrameBus` — optional screen mirror mode (user grants media projection)
- `DualScreenNowPlayingNotificationAccessCard` — reads media notifications for now-playing art

**Contrast with NeoStation:** RetroHrai uses a **second Activity** on the secondary display rather than a Presentation + second Flutter engine. Simpler for pure Compose; Wajiha KMP can follow this pattern in `androidApp`.

## Emulator launch

- Players use **`amStartArguments`** strings (Daijishō format)
- **`killPackageProcesses`** supported
- **`UnifiedEmulatorLauncher`** centralizes launch; injected via Hilt

## Ads / analytics

Firebase + AdMob integrated — not relevant to Wajiha unless product requires monetization; note for UX (interstitial risk on launch flows).

## Limitations of APK-only analysis

- No readable source for UI composition details
- Obfuscated/minified class names in places
- Use alongside **NeoStation open source** for launch intent edge cases

## Suggested Wajiha takeaways

1. Mirror manifest pattern: `SecondaryDisplayActivity` + `SECONDARY_HOME`
2. Import RetroHrai `assets/platforms/*.json` as test fixtures
3. Study `UnifiedEmulatorLauncher` / `AmStartArgumentsParser` behavior by comparing launched intents with NeoStation's `EmulatorLauncher.kt`
4. Room schema is a solid reference for Wajiha's Android DB design
