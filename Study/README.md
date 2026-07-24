# Study corpus — emulation launcher reference

This folder holds **reference apps and APKs** for building **Wajiha**: an Android-first home launcher / system shell focused on **AYN Thor** (dual-display handheld) with fallback support for single-screen devices.

Read this index first, then the docs under `docs/`. For machine-readable metadata, see [`INDEX.json`](./INDEX.json).

## Contents

| Item | Type | Status | Doc |
|------|------|--------|-----|
| `neostation-frontend/` | Open source (Flutter) | Full source | [docs/02-neostation-frontend.md](./docs/02-neostation-frontend.md) |
| `Daijishou/` | Open source (assets/docs only) | Platform JSON, themes, docs (`git pull` if empty) | [docs/03-daijishou.md](./docs/03-daijishou.md) |
| `RetroHrai-v0.5.1.apk` | Closed source APK | Dissected → `_extracted/retrohrai/` | [docs/04-retrohrai-apk.md](./docs/04-retrohrai-apk.md) |
| `cocoon-3.apk` | Closed source APK (**3.0**) | Dissected → `_extracted/cocoon-3/` | [docs/05-cocoon-apk.md](./docs/05-cocoon-apk.md) |
| `cocoon-beta-2-2.apk` | Prior beta (kept for diff) | `_extracted/cocoon/` | same doc (delta section) |
| `iiSU-Alpha-0.0.7.3.apk` | Closed source APK (alpha) | Dissected → `_extracted/iisu/` | [docs/09-iisu-apk.md](./docs/09-iisu-apk.md) |
| `lawnchair/` | Open source (Apache 2.0) | Icon packs / shapes reference | [docs/10-lawnchair-icons.md](./docs/10-lawnchair-icons.md) |

## Recommended reading order

1. [docs/00-overview.md](./docs/00-overview.md) — Wajiha goals and how these references fit
2. [docs/01-comparison-matrix.md](./docs/01-comparison-matrix.md) — feature/stack comparison at a glance
3. [docs/07-dual-display-handhelds.md](./docs/07-dual-display-handhelds.md) — **critical for AYN Thor**
4. [docs/06-emulator-launch-patterns.md](./docs/06-emulator-launch-patterns.md) — how games are launched on Android
5. [docs/08-platform-json-schema.md](./docs/08-platform-json-schema.md) — shared platform/player config format
6. Deep dives: NeoStation source, then APK analyses

## Version control

Git tracks only the **lightweight study docs** (this file, `AGENTS.md`, `INDEX.json`, `docs/`).

Ignored locally (see root `.gitignore`):

| Path | Why |
|------|-----|
| `*.apk` | Large binaries (~28–136 MB each) |
| `_extracted/` | APK unzip output (regenerate with commands below) |
| `neostation-frontend/` | Full GPL clone — re-clone when needed |
| `Daijishou/` | Assets repo (~6.5 GB themes) — `git clone` locally |
| `lawnchair/` | Full launcher clone — icon-pack reference only |

After cloning Wajiha, populate references locally:

```bash
# Optional: open-source reference
git clone https://github.com/misobadev/neostation-frontend.git Study/neostation-frontend

# Optional: Daijishō platform packs
git clone https://github.com/TapiocaFox/Daijishou.git Study/Daijishou

# Optional: Lawnchair icon-pack / shape reference
git clone --depth 1 --branch 15-dev \
  https://github.com/LawnchairLauncher/lawnchair.git Study/lawnchair

# APKs: obtain separately and place in Study/
```

## APK dissection artifacts

Binary extracts live in `_extracted/` (not required for git; regenerate with unzip):

```bash
mkdir -p Study/_extracted/cocoon-3 Study/_extracted/cocoon Study/_extracted/retrohrai Study/_extracted/iisu
unzip -qo Study/cocoon-3.apk -d Study/_extracted/cocoon-3
unzip -qo Study/cocoon-beta-2-2.apk -d Study/_extracted/cocoon
unzip -qo Study/RetroHrai-v0.5.1.apk -d Study/_extracted/retrohrai
unzip -qo Study/iiSU-Alpha-0.0.7.3.apk -d Study/_extracted/iisu
```

Manifest dump (requires Android SDK `apkanalyzer`):

```bash
apkanalyzer manifest print Study/cocoon-3.apk
apkanalyzer manifest print Study/RetroHrai-v0.5.1.apk
apkanalyzer manifest print Study/iiSU-Alpha-0.0.7.3.apk
```

Dex string mining (no decompiler):

```bash
strings Study/_extracted/cocoon-3/classes*.dex | rg 'rip/moth/cocoonshell'
strings Study/_extracted/retrohrai/classes*.dex | rg 'com/retrohrai/launcher'
strings Study/_extracted/iisu/classes*.dex | rg 'com/iisulauncher'
```

## AI agent entry point

See [AGENTS.md](./AGENTS.md) for conventions when extending this study or implementing Wajiha features.
