package com.wajiha.android.detect.probes

object IniKeyParser {
    fun extractValue(text: String, keys: List<String>): String? {
        for (key in keys) {
            val pattern = Regex("""^\s*$key\s*=\s*(.+)\s*$""", RegexOption.MULTILINE)
            val match = pattern.find(text) ?: continue
            val value = match.groupValues[1].trim().trim('"')
            if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) {
                return value
            }
        }
        return null
    }

    fun cfgValue(cfg: String, key: String): String? = extractValue(cfg, listOf(key))
}
