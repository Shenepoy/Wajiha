---
name: mobile-mcp-thor
description: >-
  Automate Wajiha UI testing on the AYN Thor via Mobile MCP (user-Mobile MCP).
  Use when testing Wajiha on Thor, verifying session grid / dual-display behavior,
  gamepad flows, or any Thor UI automation. Covers device targeting, MCP tool
  workflow, dual-display caveats, and adb fallbacks.
---

# Mobile MCP — Thor (Wajiha)

## When to use

- Testing **Wajiha** (`com.wajiha`) on the **AYN Thor** (Android 13)
- Verifying **session grid**, multi-session, game launch, or gamepad chrome
- UI automation when the app is already running (user rule: do not build/run the project)
- Any task mentioning **Mobile MCP**, **Thor**, or **10.0.0.174**

## Setup

| Item | Value |
|------|-------|
| MCP server | `user-Mobile MCP` (configured in `~/.cursor/mcp.json` via `npx -y @mobilenext/mobile-mcp@latest`) |
| Thor device ID | `10.0.0.174:39537` |
| Wajiha package | `com.wajiha` |

**Always pass `"device": "10.0.0.174:39537"` on every Mobile MCP call.**

Before calling tools, read the tool schema from the MCP descriptors folder.

## Standard workflow

```
Task progress:
- [ ] List devices (confirm Thor online)
- [ ] Launch or foreground Wajiha
- [ ] List elements (or screenshot) on focused display
- [ ] Interact: tap coordinates, DPAD, or swipe
- [ ] Re-list elements / screenshot to verify
```

### 1. Confirm device

`mobile_list_available_devices` — expect `10.0.0.174:39537`. If missing, stop; do not guess another device.

### 2. Launch Wajiha

```json
{ "device": "10.0.0.174:39537", "packageName": "com.wajiha" }
```

Use `mobile_launch_app`. To cold-start, `mobile_terminate_app` first, then launch.

### 3. Inspect UI

Prefer **`mobile_list_elements_on_screen`** — returns text/labels and pixel coordinates. **Do not cache**; re-list after every action.

Use **`mobile_take_screenshot`** when hierarchy is sparse (Compose) or to confirm visual state. Also **focused display only** (see dual-display).

### 4. Interact

| Goal | Tool |
|------|------|
| Tap element | `mobile_click_on_screen_at_coordinates` with `x`, `y` from list_elements |
| Gamepad nav | `mobile_press_button` — `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`, `DPAD_CENTER`, `BACK`, `HOME` |
| Long press / double tap | `mobile_long_press_on_screen_at_coordinates`, `mobile_double_tap_on_screen` |
| Scroll | `mobile_swipe_on_screen` |

### 5. Verify

Re-run `mobile_list_elements_on_screen` or `mobile_take_screenshot` after each interaction.

## Thor dual-display

Thor has **two physical screens**. Mobile MCP only sees the **currently focused display**.

| Display | Role | Typical content |
|---------|------|-----------------|
| **0** (top) | Game / emulator | Running game, featured session |
| **4** (bottom) | Launcher | Wajiha home grid, session grid, gamepad hints |

Implications:

- `mobile_list_elements_on_screen` and `mobile_take_screenshot` show **one display at a time**
- Bottom grid / session tiles live on **display 4** when Wajiha owns the launcher
- Top screen shows the active game on **display 0**
- Foreground on the bottom launcher during a game session is **expected** — session is not ended

### Switching focus between displays

1. Launch/switch apps so the target display is focused, then list elements
2. Use gamepad: `SELECT` swaps displays in Wajiha (see gamepad hints)
3. If MCP taps miss on the bottom grid, use **adb display fallback** (below)

## Wajiha-specific patterns

### Session grid (bottom display)

- Lives in `BottomScreen` / `SessionGridTile` — tiles for active emulator sessions
- Tiles use the same **3:4 GamepadTile** shell as game tiles
- Labels come from `gameName` → `appLabel` → `packageName`
- In accessibility tree, look for **ImageView** nodes with game/session labels
- Green dot = session running on **top display** (`SessionOnTopGreen`)

**Session-focused gamepad hints** (action bar): A Switch, Y Close, Up/Down Sessions, Right Games, B Back, SELECT Swap.

**Library-focused hints**: A Launch, X Menu, B Back, L1/R1 Filter, SELECT Swap.

Navigate with `mobile_press_button` DPAD keys when coordinates are unreliable.

### Game tiles

- Platform-filtered grid of games; tiles are ImageViews with game title text
- A = launch, X = context menu, L1/R1 = cycle platform filter

### Multi-session tests

1. **Kill stray emulators** before starting — cached sessions pollute the grid
2. Launch game A → verify session tile appears
3. Launch game B → verify two tiles; Up/Down cycles focus
4. A on focused session = switch to that session; Y = close session
5. Confirm top display shows the switched game (may need focus on display 0 + screenshot)

Use `mobile_terminate_app` with emulator package names to clean up between runs.

### Chrome / layers

- `WajihaScreen` action bar shows **gamepad hints** — useful sanity check in screenshots
- Context menu, platform filter, and session focus change which hints appear
- `layerId` `home_grid` = main bottom launcher

## Prefer Mobile MCP over blind adb

**Default: Mobile MCP** — structured element list, coordinates, and screenshots beat guessing tap positions.

Use **adb fallback** only when:

- Taps must target **display 4** explicitly and MCP keeps hitting display 0
- `input keyevent` for keys Mobile MCP does not expose
- Killing background emulator processes

```bash
# Tap on bottom display (display 4)
adb -s 10.0.0.174:39537 shell input -d 4 tap <x> <y>

# Key events (gamepad mapping)
adb -s 10.0.0.174:39537 shell input keyevent <KEYCODE>
```

Get `x`, `y` from `mobile_list_elements_on_screen` while display 4 is focused; if listing while wrong display is focused, re-focus first or estimate from a display-4 screenshot.

## Debug broadcasts (debug APK only)

Wajiha debug builds register a `DebugBroadcastReceiver` for agent-driven session inspection. Output goes to logcat tag **`Wajiha/Debug`**.

**Requires a debug APK** (`installDebug`) — release builds omit the receiver.

```bash
ADB="adb -s 10.0.0.174:39537"
PKG="com.wajiha"

# Dump activeSessions, topDisplay, featured session, suppressRediscovery list
$ADB shell am broadcast -a com.wajiha.DEBUG_DUMP_SESSIONS -p $PKG

# Force ForegroundAppMonitor poll + top-display refresh + session discovery
$ADB shell am broadcast -a com.wajiha.DEBUG_REFRESH_SESSIONS -p $PKG

# Clear Y-close rediscovery suppression (useful after killApp / stale tiles)
$ADB shell am broadcast -a com.wajiha.DEBUG_CLEAR_SUPPRESS -p $PKG

# Simulate a foreground-package event (extra: package name)
$ADB shell am broadcast -a com.wajiha.DEBUG_SIMULATE_FOREGROUND -p $PKG --es package org.ppsspp.ppsspp

# Tail debug output while testing
$ADB logcat -s Wajiha/Debug:I Wajiha/NowPlaying:D
```

| Action | Purpose |
|--------|---------|
| `DEBUG_DUMP_SESSIONS` | Log session grid state without UI navigation |
| `DEBUG_REFRESH_SESSIONS` | Force monitor refresh (top display, running apps, discovery) |
| `DEBUG_CLEAR_SUPPRESS` | Clear post-close rediscovery blocks |
| `DEBUG_SIMULATE_FOREGROUND` | Inject `onForegroundPackage` for a package |

Use broadcasts **before** Mobile MCP when diagnosing session-grid / top-display mismatches — faster than gamepad navigation for state checks.

## Pre-test cleanup checklist

```
- [ ] mobile_list_available_devices — Thor present
- [ ] Terminate stray emulator packages (not just Wajiha)
- [ ] mobile_launch_app com.wajiha
- [ ] Confirm expected display focused before listing elements
```

## Common pitfalls

- **Forgot `device` param** — every call fails or hits wrong target
- **Stale element list** — always re-list after navigation
- **Wrong display** — bottom grid invisible in screenshot → focus display 4 or use `-d 4`
- **Cached emulator sessions** — old tiles skew multi-session tests; kill emulators first
- **Running the project** — assume app is already installed; do not Gradle-run unless asked

## All 23 Mobile MCP tools

`mobile_list_available_devices`, `mobile_list_apps`, `mobile_launch_app`, `mobile_terminate_app`, `mobile_list_elements_on_screen`, `mobile_click_on_screen_at_coordinates`, `mobile_long_press_on_screen_at_coordinates`, `mobile_double_tap_on_screen`, `mobile_swipe_on_screen`, `mobile_press_button`, `mobile_take_screenshot`, `mobile_save_screenshot`, `mobile_get_screen_size`, `mobile_get_orientation`, `mobile_set_orientation`, `mobile_type_keys`, `mobile_open_url`, `mobile_install_app`, `mobile_uninstall_app`, `mobile_start_screen_recording`, `mobile_stop_screen_recording`, `mobile_list_crashes`, `mobile_get_crash`
