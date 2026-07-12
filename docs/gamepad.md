# Gamepad navigation

Wajiha is designed for handheld controllers (AYN Thor, etc.). Gamepad input is a first-class navigation layer on top of Compose focus.

## Architecture

| Layer | Location | Role |
|-------|----------|------|
| Key routing (Android) | `androidApp/.../input/GamepadKeyRouter.kt` | Routes hardware keys to primary vs secondary display owners |
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

`GamepadActionBar` at the bottom of screens shows context-specific hints.

## Components

Reusable primitives in `ui/components/gamepad/`:

- `GamepadTile` — game grid cells
- `GamepadButton` / `GamepadChip` — actions
- `GamepadList` — scrollable focusable lists
- `GamepadForm` / `GamepadSettingRow` — settings screens
- `GamepadSlider` — brightness/volume in Quick Settings
- `GamepadNavHost` — full-screen gamepad routing (scraper, platform picker)

## Android host

`MainActivity` and `SecondaryHomeActivity` share `dispatchLauncherKeyEvent()` (`androidApp/.../input/LauncherKeyRouting.kt`) to feed keys into `GamepadKeyRouter` before the framework.

`GamepadGate` blocks gamepad events while the launcher is backgrounded during gameplay (configurable).
