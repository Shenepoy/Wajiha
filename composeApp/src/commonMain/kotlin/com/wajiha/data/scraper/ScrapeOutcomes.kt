package com.wajiha.data.scraper

import kotlinx.serialization.Serializable

/** Why a scrape or source lookup failed (or did not match). */
@Serializable
enum class ScrapeFailureKind {
    NoMatch,
    NotConfigured,
    Auth,
    Network,
    RateLimited,
    Http,
    Parse,
    Download,
    Cancelled,
    Unknown,
    ;

    fun displayLabel(): String =
        when (this) {
            NoMatch -> "No match"
            NotConfigured -> "Not configured"
            Auth -> "Auth"
            Network -> "Network"
            RateLimited -> "Rate limited"
            Http -> "HTTP error"
            Parse -> "Parse error"
            Download -> "Download failed"
            Cancelled -> "Cancelled"
            Unknown -> "Error"
        }
}

@Serializable
data class ScrapeFailure(
    val kind: ScrapeFailureKind,
    val message: String,
    val httpStatus: Int? = null,
) {
    fun displayLabel(): String =
        when (kind) {
            ScrapeFailureKind.Http -> httpStatus?.let { "HTTP $it" } ?: kind.displayLabel()
            else -> kind.displayLabel()
        }
}

/** Result of one source lookup for a game. */
sealed class SourceLookupOutcome {
    data class Hit(
        val candidate: ScrapeCandidate,
    ) : SourceLookupOutcome()

    data object Miss : SourceLookupOutcome()

    data class Failed(
        val failure: ScrapeFailure,
    ) : SourceLookupOutcome()
}

/** Compact per-source line for UI / batch issues (serializable). */
@Serializable
enum class SourceResultStatus {
    @kotlinx.serialization.SerialName("hit")
    Hit,

    @kotlinx.serialization.SerialName("miss")
    Miss,

    @kotlinx.serialization.SerialName("failed")
    Failed,
}

@Serializable
data class SourceResultSummary(
    val sourceId: String,
    val status: SourceResultStatus,
    val kind: ScrapeFailureKind? = null,
    val message: String? = null,
) {
    fun shortLabel(displayName: String = sourceId): String =
        when (status) {
            SourceResultStatus.Hit -> {
                "$displayName: ok"
            }

            SourceResultStatus.Miss -> {
                "$displayName: no match"
            }

            SourceResultStatus.Failed -> {
                val detail =
                    kind?.let { ScrapeFailure(it, message ?: "").displayLabel() }
                        ?: message
                        ?: "error"
                "$displayName: $detail"
            }
        }
}

fun SourceLookupOutcome.toSummary(sourceId: String): SourceResultSummary =
    when (this) {
        is SourceLookupOutcome.Hit -> {
            SourceResultSummary(sourceId, SourceResultStatus.Hit)
        }

        SourceLookupOutcome.Miss -> {
            SourceResultSummary(
                sourceId,
                SourceResultStatus.Miss,
                ScrapeFailureKind.NoMatch,
            )
        }

        is SourceLookupOutcome.Failed -> {
            SourceResultSummary(
                sourceId = sourceId,
                status = SourceResultStatus.Failed,
                kind = failure.kind,
                message = failure.message,
            )
        }
    }

/** Candidates + optional hard failure from a name search. */
data class SourceSearchBundle(
    val candidates: List<ScrapeCandidate>,
    val failure: ScrapeFailure? = null,
) {
    fun toLookupOutcome(): SourceLookupOutcome =
        when {
            failure != null -> SourceLookupOutcome.Failed(failure)
            candidates.isEmpty() -> SourceLookupOutcome.Miss
            else -> SourceLookupOutcome.Hit(candidates.first())
        }
}

/** High-level outcome for one game scrape. */
@Serializable
enum class GameScrapeOutcome {
    /** Metadata and/or media applied successfully. */
    Matched,

    /** Match found but no media saved (or all downloads failed). */
    Partial,

    /** Sources ran; nothing matched. */
    NoMatch,

    /** Hard failure (config, auth, exception, …). */
    Error,
}

/** Result of scraping a single game. */
data class GameScrapeResult(
    val gameId: Long,
    val outcome: GameScrapeOutcome,
    val metadataSource: String? = null,
    val mediaSaved: Int = 0,
    val mediaFailed: Int = 0,
    val failureKind: ScrapeFailureKind? = null,
    val message: String? = null,
    val sourceSummaries: List<SourceResultSummary> = emptyList(),
) {
    fun sourceSummaryLine(displayNames: Map<String, String> = emptyMap()): String? {
        if (sourceSummaries.isEmpty()) return null
        return sourceSummaries.joinToString(" · ") { summary ->
            summary.shortLabel(displayNames[summary.sourceId] ?: summary.sourceId)
        }
    }

    /**
     * UI message for manual/review/detail scrape actions.
     * @return message text and whether the outcome is a success highlight.
     */
    fun userMessage(
        verb: String = "Applied",
        sourceId: String? = null,
    ): Pair<String, Boolean> {
        val sourceLine = sourceSummaryLine()
        return when (outcome) {
            GameScrapeOutcome.Matched -> {
                val base =
                    message
                        ?: "$verb: $mediaSaved media from ${metadataSource ?: sourceId ?: "sources"}"
                base to true
            }

            GameScrapeOutcome.Partial -> {
                val base = message ?: "Partial — $mediaSaved media saved"
                (if (sourceLine != null) "$base\n$sourceLine" else base) to false
            }

            GameScrapeOutcome.NoMatch -> {
                val base = sourceLine ?: message ?: "No match found"
                base to false
            }

            GameScrapeOutcome.Error -> {
                val base = message ?: "Scrape failed"
                (if (sourceLine != null) "$base\n$sourceLine" else base) to false
            }
        }
    }

    companion object {
        fun notConfigured(gameId: Long) =
            GameScrapeResult(
                gameId = gameId,
                outcome = GameScrapeOutcome.Error,
                failureKind = ScrapeFailureKind.NotConfigured,
                message = "No sources configured",
            )

        /** FillGaps when preferred slots are already filled — nothing to fetch. */
        fun alreadyComplete(gameId: Long) =
            GameScrapeResult(
                gameId = gameId,
                outcome = GameScrapeOutcome.Matched,
                message = "Artwork already present — use Force or Manual to replace",
            )

        fun noMatch(
            gameId: Long,
            summaries: List<SourceResultSummary>,
            preferredFailure: ScrapeFailure? = null,
            message: String = "No match",
        ): GameScrapeResult {
            val actionable =
                preferredFailure
                    ?: summaries
                        .filter { it.status == SourceResultStatus.Failed }
                        .mapNotNull { s ->
                            s.kind?.let { ScrapeFailure(it, s.message ?: it.name, null) }
                        }.minByOrNull { it.kind.priority() }
            return if (actionable != null && actionable.kind.isActionableOverNoMatch()) {
                GameScrapeResult(
                    gameId = gameId,
                    outcome = GameScrapeOutcome.Error,
                    failureKind = actionable.kind,
                    message = actionable.message,
                    sourceSummaries = summaries,
                )
            } else {
                GameScrapeResult(
                    gameId = gameId,
                    outcome = GameScrapeOutcome.NoMatch,
                    failureKind = ScrapeFailureKind.NoMatch,
                    message = message,
                    sourceSummaries = summaries,
                )
            }
        }
    }
}

private fun ScrapeFailureKind.priority(): Int =
    when (this) {
        ScrapeFailureKind.Auth -> 0
        ScrapeFailureKind.RateLimited -> 1
        ScrapeFailureKind.NotConfigured -> 2
        ScrapeFailureKind.Network -> 3
        ScrapeFailureKind.Http -> 4
        ScrapeFailureKind.Parse -> 5
        ScrapeFailureKind.Download -> 6
        ScrapeFailureKind.Unknown -> 7
        ScrapeFailureKind.NoMatch -> 8
        ScrapeFailureKind.Cancelled -> 9
    }

private fun ScrapeFailureKind.isActionableOverNoMatch(): Boolean =
    this == ScrapeFailureKind.Auth ||
        this == ScrapeFailureKind.RateLimited ||
        this == ScrapeFailureKind.NotConfigured ||
        this == ScrapeFailureKind.Network

fun classifyHttpStatus(
    status: Int,
    bodyHint: String? = null,
): ScrapeFailure {
    val lower = bodyHint?.lowercase().orEmpty()
    return when {
        status == 401 || status == 403 ||
            "invalid" in lower && ("key" in lower || "auth" in lower || "credential" in lower) ||
            "unauthorized" in lower || "forbidden" in lower -> {
            val message =
                when {
                    "développeur" in lower ||
                        "developpeur" in lower ||
                        "developer" in lower -> {
                        "ScreenScraper developer ID missing or invalid " +
                            "(Settings → Dev ID, or gradle devid — not your user login)"
                    }

                    else -> {
                        "Authentication failed"
                    }
                }
            ScrapeFailure(ScrapeFailureKind.Auth, message, status)
        }

        status == 429 || "rate" in lower && "limit" in lower || "quota" in lower -> {
            ScrapeFailure(ScrapeFailureKind.RateLimited, "Rate limited", status)
        }

        status in 500..599 -> {
            ScrapeFailure(ScrapeFailureKind.Http, "Server error ($status)", status)
        }

        status !in 200..299 -> {
            ScrapeFailure(ScrapeFailureKind.Http, "HTTP $status", status)
        }

        else -> {
            ScrapeFailure(ScrapeFailureKind.Unknown, "Unexpected response", status)
        }
    }
}

fun classifyThrowable(e: Throwable): ScrapeFailure {
    val msg = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "error"
    val lower = msg.lowercase()
    return when {
        "unable to resolve host" in lower ||
            "failed to connect" in lower ||
            "connection" in lower ||
            "timeout" in lower ||
            "network" in lower -> {
            ScrapeFailure(ScrapeFailureKind.Network, msg)
        }

        else -> {
            ScrapeFailure(ScrapeFailureKind.Unknown, msg)
        }
    }
}
