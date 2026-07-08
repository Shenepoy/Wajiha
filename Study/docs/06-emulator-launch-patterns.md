# Emulator launch patterns (Android)

How study references launch external emulators — essential for Wajiha `androidMain`.

## Pattern overview

```mermaid
sequenceDiagram
  participant UI as Launcher UI
  participant Bridge as Android bridge
  participant EMU as Emulator APK

  UI->>UI: Resolve platform + player for ROM
  UI->>Bridge: Build launch spec
  Bridge->>Bridge: Resolve file URI / path placeholders
  Bridge->>Bridge: grantUriPermission + ClipData
  Bridge->>EMU: startActivity(Intent)
  Note over UI,EMU: Launcher pauses; onResume updates play time
```

None of the study apps embed cores — all delegate to installed APKs.

## Daijishō: `amStartArguments`

Single multiline string mimicking `adb shell am start` args:

```
-n com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture
 -e ROM {file.path}
 -e LIBRETRO snes9x
 -e CONFIGFILE /storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg
 --activity-clear-top
```

**Placeholders:**

| Token | Meaning |
|-------|---------|
| `{file.path}` | Absolute filesystem path |
| `{file.uri}` | content:// or file:// URI |
| `{tags.field}` | From `.dpt` player template |

**Flags:**

| Player field | Behavior |
|--------------|----------|
| `killPackageProcesses` | `ActivityManager.killBackgroundProcesses` before launch |
| `killPackageProcessesWarning` | Confirm with user |

RetroHrai: `AmStartArgumentsParser` + `UnifiedEmulatorLauncher`.

## NeoStation: structured JSON → Intent

`LauncherService` produces a map:

```json
{
  "package": "com.retroarch.aarch64",
  "activity": "com.retroarch.browser.retroactivity.RetroActivityFuture",
  "data": "content://...",
  "extras": [
    { "key": "LIBRETRO", "value": "snes9x", "type": "string" }
  ],
  "activity_flags": ["clear-top"],
  "keep_saf_uri": false
}
```

`EmulatorLauncher.kt` converts to `Intent`.

### NeoStation-specific markers

| Marker | Use |
|--------|-----|
| `neostation-realpath:<uri>` | Resolve SAF to filesystem path; cache remote NAS to `NeoStation/rom_import` |
| `neostation-localuri:<uri>` | Keep content:// for permission grant |

### RetroArch defaults

If package starts with `com.retroarch` and no `CONFIGFILE`:

```
/storage/emulated/0/Android/data/<package>/files/retroarch.cfg
```

`LIBRETRO` relative names expanded to:

```
<app.dataDir>/cores/<name>_libretro_android.so
```

## Scoped storage / SAF (critical on Android 10+)

NeoStation `EmulatorLauncher.kt` implements production rules:

### 1. Explicit `grantUriPermission` before `startActivity`

`FLAG_GRANT_READ_URI_PERMISSION` alone can race; synchronous grant required.

### 2. FileProvider rewrap for single-file ROMs

Convert SAF `content://` → app `FileProvider` URI so emulators don't try to write `.frz` next to ROM (fails on scoped storage).

**Skip rewrap when:**

- Multi-file: `.cue`, `.gdi`, `.m3u` (sibling tracks need directory context)
- `keep_saf_uri: true` in JSON (Flycast zip loading)
- DuckStation: needs real paths for track resolution

### 3. Sibling track permissions

For cue/gdi/m3u, grant read on sibling `.bin`, `.iso`, etc. via ClipData items.

### 4. Tree / prefix grants

Grant parent SAF tree + `FLAG_GRANT_PREFIX_URI_PERMISSION` (API 26+) for subfolder ROMs.

### 5. Zip sidecar folders

For `game.zip` + `game/` folder (NAOMI), grant each file in subfolder.

## Activity flags (NeoStation JSON)

| Flag string | Intent flag |
|-------------|-------------|
| `clear-task` | `FLAG_ACTIVITY_CLEAR_TASK` |
| `clear-top` | `FLAG_ACTIVITY_CLEAR_TOP` |
| `no-history` | `FLAG_ACTIVITY_NO_HISTORY` |
| `single-top` | `FLAG_ACTIVITY_SINGLE_TOP` |

Always add `FLAG_ACTIVITY_NEW_TASK` when launching from non-Activity context.

## Common emulator packages (from manifests/queries)

| Package | Notes |
|---------|-------|
| `com.retroarch.aarch64` | 64-bit RetroArch |
| `com.retroarch` / `.ra32` / `.plus` | Variants |
| `com.github.stenzek.duckstation` | Needs real paths for multi-file |
| `com.explusalpha.*` | .emu series |
| `com.dsemu.drastic` | NDS |
| `com.cmodded.winlator` | PC games |

Wajiha manifest should include `<queries>` for target emulators (Android 11+ visibility).

## Returning from emulator

| App | Mechanism |
|-----|-----------|
| NeoStation | `onResume` → `onGameReturned` channel; `setGamepadBlock`; elapsed time |
| Cocoon | `cocoonHasWindowFocus`, usage stats, event-pair foreground winner |
| RetroHrai | Session store / dual screen state (inferred) |

Implement: block launcher gamepad input while game active; restore on resume; update play count.

## Wajiha implementation sketch

```kotlin
// composeApp/src/androidMain
expect class EmulatorLauncher {
    fun launch(spec: LaunchSpec): LaunchResult
}

data class LaunchSpec(
    val packageName: String,
    val activity: String?,
    val dataUri: String?,
    val extras: Map<String, String>,
    val flags: List<String>,
    val keepSafUri: Boolean = false,
)
```

1. Port permission logic from NeoStation `EmulatorLauncher.kt` first
2. Add Daijishō `amStartArguments` parser (see RetroHrai concept) as import path
3. Unit-test with content:// URIs and `.cue` + `.bin` pairs

## Test ROM scenarios

| Scenario | Must work |
|----------|-----------|
| Single `.sfc` on SAF | Grant + optional FileProvider |
| `.cue` + multiple `.bin` | Sibling grants or file:// for DuckStation |
| `.zip` with folder sidecar | Subfolder grants (Flycast) |
| RetroArch core name only | Expand LIBRETRO path |
| NAS / non-local SAF | Cache to public import dir |
