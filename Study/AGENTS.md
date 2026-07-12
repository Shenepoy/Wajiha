# Agent guide — Wajiha study & implementation

## Project context

**Wajiha** (`/home/zyzto/Documents/Code/Wajiha`) is a **Kotlin Multiplatform + Compose** dual-screen Android launcher (`composeApp` + `androidApp`). It is **feature-complete on Android** for Thor-style handhelds; iOS targets are scaffolding only. Target product:

- Android **HOME launcher** and device shell
- **Emulation frontend** optimized for **AYN Thor** (top + bottom displays)
- Must degrade gracefully on **single-display** handhelds / phones

Authoritative implementation docs: root `README.md`, `docs/architecture.md`, `docs/sessions.md`, `docs/gamepad.md`.

Do **not** assume iOS/desktop parity for launcher features; Android is the primary platform.

## Study folder rules

1. **Treat `Study/` as read-only reference** unless the user asks to update docs or refresh APK extracts.
2. **`neostation-frontend/`** is GPL-3.0 — copy patterns, not large code blocks, into Wajiha without license compliance review.
3. **`Daijishou/`** clone may be empty; use GitHub (`TapiocaFox/Daijishou`) for platform JSON assets and docs.
4. **APKs are closed source** — infer architecture from manifest + dex strings + assets; do not treat decompiled output as reusable code.
5. User rule: **do not run the project** — assume dev environment is already running.

## Where to look for what

| Need | Primary reference |
|------|-------------------|
| Dual-display Flutter engine + Presentation | `neostation-frontend/android/.../MainActivity.kt`, `SecondaryAppsPresentation.kt` |
| Secondary UI state sync | `neostation-frontend/lib/models/secondary_display_state.dart` |
| Emulator intent launch + SAF | `neostation-frontend/android/.../EmulatorLauncher.kt` |
| JSON-driven emulator configs | `neostation-frontend/lib/services/launcher_service.dart`, `assets/systems/` |
| Daijishou platform/player model | `docs/08-platform-json-schema.md`, RetroHrai `assets/platforms/*.json` |
| Native Compose dual-screen HOME | RetroHrai `SecondaryDisplayActivity`; **iiSU** `SecondaryHomeActivity` + `DualDisplayStateMachine` |
| Dual-display power UX (Thor) | **iiSU** `blackOutUnusedDisplayOnLaunch`, `LauncherKeepAliveService` — `docs/09-iisu-apk.md` |
| iiSU emulator JSON (173 consoles) | `Study/_extracted/iisu/assets/emuladores_default.json` |
| Widget grid + smart folders | Cocoon APK Room schema (`folders`, `widgets`, `grid_positions`) |
| RetroAchievements | All study apps integrate RA; NeoStation has fullest open-source implementation |

## Wajiha module map

```
androidApp/          # Android application shell, manifest, themes
composeApp/          # Shared Compose UI (commonMain + androidMain)
Study/               # Reference material (this tree)
```

## When implementing a feature

1. Read the comparison matrix (`docs/01-comparison-matrix.md`) to pick the closest reference.
2. For Thor dual-screen, read `docs/07-dual-display-handhelds.md` before designing UI split.
3. For game launch, read `docs/06-emulator-launch-patterns.md` and mirror NeoStation's permission-grant strategy on Android.
4. Prefer **Compose Multiplatform** in `composeApp`; put Android-only APIs (DisplayManager, HOME intent, SAF) in `androidApp` or bind plain interfaces in `com.wajiha.platform` via Koin (not `expect/actual`).

## Updating this study

After adding a new reference APK or repo:

1. Add row to `INDEX.json` and `README.md`
2. Add or extend a doc under `docs/`
3. Extract APK to `_extracted/<name>/` and record package id, version, key permissions
