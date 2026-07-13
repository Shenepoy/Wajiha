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
./gradlew :androidApp:test                 # Android unit tests (ROM path probes)
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
2. **Settings → Scraper → Sources** — enter credentials for the sources you use (ScreenScraper account, SteamGridDB API key, RetroAchievements username + web API key, RomM server, or a local media folder in ES-DE layout). Use **Test login** where available.
3. **Settings → Scraper → Batch** — pick **Fill gaps** or **Force**, then scrape everything or per platform. Runs as a foreground WorkManager job with progress, pause/resume, cancel, and **Retry failed** for partial/error games. Outcomes are split into matched / partial / no match / errors, with an expandable issues list.
4. **Platform → Scraper** — same modes plus **Review** (interactive queue). Warnings when linkage IDs (ScreenScraper / RA / Libretro) are missing.
5. **Game detail → Scraper** — Fill gaps / Force one-shot, or open the shared review picker for that game.

## Gamepad navigation

Full controller support for handhelds: D-pad focus, A confirm, B back, layered modals, and per-screen hint bars with scheme-aware glyphs (Auto / Xbox / PlayStation / Switch). See [docs/gamepad.md](docs/gamepad.md).

Button icons use **[Kenney Input Prompts](https://kenney.nl/assets/input-prompts)** (CC0) by [Kenney](https://kenney.nl).

## Game detail & platform settings

- **Game detail** — per-game metadata, media, launch options, play history (`GameDetailScreen`)
- **Platform settings** — per-platform ROM folders, emulator overrides, scraper overrides (`PlatformSettingsScreen`)

## ROM reconciliation

When **Settings → Library → detect external games** is enabled, Wajiha probes emulator data files (RetroArch history, AetherSX2 playtime) to match games launched outside the launcher.

## Multi-session Now Playing

Concurrent emulator sessions appear as grid tiles in the game library; tap to switch, Y to close. Playtime is tracked per package. See [docs/sessions.md](docs/sessions.md). Thor debug: [docs/debug.md](docs/debug.md).

## Scraper details

- **Match tool** — Fill gaps / Force / Review all go through `ScrapeMatchTool`: search sources, rank by confidence + score + author prefer/blacklist, then auto-pick or hand the list to Review.
- **Metadata** comes from the first source in the priority chain that matched (default: ScreenScraper → RomM → RA). Hash lookups (CRC32/MD5) are tried before name search.
- **Media** is resolved per type with ranking (confidence / score / author prefs can override the default source priority chips).
- **Region & language** preference chains apply to names, synopses, and per-region media variants.
- **Image size cap** — images larger than the configured max resolution (Sources tab, default 1024 px longest edge, 0 = keep originals) are downscaled and recompressed (PNG for logos/icons, JPEG otherwise) before being stored.
- **Per-platform overrides** (Sources tab, bottom) — pick a platform to override which sources it uses and its region priority; unset fields inherit the global options. Overridden platforms are marked with `*`.
- **Scrape modes** (per run):
  - **Fill gaps** — only games missing metadata or preferred artwork (boxart / logo / hero); never overwrites existing media.
  - **Force** — re-scrape everything and overwrite metadata + media.
  - **Review** — UI-only: look up candidates, pick metadata and each media slot (or leave / clear), then Apply / Skip. Used from Platform → Scraper (queue), Game detail, and Manual match. Not enqueued to WorkManager.
- **Manual match** (Scraper → Manual): search your library, search all sources by name, preview candidates with thumbnails, Apply one or open **Review**; or manage each media asset per game (view source, delete). Success and failure messages are color-coded.
- **Outcomes** — each game ends as matched, partial (match but media downloads failed), no match, or error (auth / network / rate limit / config). Batch UI and notifications show split counts plus per-game issues; **Retry failed** re-runs Error + Partial games from the last run.
- **Local media** uses the ES-DE folder layout: `<root>/<platform id>/<covers|marquees|screenshots|fanart|videos|...>/<rom base name>.<ext>`.
- **Batch progress survives restarts** — progress is checkpointed after every game; if the process dies mid-run, the restarted WorkManager job resumes Fill-gaps counts and the UI shows the last run's summary after an app restart.

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
    domain/            repositories, library scanner, GamingAppCatalog
    state/             DualScreenStore (dual-screen state machine)
    input/             gamepad nav controller, layer stack, keys
    ui/                home, secondary, apps, settings, scraper, ra,
                       system, onboarding, gamedetail, running, theme,
                       components/gamepad, navigation
    platform/          host-bound interfaces (AppActions, SystemControls, ...)
  src/androidMain/     Android actuals (SAF scanner, hasher, media storage)

androidApp/            Android host application
  .../MainActivity     HOME + LAUNCHER (primary display)
  .../SecondaryHomeActivity  SECONDARY_HOME (bottom display)
  .../launch/          EmulatorLauncher, GameLauncher, PlaySessionTracker
  .../display/         DisplayCoordinator
  .../monitor/         ForegroundAppMonitor, GameSessionController
  .../detect/          ExternalGameResolver, ROM path probes
  .../input/           GamepadKeyRouter, LauncherKeyRouting
  .../service/         KeepAliveService
  .../system/          SystemController, BootReceiver
  .../work/            LibraryScanWorker, ScrapeWorker
  .../library/         RomFolderManager, RomFileDeleter

docs/                  architecture, gamepad, sessions, debug, external-apis
Study/                 dissection docs + reference apps (not shipped)
```

Full architecture: [docs/architecture.md](docs/architecture.md). Session model: [docs/sessions.md](docs/sessions.md). Thor debug: [docs/debug.md](docs/debug.md).

## Known gaps

- iOS target compiles as scaffolding only; all host services are Android.
- **Collections** — DB schema and `CollectionRepository` exist; UI is not implemented yet.
