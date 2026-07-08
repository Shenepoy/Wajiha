# NeoStation (open source)

**Path:** `Study/neostation-frontend/`  
**Package:** `com.neogamelab.neostation`  
**License:** GPL-3.0  
**Upstream docs:** `README.md`, `ARCHITECTURE.md`, `AI_GUIDELINES.md`

## What it is

Cross-platform **Flutter** emulation frontend (Windows, Linux, macOS, Android). Landscape-only. Not a web/iOS target. Android build is a **HOME launcher** with **dual-screen** support for clamshell/dual-display handhelds.

## Layered architecture (from ARCHITECTURE.md)

```
screens/ + widgets/     → UI
providers/              → ChangeNotifier state
services/               → APIs, launching, logging
repositories/           → abstract data access
data/datasources/       → SQLite, migrations
```

**Rule:** Services never touch SQLite directly; repositories wrap datasources.

## Key Android files

| File | Role |
|------|------|
| `android/app/src/main/AndroidManifest.xml` | HOME + LAUNCHER, storage, `QUERY_ALL_PACKAGES`, RetroArch package queries |
| `android/.../MainActivity.kt` | `MultiDisplayFlutterActivity`, method channels, SAF, secondary display |
| `android/.../SecondaryAppsPresentation.kt` | Method channel on secondary Flutter engine for app dock |
| `android/.../EmulatorLauncher.kt` | Intent construction, SAF URI grants, multi-disc ROM handling |
| `android/.../ScreenshotAccessibilityService.kt` | Secondary screen restore when dock app closes |

## Method channels (main engine)

| Channel | Methods (sample) |
|---------|------------------|
| `com.neogamelab.neostation/game` | `launchGenericIntent`, `getInstalledApps`, `launchPackage`, SAF ops, `mirrorEmulatorNand` |
| `com.neogamelab.neostation/launcher` | `isDefaultLauncher`, `openDefaultAppsSettings` |
| `com.neogamelab.neostation/secondary_display` | `setSecondaryDisplayVisible`, events `onSecondaryDisplayConnected` |

Secondary engine channel: `com.neogamelab.neostation/secondary_apps` (`getInstalledApps`, `launchAppOnSecondary`).

## Dual-display architecture

Uses package **`sub_screen`** (`MultiDisplayFlutterActivity`):

1. **Main engine** — primary UI (`main.dart`)
2. **Sub engine** — entry `subDisplay` → `SecondaryScreen` widget
3. **SharedStateManager** — JSON-serializable `SecondaryDisplayState` synced between engines
4. **FlutterPresentation** on secondary `Display`

Important behaviors in `MainActivity.kt`:

- Reads `hide_bottom_screen` from SQLite `user_config` to suppress presentation
- `pushDeviceScreenOn()` — secondary play-time timer respects screen off
- `launchPackageOnSecondaryDisplay()` — `ActivityOptions.setLaunchDisplayId`, hides presentation while app runs
- Clears stale `nowPlayingActive` on cold start

Dart model: `lib/models/secondary_display_state.dart` — fanart, video, RA panel, scraping progress, `deviceScreenOn`, etc.

## Game launch flow (Dart → Kotlin)

1. `GameService` resolves system + emulator via repositories
2. `LauncherService.getLaunchCommand()` reads JSON from `assets/systems/<system>.json`
3. Placeholders: `{file.path}`, `{file.name}`, `neostation-realpath:`, `neostation-localuri:`
4. `AndroidService.launchGenericIntent()` → `EmulatorLauncher.launchGenericIntent()`

See [06-emulator-launch-patterns.md](./06-emulator-launch-patterns.md).

## System JSON config

Bundled under `assets/systems/` (and updatable via `SystemsUpdateService`). Structure:

```json
{
  "system": { "id": "...", "name": "..." },
  "emulators": [
    {
      "unique_id": "...",
      "name": "...",
      "package": "com.retroarch.aarch64",
      "activity": "com.retroarch.browser.retroactivity.RetroActivityFuture",
      "data": "{file.localuri}",
      "extras": [
        { "key": "LIBRETRO", "value": "snes9x", "type": "string" }
      ],
      "activity_flags": ["clear-top"]
    }
  ]
}
```

NeoStation format is **similar to** Daijishō but uses structured JSON instead of a single `amStartArguments` string.

## Gamepad

- `GamepadsCompatibleActivity` on `MainActivity`
- `GamepadNavigationManager` focus stack in `game_service.dart`
- Blocks BACK and optionally all input while emulator foreground (`setGamepadBlock`)

## Database

SQLite file: `<user-data>/data.sqlite`  
Migrations: `lib/data/datasources/sqlite_migrations.dart`  
Main config table includes `hide_bottom_screen` for dual-display UX.

## Tests worth reading

- `test/secondary_display_state_test.dart` — shared state contract
- `test/game_service_test.dart` — launch / resume behavior
- `test/emulator_repository_test.dart`

## Using NeoStation for Wajiha

**Copy concepts, not code verbatim** (GPL). Highest-value ports:

1. `EmulatorLauncher` permission-grant strategy → Kotlin in `composeApp/src/androidMain`
2. Dual-display lifecycle patterns → Wajiha dual-screen module
3. Launcher manifest permission set
4. Layered data access → KMP repositories + SQLDelight/Room
