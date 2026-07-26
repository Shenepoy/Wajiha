# External APIs consumed by Wajiha

Wajiha is a client app — these are third-party services called from scraper sources and `RaClient`. Implementation: `composeApp/.../data/scraper/sources/` and `composeApp/.../data/ra/RaClient.kt`.

**Match orchestration:** [`ScrapeMatchTool`](../composeApp/src/commonMain/kotlin/com/wajiha/data/scraper/ScrapeMatchTool.kt) searches configured sources, ranks media by **match confidence** (Hash > Name > Autocomplete), community **score**, and **author prefer/blacklist**, then auto-picks or returns the list for Review. Each source uses [`SourceHealthBudget`](../composeApp/src/commonMain/kotlin/com/wajiha/data/scraper/SourceHealthBudget.kt) for call budgets and short circuit cool-downs (auth / rate-limit / network).

## ScreenScraper (api2)

- **Base:** `https://api.screenscraper.fr/api2/`
- **Auth:** Developer `devid` / `devpassword` (required by SS API — Settings Dev fields, else `~/.gradle/gradle.properties`) plus user `ssid` / `sspassword` (Settings). Blank developer values are omitted from requests (never sent as empty params).
- **Endpoints:** `jeuInfos.php` (hash + name + `gameid` hydrate), `jeuRecherche.php` (search), `ssuserInfos.php` (credential test)
- **Lookup ladder:** hash/`romnom`+`systemeid` → cleaned name → `jeuRecherche` → hydrate via `jeuInfos&gameid=`
- **`romtype`:** `iso` for chd/cue/iso/gdi/…; `rom` otherwise
- **Notes:** Region/language chains. `systemeid` is required when ROM CRC/MD5 is missing (common for large Switch/PS2 images) — Wajiha fills it from `platforms.screenScraperId` or `ScreenScraperSystemIds` (e.g. Switch=225, PS2=58). Huge `romtaille` without a hash is omitted so name lookups don’t stall. Role: metadata + retro media (box/screenshot/fanart/video).

## RetroAchievements

- **Base:** `https://retroachievements.org/API/`
- **Media:** `https://media.retroachievements.org` (icons, badges)
- **Auth:** Web API authenticates with `y` (web API key). Clients also send `z` (username) for parity with the official JS client. User-targeted endpoints take `u` (username or ULID — prefer ULID after profile login).
- **Endpoints:** `API_GetUserProfile.php`, `API_GetGameList.php` (`f=1` achievements-only, `h=1` hashes), `API_GetGame.php`, `API_GetGameInfoAndUserProgress.php`
- **Caching / pacing:** GetGameList hash libraries are disk-cached (~7 days) plus in-memory; game summaries ~24h memory; progress ~12 min memory. Real HTTP is spaced (~400ms) with a cooldown on 429/5xx. There is **no** public Web `GetGameInfoByHash` — hash→game uses GetGameList (title fallback when ROM MD5 ≠ RA hash).
- **Notes:** JSON uses PascalCase keys (`ID`, `Title`, …). Web API only (not Connect/`dorequest.php`).

## SteamGridDB

- **Base:** `https://www.steamgriddb.com/api/v2/`
- **Auth:** `Authorization: Bearer <api_key>`
- **Endpoints:** `/search/autocomplete/{term}` (cheap game search), `/grids|heroes|logos|icons/game/{id}` (lazy art)
- **Grids → two media types:** unfiltered grids map to **Boxart**; Square requests the same `/grids` endpoint with `dimensions=1024x1024,512x512` (Cocoon-style 1:1) and stores as `MediaType.Square` — parallel to boxart, not a replacement
- **Ranking fields:** per-type style preference (`steamGridDb*Styles`) then asset `score` (likes), then `author.{name,steam64}` via `MediaRanker` prefer/blacklist
- **Notes:** Auto-scrape fetches art only for the chosen game id and needed media types (not all autocomplete hits). Role: modern art (grids→boxart + square, heroes, logos, icons).

## libretro thumbnails

- **Base:** `https://thumbnails.libretro.com/`
- **Auth:** None
- **Match:** `{system}/{romBase}.{png|jpg}` — requires `platforms.libretroName`
- **Notes:** HEAD check before download

## RomM

- **Base:** User-configured server URL + `/api/roms`
- **Auth:** HTTP Basic (`username:password` base64) when configured
- **Notes:** Lenient JSON (numbers may arrive as strings)

## Local ES-DE media

- **Path:** User-configured folder
- **Layout:** `<root>/<platformId>/<covers|marquees|screenshots|fanart|videos|...>/<rom base name>.<ext>`
- **Auth:** None (local files)
- **Notes:** `MediaType.Square` is listed in source priority defaults but has no ES-DE folder mapping yet — local square lookups miss until folders are added.
