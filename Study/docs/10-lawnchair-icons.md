# Lawnchair icon packs & shapes (reference)

Source: local clone `Study/lawnchair/` (`15-dev`), remote [LawnchairLauncher/lawnchair](https://github.com/LawnchairLauncher/lawnchair).

## License

- Repo root: **Apache License 2.0** (`LICENSE.txt`).
- Several files under `lawnchair/src/app/lawnchair/icons/shape/` still carry **GPL-3 file headers**. Do **not** copy those sources into Wajiha; reimplement shape paths in Compose.

## Portable patterns (use in Wajiha)

| Pattern | Lawnchair location | Wajiha use |
|---------|--------------------|------------|
| Pack discovery (Nova/ADW/Atom/Apex intents) | `PreferenceViewModel.kt` | `IconPackDiscovery` |
| `appfilter.xml` / `R.xml.appfilter` | `CustomIconPack.kt` | Pack → `ComponentName` → drawable |
| Resolve chain (pack → system) | `LawnchairIconProvider.kt` | `IconResolver` inside `installedApps()` |
| Shape presets UX | `IconShapePreference.kt` | Appearance choice (reimplemented shapes) |

## Skip for Wajiha v1

- Launcher3 `IconCache` / `BaseIconCache` / `CustomAdaptiveIconDrawable`
- Lawnicons / themed monochrome icons
- Per-app icon picker + Room overrides
- Calendar / dynamic-clock icons
- Live launcher preview pager

## Clone (local only; gitignored)

```bash
git clone --depth 1 --branch 15-dev \
  https://github.com/LawnchairLauncher/lawnchair.git Study/lawnchair
```
