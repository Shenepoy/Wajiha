# Platform / player JSON schema

Shared vocabulary across **Daijishō**, **RetroHrai**, **Cocoon** (Room), and **NeoStation** (variant).

## Daijishō / RetroHrai file format

One JSON file per platform, typically `assets/platforms/<Name>.json`.

### Root object

| Field | Type | Description |
|-------|------|-------------|
| `databaseVersion` | int | Schema version for migrations |
| `revisionNumber` | int | Platform pack revision |
| `platform` | object | Console metadata |
| `playerList` | array | Emulator launch profiles |

### `platform` object

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Display name ("Atari 2600") |
| `uniqueId` | string | Stable id (`atari2600`) |
| `shortname` | string | Short id |
| `description` | string? | Optional |
| `acceptedFilenameRegex` | string | ROM filter at platform level |
| `scraperSourceList` | string[] | `LIBRETRO:...`, `DSESS:...`, ScreenScraper ids |
| `boxArtAspectRatioId` | int | Aspect ratio enum |
| `screenAspectRatioId` | int | |
| `useCustomBoxArtAspectRatio` | bool | |
| `customBoxArtAspectRatio` | float? | |
| `retroAchievementsConsoleIdList` | int[] | RA console ids |
| `extra` | string | Opaque extension field |

### `playerList[]` object

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Display label |
| `uniqueId` | string | Stable player id |
| `description` | string? | |
| `acceptedFilenameRegex` | string | Per-player ROM filter |
| `amStartArguments` | string | **Primary launch spec** (am start style) |
| `killPackageProcesses` | bool | Force-stop package before launch |
| `killPackageProcessesWarning` | bool | Show warning |
| `extra` | string | |

### Example (minimal)

See `Study/_extracted/retrohrai/assets/platforms/Atari2600.json` or run:

```bash
head -60 Study/_extracted/retrohrai/assets/platforms/Atari2600.json
```

## Cocoon Room equivalent

Cocoon normalizes into SQLite instead of loose JSON files at runtime, but fields align:

**`platforms`:** `id`, `name`, `shortname`, `acceptedFilenameRegex`, `retroAchievementsConsoleIds`, aspect fields

**`players`:** `id`, `platformId`, `intentPackage`, `intentActivity`, `intentAction`, `amStartArguments`, `killPackageProcesses`

Games reference `platformId` + optional `playerId`.

## NeoStation variant

Files under `neostation-frontend/assets/systems/*.json`:

```json
{
  "system": {
    "id": "snes",
    "name": "Super Nintendo",
    "folder": "snes",
    "extensions": ["smc", "sfc", "zip"]
  },
  "emulators": [
    {
      "unique_id": "retroarch_snes9x",
      "name": "RetroArch snes9x",
      "package": "com.retroarch.aarch64",
      "activity": "com.retroarch.browser.retroactivity.RetroActivityFuture",
      "data": "{file.localuri}",
      "extras": [
        { "key": "LIBRETRO", "value": "snes9x", "type": "string" },
        { "key": "CONFIGFILE", "value": "/storage/.../retroarch.cfg", "type": "string" }
      ],
      "keep_saf_uri": false,
      "activity_flags": ["clear-top"]
    }
  ]
}
```

Keys: `emulators` or `players`; `unique_id` or `uniqueId`.

## Mapping: Daijishō → NeoStation/Wajiha structured

| Daijishō | Structured equivalent |
|----------|----------------------|
| `-n pkg/activity` | `package` + `activity` |
| `-a action` | `action` |
| `-d path` | `data` |
| `-e KEY value` | `extras[]` |
| `--activity-clear-top` | `activity_flags: ["clear-top"]` |
| `{file.path}` | resolved absolute path |
| `{file.uri}` | content URI with grants |

Parser implementation reference: RetroHrai `AmStartArgumentsParser` (APK); NeoStation `LauncherService` + `EmulatorLauncher.kt`.

## Scraper source prefixes

| Prefix | Meaning |
|--------|---------|
| `LIBRETRO:<system>` | Libretro thumbnail database |
| `DSESS:<type>:TAGS(key):url...` | HTML scrape chain (Daijishō DSESS) |
| ScreenScraper ids | Via separate API (NeoStation) |

## Wajiha internal model (proposed)

```kotlin
data class Platform(
    val id: String,
    val displayName: String,
    val acceptedExtensions: List<String>,
    val scraperSources: List<ScraperSource>,
    val raConsoleIds: List<Int>,
    val players: List<Player>,
)

data class Player(
    val id: String,
    val displayName: String,
    val acceptedRegex: Regex,
    val launch: LaunchSpec,
    val killBeforeLaunch: Boolean,
)

sealed class LaunchSpec {
    data class AmStart(val arguments: String) : LaunchSpec()
    data class Intent(val intent: StructuredIntent) : LaunchSpec()
}
```

Import pipeline:

1. Load Daijishō JSON (RetroHrai assets for tests)
2. Parse `amStartArguments` → `StructuredIntent`
3. Persist to Room/SQLDelight
4. At launch, run Android permission grant layer ([06-emulator-launch-patterns.md](./06-emulator-launch-patterns.md))

## Where to get platform packs

| Source | Location |
|--------|----------|
| Daijishō GitHub | `Study/Daijishou/platforms/` |
| RetroHrai APK | `Study/_extracted/retrohrai/assets/platforms/` (128 JSON + stubs/tooling) |
| NeoStation | `neostation-frontend/assets/systems/` |
| **iiSU APK** | `Study/_extracted/iisu/assets/emuladores_default.json` (**173 consoles**, iiSU-native schema) |

## iiSU format (third schema)

Not Daijishō-compatible. See `docs/09-iisu-apk.md`. Uses `consoles[]` with `emulators[].commands[]` and `%ROM%` / `%PACKAGE%` placeholders.

## Gamelist assets (RetroHrai)

Arcade platforms ship huge name lists:

- `assets/gamelists/MAME gamelist.txt`
- `assets/gamelists/fbneonames.xml`

Used for title matching / scraping — optional for Wajiha v1.
