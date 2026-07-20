package com.wajiha.data.prefs

/** Preference values for Apps drawer icon pack and icon shape. */
object IconAppearancePreferences {
    const val SYSTEM_PACK = ""
    const val DEFAULT_SHAPE = "system"

    val shapes =
        listOf(
            "system",
            "circle",
            "squircle",
            "rounded_square",
            "square",
        )

    fun normalizeShape(value: String?): String {
        val raw = value?.trim().orEmpty().ifBlank { DEFAULT_SHAPE }
        return shapes.firstOrNull { it.equals(raw, ignoreCase = true) } ?: DEFAULT_SHAPE
    }

    fun shapeLabel(value: String): String =
        when (normalizeShape(value)) {
            "circle" -> "Circle"
            "squircle" -> "Squircle"
            "rounded_square" -> "Rounded square"
            "square" -> "Square"
            else -> "System"
        }

    fun normalizePackPackage(value: String?): String = value?.trim().orEmpty()
}
