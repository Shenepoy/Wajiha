---
name: mobile-mcp-thor
description: >-
  Automate Wajiha UI testing on the AYN Thor via PolyScreen MCP (user-polyscreen).
  Use when testing Wajiha on Thor, verifying session grid / dual-display behavior,
  gamepad flows, or any Thor UI automation. Covers device targeting, per-display
  capture/input, dual-display caveats, and install → launch.
---

# PolyScreen MCP — Thor (Wajiha)

## When to use

- Testing **Wajiha** (`com.wajiha`) on the **AYN Thor** (Android 13)
- Verifying **session grid**, multi-session, game launch, or gamepad chrome
- Dual-display capture / input (top + bottom)
- Any task mentioning **PolyScreen**, **polyscreen-mcp**, **Thor**, or **10.0.0.174**
- **Installing / updating** the debug APK on Thor

## Setup

| Item | Value |
|------|-------|
| MCP server | `user-polyscreen` (`npx -y polyscreen-mcp@latest`) |
| Thor serial | Prefer `10.0.0.174:5555` from `mobile_devices_list` (TLS mDNS serial may also appear — use the IP one) |
| Wajiha package | `com.wajiha` |
| Top display | logicalId **0** — `MainActivity` / hero |
| Bottom display | logicalId **4** — `SecondaryHomeActivity` / grid launcher |

**Always pass `serial` and the correct logical `displayId` on every call.** Never confuse logical display IDs with SurfaceFlinger physical IDs — PolyScreen resolves that.

Before calling tools, read the tool schema from GetMcpTools (`server`: `user-polyscreen`).

Fallback only if PolyScreen is offline: `user-Mobile MCP`, or adb with `screencap -d <physicalId>`.

## Install / update APK

```
Task progress:
- [ ] Confirm Thor online (`mobile_devices_list`)
- [ ] Build debug APK if needed (`:androidApp:assembleDebug`)
- [ ] Install APK (`adb install -r` or `mobile_app_install`)
- [ ] Launch Wajiha on both displays
```

1. **Build** (when sources changed):

```bash
./gradlew :androidApp:assembleDebug
```

APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

2. **Install**:

```bash
adb -s 10.0.0.174:5555 install -r -d androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Or PolyScreen: `mobile_app_install` with `serial` + APK path.

3. **Launch after install** (cold start preferred):

```json
{ "serial": "10.0.0.174:5555", "packageName": "com.wajiha" }
```

`mobile_app_stop` then:

- Display 0: `mobile_app_launch` → `com.wajiha` / `com.wajiha.android.MainActivity` / `displayId: 0`
- Display 4: `mobile_app_launch` → `com.wajiha` / `com.wajiha.android.SecondaryHomeActivity` / `displayId: 4`

If launch reports focus on the wrong display, `mobile_input_tap` the target display to reclaim focus.

Wake if captures are black: `mobile_input_key` with `key: "WAKEUP"` on display 0.

## Standard workflow

```
Task progress:
- [ ] mobile_devices_list → pick serial
- [ ] mobile_displays_list → confirm 0 + 4 ON, focused activities
- [ ] Launch / focus target display
- [ ] mobile_screen_capture per displayId (do not cache)
- [ ] Interact: mobile_input_tap / mobile_input_key / mobile_input_swipe with displayId
- [ ] Re-capture both displays to verify
```

### Preferred tools

| Goal | Tool |
|------|------|
| Devices | `mobile_devices_list` |
| Displays + focus | `mobile_displays_list` |
| Screenshot (logical) | `mobile_screen_capture` (`displayId` 0 or 4) |
| Tap | `mobile_input_tap` |
| Gamepad / D-pad | `mobile_input_key` (`source: "gamepad"`, keys like `DPAD_RIGHT`, `BUTTON_A`) |
| Swipe scroll | `mobile_input_swipe` |
| Launch / stop | `mobile_app_launch` / `mobile_app_stop` |
| UI dump | `mobile_ui_snapshot` (display 0 via ADB; other displays need companion) |

## Thor dual-display

| Display | Role | Typical content |
|---------|------|-----------------|
| **0** (top) | Game / hero | Featured session, running emulator, top chrome |
| **4** (bottom) | Launcher | Home grid, sessions, Apps, Settings |

Implications:

- Capture **each** `displayId` separately — one call never shows both
- Bottom grid lives on **4**; top hero on **0**
- Input must target the same `displayId` as the UI you intend to drive
- After gamepad SELECT swap / focus handoff, re-run `mobile_displays_list`

## Wajiha-specific patterns

### Session / game grid (display 4)

- `BottomScreen` / `SessionGridTile` — 3:4 `GamepadTile` shell
- Focus chrome uses compact GameGrid style (Inside ring, no Outside glow blobs)
- Hints: A Launch, X Menu, B Back, L1/R1 Filter, SELECT Swap

### Multi-session

1. Kill stray emulators first
2. Launch A → session tile; launch B → two tiles
3. Up/Down cycles sessions; A switches; Y closes
4. Confirm top display 0 shows the switched game via `mobile_screen_capture`

## Common pitfalls

- **Wrong serial** — two Thor entries may appear; prefer `10.0.0.174:5555`
- **Logical vs physical IDs** — always pass logical `displayId` (0 / 4) to PolyScreen
- **Installed but not launched** — always stop + launch after APK update
- **Stale capture** — re-capture after every navigation
- **Display 4 flake** — if a call says display unavailable, re-list displays and tap display 4
- **uiautomator on display 4** — portable ADB snapshot is display-0 only without companion

## Debug broadcasts (debug APK)

```bash
ADB="adb -s 10.0.0.174:5555"
PKG="com.wajiha"
$ADB shell am broadcast -a com.wajiha.DEBUG_DUMP_SESSIONS -p $PKG
$ADB shell am broadcast -a com.wajiha.DEBUG_REFRESH_SESSIONS -p $PKG
$ADB logcat -s Wajiha/Debug:I Wajiha/Session:I Wajiha/Launch:I Wajiha/Display:D
```
