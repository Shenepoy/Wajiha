# Comparison matrix

Quick reference for choosing patterns when implementing Wajiha features.

## Stack

| | NeoStation | Daijishō | RetroHrai | Cocoon | iiSU |
|---|:---:|:---:|:---:|:---:|:---:|
| UI framework | Flutter | Closed | Jetpack Compose | Jetpack Compose | Jetpack Compose |
| Source available | Full | Assets/docs | APK only | APK only | APK only |
| Min SDK | — | — | 26 | 24 | **30** |

## Launcher / shell

| Feature | NeoStation | Daijishō | RetroHrai | Cocoon | iiSU |
|---------|:---:|:---:|:---:|:---:|:---:|
| HOME launcher | Yes | Yes | Yes | Yes | Yes |
| Safe-mode / recovery entry | — | — | — | — | **StartupSafeModeActivity** |
| Back button blocked | Yes | — | — | — | — |
| Default launcher settings | MethodChannel | — | — | — | — |
| Android apps drawer | Yes | Widgets | Yes | Yes | Yes |
| Install APK | Yes | — | — | — | — |
| Widget grid / columns | — | Yes | Yes (dual widgets) | Yes (core UX) | Yes (home widgets) |
| Smart folders | — | Yes | — | Yes | — |
| Themes / wallpapers | Yes | Yes | Yes | Yes (theme store) | Yes + XMB style |
| ES-DE migration | — | — | — | Yes | — |
| XMB-style browser | — | — | — | — | **Yes** |

## Dual display

| Feature | NeoStation | Daijishō | RetroHrai | Cocoon | iiSU |
|---------|:---:|:---:|:---:|:---:|:---:|
| Second screen UI | 2nd Flutter engine (`sub_screen`) | — | `SecondaryDisplayActivity` | `ExternalDisplayActivity` | `SecondaryHomeActivity` + `DualDisplayPresentation` |
| Launch app on bottom display | `setLaunchDisplayId` | — | DualScreenManager | — | Dual display routing |
| Black out unused display on launch | — | — | — | — | **Yes** |
| Keep-alive during external app | — | — | — | — | **LauncherKeepAliveService** |
| Mirror main → secondary | — | — | MediaProjection | — | — |
| Now playing on secondary | Yes | — | Yes | Yes | Yes |
| RA on secondary | Yes | — | — | — | — |
| Hide bottom screen pref | SQLite | — | DualScreenPreferences | — | disable heroes on secondary |

## Emulation

| Feature | NeoStation | Daijishō | RetroHrai | Cocoon | iiSU |
|---------|:---:|:---:|:---:|:---:|:---:|
| Config format | NeoStation JSON | Daijishō `amStartArguments` | Daijishō JSON in APK | Room `players` | **iiSU JSON** (`%ROM%`, `%PACKAGE%`) |
| Bundled console defs | assets/systems | GitHub platforms (119) | 128 JSON (+131 dir entries) | In DB | **173 consoles** |

## Integrations

| | NeoStation | Daijishō | RetroHrai | Cocoon | iiSU |
|---|:---:|:---:|:---:|:---:|:---:|
| RetroAchievements | Yes | Yes | Yes | Yes (`rcheevos`) | Yes (`rcheevos_jni`) |
| ScreenScraper | Yes | DSESS | Yes | ScrapeActivity | Yes |
| Cloud / server ROM sync | NeoSync | — | — | — | **RomM** |
| Discord | — | — | — | Yes | Yes |
| SteamGridDB | — | — | — | — | Yes |
| Google Calendar | — | — | — | — | Yes |

## Best reference per Wajiha feature

| Wajiha feature | Start here |
|--------------|------------|
| Overall Flutter-like layering in KMP | NeoStation `ARCHITECTURE.md` (adapt to Compose) |
| Android HOME + permissions manifest | `neostation-frontend/android/app/src/main/AndroidManifest.xml` |
| Dual-screen (Compose-native) | RetroHrai + **iiSU** APK analyses |
| Dual-screen power UX (blackout + keep-alive) | **iiSU** `docs/09-iisu-apk.md` |
| Dual-screen (max documented code) | NeoStation `MainActivity.kt` + `secondary_screen.dart` |
| Platform JSON import | RetroHrai `assets/platforms/`, Daijishō GitHub |
| Intent launch edge cases | `EmulatorLauncher.kt` |
| Widget home screen | Cocoon Room `widgets` / `widget_columns` |
| Gamepad focus stack | NeoStation `game_service.dart` (`GamepadNavigationManager`) |
