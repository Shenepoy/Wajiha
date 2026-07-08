# Daijishō (reference assets)

**Remote:** https://github.com/TapiocaFox/Daijishou  
**Local path:** `Study/Daijishou/` — **clone complete** (verified 2026-07-08)  
**Latest commit:** `393f2928` — *Update psx platform* (2026-07-07)

**App package (store):** `com.magneticchen.daijishou`  
**App status:** **Closed source** — this repo is assets + docs only (no Kotlin/Java app source).

## Local inventory (verified)

| Path | Contents | Size |
|------|----------|------|
| `platforms/` | **119** platform JSONs + `index.json` catalog | ~936 KB |
| `platforms/*.json.test` | **9** stub/test platforms (not in `index.json`) | — |
| `docs/` | Console, DSESS, player templates, RA status | 28 KB |
| `themes/` | Wallpaper + thumbnail packs, templates | **~6.5 GB** |
| `extension-repositories/` | Official + community extension indices | 16 KB |
| `imgs/`, `vids/`, `release-notes/` | Marketing / changelog media | ~50 MB |
| `messages/`, `messages-beacon/` | In-app message payloads | small |

### `platforms/index.json`

Canonical catalog the app uses when downloading platforms:

- `baseUri` → `https://raw.githubusercontent.com/magneticchen/Daijishou/main/platforms/`
- `platformList[]` → `filename`, `platformName`, `platformUniqueId`, `revisionNumber`

Use this index for import UI and update checks (compare `revisionNumber` per platform).

### Tooling in `platforms/`

- `generate_index.py` — rebuilds `index.json` from JSON files
- `curator.py` — platform maintenance helper

### Docs worth reading for Wajiha

| File | Topic |
|------|--------|
| `docs/daijishou_player_template.md` | `.dpt` template files for `{tags.*}` placeholders |
| `docs/dsess.md` | DSESS scraper URL syntax in `scraperSourceList` |
| `docs/daijishou_console.md` | Debug console (`\am_start`, `\dsess`, hidden prefs) |
| `docs/retro_achievements_status.md` | RA integration notes |

### Example platform (PSX)

`platforms/SonyPlayStation.json` — revision 12, DuckStation players using `{file.uri}` / `{file.path}`, RetroArch cores, RA console id `12`. Good reference for multi-emulator per platform.

### Size warning

`themes/` is **multi-GB**. Do not copy into Wajiha repo; reference or subset only what you need.

## Core concepts

### Platform

A **console/system** definition:

- Accepted filename regex
- Scraper sources (ScreenScraper, LIBRETRO, DSESS URLs)
- Aspect ratios for box art / screen
- RetroAchievements console IDs
- List of **players**

### Player

A **launch profile** for an emulator:

- `amStartArguments` — shell-style args passed to `am start`
- `acceptedFilenameRegex` — which ROM extensions this player handles
- `killPackageProcesses` — force-stop emulator when switching games
- Placeholders: `{file.path}`, `{file.uri}`, `{tags.*}` from `.dpt` template files

Example (from RetroHrai-bundled JSON, Daijishō-compatible):

```
-n com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture
 -e ROM {file.path}
 -e LIBRETRO stella
 -e CONFIGFILE /storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg
```

### Player template (.dpt)

Text file for non-ROM launch targets (Tasker tasks, Vita title IDs). First line must be `# Daijishou Player Template` or `# DST`. See upstream `docs/daijishou_player_template.md`.

### DSESS

Domain-Specific External Scraper Syntax — embedded in platform JSON `scraperSourceList` as `DSESS:...` chains for box art / metadata without ScreenScraper API.

## Platform JSON top-level shape

```json
{
  "databaseVersion": 14,
  "revisionNumber": 11,
  "platform": {
    "name": "Atari 2600",
    "uniqueId": "atari2600",
    "shortname": "atari2600",
    "acceptedFilenameRegex": "...",
    "scraperSourceList": ["LIBRETRO:...", "DSESS:..."],
    "retroAchievementsConsoleIdList": [25]
  },
  "playerList": [
    {
      "name": "atari2600 - RetroArch 64 - stella",
      "uniqueId": "atari2600.ra64.stella",
      "acceptedFilenameRegex": "^(.*)\\.(?:a26|bin|zip|7z)$",
      "amStartArguments": "-n com.retroarch.aarch64/...",
      "killPackageProcesses": true,
      "killPackageProcessesWarning": true
    }
  ]
}
```

Full schema notes: [08-platform-json-schema.md](./08-platform-json-schema.md).

## UI features (from README)

- Widget page (RSS, activity, pinned games)
- Genres, search, RetroAchievements login
- Wallpaper packs + theme colors
- Hotkey / gamepad navigation modes per section
- Pegasus config import (partial)
- Platform download + re-import without losing play records

## Relationship to other study apps

| App | Daijishō relationship |
|-----|----------------------|
| **RetroHrai** | Bundles **131** entries in `assets/platforms/` (128 `.json` + `.test` stub + tooling scripts); Daijishō-compatible format |
| **Cocoon** | Room `platforms` + `players` tables mirror same fields (`amStartArguments`, `intentPackage`, etc.) |
| **NeoStation** | Own JSON schema but same intent/extras ideas; can import similar player definitions |

## Wajiha recommendation

1. **Adopt Daijishō platform JSON** as import format (community packs, RetroHrai assets as test data)
2. Internally normalize to Wajiha types; keep `amStartArguments` parser or map to structured intents like NeoStation
3. Pull platform packs from Daijishō GitHub after `git pull` in `Study/Daijishou/`

## Upstream documentation links

- [Player template](https://github.com/TapiocaFox/Daijishou/blob/main/docs/daijishou_player_template.md)
- [DSESS](https://github.com/TapiocaFox/Daijishou/blob/main/docs/dsess.md)
- [Wiki](https://github.com/TapiocaFox/Daijishou/wiki)
