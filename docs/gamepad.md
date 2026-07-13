# Gamepad navigation

Wajiha is designed for handheld controllers (AYN Thor, etc.). Gamepad input is a first-class navigation layer on top of Compose focus.

## Architecture

| Layer | Location | Role |
|-------|----------|------|
| Key routing (Android) | `androidApp/.../input/GamepadKeyRouter.kt` | Routes hardware keys to primary vs secondary display owners; notes last-input device for Auto glyphs |
| Device registry (Android) | `androidApp/.../input/GamepadDeviceRegistry.kt` | Enumerates / hot-plugs gamepads; classifies Xbox / PlayStation / Switch / Generic |
| Trigger axes (Android) | `androidApp/.../input/TriggerAxisHandler.kt` | Analog L2/R2 edge detect **per deviceId** (no multi-pad races) |
| Glyph store | `composeApp/.../input/ControllerGlyphStore.kt` | Connected pads + sticky last-input type for Auto scheme |
| Nav controller | `composeApp/.../input/GamepadNavController.kt` | D-pad focus graph inside a screen |
| Layer stack | `composeApp/.../input/GamepadLayers.kt` | Modal/dialog layering; BACK dismisses top layer |
| Focus chrome | `composeApp/.../ui/components/gamepad/` | `GamepadButton`, `GamepadTile`, `GamepadFocusable`, sliders, lists |
| Overlay wrapper | `composeApp/.../input/GamepadOverlayLayer.kt` | Shared BACK/confirm handling for dialogs |

When `GamepadNavHost` is active (`LocalGamepadNavController` set), controls defer to the custom nav graph instead of Compose focus traversal.

## Conventions

- **A / Enter** — confirm / activate focused control
- **B / Escape** — back; dismisses text edit, then top gamepad layer, then in-screen back
- **Y** — context actions (e.g. close session tile on grid)
- **L1/R1** — section/tab switching in settings and scraper
- **L2** — switch gamepad focus between top and bottom screens (dual display); sticky until toggled again or single-display. Works while a game is running with the bottom grid visible; disabled during secondary blackout. Accepts digital `BUTTON_L2` and analog `AXIS_LTRIGGER` (Xbox / Thor).
- **R2** — toggle the top-screen system notification panel (open and close)
- **SELECT** — swap screen roles (dual display)

Keycode semantics stay Xbox/Thor everywhere. Switch glyph labels swap face-button **display** only (B shown for confirm); they do not remap physical keycodes.

## Action bar hints + glyphs

`GamepadActionBar` sits inset under `WajihaScreen` / `SecondaryPanelScaffold` content. Height is fixed (`GamepadActionBarHeight` = 36dp); each hint uses `maxLines = 1` + ellipsis so a long hint list never pushes the content area.

Hints are semantic (`GamepadHint` / `GamepadHintButton`), not raw `"A"` strings. When controller glyphs are enabled, button icons come from **[Kenney Input Prompts](https://kenney.nl/assets/input-prompts)** (CC0) under `composeResources/drawable/kenney_*`. Text scheme / glyphs-off falls back to plain labels.

| Scheme | Face buttons (confirm / back / X / Y) |
|--------|----------------------------------------|
| Xbox / Auto | A / B / X / Y (Kenney Xbox Series) |
| PlayStation | Cross / Circle / Square / Triangle |
| Switch | B / A / Y / X (display only; keycodes stay Xbox/Thor) |
| Steam Deck | A / B / X / Y (Kenney Steam Deck) |
| Steam Controller | A / B / X / Y (Kenney Steam Controller) |
| Auto | Derived from last-input device type; hint bar hidden when no pad is connected |

Face-button appearance (Settings → Appearance → **Face button glyphs**, default **Color**):

| Style | Look |
|-------|------|
| Color | Filled brand colors (Xbox / PlayStation / Steam Controller) |
| Color outline | Brand-colored outline |
| White | Monochrome filled (Kenney Default) |
| Dark | Monochrome outline (Kenney Outline) |

Other buttons (Settings → Appearance → **Other button glyphs**, default **Filled**):

| Style | Look |
|-------|------|
| Filled | Kenney Default (filled mono) for shoulders / triggers / d-pad / system |
| Outline | Kenney Outline for those same buttons |

In **light** themes, non-face Filled glyphs fill transparent letter cutouts with black so labels stay readable; Outline glyphs are tinted black. Dark themes are unchanged.

Switch and Steam Deck have no dedicated Kenney color set, so Color / Color outline reuse Xbox lettered color faces (A/B/X/Y).

License copy: `composeResources/files/kenney_input_prompts_LICENSE.txt`.

Settings → Appearance:

- **Controller glyphs** — off forces Text labels
- **Glyph style** — Auto (last controller that pressed a button; hides the hint bar when no pad is connected) / Xbox / PlayStation / Switch / Steam Deck / Steam Controller / Text. Dropdown uses Kenney controller silhouettes.
- **Face button glyphs** — Color / Color outline / White / Dark
- **Other button glyphs** — Filled / Outline

## Multi-controller

- Connected devices are listed via `InputManager` hot-plug; stable ids use `vendorId:productId` when available.
- Analog trigger edges are tracked per `deviceId` so two pads cannot flip a single shared L2/R2 pressed flag.
- Display ownership (`GamepadOwner`) is unchanged — which **screen** receives input, not which physical pad.

## Components

Reusable primitives in `ui/components/gamepad/`:

- `GamepadTile` — game grid cells
- `GamepadButton` / `GamepadChip` — actions
- `GamepadList` — scrollable focusable lists
- `GamepadForm` / `GamepadSettingRow` — settings screens
- `GamepadSlider` — brightness/volume in Quick Settings
- `GamepadNavHost` — full-screen gamepad routing (scraper, platform picker)
- `GamepadActionBar` — fixed-height bottom hint chrome with scheme-aware glyphs

## Android host

`MainActivity` and `SecondaryHomeActivity` share `dispatchLauncherKeyEvent()` (`androidApp/.../input/LauncherKeyRouting.kt`) to feed keys into `GamepadKeyRouter` before the framework.

`GamepadGate` blocks gamepad events while the launcher is backgrounded during gameplay (configurable).

`GamepadDeviceRegistry` starts from `WajihaApplication` after Koin init.
