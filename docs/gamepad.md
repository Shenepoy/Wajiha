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
- **X** — contextual secondary (Menu, Edit, Remove, …) when the screen defines it
- **L1/R1** — section/tab switching in settings and scraper
- **L2** — switch gamepad focus between top and bottom screens (dual display); sticky until toggled again or single-display. Disabled in single-screen mode and secondary blackout. Accepts digital `BUTTON_L2` and analog `AXIS_LTRIGGER` (Xbox / Thor). The **Focus** hint (`l2HintOwner`) appears mainly when the hero is interactive (settings hero picking, scrape review, hero layout editor); the toggle itself still works more broadly while browsing / grid-visible.
- **R2** — toggle the top-screen system notification panel (open and close); ignored while a game is running or secondary is blacked out
- **L3** (left stick click) — open Apps on the menu-owning display; ignored while a game is running or secondary is blacked out
- **Start** — open Settings (or Apps options when the menu is already on Apps); ignored while a game is running or secondary is blacked out
- **SELECT** — swap screen roles (dual display); no-op in single-screen mode

Keycode semantics stay Xbox/Thor everywhere. Switch glyph labels swap face-button **display** only (B shown for confirm); they do not remap physical keycodes.

### Hero layout editor

Settings → Screens → Hero layout → **Customize layout** is a fullscreen drill-in. Gamepad follows the hero display; L2 flips canvas ↔ controls (“Focus”) when the editor is active. Save persists the slot via `SettingsRepository`; B / Back exits edit (draft discarded or saved per the editor’s exit path).

## Action bar hints + glyphs

`GamepadActionBar` sits inset under `WajihaScreen` / `SecondaryPanelScaffold` content (or on the **hero** display when Settings → Screens → **Swap gamepad hints** is on). Height is fixed (`GamepadActionBarHeight` = 36dp); each hint uses `maxLines = 1` + ellipsis so a long hint list never pushes the content area.

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
- `GamepadForm` / `GamepadSettingRow` / public `Wajiha*Setting` wrappers — settings screens
- `GamepadSlider` — brightness/volume in Quick Settings
- `GamepadSafeTextField` — Idle → Selected → Editing text fields (B exits edit)
- `GamepadNavHost` — full-screen gamepad routing (scraper, platform picker)
- `GamepadActionBar` — fixed-height bottom hint chrome with scheme-aware glyphs

## Touch scroll → D-pad snap

Mixing finger scroll with D-pad on lazy grids/lists needs an explicit handoff: the old selection is often off-screen, and Compose focus alone will happily highlight a peek tile.

### Home library (`BottomScreen`)

1. Finger-down captures whether the current selection sits on the **left or right half** of the viewport.
2. Touch-driven scroll (while the finger is down) arms a **pending viewport snap**. Extra swipes keep that side until the snap is consumed or a tile is tapped.
3. The **first** D-pad press after that snap lands on a top-row game:
   - Left-half origin → **leading** (≥50% visible; prefer fully on-screen)
   - Right-half origin → **trailing** (same visibility rules)
   - D-pad **direction is ignored** for choosing the edge
4. Later presses move with column-major `selectedGameId` math and bring-into-view by at most one column (no whole-page `animateScrollToItem` teleport).
5. Library tiles use `selectOnFocus = false` and only the **selected** tile is Compose-focusable, so scroll cannot light up a stray peek while selection stays elsewhere.
6. After scroll, the selected item may leave composition; D-pad / X / Y still reach the screen via the Android preview-key bridge (`GamepadPreviewKeyBridge`), walking the shared [GamepadLayers] stack past layers with no handler (e.g. dual-display `launcher_hero`) so the bottom `home_grid` still opens the context menu.

### Lists / settings / app drawer

Simpler rule: after touch scroll, the next D-pad press lands on the **topmost (or first) ≥50%-visible** row/tile, then normal stepping resumes. Settings sections snap to the top visible row in view.

### Retouch notes

Behavior is easy to regress (focus steal, pending-side overwrite on a second swipe, sub-50% peeks, keys dropped when the selected tile is disposed). Prefer re-testing on Thor: right-side select → multi-swipe → D-pad (expect trailing full tile); left-side select → swipe → D-pad (expect leading full tile). Debug tags: `Wajiha/Debug` `gridFocus:`.

## Android host

`MainActivity` and `SecondaryHomeActivity` share `dispatchLauncherKeyEvent()` (`androidApp/.../input/LauncherKeyRouting.kt`) to feed keys into `GamepadKeyRouter` before the framework.

`GamepadGate` blocks gamepad events while the launcher is backgrounded during gameplay (configurable).

`GamepadDeviceRegistry` starts from `WajihaApplication` after Koin init.
