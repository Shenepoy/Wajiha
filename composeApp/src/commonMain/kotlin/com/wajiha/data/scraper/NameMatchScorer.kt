package com.wajiha.data.scraper

/**
 * Cocoon-style game-name confidence for Auto scrape.
 *
 * Hash matches always pass. Name / autocomplete hits must score at or above
 * [AutoConfidenceThreshold] (normalized Levenshtein similarity) or they are
 * rejected before media is fetched — matching Cocoon's
 * `No confident match: score %.2f below %.2f` gate.
 */
object NameMatchScorer {
    /** Minimum similarity (0–1) for Auto to accept a non-hash name match. */
    const val AutoConfidenceThreshold = 0.70f

    private val ParenOrBracket = Regex("""[\(\[\{].*?[\)\]\}]""")
    private val NonAlnum = Regex("""[^a-z0-9]+""")
    private val Whitespace = Regex("""\s+""")

    /**
     * Lowercase, strip path/extension, drop region/dump tags, collapse
     * non-alphanumerics to spaces.
     */
    fun normalize(raw: String): String {
        var s = raw.trim().lowercase()
        val slash = maxOf(s.lastIndexOf('/'), s.lastIndexOf('\\'))
        if (slash >= 0 && slash < s.lastIndex) s = s.substring(slash + 1)
        val dot = s.lastIndexOf('.')
        if (dot > 0) s = s.substring(0, dot)
        s = ParenOrBracket.replace(s, " ")
        s = NonAlnum.replace(s, " ")
        return s.split(Whitespace).filter { it.isNotBlank() }.joinToString(" ")
    }

    /**
     * Similarity in `0f..1f`. Exact normalized match → 1. Containment of the
     * shorter string → at least the Levenshtein ratio of the two strings.
     */
    fun score(
        queryName: String,
        candidateName: String,
    ): Float {
        val a = normalize(queryName)
        val b = normalize(candidateName)
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f
        val ratio = levenshteinRatio(a, b)
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        if (longer.contains(shorter) && shorter.length >= 3) {
            // Contained title (e.g. "zelda" vs "the legend of zelda") — floor by length ratio.
            val containFloor = shorter.length.toFloat() / longer.length.toFloat()
            return maxOf(ratio, containFloor.coerceAtMost(0.95f))
        }
        return ratio
    }

    /** True when Auto may accept this hit. Hash always passes. */
    fun passesAutoGate(
        confidence: MatchConfidence,
        queryName: String,
        candidateName: String,
    ): Boolean {
        if (confidence == MatchConfidence.Hash) return true
        return score(queryName, candidateName) >= AutoConfidenceThreshold
    }

    private fun levenshteinRatio(
        a: String,
        b: String,
    ): Float {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1f
        val dist = levenshteinDistance(a, b)
        return (1f - dist.toFloat() / maxLen.toFloat()).coerceIn(0f, 1f)
    }

    internal fun levenshteinDistance(
        a: String,
        b: String,
    ): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] =
                    minOf(
                        cur[j - 1] + 1,
                        prev[j] + 1,
                        prev[j - 1] + cost,
                    )
            }
            for (j in prev.indices) prev[j] = cur[j]
        }
        return prev[b.length]
    }
}
