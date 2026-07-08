# Wajiha

A dual-screen emulation launcher, HOME replacement, and system controller for Android — built for the **AYN Thor** (top + bottom displays) with a graceful single-display fallback. The UX is inspired by the Nintendo 3DS home screen: hero/preview on the top screen, touch grid on the bottom.

Wajiha combines the best patterns from four studied apps (NeoStation, Daijishō, Cocoon, iiSU — see `Study/docs/`):

- **3DS-style UX** — top screen shows the focused game's box art / video preview / metadata, bottom screen is a horizontally scrolling icon grid
- **Dual-screen engine** — a shared state machine drives both displays from one process; the bottom screen can show Now Playing, Quick Settings, Running Apps, Achievements, a Clock, or black out entirely
- **Running-app detection** — even games launched *outside* Wajiha are detected (UsageStats polling + optional AccessibilityService) and pushed to the secondary screen
- **NeoStation-grade launching** — SAF URI grants, FileProvider rewrap, multi-disc sibling grants, RetroArch core/config defaults, per-game emulator overrides
- **Deep scraping** — six sources (ScreenScraper, SteamGridDB, libretro-thumbnails, RetroAchievements, RomM, local media) with per-media-type source priority, region/language chains, batch jobs, and a manual match UI
- **RetroAchievements** — hash-based game linking, badge lists with earned state, optional achievements panel on the second screen while you play

## Requirements

- Android 11+ (minSdk 30, the Thor's shipping OS)
- JDK 17+, Android SDK (set `sdk.dir` in `local.properties`)
- Kotlin Multiplatform project — Android is the only finished target; the iOS source sets are scaffolding

## Build

```bash
./gradlew :androidApp:assembleDebug     # debug APK
./gradlew :androidApp:assembleRelease   # R8-minified release APK
./gradlew :composeApp:testAndroidHostTest  # host-side unit tests (importers, parsers, scanner)
```

Optional ScreenScraper developer credentials (improves API rate limits; the app also works with just a user account) go into `~/.gradle/gradle.properties`:

```properties
wajiha.screenscraper.devid=YOUR_DEV_ID
wajiha.screenscraper.devpassword=YOUR_DEV_PASSWORD
```

## First-run setup

The onboarding wizard walks through everything, but for reference:

| Permission / role | Why | Required? |
|---|---|---|
| Default home app | Own both screens, survive the HOME button | Recommended |
| Usage access | Detect games launched outside Wajiha | For manual-launch detection |
| Modify system settings | Brightness / screen-timeout sliders | For quick settings |
| Notifications | Scan & scrape progress, keep-alive service | Recommended |
| All files access | Direct file paths for emulators that reject `content://` URIs | Optional |
| Accessibility service | Instant (event-driven) game detection instead of 2 s polling | Optional |

Then:

1. **Settings → Library** — add ROM folders per platform (SAF folder picker). A background scan starts automatically; CRC32/MD5 hashes are computed lazily.
2. **Settings → Scraper → Sources** — enter credentials for the sources you use (ScreenScraper account, SteamGridDB API key, RetroAchievements username + web API key, RomM server, or a local media folder in ES-DE layout).
3. **Settings → Scraper → Batch** — scrape everything, or per platform. Runs as a foreground WorkManager job with progress, pause/resume, and cancel.

## Scraper details

- **Metadata** comes from the first source in the priority chain that matches (default: ScreenScraper → RomM → RA). Hash lookups (CRC32/MD5) are tried before name search.
- **Media** is resolved per type: each of boxart / logo / hero / screenshot / fanart / video / icon / banner has its own source priority chain (editable in the Sources tab — tap a source chip to promote it).
- **Region & language** preference chains apply to names, synopses, and per-region media variants.
- **Image size cap** — images larger than the configured max resolution (Sources tab, default 1024 px longest edge, 0 = keep originals) are downscaled and recompressed (PNG for logos/icons, JPEG otherwise) before being stored.
- **Per-platform overrides** (Sources tab, bottom) — pick a platform to override which sources it uses and its region priority; unset fields inherit the global options. Overridden platforms are marked with `*`.
- **Manual match** (Scraper → Manual): search your library, search all sources by name, preview candidates with thumbnails, apply one; or manage each media asset per game (view source, delete).
- **Local media** uses the ES-DE folder layout: `<root>/<platform id>/<covers|marquees|screenshots|fanart|videos|...>/<rom base name>.<ext>`.
- **Batch progress survives restarts** — progress is checkpointed after every game; if the process dies mid-run, the restarted WorkManager job resumes the counts (with "skip already-scraped" on) and the UI shows the last run's summary after an app restart.

## Dual-screen behavior

State machine (see `DualScreenStore`): `SingleDisplay`, `DualBrowsing`, `GameRunning`, `AppOnSecondary`, `BlackoutSecondary`.

- While browsing: top = hero, bottom = game grid (roles swappable in Settings → Dual screen).
- While a game runs: the other screen shows your chosen mode — Now Playing, Quick Settings, Running Apps, Achievements, Clock, or Off (blackout, also available as blackout-on-launch). Every non-grid mode has a header with tabs to switch modes or return to the grid; a blacked-out screen restores on tap. The App Dock mode reuses the app drawer for launching regular apps from the bottom screen.
- A foreground `KeepAliveService` holds launcher state while an external game is up; returning to Wajiha closes the play session and records playtime.
- On single-display devices everything collapses into a combined vertical 3DS-like layout.

## Project structure

```
composeApp/            shared KMP module (UI, domain, data)
  src/commonMain/kotlin/com/wajiha/
    data/db/           Room KMP entities, DAOs, database
    data/config/       unified platform schema + Daijishō/iiSU importers
    data/prefs/        app settings (DataStore)
    data/scraper/      scrape engine, batch job, 6 scraper sources
    data/ra/           RetroAchievements client + repository
    domain/            repositories, library scanner
    state/             DualScreenStore (the dual-screen state machine)
    ui/                home, secondary, apps, settings, scraper, ra,
                       system, onboarding, theme
    platform/          expect interfaces (AppActions, SystemControls, ...)
  src/androidMain/     Android actuals (SAF scanner, hasher, media storage)

androidApp/            Android host application
  .../MainActivity     HOME + LAUNCHER (primary display)
  .../SecondaryHomeActivity  SECONDARY_HOME (bottom display)
  .../launch/          EmulatorLauncher port, GameLauncher, session tracker
  .../display/         DisplayCoordinator (DisplayManager listener)
  .../monitor/         ForegroundAppMonitor + accessibility service
  .../service/         KeepAliveService
  .../system/          SystemController, BootReceiver
  .../work/            LibraryScanWorker, ScrapeWorker

Study/                 dissection docs + reference apps (not shipped)
```

Full architecture write-up: [docs/architecture.md](docs/architecture.md).

## Known gaps

- iOS target compiles as scaffolding only; all host services are Android.
