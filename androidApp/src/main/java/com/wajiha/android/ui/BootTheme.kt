package com.wajiha.android.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import com.wajiha.android.R

/**
 * Synchronous last-known theme for window background before DataStore/Compose
 * loads. Without this, DayNight + default AppSettings(theme=dark) flash a near-
 * black frame on cold start when the user actually uses light theme.
 */
object BootTheme {
    private const val PREFS = "wajiha_boot"
    private const val KEY_DARK = "theme_dark"

    fun applyWindowBackground(activity: Activity) {
        val dark = isDark(activity)
        val colorRes = if (dark) R.color.wajiha_background else R.color.wajiha_background_light
        val color = ContextCompat.getColor(activity, colorRes)
        activity.window.setBackgroundDrawable(ColorDrawable(color))
        activity.window.decorView.setBackgroundColor(color)
    }

    fun persist(
        context: Context,
        themePreference: String,
    ) {
        val dark =
            when (themePreference) {
                "light" -> {
                    false
                }

                "dark" -> {
                    true
                }

                else -> {
                    val night =
                        context.resources.configuration.uiMode and
                            Configuration.UI_MODE_NIGHT_MASK
                    night == Configuration.UI_MODE_NIGHT_YES
                }
            }
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK, dark)
            .apply()
    }

    fun isDark(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // Prefer light when unknown — DayNight default-dark was the cold-start flash.
            .getBoolean(KEY_DARK, false)
}
