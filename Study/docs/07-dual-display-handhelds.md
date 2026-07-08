# Dual-display handheld patterns

Focus: **AYN Thor** and similar Android devices with **two physical displays**.

## Device model

```text
┌─────────────────────┐  displayId = 0 (DEFAULT)
│   Main content      │  Games, platforms, settings
│   (landscape)       │
└─────────────────────┘
┌─────────────────────┐  displayId ≠ 0
│   Secondary UI      │  Touch, dock, now playing, controls
│   (often portrait)  │
└─────────────────────┘
```

Android APIs:

- `DisplayManager.getDisplays()`
- `ActivityOptions.makeBasic().setLaunchDisplayId(id)`
- `Presentation` for system-managed secondary window
- `SECONDARY_HOME` category for secondary launcher Activity

## Three reference architectures

### A. NeoStation — Presentation + second Flutter engine

| Piece | Implementation |
|-------|----------------|
| Base class | `MultiDisplayFlutterActivity` (`sub_screen` package) |
| Secondary UI | `FlutterPresentation` → Dart entry `subDisplay` |
| State sync | `SharedStateManager` + `SecondaryDisplayStateData` JSON |
| App dock on bottom | `SecondaryAppsPresentation` + `launchPackageOnSecondaryDisplay` |
| Hide bottom | SQLite `user_config.hide_bottom_screen` |

**Pros:** Rich secondary UI (video, RA panel); shared state well documented in tests.  
**Cons:** Two Flutter engines — memory/CPU; KMP won't use Flutter directly.

**Wajiha mapping:** Use **two Compose contexts** or `Presentation` with ComposeView instead of second Flutter engine; keep SharedState pattern in Kotlin.

### B. RetroHrai — Secondary Activity (`SECONDARY_HOME`)

| Piece | Implementation |
|-------|----------------|
| Main | `MainActivity` (HOME) |
| Secondary | `SecondaryDisplayActivity` (`taskAffinity` = `.secondary`) |
| Manager | `DualScreenManager`, `DualScreenPreferences` |
| Mirror mode | `MirrorProjectionCaptureService` (MediaProjection) |
| Widgets | `DualScreenExternalWidgetBoardSection` |

Manifest intent filter on secondary:

```xml
<category android:name="android.intent.category.SECONDARY_HOME" />
```

**Pros:** Idiomatic Android; fits Jetpack Compose single-stack per display.  
**Cons:** Two activity back stacks to coordinate; mirror mode needs user consent.

**Wajiha mapping:** **Recommended primary approach** for Compose Multiplatform on Android.

### C. Cocoon — ExternalDisplayActivity

| Piece | Implementation |
|-------|----------------|
| Secondary | `ExternalDisplayActivity` + `SECONDARY_HOME` |
| Content routing | `ScreenType` on widgets/folders; `launchOnExternalDisplay` on games |
| Folders | `forceChildrenToBottomScreen` |

**Pros:** Explicit content routing per item.  
**Cons:** Less documentation (APK only).

## Launching apps on secondary display

NeoStation pattern (`MainActivity.kt`):

```kotlin
val options = ActivityOptions.makeBasic()
    .setLaunchDisplayId(secondaryDisplay.displayId)
startActivity(intent, options.toBundle())
// Hide Presentation overlay so app is visible
subScreenPresentation?.hide()
```

Restore presentation in `onResume()` when dock app closes; optional `AccessibilityService` watches foreground package.

## State that must sync across displays

From NeoStation `SecondaryDisplayStateData`:

| Field | Purpose |
|-------|---------|
| `systemName`, game media paths | Artwork on bottom |
| `nowPlayingActive` | In-game panel |
| `deviceScreenOn` | Pause play timer when screen off |
| `hideBottomScreen` | User pref |
| RA achievements | Badge grid on secondary |
| `screenshotTrigger` | Request main screen capture |

Use a **single source of truth** (DataStore + Flow, or shared ViewModel with display-scoped UI).

## Single-display fallback

| Condition | Behavior |
|-----------|----------|
| `displays.size == 1` | Skip secondary Activity/Presentation |
| User disables bottom screen | Don't start secondary; merge dock into main |
| Secondary disconnected | `DisplayListener.onDisplayRemoved` → collapse UI |

NeoStation: `isSecondaryDisplayHiddenInDb()` checked before `onLaunchSubScreen`.

## Gamepad input

- Only **primary** display should receive gamepad for launcher navigation while browsing
- Block input when emulator foreground (all references)
- BACK often consumed entirely on launcher (`MainActivity.dispatchKeyEvent`)

## Brightness / system control

RetroHrai: `brightnessAffectsSecondaryDisplay` — bottom screen brightness tied to slider.

Cocoon: system stats widgets (inferred).

Wajiha system control scope: document separately; start with brightness + default launcher settings.

### C. iiSU — SecondaryHome + state machine + power UX

| Piece | Implementation |
|-------|----------------|
| Secondary | `SecondaryHomeActivity` + `SECONDARY_HOME` |
| Coordinator | `DualDisplayStateMachine`, `DualDisplayPresentation` |
| During game | `LauncherKeepAliveService` + optional `blackOutUnusedDisplayOnLaunch` |
| Recovery | `StartupSafeModeActivity` as HOME entry |

**Pros:** Best reference for **turning off unused Thor panel** during emulation; keep-alive restore after game exit.  
**Cons:** APK only; minSdk 30; Discord-heavy.

## Recommended Wajiha architecture

```text
androidApp/
  MainActivity.kt          # HOME, display 0, gamepad hub
  SecondaryActivity.kt     # SECONDARY_HOME, display 1
  DisplayCoordinator.kt    # DisplayManager listener, fallback logic

composeApp/
  commonMain/              # Shared UI; param isDualDisplay
  androidMain/             # Display-specific entry points if needed
```

```kotlin
interface DisplayCoordinator {
    val hasSecondaryDisplay: Boolean
    val secondaryDisplayId: Int?
    fun launchOnSecondary(intent: Intent)
    fun setSecondaryContentVisible(visible: Boolean)
}
```

## Testing without Thor hardware

- Android emulator with **dual display** enabled (API 29+ simulated displays)
- Verify `DisplayManager` reports >1 display
- Test fallback when only one display present

## Files to read

| Reference | Path |
|-----------|------|
| NeoStation coordinator | `neostation-frontend/android/.../MainActivity.kt` |
| NeoStation secondary UI | `neostation-frontend/lib/screens/secondary_screen/secondary_screen.dart` |
| NeoStation state model | `neostation-frontend/lib/models/secondary_display_state.dart` |
| RetroHrai manifest | `apkanalyzer manifest print Study/RetroHrai-v0.5.1.apk` |
| Cocoon external | `rip.moth.cocoonshell.ExternalDisplayActivity` in manifest |
