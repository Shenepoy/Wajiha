# Debug tooling (Thor / agents)

Debug hooks are **adb broadcasts + logcat** only — no in-app dev screen. The `DebugBroadcastReceiver` is compiled into **debug APKs only** (`androidApp/src/debug/`).

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
```

## Logcat tags

```bash
adb logcat -s Wajiha/Debug:I Wajiha/NowPlaying:D Wajiha/Display:D Wajiha/Launch:D
```

| Tag | Contents |
|-----|----------|
| `Wajiha/Debug` | Debug broadcast actions, `debugDumpState` output |
| `Wajiha/NowPlaying` | Session begin/end, featured vs top-display changes |
| `Wajiha/Display` | DisplayCoordinator, activity display routing |
| `Wajiha/Launch` | Emulator launch, URI grants |
| `Wajiha/ExternalResolve` | ROM reconciliation probes |

## Suppress list

When the user Y-closes a session grid tile, the package is added to `suppressRediscoveryUntil` so a lingering emulator process is not immediately re-detected. Use `DEBUG_CLEAR_SUPPRESS` to reset during testing.

## Agent workflow

See `.cursor/skills/mobile-mcp-thor/SKILL.md` for the full Thor test loop (install debug APK, dump sessions, verify dual-display).
