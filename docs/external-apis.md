# External APIs consumed by Wajiha

Wajiha is a client app — these are third-party services called from scraper sources and `RaClient`. Implementation: `composeApp/.../data/scraper/sources/` and `composeApp/.../data/ra/RaClient.kt`.

## ScreenScraper (api2)

- **Base:** `https://api.screenscraper.fr/api2/`
- **Auth:** User `ssid` / `sspassword` query params; optional developer `devid` / `devpassword` from `~/.gradle/gradle.properties`
- **Endpoints:** `jeuInfos.php` (hash + name lookup), `jeuRecherche.php` (search), `ssuserInfos.php` (credential test)
- **Notes:** Region/language chains; rate limits improve with dev credentials

## RetroAchievements

- **Base:** `https://retroachievements.org/API/`
- **Media:** `https://media.retroachievements.org` (icons, badges)
- **Auth:** `z` (username) + `y` (web API key) on every request
- **Endpoints:** `API_GetUserProfile.php`, `API_GetGameInfoByHash.php`, `API_GetGameInfoAndUserProgress.php`
- **Notes:** JSON uses PascalCase keys (`ID`, `Title`, …)

## SteamGridDB

- **Base:** `https://www.steamgriddb.com/api/v2/`
- **Auth:** `Authorization: Bearer <api_key>`
- **Endpoints:** `/user` (credential test), `/search/autocomplete`, `/grids/game/{id}`, etc.
- **Notes:** Used for box art, heroes, logos, icons per game name

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
