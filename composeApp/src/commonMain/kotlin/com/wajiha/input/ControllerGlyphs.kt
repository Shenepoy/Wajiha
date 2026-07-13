package com.wajiha.input

/**
 * Semantic face / shoulder / system buttons used by [com.wajiha.ui.components.gamepad.GamepadActionBar]
 * hints. Display labels are resolved via [ControllerGlyphScheme] — keycodes stay Xbox/Thor semantics.
 */
enum class GamepadHintButton {
    A,
    B,
    X,
    Y,
    L1,
    R1,
    L1R1,
    L2,
    R2,
    Select,
    Start,
    DpadUpDown,
    DpadLeftRight,
    DpadRight,
    Search,
}

data class GamepadHint(
    val button: GamepadHintButton,
    val action: String,
)

/** Persisted / resolved glyph style for the action bar. */
enum class ControllerGlyphScheme {
    Auto,
    Xbox,
    PlayStation,
    Switch,
    SteamDeck,
    SteamController,
    Text,
    ;

    companion object {
        fun fromName(value: String?): ControllerGlyphScheme {
            val raw = value?.trim().orEmpty()
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Auto
        }
    }
}

/**
 * Kenney face-button appearance (A/B/X/Y and PlayStation shapes).
 * Switch / Steam Deck have no dedicated Kenney color set — Color / ColorOutline reuse
 * Xbox lettered color faces (A/B/X/Y) so the face-style setting still shows brand colors.
 */
enum class ControllerGlyphFaceStyle {
    /** Filled brand colors (default). */
    Color,

    /** Brand-colored outline. */
    ColorOutline,

    /** Monochrome filled (Kenney Default / white). */
    White,

    /** Monochrome outline (Kenney Outline / dark). */
    Dark,
    ;

    companion object {
        fun fromName(value: String?): ControllerGlyphFaceStyle {
            val raw = value?.trim().orEmpty()
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Color
        }
    }
}

/**
 * Kenney look for non-face glyphs (shoulders, triggers, d-pad, system).
 * Filled = Default set; Outline = Outline set.
 */
enum class ControllerGlyphOtherStyle {
    /** Monochrome filled (default). */
    Filled,

    /** Monochrome outline. */
    Outline,
    ;

    companion object {
        fun fromName(value: String?): ControllerGlyphOtherStyle {
            val raw = value?.trim().orEmpty()
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Filled
        }
    }
}

/** Classified physical controller family (Auto maps these to a concrete scheme). */
enum class ControllerDeviceType {
    Xbox,
    PlayStation,
    Switch,
    SteamDeck,
    SteamController,
    Generic,
    ;

    fun toGlyphScheme(): ControllerGlyphScheme =
        when (this) {
            Xbox, Generic -> ControllerGlyphScheme.Xbox
            PlayStation -> ControllerGlyphScheme.PlayStation
            Switch -> ControllerGlyphScheme.Switch
            SteamDeck -> ControllerGlyphScheme.SteamDeck
            SteamController -> ControllerGlyphScheme.SteamController
        }
}

data class ConnectedController(
    /** Stable across reconnects when vendor/product are known. */
    val stableId: String,
    val displayName: String,
    val type: ControllerDeviceType,
    /** Transient Android InputDevice id; may change across reconnects. */
    val deviceId: Int = -1,
)

/**
 * Keyword classifier for controller names / vendor IDs.
 * Pure Kotlin so unit tests do not need Android InputDevice.
 */
object GamepadDeviceClassifier {
    /** Sony Interactive Entertainment. */
    const val VENDOR_SONY = 0x054C

    /** Microsoft. */
    const val VENDOR_MICROSOFT = 0x045E

    /** Nintendo. */
    const val VENDOR_NINTENDO = 0x057E

    /** Valve. */
    const val VENDOR_VALVE = 0x28DE

    fun classify(
        name: String,
        vendorId: Int = 0,
    ): ControllerDeviceType {
        val n = name.lowercase()
        if (
            "steam deck" in n ||
            "steamdeck" in n ||
            (vendorId == VENDOR_VALVE && ("deck" in n || "jupiter" in n))
        ) {
            return ControllerDeviceType.SteamDeck
        }
        if (
            "steam controller" in n ||
            (vendorId == VENDOR_VALVE && "controller" in n && "deck" !in n)
        ) {
            return ControllerDeviceType.SteamController
        }
        if (
            vendorId == VENDOR_SONY ||
            "dualshock" in n ||
            "dualsense" in n ||
            "playstation" in n ||
            "sony" in n
        ) {
            return ControllerDeviceType.PlayStation
        }
        if (
            vendorId == VENDOR_NINTENDO ||
            "nintendo" in n ||
            "switch" in n ||
            "pro controller" in n ||
            "joy-con" in n ||
            "joycon" in n
        ) {
            return ControllerDeviceType.Switch
        }
        if (
            vendorId == VENDOR_MICROSOFT ||
            "xbox" in n ||
            "xinput" in n ||
            "x-box" in n
        ) {
            return ControllerDeviceType.Xbox
        }
        return ControllerDeviceType.Generic
    }

    fun stableId(
        name: String,
        vendorId: Int,
        productId: Int,
    ): String {
        if (vendorId != 0 || productId != 0) {
            return "$vendorId:$productId"
        }
        return "name:${name.trim().lowercase().hashCode()}"
    }
}

/**
 * Resolves [GamepadHintButton] → display label for a concrete (non-Auto) scheme.
 * When glyphs are disabled, callers force [ControllerGlyphScheme.Text].
 */
object ControllerGlyphLabels {
    fun label(
        button: GamepadHintButton,
        scheme: ControllerGlyphScheme,
    ): String {
        val resolved =
            when (scheme) {
                ControllerGlyphScheme.Auto -> ControllerGlyphScheme.Xbox
                else -> scheme
            }
        return when (resolved) {
            ControllerGlyphScheme.PlayStation -> playstationLabel(button)
            ControllerGlyphScheme.Switch -> switchLabel(button)
            ControllerGlyphScheme.SteamDeck -> steamDeckLabel(button)
            ControllerGlyphScheme.SteamController -> steamControllerLabel(button)
            ControllerGlyphScheme.Text -> textLabel(button)
            ControllerGlyphScheme.Xbox, ControllerGlyphScheme.Auto -> xboxLabel(button)
        }
    }

    /**
     * Effective scheme from prefs + last-input device.
     * Auto → last controller that pressed a button; any other pref is used as-is.
     */
    fun resolveEffectiveScheme(
        glyphsEnabled: Boolean,
        schemePref: String,
        lastInputType: ControllerDeviceType,
    ): ControllerGlyphScheme {
        if (!glyphsEnabled) return ControllerGlyphScheme.Text
        val pref = ControllerGlyphScheme.fromName(schemePref)
        if (pref != ControllerGlyphScheme.Auto) return pref
        return lastInputType.toGlyphScheme()
    }

    /** Auto with no connected pads — hide the bottom hint bar entirely. */
    fun shouldShowActionBarHints(
        schemePref: String,
        connectedDeviceCount: Int,
    ): Boolean {
        val pref = ControllerGlyphScheme.fromName(schemePref)
        return pref != ControllerGlyphScheme.Auto || connectedDeviceCount > 0
    }

    private fun xboxLabel(button: GamepadHintButton): String =
        when (button) {
            GamepadHintButton.A -> "A"
            GamepadHintButton.B -> "B"
            GamepadHintButton.X -> "X"
            GamepadHintButton.Y -> "Y"
            GamepadHintButton.L1 -> "LB"
            GamepadHintButton.R1 -> "RB"
            GamepadHintButton.L1R1 -> "LB/RB"
            GamepadHintButton.L2 -> "LT"
            GamepadHintButton.R2 -> "RT"
            GamepadHintButton.Select -> "View"
            GamepadHintButton.Start -> "Menu"
            GamepadHintButton.DpadUpDown -> "Up/Down"
            GamepadHintButton.DpadLeftRight -> "←→"
            GamepadHintButton.DpadRight -> "Right"
            GamepadHintButton.Search -> "Search"
        }

    private fun textLabel(button: GamepadHintButton): String = xboxLabel(button)

    private fun playstationLabel(button: GamepadHintButton): String =
        when (button) {
            GamepadHintButton.A -> "✕"
            GamepadHintButton.B -> "○"
            GamepadHintButton.X -> "□"
            GamepadHintButton.Y -> "△"
            GamepadHintButton.L1 -> "L1"
            GamepadHintButton.R1 -> "R1"
            GamepadHintButton.L1R1 -> "L1/R1"
            GamepadHintButton.L2 -> "L2"
            GamepadHintButton.R2 -> "R2"
            GamepadHintButton.Select -> "Create"
            GamepadHintButton.Start -> "Options"
            GamepadHintButton.DpadUpDown -> "Up/Down"
            GamepadHintButton.DpadLeftRight -> "←→"
            GamepadHintButton.DpadRight -> "Right"
            GamepadHintButton.Search -> "Search"
        }

    /** Display-only Nintendo face layout (confirm/back glyphs swapped vs Xbox strings). */
    private fun switchLabel(button: GamepadHintButton): String =
        when (button) {
            GamepadHintButton.A -> "B"
            GamepadHintButton.B -> "A"
            GamepadHintButton.X -> "Y"
            GamepadHintButton.Y -> "X"
            GamepadHintButton.L1 -> "L"
            GamepadHintButton.R1 -> "R"
            GamepadHintButton.L1R1 -> "L/R"
            GamepadHintButton.L2 -> "ZL"
            GamepadHintButton.R2 -> "ZR"
            GamepadHintButton.Select -> "-"
            GamepadHintButton.Start -> "+"
            GamepadHintButton.DpadUpDown -> "Up/Down"
            GamepadHintButton.DpadLeftRight -> "←→"
            GamepadHintButton.DpadRight -> "Right"
            GamepadHintButton.Search -> "Search"
        }

    private fun steamDeckLabel(button: GamepadHintButton): String =
        when (button) {
            GamepadHintButton.A -> "A"
            GamepadHintButton.B -> "B"
            GamepadHintButton.X -> "X"
            GamepadHintButton.Y -> "Y"
            GamepadHintButton.L1 -> "L1"
            GamepadHintButton.R1 -> "R1"
            GamepadHintButton.L1R1 -> "L1/R1"
            GamepadHintButton.L2 -> "L2"
            GamepadHintButton.R2 -> "R2"
            GamepadHintButton.Select -> "View"
            GamepadHintButton.Start -> "⋯"
            GamepadHintButton.DpadUpDown -> "Up/Down"
            GamepadHintButton.DpadLeftRight -> "←→"
            GamepadHintButton.DpadRight -> "Right"
            GamepadHintButton.Search -> "Search"
        }

    private fun steamControllerLabel(button: GamepadHintButton): String = steamDeckLabel(button)
}
