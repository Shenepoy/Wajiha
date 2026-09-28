# Design guide and restrictions

Visual and interaction rules for Wajiha UI. Tokens live in `composeApp/src/commonMain/kotlin/com/wajiha/ui/theme/`. Shared controls live in `com.wajiha.ui.components` and `com.wajiha.ui.components.gamepad`. Gamepad key behavior is in [gamepad.md](gamepad.md).

The look is a handheld console: deep navy in dark, soft blue-grey in light, one cyan accent. Both themes ship. A screen that is readable in only one of them is unfinished.

## Themes

`WajihaTheme` maps the saved preference (`dark`, `light`, `system`) onto `DarkColors` or `LightColors`. Default preference is `dark`.

| Role | Dark | Light | Use |
|---|---|---|---|
| `background` / `onBackground` | `#0B101B` / `#F2F5FA` | `#E8EDF4` / `#16202C` | Full-screen chrome and text on it |
| `surface` / `onSurface` | `#121927` / `#F2F5FA` | `#F4F7FB` / `#16202C` | Cards, dialogs, menus |
| `onSurfaceVariant` | `#B9C5D8` | `#4A5A6E` | Secondary labels and descriptions |
| `primary` / `onPrimary` | `#5EB8C9` / `#080B12` | `#2A7A88` / `#F4F7FB` | Accent, selected chips, filled buttons |
| `error` | `#FFB4AB` | `#BA1A1A` | Destructive labels |

Named extras in `WajihaColors` that are not theme-swapped:

- `OnDark` and `OnDarkMuted` — text on dark chrome. `OnLight` and `OnLightMuted` — text on light chrome. Prefer the scheme roles above; these exist so callers do not fade `OnDark` with alpha.
- `TileScrim` plus `OnDark` — labels drawn on artwork. Stay dark even in the light theme, because the art is the background.
- `MenuScrim` — dimmer behind context menus. `HeroScrim` — dimmer on the hero. They are not interchangeable.
- `OverrideAmber` — a value that overrides a default. `StatusGreen` / `StatusGreenDeep` — granted or “on top” status.

## Text

Every `Text` that sits on a screen background sets an explicit scheme color:

- Titles and primary labels: `onBackground` or `onSurface`.
- Descriptions, counts, and captions: `onSurfaceVariant`.
- Accent values (points, selected state, “GRANTED”): `primary`.
- Destructive rows: `error`.

Compose’s default content color is black. A `Text` with no `color`, inside a `Box` or `Column` that only paints `background`, renders black. On the dark theme that is unreadable. `WajihaScreen` provides `onBackground` as the content color so a missed call still follows the theme. Do not depend on that for new text. Set the color at the call site.

`Surface(color = someColor.copy(alpha = …))` does not match a scheme slot, so Material does not pick a content color for it. Titles inside those surfaces set `onSurface` themselves. The same applies to any custom background that is not a scheme color.

Do not fade text with `copy(alpha = …)`. Use `onSurfaceVariant`, `OnDarkMuted`, or `OnLightMuted`. Alpha on near-white washes out on navy.

Check both themes before calling a screen done. Dark text on navy and light text on the blue-grey background are both failures.

## Composition

Build from the shared controls. Add a new primitive only when an existing one cannot express the behavior.

Settings screens compose in this order:

1. `WajihaScreen`
2. `WajihaFolderSettingChrome` (gutters from `WajihaFolderChromeMetrics`)
3. Section content made of the public `Wajiha*Setting` rows, fields, and actions
4. Shared hint bar and hero publication

`WajihaToolbar` uses the same folder gutters. Back, folder tabs, and setting rows share one minimum height: `SettingsCompactRowMinHeight` / `LocalSettingRowMinHeight` (44.dp).

Use these instead of raw Material equivalents:

| Need | Use |
|---|---|
| Setting row | `GamepadSettingRow` or a public `Wajiha*Setting` |
| Button | `GamepadButton` |
| Filter chip | `GamepadChip` |
| Text field | `GamepadSafeTextField` |
| Empty / loading / error | `WajihaEmptyState`, `WajihaLoadingState`, `WajihaErrorState` |
| Confirm | `WajihaDialog` |

Empty-state titles use `onBackground`. Subtitles use `onSurfaceVariant`.

Spacing, corners, icon sizes, elevation, and outline alphas come from `WajihaSpacing`, `WajihaShapes`, `WajihaIconSize`, `WajihaElevation`, and `WajihaAlphas`. Outlines use `softOutlineBorder()`. Touch targets on Thor stay at least `WajihaSpacing.touchMin` (48.dp).

`GamepadTile`, system overlays, context menus, and onboarding / scrape-review may keep their own layout. They still use the color tokens and the text rules above.

Onboarding pins Back to the bottom-start corner and Continue to the bottom-end corner. Each shows its glyph: L1 goes back (Skip on the first step), R1 continues. The step list stays tight and shows a scrollbar when it overflows.

## Interaction

- **A** confirms the focused control.
- **X** is the contextual secondary (Menu, Edit, Remove) when the screen defines one.
- **Y** resets only when the row supports reset and the value is not already the default.
- **B** leaves text editing or collapses a row before it navigates back.
- **L1 / R1** change sections.
- Touch gets the same action in one tap. Do not nest a second control that repeats it.

Text fields move `Idle` → `Selected` → `Editing`. D-pad selects a field and does not open the keyboard. Touch or A starts editing. B leaves editing and restores the focus anchor. Recomposition, filtering, and validation must not take focus.

Disabled controls say why they are disabled. Focus returns after a dialog or a navigation change. Each settings row either publishes dual-display hero state or is explicitly local. Defaults, reset, parsing, and destructive confirmation follow the repository that owns the value.

Dialogs use `WajihaDialog` or a `Dialog` wrapped in `GamepadOverlayLayer` with `RememberDialogGamepadKeyRouting`, so keys still reach the gamepad router. The Android host must have `DialogGamepadKeyInstaller` bound in Koin.

## Restrictions

- No new raw `.dp` values or hex colors in settings-family chrome. Map layout numbers onto the tokens. Tiles, overlays, onboarding, and review may keep layout values that are already on the spacing scale.
- No parallel settings API, one-off preference chrome, custom focus ring, or second gamepad key path.
- No `Color.Black` or `Color.White` for UI text. Artwork scrims are the exception, and they use `OnDark`.
- No text that is correct in one theme and missing in the other.
- No `MenuScrim` where the hero dimmer belongs, and no `HeroScrim` behind a menu.
- No icon picker or drag-reorder on collections. Collection order is insert order.
- `Study/` is reference material. Do not restyle it to match this guide.
