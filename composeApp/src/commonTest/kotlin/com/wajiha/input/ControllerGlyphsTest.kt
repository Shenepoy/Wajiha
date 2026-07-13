package com.wajiha.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControllerGlyphsTest {
    @Test
    fun classifier_detectsXboxPlayStationSwitch() {
        assertEquals(
            ControllerDeviceType.Xbox,
            GamepadDeviceClassifier.classify("Xbox Wireless Controller", GamepadDeviceClassifier.VENDOR_MICROSOFT),
        )
        assertEquals(
            ControllerDeviceType.PlayStation,
            GamepadDeviceClassifier.classify("DualSense Wireless Controller", GamepadDeviceClassifier.VENDOR_SONY),
        )
        assertEquals(
            ControllerDeviceType.Switch,
            GamepadDeviceClassifier.classify("Pro Controller", GamepadDeviceClassifier.VENDOR_NINTENDO),
        )
        assertEquals(
            ControllerDeviceType.SteamDeck,
            GamepadDeviceClassifier.classify("Steam Deck", GamepadDeviceClassifier.VENDOR_VALVE),
        )
        assertEquals(
            ControllerDeviceType.SteamController,
            GamepadDeviceClassifier.classify("Steam Controller", GamepadDeviceClassifier.VENDOR_VALVE),
        )
        assertEquals(
            ControllerDeviceType.Generic,
            GamepadDeviceClassifier.classify("Generic USB Joystick", 0),
        )
    }

    @Test
    fun labels_playStationAndSwitchFaceButtons() {
        assertEquals("✕", ControllerGlyphLabels.label(GamepadHintButton.A, ControllerGlyphScheme.PlayStation))
        assertEquals("○", ControllerGlyphLabels.label(GamepadHintButton.B, ControllerGlyphScheme.PlayStation))
        assertEquals("B", ControllerGlyphLabels.label(GamepadHintButton.A, ControllerGlyphScheme.Switch))
        assertEquals("A", ControllerGlyphLabels.label(GamepadHintButton.B, ControllerGlyphScheme.Switch))
        assertEquals("A", ControllerGlyphLabels.label(GamepadHintButton.A, ControllerGlyphScheme.Xbox))
        assertEquals("LB/RB", ControllerGlyphLabels.label(GamepadHintButton.L1R1, ControllerGlyphScheme.Text))
        assertEquals("L1/R1", ControllerGlyphLabels.label(GamepadHintButton.L1R1, ControllerGlyphScheme.PlayStation))
    }

    @Test
    fun resolveEffectiveScheme_respectsToggleAndExplicitPref() {
        assertEquals(
            ControllerGlyphScheme.Text,
            ControllerGlyphLabels.resolveEffectiveScheme(
                glyphsEnabled = false,
                schemePref = "PlayStation",
                lastInputType = ControllerDeviceType.PlayStation,
            ),
        )
        assertEquals(
            ControllerGlyphScheme.PlayStation,
            ControllerGlyphLabels.resolveEffectiveScheme(
                glyphsEnabled = true,
                schemePref = "PlayStation",
                lastInputType = ControllerDeviceType.Xbox,
            ),
        )
    }

    @Test
    fun resolveEffectiveScheme_autoUsesLastInput() {
        assertEquals(
            ControllerGlyphScheme.PlayStation,
            ControllerGlyphLabels.resolveEffectiveScheme(
                glyphsEnabled = true,
                schemePref = "Auto",
                lastInputType = ControllerDeviceType.PlayStation,
            ),
        )
        assertEquals(
            ControllerGlyphScheme.Xbox,
            ControllerGlyphLabels.resolveEffectiveScheme(
                glyphsEnabled = true,
                schemePref = "Xbox",
                lastInputType = ControllerDeviceType.PlayStation,
            ),
        )
    }

    @Test
    fun shouldShowActionBarHints_hidesAutoWhenNoController() {
        assertTrue(
            !ControllerGlyphLabels.shouldShowActionBarHints(
                schemePref = "Auto",
                connectedDeviceCount = 0,
            ),
        )
        assertTrue(
            ControllerGlyphLabels.shouldShowActionBarHints(
                schemePref = "Auto",
                connectedDeviceCount = 1,
            ),
        )
        assertTrue(
            ControllerGlyphLabels.shouldShowActionBarHints(
                schemePref = "Xbox",
                connectedDeviceCount = 0,
            ),
        )
        assertTrue(
            ControllerGlyphLabels.shouldShowActionBarHints(
                schemePref = "Text",
                connectedDeviceCount = 0,
            ),
        )
    }

    @Test
    fun glyphStore_noteInputAlwaysUpdatesLastType() {
        val store = ControllerGlyphStore()
        store.noteInput("ps", ControllerDeviceType.PlayStation)
        assertEquals(ControllerDeviceType.PlayStation, store.lastInputType.value)
        store.noteInput("xb", ControllerDeviceType.Xbox)
        assertEquals(ControllerDeviceType.Xbox, store.lastInputType.value)
    }

    @Test
    fun stableId_usesVendorProductWhenPresent() {
        assertEquals(
            "1356:3302",
            GamepadDeviceClassifier.stableId("DualSense", 1356, 3302),
        )
        assertEquals(
            "name:${"My Pad".trim().lowercase().hashCode()}",
            GamepadDeviceClassifier.stableId("My Pad", 0, 0),
        )
    }

    @Test
    fun assets_textAndSearchHaveNoDrawables() {
        assertTrue(ControllerGlyphAssets.drawables(GamepadHintButton.A, ControllerGlyphScheme.Text).isEmpty())
        assertTrue(ControllerGlyphAssets.drawables(GamepadHintButton.Search, ControllerGlyphScheme.Xbox).isEmpty())
    }

    @Test
    fun assets_switchFaceSwapMatchesTextLabels() {
        // Semantic A = confirm → Switch shows B glyph / "B" text
        assertEquals(
            1,
            ControllerGlyphAssets.drawables(GamepadHintButton.A, ControllerGlyphScheme.Switch).size,
        )
        assertEquals(
            1,
            ControllerGlyphAssets.drawables(GamepadHintButton.B, ControllerGlyphScheme.Switch).size,
        )
        assertEquals("B", ControllerGlyphLabels.label(GamepadHintButton.A, ControllerGlyphScheme.Switch))
        assertEquals("A", ControllerGlyphLabels.label(GamepadHintButton.B, ControllerGlyphScheme.Switch))
    }

    @Test
    fun assets_l1r1ReturnsTwoIcons() {
        assertEquals(
            2,
            ControllerGlyphAssets.drawables(GamepadHintButton.L1R1, ControllerGlyphScheme.Xbox).size,
        )
        assertEquals(
            2,
            ControllerGlyphAssets.drawables(GamepadHintButton.L1R1, ControllerGlyphScheme.PlayStation).size,
        )
        assertEquals(
            2,
            ControllerGlyphAssets.drawables(GamepadHintButton.L1R1, ControllerGlyphScheme.Switch).size,
        )
        assertEquals(
            2,
            ControllerGlyphAssets.drawables(GamepadHintButton.L1R1, ControllerGlyphScheme.SteamDeck).size,
        )
        assertEquals(
            2,
            ControllerGlyphAssets.drawables(GamepadHintButton.L1R1, ControllerGlyphScheme.SteamController).size,
        )
    }

    @Test
    fun assets_otherStyleFilledDiffersFromOutline() {
        val filled =
            ControllerGlyphAssets
                .drawables(
                    GamepadHintButton.L1,
                    ControllerGlyphScheme.Xbox,
                    otherStyle = ControllerGlyphOtherStyle.Filled,
                ).single()
        val outline =
            ControllerGlyphAssets
                .drawables(
                    GamepadHintButton.L1,
                    ControllerGlyphScheme.Xbox,
                    otherStyle = ControllerGlyphOtherStyle.Outline,
                ).single()
        assertTrue(filled != outline)
    }

    @Test
    fun assets_faceStyleColorDiffersFromWhite() {
        val color =
            ControllerGlyphAssets
                .drawables(
                    GamepadHintButton.A,
                    ControllerGlyphScheme.Xbox,
                    faceStyle = ControllerGlyphFaceStyle.Color,
                ).single()
        val white =
            ControllerGlyphAssets
                .drawables(
                    GamepadHintButton.A,
                    ControllerGlyphScheme.Xbox,
                    faceStyle = ControllerGlyphFaceStyle.White,
                ).single()
        val outline =
            ControllerGlyphAssets
                .drawables(
                    GamepadHintButton.A,
                    ControllerGlyphScheme.Xbox,
                    faceStyle = ControllerGlyphFaceStyle.ColorOutline,
                ).single()
        val dark =
            ControllerGlyphAssets
                .drawables(
                    GamepadHintButton.A,
                    ControllerGlyphScheme.Xbox,
                    faceStyle = ControllerGlyphFaceStyle.Dark,
                ).single()
        assertTrue(color != white)
        assertTrue(outline != dark)
        assertTrue(color != outline)
    }

    @Test
    fun assets_switchColorUsesLetterColorFaces() {
        val color =
            ControllerGlyphAssets
                .drawables(
                    GamepadHintButton.A,
                    ControllerGlyphScheme.Switch,
                    faceStyle = ControllerGlyphFaceStyle.Color,
                ).single()
        val white =
            ControllerGlyphAssets
                .drawables(
                    GamepadHintButton.A,
                    ControllerGlyphScheme.Switch,
                    faceStyle = ControllerGlyphFaceStyle.White,
                ).single()
        assertTrue(color != white)
    }

    @Test
    fun assets_steamDeckColorUsesLetterColorFaces() {
        val color =
            ControllerGlyphAssets
                .drawables(
                    GamepadHintButton.A,
                    ControllerGlyphScheme.SteamDeck,
                    faceStyle = ControllerGlyphFaceStyle.Color,
                ).single()
        val white =
            ControllerGlyphAssets
                .drawables(
                    GamepadHintButton.A,
                    ControllerGlyphScheme.SteamDeck,
                    faceStyle = ControllerGlyphFaceStyle.White,
                ).single()
        assertTrue(color != white)
    }
}
