# Debug tooling (Thor / agents)

Debug hooks are **adb broadcasts + logcat** only — no in-app dev screen. The `DebugBroadcastReceiver` is compiled into **debug APKs only** (`androidApp/src/debug/`).

## Logging model

Logs use **kinds** (`WajihaLogKind`) and **levels** (`DEBUG` / `INFO` / `WARN`):

| Kind | Tag | Production | Debuggable (`verbose`) |
|------|-----|------------|------------------------|
| INPUT | `Wajiha/Input` | WARN only | DEBUG key / trigger edges |
| LAUNCH | `Wajiha/Launch` | INFO+ | full |
| WINDOW | `Wajiha/Window` | INFO+ | + DEBUG focus noise |
| DISPLAY | `Wajiha/Display` | INFO+ | + DEBUG reclaim ticks |
| SETTINGS | `Wajiha/Settings` | INFO+ | preference writes |
| NETWORK | `Wajiha/Network` | INFO+ | scraper/RA HTTP (URLs redacted) |
| WORK | `Wajiha/Work` | INFO+ | workers / trim / poll sample |
| SESSION | `Wajiha/Session` | INFO+ | Now Playing lifecycle |
| LIBRARY | `Wajiha/Library` | INFO+ | scan details |
| DEBUG | `Wajiha/Debug` | INFO+ | adb dump responses |

- **No file sink** — logcat ring buffer only (no storage growth).
- Messages truncated (~400 chars on logcat); scrape API viewer keeps fuller URLs.
- High-frequency kinds (`INPUT`, poll samples) are rate-limited.
- Scraper **API logs** button (Settings → Scraper) opens an in-app viewer of recent HTTP
  calls with full URLs (secrets → `***`), status, timing, and body size. Also on logcat
  as `Wajiha/Network`.
- `WajihaLogGate.verbose` is set from `ApplicationInfo.FLAG_DEBUGGABLE` at startup.
- Kill a kind's INFO/DEBUG in production: `WajihaLogGate.setKindEnabled(WajihaLogKind.DISPLAY, false)` (WARN still emits).
- Force a kind on (all levels): `WajihaLogGate.setKindEnabled(WajihaLogKind.INPUT, true)`.

Renames: `Wajiha/Gamepad`→`Input`, `Wajiha/NowPlaying`→`Session`, `Wajiha/Scrape`→`Network`.

```bash
# Typical Thor debug filter
adb logcat -s \
  Wajiha/Launch:I Wajiha/Session:I Wajiha/Input:D \
  Wajiha/Window:D Wajiha/Display:D Wajiha/Settings:I \
  Wajiha/Network:I Wajiha/Work:I Wajiha/Debug:I
```

## adb commands

```bash
# Dump session state (top display, featured, all active, suppress list)
adb shell am broadcast -a com.wajiha.DEBUG_DUMP_SESSIONS

# Force a full monitor refresh (re-poll usage stats, rediscover sessions)
adb shell am broadcast -a com.wajiha.DEBUG_REFRESH_SESSIONS

# Clear Y-close rediscovery suppress list
adb shell am broadcast -a com.wajiha.DEBUG_CLEAR_SUPPRESS

# Simulate external foreground package (no real launch)
adb shell am broadcast -a com.wajiha.DEBUG_SIMULATE_FOREGROUND --es package com.retroarch

# Flip primary ↔ secondary gamepad owner (dual display)
adb shell am broadcast -a com.wajiha.DEBUG_TOGGLE_GAMEPAD_OWNER

# Dump gamepad owner / device registry snapshot
adb shell am broadcast -a com.wajiha.DEBUG_DUMP_GAMEPAD
```

## Suppress list

When the user Y-closes a session grid tile, the package is added to `suppressRediscoveryUntil` so a lingering emulator process is not immediately re-detected. Use `DEBUG_CLEAR_SUPPRESS` to reset during testing.

## Agent workflow

See `.cursor/skills/mobile-mcp-thor/SKILL.md` for the full Thor test loop (install debug APK, dump sessions, verify dual-display).
