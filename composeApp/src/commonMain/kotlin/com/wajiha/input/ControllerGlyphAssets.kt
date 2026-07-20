package com.wajiha.input

import org.jetbrains.compose.resources.DrawableResource
import wajiha.composeapp.generated.resources.Res
import wajiha.composeapp.generated.resources.kenney_ps_circle_color
import wajiha.composeapp.generated.resources.kenney_ps_circle_color_outline
import wajiha.composeapp.generated.resources.kenney_ps_circle_dark
import wajiha.composeapp.generated.resources.kenney_ps_circle_white
import wajiha.composeapp.generated.resources.kenney_ps_create_dark
import wajiha.composeapp.generated.resources.kenney_ps_create_white
import wajiha.composeapp.generated.resources.kenney_ps_cross_color
import wajiha.composeapp.generated.resources.kenney_ps_cross_color_outline
import wajiha.composeapp.generated.resources.kenney_ps_cross_dark
import wajiha.composeapp.generated.resources.kenney_ps_cross_white
import wajiha.composeapp.generated.resources.kenney_ps_dpad_horizontal_dark
import wajiha.composeapp.generated.resources.kenney_ps_dpad_horizontal_white
import wajiha.composeapp.generated.resources.kenney_ps_dpad_right_dark
import wajiha.composeapp.generated.resources.kenney_ps_dpad_right_white
import wajiha.composeapp.generated.resources.kenney_ps_dpad_vertical_dark
import wajiha.composeapp.generated.resources.kenney_ps_dpad_vertical_white
import wajiha.composeapp.generated.resources.kenney_ps_l1_dark
import wajiha.composeapp.generated.resources.kenney_ps_l1_white
import wajiha.composeapp.generated.resources.kenney_ps_l2_dark
import wajiha.composeapp.generated.resources.kenney_ps_l2_white
import wajiha.composeapp.generated.resources.kenney_ps_options_dark
import wajiha.composeapp.generated.resources.kenney_ps_options_white
import wajiha.composeapp.generated.resources.kenney_ps_r1_dark
import wajiha.composeapp.generated.resources.kenney_ps_r1_white
import wajiha.composeapp.generated.resources.kenney_ps_r2_dark
import wajiha.composeapp.generated.resources.kenney_ps_r2_white
import wajiha.composeapp.generated.resources.kenney_ps_square_color
import wajiha.composeapp.generated.resources.kenney_ps_square_color_outline
import wajiha.composeapp.generated.resources.kenney_ps_square_dark
import wajiha.composeapp.generated.resources.kenney_ps_square_white
import wajiha.composeapp.generated.resources.kenney_ps_stick_l_press_dark
import wajiha.composeapp.generated.resources.kenney_ps_stick_l_press_white
import wajiha.composeapp.generated.resources.kenney_ps_triangle_color
import wajiha.composeapp.generated.resources.kenney_ps_triangle_color_outline
import wajiha.composeapp.generated.resources.kenney_ps_triangle_dark
import wajiha.composeapp.generated.resources.kenney_ps_triangle_white
import wajiha.composeapp.generated.resources.kenney_steam_a_color
import wajiha.composeapp.generated.resources.kenney_steam_a_color_outline
import wajiha.composeapp.generated.resources.kenney_steam_a_dark
import wajiha.composeapp.generated.resources.kenney_steam_a_white
import wajiha.composeapp.generated.resources.kenney_steam_b_color
import wajiha.composeapp.generated.resources.kenney_steam_b_color_outline
import wajiha.composeapp.generated.resources.kenney_steam_b_dark
import wajiha.composeapp.generated.resources.kenney_steam_b_white
import wajiha.composeapp.generated.resources.kenney_steam_dpad_horizontal_dark
import wajiha.composeapp.generated.resources.kenney_steam_dpad_horizontal_white
import wajiha.composeapp.generated.resources.kenney_steam_dpad_right_dark
import wajiha.composeapp.generated.resources.kenney_steam_dpad_right_white
import wajiha.composeapp.generated.resources.kenney_steam_dpad_vertical_dark
import wajiha.composeapp.generated.resources.kenney_steam_dpad_vertical_white
import wajiha.composeapp.generated.resources.kenney_steam_lb_dark
import wajiha.composeapp.generated.resources.kenney_steam_lb_white
import wajiha.composeapp.generated.resources.kenney_steam_lt_dark
import wajiha.composeapp.generated.resources.kenney_steam_lt_white
import wajiha.composeapp.generated.resources.kenney_steam_options_dark
import wajiha.composeapp.generated.resources.kenney_steam_options_white
import wajiha.composeapp.generated.resources.kenney_steam_rb_dark
import wajiha.composeapp.generated.resources.kenney_steam_rb_white
import wajiha.composeapp.generated.resources.kenney_steam_rt_dark
import wajiha.composeapp.generated.resources.kenney_steam_rt_white
import wajiha.composeapp.generated.resources.kenney_steam_stick_l_press_dark
import wajiha.composeapp.generated.resources.kenney_steam_stick_l_press_white
import wajiha.composeapp.generated.resources.kenney_steam_view_dark
import wajiha.composeapp.generated.resources.kenney_steam_view_white
import wajiha.composeapp.generated.resources.kenney_steam_x_color
import wajiha.composeapp.generated.resources.kenney_steam_x_color_outline
import wajiha.composeapp.generated.resources.kenney_steam_x_dark
import wajiha.composeapp.generated.resources.kenney_steam_x_white
import wajiha.composeapp.generated.resources.kenney_steam_y_color
import wajiha.composeapp.generated.resources.kenney_steam_y_color_outline
import wajiha.composeapp.generated.resources.kenney_steam_y_dark
import wajiha.composeapp.generated.resources.kenney_steam_y_white
import wajiha.composeapp.generated.resources.kenney_steamdeck_a_dark
import wajiha.composeapp.generated.resources.kenney_steamdeck_a_white
import wajiha.composeapp.generated.resources.kenney_steamdeck_b_dark
import wajiha.composeapp.generated.resources.kenney_steamdeck_b_white
import wajiha.composeapp.generated.resources.kenney_steamdeck_dpad_horizontal_dark
import wajiha.composeapp.generated.resources.kenney_steamdeck_dpad_horizontal_white
import wajiha.composeapp.generated.resources.kenney_steamdeck_dpad_right_dark
import wajiha.composeapp.generated.resources.kenney_steamdeck_dpad_right_white
import wajiha.composeapp.generated.resources.kenney_steamdeck_dpad_vertical_dark
import wajiha.composeapp.generated.resources.kenney_steamdeck_dpad_vertical_white
import wajiha.composeapp.generated.resources.kenney_steamdeck_l1_dark
import wajiha.composeapp.generated.resources.kenney_steamdeck_l1_white
import wajiha.composeapp.generated.resources.kenney_steamdeck_l2_dark
import wajiha.composeapp.generated.resources.kenney_steamdeck_l2_white
import wajiha.composeapp.generated.resources.kenney_steamdeck_options_dark
import wajiha.composeapp.generated.resources.kenney_steamdeck_options_white
import wajiha.composeapp.generated.resources.kenney_steamdeck_r1_dark
import wajiha.composeapp.generated.resources.kenney_steamdeck_r1_white
import wajiha.composeapp.generated.resources.kenney_steamdeck_r2_dark
import wajiha.composeapp.generated.resources.kenney_steamdeck_r2_white
import wajiha.composeapp.generated.resources.kenney_steamdeck_stick_l_press_dark
import wajiha.composeapp.generated.resources.kenney_steamdeck_stick_l_press_white
import wajiha.composeapp.generated.resources.kenney_steamdeck_view_dark
import wajiha.composeapp.generated.resources.kenney_steamdeck_view_white
import wajiha.composeapp.generated.resources.kenney_steamdeck_x_dark
import wajiha.composeapp.generated.resources.kenney_steamdeck_x_white
import wajiha.composeapp.generated.resources.kenney_steamdeck_y_dark
import wajiha.composeapp.generated.resources.kenney_steamdeck_y_white
import wajiha.composeapp.generated.resources.kenney_switch_a_dark
import wajiha.composeapp.generated.resources.kenney_switch_a_white
import wajiha.composeapp.generated.resources.kenney_switch_b_dark
import wajiha.composeapp.generated.resources.kenney_switch_b_white
import wajiha.composeapp.generated.resources.kenney_switch_dpad_horizontal_dark
import wajiha.composeapp.generated.resources.kenney_switch_dpad_horizontal_white
import wajiha.composeapp.generated.resources.kenney_switch_dpad_right_dark
import wajiha.composeapp.generated.resources.kenney_switch_dpad_right_white
import wajiha.composeapp.generated.resources.kenney_switch_dpad_vertical_dark
import wajiha.composeapp.generated.resources.kenney_switch_dpad_vertical_white
import wajiha.composeapp.generated.resources.kenney_switch_l_dark
import wajiha.composeapp.generated.resources.kenney_switch_l_white
import wajiha.composeapp.generated.resources.kenney_switch_minus_dark
import wajiha.composeapp.generated.resources.kenney_switch_minus_white
import wajiha.composeapp.generated.resources.kenney_switch_plus_dark
import wajiha.composeapp.generated.resources.kenney_switch_plus_white
import wajiha.composeapp.generated.resources.kenney_switch_r_dark
import wajiha.composeapp.generated.resources.kenney_switch_r_white
import wajiha.composeapp.generated.resources.kenney_switch_stick_l_press_dark
import wajiha.composeapp.generated.resources.kenney_switch_stick_l_press_white
import wajiha.composeapp.generated.resources.kenney_switch_x_dark
import wajiha.composeapp.generated.resources.kenney_switch_x_white
import wajiha.composeapp.generated.resources.kenney_switch_y_dark
import wajiha.composeapp.generated.resources.kenney_switch_y_white
import wajiha.composeapp.generated.resources.kenney_switch_zl_dark
import wajiha.composeapp.generated.resources.kenney_switch_zl_white
import wajiha.composeapp.generated.resources.kenney_switch_zr_dark
import wajiha.composeapp.generated.resources.kenney_switch_zr_white
import wajiha.composeapp.generated.resources.kenney_xbox_a_color
import wajiha.composeapp.generated.resources.kenney_xbox_a_color_outline
import wajiha.composeapp.generated.resources.kenney_xbox_a_dark
import wajiha.composeapp.generated.resources.kenney_xbox_a_white
import wajiha.composeapp.generated.resources.kenney_xbox_b_color
import wajiha.composeapp.generated.resources.kenney_xbox_b_color_outline
import wajiha.composeapp.generated.resources.kenney_xbox_b_dark
import wajiha.composeapp.generated.resources.kenney_xbox_b_white
import wajiha.composeapp.generated.resources.kenney_xbox_dpad_horizontal_dark
import wajiha.composeapp.generated.resources.kenney_xbox_dpad_horizontal_white
import wajiha.composeapp.generated.resources.kenney_xbox_dpad_right_dark
import wajiha.composeapp.generated.resources.kenney_xbox_dpad_right_white
import wajiha.composeapp.generated.resources.kenney_xbox_dpad_vertical_dark
import wajiha.composeapp.generated.resources.kenney_xbox_dpad_vertical_white
import wajiha.composeapp.generated.resources.kenney_xbox_lb_dark
import wajiha.composeapp.generated.resources.kenney_xbox_lb_white
import wajiha.composeapp.generated.resources.kenney_xbox_lt_dark
import wajiha.composeapp.generated.resources.kenney_xbox_lt_white
import wajiha.composeapp.generated.resources.kenney_xbox_menu_dark
import wajiha.composeapp.generated.resources.kenney_xbox_menu_white
import wajiha.composeapp.generated.resources.kenney_xbox_rb_dark
import wajiha.composeapp.generated.resources.kenney_xbox_rb_white
import wajiha.composeapp.generated.resources.kenney_xbox_rt_dark
import wajiha.composeapp.generated.resources.kenney_xbox_rt_white
import wajiha.composeapp.generated.resources.kenney_xbox_stick_l_press_dark
import wajiha.composeapp.generated.resources.kenney_xbox_stick_l_press_white
import wajiha.composeapp.generated.resources.kenney_xbox_view_dark
import wajiha.composeapp.generated.resources.kenney_xbox_view_white
import wajiha.composeapp.generated.resources.kenney_xbox_x_color
import wajiha.composeapp.generated.resources.kenney_xbox_x_color_outline
import wajiha.composeapp.generated.resources.kenney_xbox_x_dark
import wajiha.composeapp.generated.resources.kenney_xbox_x_white
import wajiha.composeapp.generated.resources.kenney_xbox_y_color
import wajiha.composeapp.generated.resources.kenney_xbox_y_color_outline
import wajiha.composeapp.generated.resources.kenney_xbox_y_dark
import wajiha.composeapp.generated.resources.kenney_xbox_y_white

/**
 * Kenney Input Prompts (CC0) drawable mapping for action-bar glyphs.
 * https://kenney.nl/assets/input-prompts
 *
 * Face buttons honor [ControllerGlyphFaceStyle]. Other buttons honor
 * [ControllerGlyphOtherStyle] (filled vs outline). Empty = fall back to text label.
 */
object ControllerGlyphAssets {
    fun drawables(
        button: GamepadHintButton,
        scheme: ControllerGlyphScheme,
        faceStyle: ControllerGlyphFaceStyle = ControllerGlyphFaceStyle.Color,
        otherStyle: ControllerGlyphOtherStyle = ControllerGlyphOtherStyle.Filled,
    ): List<DrawableResource> {
        val resolved =
            when (scheme) {
                ControllerGlyphScheme.Auto -> ControllerGlyphScheme.Xbox
                ControllerGlyphScheme.Text -> return emptyList()
                else -> scheme
            }
        return when (resolved) {
            ControllerGlyphScheme.PlayStation -> playstation(button, faceStyle, otherStyle)
            ControllerGlyphScheme.Switch -> switch(button, faceStyle, otherStyle)
            ControllerGlyphScheme.SteamDeck -> steamDeck(button, faceStyle, otherStyle)
            ControllerGlyphScheme.SteamController -> steamController(button, faceStyle, otherStyle)
            ControllerGlyphScheme.Xbox, ControllerGlyphScheme.Auto -> xbox(button, faceStyle, otherStyle)
            ControllerGlyphScheme.Text -> emptyList()
        }
    }

    private data class FaceSet(
        val white: DrawableResource,
        val dark: DrawableResource,
        val color: DrawableResource? = null,
        val colorOutline: DrawableResource? = null,
    ) {
        fun resolve(style: ControllerGlyphFaceStyle): DrawableResource =
            when (style) {
                ControllerGlyphFaceStyle.Color -> color ?: white
                ControllerGlyphFaceStyle.ColorOutline -> colorOutline ?: dark
                ControllerGlyphFaceStyle.White -> white
                ControllerGlyphFaceStyle.Dark -> dark
            }
    }

    private fun other(
        filled: DrawableResource,
        outline: DrawableResource,
        style: ControllerGlyphOtherStyle,
    ): DrawableResource =
        when (style) {
            ControllerGlyphOtherStyle.Filled -> filled
            ControllerGlyphOtherStyle.Outline -> outline
        }

    private fun xbox(
        button: GamepadHintButton,
        faceStyle: ControllerGlyphFaceStyle,
        otherStyle: ControllerGlyphOtherStyle,
    ): List<DrawableResource> =
        when (button) {
            GamepadHintButton.A -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_xbox_a_white,
                        Res.drawable.kenney_xbox_a_dark,
                        Res.drawable.kenney_xbox_a_color,
                        Res.drawable.kenney_xbox_a_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.B -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_xbox_b_white,
                        Res.drawable.kenney_xbox_b_dark,
                        Res.drawable.kenney_xbox_b_color,
                        Res.drawable.kenney_xbox_b_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.X -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_xbox_x_white,
                        Res.drawable.kenney_xbox_x_dark,
                        Res.drawable.kenney_xbox_x_color,
                        Res.drawable.kenney_xbox_x_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.Y -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_xbox_y_white,
                        Res.drawable.kenney_xbox_y_dark,
                        Res.drawable.kenney_xbox_y_color,
                        Res.drawable.kenney_xbox_y_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.L1 -> {
                listOf(other(Res.drawable.kenney_xbox_lb_white, Res.drawable.kenney_xbox_lb_dark, otherStyle))
            }

            GamepadHintButton.R1 -> {
                listOf(other(Res.drawable.kenney_xbox_rb_white, Res.drawable.kenney_xbox_rb_dark, otherStyle))
            }

            GamepadHintButton.L1R1 -> {
                listOf(
                    other(Res.drawable.kenney_xbox_lb_white, Res.drawable.kenney_xbox_lb_dark, otherStyle),
                    other(Res.drawable.kenney_xbox_rb_white, Res.drawable.kenney_xbox_rb_dark, otherStyle),
                )
            }

            GamepadHintButton.L2 -> {
                listOf(other(Res.drawable.kenney_xbox_lt_white, Res.drawable.kenney_xbox_lt_dark, otherStyle))
            }

            GamepadHintButton.R2 -> {
                listOf(other(Res.drawable.kenney_xbox_rt_white, Res.drawable.kenney_xbox_rt_dark, otherStyle))
            }

            GamepadHintButton.Select -> {
                listOf(other(Res.drawable.kenney_xbox_view_white, Res.drawable.kenney_xbox_view_dark, otherStyle))
            }

            GamepadHintButton.Start -> {
                listOf(other(Res.drawable.kenney_xbox_menu_white, Res.drawable.kenney_xbox_menu_dark, otherStyle))
            }

            GamepadHintButton.L3 -> {
                listOf(
                    other(
                        Res.drawable.kenney_xbox_stick_l_press_white,
                        Res.drawable.kenney_xbox_stick_l_press_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadUpDown -> {
                listOf(
                    other(
                        Res.drawable.kenney_xbox_dpad_vertical_white,
                        Res.drawable.kenney_xbox_dpad_vertical_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadLeftRight -> {
                listOf(
                    other(
                        Res.drawable.kenney_xbox_dpad_horizontal_white,
                        Res.drawable.kenney_xbox_dpad_horizontal_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadRight -> {
                listOf(
                    other(
                        Res.drawable.kenney_xbox_dpad_right_white,
                        Res.drawable.kenney_xbox_dpad_right_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.Search -> {
                emptyList()
            }
        }

    private fun playstation(
        button: GamepadHintButton,
        faceStyle: ControllerGlyphFaceStyle,
        otherStyle: ControllerGlyphOtherStyle,
    ): List<DrawableResource> =
        when (button) {
            GamepadHintButton.A -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_ps_cross_white,
                        Res.drawable.kenney_ps_cross_dark,
                        Res.drawable.kenney_ps_cross_color,
                        Res.drawable.kenney_ps_cross_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.B -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_ps_circle_white,
                        Res.drawable.kenney_ps_circle_dark,
                        Res.drawable.kenney_ps_circle_color,
                        Res.drawable.kenney_ps_circle_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.X -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_ps_square_white,
                        Res.drawable.kenney_ps_square_dark,
                        Res.drawable.kenney_ps_square_color,
                        Res.drawable.kenney_ps_square_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.Y -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_ps_triangle_white,
                        Res.drawable.kenney_ps_triangle_dark,
                        Res.drawable.kenney_ps_triangle_color,
                        Res.drawable.kenney_ps_triangle_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.L1 -> {
                listOf(other(Res.drawable.kenney_ps_l1_white, Res.drawable.kenney_ps_l1_dark, otherStyle))
            }

            GamepadHintButton.R1 -> {
                listOf(other(Res.drawable.kenney_ps_r1_white, Res.drawable.kenney_ps_r1_dark, otherStyle))
            }

            GamepadHintButton.L1R1 -> {
                listOf(
                    other(Res.drawable.kenney_ps_l1_white, Res.drawable.kenney_ps_l1_dark, otherStyle),
                    other(Res.drawable.kenney_ps_r1_white, Res.drawable.kenney_ps_r1_dark, otherStyle),
                )
            }

            GamepadHintButton.L2 -> {
                listOf(other(Res.drawable.kenney_ps_l2_white, Res.drawable.kenney_ps_l2_dark, otherStyle))
            }

            GamepadHintButton.R2 -> {
                listOf(other(Res.drawable.kenney_ps_r2_white, Res.drawable.kenney_ps_r2_dark, otherStyle))
            }

            GamepadHintButton.Select -> {
                listOf(other(Res.drawable.kenney_ps_create_white, Res.drawable.kenney_ps_create_dark, otherStyle))
            }

            GamepadHintButton.Start -> {
                listOf(other(Res.drawable.kenney_ps_options_white, Res.drawable.kenney_ps_options_dark, otherStyle))
            }

            GamepadHintButton.L3 -> {
                listOf(
                    other(
                        Res.drawable.kenney_ps_stick_l_press_white,
                        Res.drawable.kenney_ps_stick_l_press_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadUpDown -> {
                listOf(
                    other(
                        Res.drawable.kenney_ps_dpad_vertical_white,
                        Res.drawable.kenney_ps_dpad_vertical_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadLeftRight -> {
                listOf(
                    other(
                        Res.drawable.kenney_ps_dpad_horizontal_white,
                        Res.drawable.kenney_ps_dpad_horizontal_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadRight -> {
                listOf(
                    other(
                        Res.drawable.kenney_ps_dpad_right_white,
                        Res.drawable.kenney_ps_dpad_right_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.Search -> {
                emptyList()
            }
        }

    private fun switch(
        button: GamepadHintButton,
        faceStyle: ControllerGlyphFaceStyle,
        otherStyle: ControllerGlyphOtherStyle,
    ): List<DrawableResource> =
        when (button) {
            // Display-only Nintendo face layout (confirm → B glyph, back → A glyph).
            // Kenney has no Switch color set — reuse Xbox lettered color faces.
            GamepadHintButton.A -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_switch_b_white,
                        Res.drawable.kenney_switch_b_dark,
                        Res.drawable.kenney_xbox_b_color,
                        Res.drawable.kenney_xbox_b_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.B -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_switch_a_white,
                        Res.drawable.kenney_switch_a_dark,
                        Res.drawable.kenney_xbox_a_color,
                        Res.drawable.kenney_xbox_a_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.X -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_switch_y_white,
                        Res.drawable.kenney_switch_y_dark,
                        Res.drawable.kenney_xbox_y_color,
                        Res.drawable.kenney_xbox_y_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.Y -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_switch_x_white,
                        Res.drawable.kenney_switch_x_dark,
                        Res.drawable.kenney_xbox_x_color,
                        Res.drawable.kenney_xbox_x_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.L1 -> {
                listOf(other(Res.drawable.kenney_switch_l_white, Res.drawable.kenney_switch_l_dark, otherStyle))
            }

            GamepadHintButton.R1 -> {
                listOf(other(Res.drawable.kenney_switch_r_white, Res.drawable.kenney_switch_r_dark, otherStyle))
            }

            GamepadHintButton.L1R1 -> {
                listOf(
                    other(Res.drawable.kenney_switch_l_white, Res.drawable.kenney_switch_l_dark, otherStyle),
                    other(Res.drawable.kenney_switch_r_white, Res.drawable.kenney_switch_r_dark, otherStyle),
                )
            }

            GamepadHintButton.L2 -> {
                listOf(other(Res.drawable.kenney_switch_zl_white, Res.drawable.kenney_switch_zl_dark, otherStyle))
            }

            GamepadHintButton.R2 -> {
                listOf(other(Res.drawable.kenney_switch_zr_white, Res.drawable.kenney_switch_zr_dark, otherStyle))
            }

            GamepadHintButton.Select -> {
                listOf(
                    other(Res.drawable.kenney_switch_minus_white, Res.drawable.kenney_switch_minus_dark, otherStyle),
                )
            }

            GamepadHintButton.Start -> {
                listOf(
                    other(Res.drawable.kenney_switch_plus_white, Res.drawable.kenney_switch_plus_dark, otherStyle),
                )
            }

            GamepadHintButton.L3 -> {
                listOf(
                    other(
                        Res.drawable.kenney_switch_stick_l_press_white,
                        Res.drawable.kenney_switch_stick_l_press_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadUpDown -> {
                listOf(
                    other(
                        Res.drawable.kenney_switch_dpad_vertical_white,
                        Res.drawable.kenney_switch_dpad_vertical_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadLeftRight -> {
                listOf(
                    other(
                        Res.drawable.kenney_switch_dpad_horizontal_white,
                        Res.drawable.kenney_switch_dpad_horizontal_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadRight -> {
                listOf(
                    other(
                        Res.drawable.kenney_switch_dpad_right_white,
                        Res.drawable.kenney_switch_dpad_right_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.Search -> {
                emptyList()
            }
        }

    private fun steamDeck(
        button: GamepadHintButton,
        faceStyle: ControllerGlyphFaceStyle,
        otherStyle: ControllerGlyphOtherStyle,
    ): List<DrawableResource> =
        when (button) {
            // No Kenney Steam Deck color set — reuse Xbox lettered color faces.
            GamepadHintButton.A -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_steamdeck_a_white,
                        Res.drawable.kenney_steamdeck_a_dark,
                        Res.drawable.kenney_xbox_a_color,
                        Res.drawable.kenney_xbox_a_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.B -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_steamdeck_b_white,
                        Res.drawable.kenney_steamdeck_b_dark,
                        Res.drawable.kenney_xbox_b_color,
                        Res.drawable.kenney_xbox_b_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.X -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_steamdeck_x_white,
                        Res.drawable.kenney_steamdeck_x_dark,
                        Res.drawable.kenney_xbox_x_color,
                        Res.drawable.kenney_xbox_x_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.Y -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_steamdeck_y_white,
                        Res.drawable.kenney_steamdeck_y_dark,
                        Res.drawable.kenney_xbox_y_color,
                        Res.drawable.kenney_xbox_y_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.L1 -> {
                listOf(
                    other(Res.drawable.kenney_steamdeck_l1_white, Res.drawable.kenney_steamdeck_l1_dark, otherStyle),
                )
            }

            GamepadHintButton.R1 -> {
                listOf(
                    other(Res.drawable.kenney_steamdeck_r1_white, Res.drawable.kenney_steamdeck_r1_dark, otherStyle),
                )
            }

            GamepadHintButton.L1R1 -> {
                listOf(
                    other(Res.drawable.kenney_steamdeck_l1_white, Res.drawable.kenney_steamdeck_l1_dark, otherStyle),
                    other(Res.drawable.kenney_steamdeck_r1_white, Res.drawable.kenney_steamdeck_r1_dark, otherStyle),
                )
            }

            GamepadHintButton.L2 -> {
                listOf(
                    other(Res.drawable.kenney_steamdeck_l2_white, Res.drawable.kenney_steamdeck_l2_dark, otherStyle),
                )
            }

            GamepadHintButton.R2 -> {
                listOf(
                    other(Res.drawable.kenney_steamdeck_r2_white, Res.drawable.kenney_steamdeck_r2_dark, otherStyle),
                )
            }

            GamepadHintButton.Select -> {
                listOf(
                    other(
                        Res.drawable.kenney_steamdeck_view_white,
                        Res.drawable.kenney_steamdeck_view_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.Start -> {
                listOf(
                    other(
                        Res.drawable.kenney_steamdeck_options_white,
                        Res.drawable.kenney_steamdeck_options_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.L3 -> {
                listOf(
                    other(
                        Res.drawable.kenney_steamdeck_stick_l_press_white,
                        Res.drawable.kenney_steamdeck_stick_l_press_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadUpDown -> {
                listOf(
                    other(
                        Res.drawable.kenney_steamdeck_dpad_vertical_white,
                        Res.drawable.kenney_steamdeck_dpad_vertical_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadLeftRight -> {
                listOf(
                    other(
                        Res.drawable.kenney_steamdeck_dpad_horizontal_white,
                        Res.drawable.kenney_steamdeck_dpad_horizontal_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadRight -> {
                listOf(
                    other(
                        Res.drawable.kenney_steamdeck_dpad_right_white,
                        Res.drawable.kenney_steamdeck_dpad_right_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.Search -> {
                emptyList()
            }
        }

    private fun steamController(
        button: GamepadHintButton,
        faceStyle: ControllerGlyphFaceStyle,
        otherStyle: ControllerGlyphOtherStyle,
    ): List<DrawableResource> =
        when (button) {
            GamepadHintButton.A -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_steam_a_white,
                        Res.drawable.kenney_steam_a_dark,
                        Res.drawable.kenney_steam_a_color,
                        Res.drawable.kenney_steam_a_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.B -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_steam_b_white,
                        Res.drawable.kenney_steam_b_dark,
                        Res.drawable.kenney_steam_b_color,
                        Res.drawable.kenney_steam_b_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.X -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_steam_x_white,
                        Res.drawable.kenney_steam_x_dark,
                        Res.drawable.kenney_steam_x_color,
                        Res.drawable.kenney_steam_x_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.Y -> {
                listOf(
                    FaceSet(
                        Res.drawable.kenney_steam_y_white,
                        Res.drawable.kenney_steam_y_dark,
                        Res.drawable.kenney_steam_y_color,
                        Res.drawable.kenney_steam_y_color_outline,
                    ).resolve(faceStyle),
                )
            }

            GamepadHintButton.L1 -> {
                listOf(other(Res.drawable.kenney_steam_lb_white, Res.drawable.kenney_steam_lb_dark, otherStyle))
            }

            GamepadHintButton.R1 -> {
                listOf(other(Res.drawable.kenney_steam_rb_white, Res.drawable.kenney_steam_rb_dark, otherStyle))
            }

            GamepadHintButton.L1R1 -> {
                listOf(
                    other(Res.drawable.kenney_steam_lb_white, Res.drawable.kenney_steam_lb_dark, otherStyle),
                    other(Res.drawable.kenney_steam_rb_white, Res.drawable.kenney_steam_rb_dark, otherStyle),
                )
            }

            GamepadHintButton.L2 -> {
                listOf(other(Res.drawable.kenney_steam_lt_white, Res.drawable.kenney_steam_lt_dark, otherStyle))
            }

            GamepadHintButton.R2 -> {
                listOf(other(Res.drawable.kenney_steam_rt_white, Res.drawable.kenney_steam_rt_dark, otherStyle))
            }

            GamepadHintButton.Select -> {
                listOf(other(Res.drawable.kenney_steam_view_white, Res.drawable.kenney_steam_view_dark, otherStyle))
            }

            GamepadHintButton.Start -> {
                listOf(
                    other(Res.drawable.kenney_steam_options_white, Res.drawable.kenney_steam_options_dark, otherStyle),
                )
            }

            GamepadHintButton.L3 -> {
                listOf(
                    other(
                        Res.drawable.kenney_steam_stick_l_press_white,
                        Res.drawable.kenney_steam_stick_l_press_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadUpDown -> {
                listOf(
                    other(
                        Res.drawable.kenney_steam_dpad_vertical_white,
                        Res.drawable.kenney_steam_dpad_vertical_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadLeftRight -> {
                listOf(
                    other(
                        Res.drawable.kenney_steam_dpad_horizontal_white,
                        Res.drawable.kenney_steam_dpad_horizontal_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.DpadRight -> {
                listOf(
                    other(
                        Res.drawable.kenney_steam_dpad_right_white,
                        Res.drawable.kenney_steam_dpad_right_dark,
                        otherStyle,
                    ),
                )
            }

            GamepadHintButton.Search -> {
                emptyList()
            }
        }
}
