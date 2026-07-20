package com.wajiha.android.display

internal enum class PrimaryLaunchStrategy {
    Reorder,
    Recreate,
}

internal fun primaryLaunchStrategy(hasDisplayZeroTask: Boolean): PrimaryLaunchStrategy =
    if (hasDisplayZeroTask) {
        PrimaryLaunchStrategy.Reorder
    } else {
        PrimaryLaunchStrategy.Recreate
    }

internal data class SecondaryRecoveryGate(
    val suppressReason: String? = null,
    val deferMs: Long = 0L,
)

internal fun interface SecondaryRecoveryScheduler {
    fun post(
        delayMs: Long,
        action: () -> Unit,
    )
}

internal interface SecondaryRecoveryEffects {
    fun gate(): SecondaryRecoveryGate

    fun isHealthy(displayId: Int): Boolean

    fun recover(
        displayId: Int,
        allowLaunch: Boolean,
    )

    fun onRecovered(displayId: Int)

    fun onExhausted(displayId: Int)

    fun log(message: String)
}

/**
 * Owns one bounded recovery sequence for the secondary HOME task.
 *
 * Duplicate lifecycle callbacks join the active sequence. A generation token
 * invalidates posted callbacks on resume, suppression, or explicit teardown.
 */
internal class SecondaryRecoveryController(
    private val scheduler: SecondaryRecoveryScheduler,
    private val effects: SecondaryRecoveryEffects,
    private val retryDelaysMs: LongArray = longArrayOf(80L, 120L, 300L),
    private val finalVerifyDelayMs: Long = 500L,
) {
    private var generation = 0L
    private var activeDisplayId: Int? = null
    private var activeReason: String? = null

    val isActive: Boolean
        get() = activeDisplayId != null

    fun request(
        displayId: Int,
        reason: String,
    ) {
        if (effects.isHealthy(displayId)) return
        if (activeDisplayId == displayId) {
            effects.log("coalesced displayId=$displayId reason=$reason activeReason=$activeReason")
            return
        }
        cancel("replace")
        activeDisplayId = displayId
        activeReason = reason
        generation += 1
        val token = generation
        effects.log("start displayId=$displayId reason=$reason")
        attempt(token, displayId, attempt = 0)
    }

    fun probe(
        displayId: Int,
        reason: String,
    ) {
        if (!effects.isHealthy(displayId)) {
            request(displayId, reason)
        }
    }

    fun onResumed(displayId: Int) {
        if (activeDisplayId != displayId) return
        finishRecovered(displayId)
    }

    fun cancel(reason: String) {
        if (activeDisplayId != null) {
            effects.log("cancel displayId=$activeDisplayId reason=$reason")
        }
        generation += 1
        activeDisplayId = null
        activeReason = null
    }

    private fun attempt(
        token: Long,
        displayId: Int,
        attempt: Int,
    ) {
        if (!isCurrent(token, displayId)) return
        if (effects.isHealthy(displayId)) {
            finishRecovered(displayId)
            return
        }
        val gate = effects.gate()
        gate.suppressReason?.let { reason ->
            effects.log("suppressed displayId=$displayId reason=$reason")
            cancel(reason)
            return
        }
        if (gate.deferMs > 0L) {
            effects.log("deferred displayId=$displayId delayMs=${gate.deferMs}")
            scheduler.post(gate.deferMs) { attempt(token, displayId, attempt) }
            return
        }

        effects.log("attempt displayId=$displayId number=$attempt")
        effects.recover(displayId, allowLaunch = attempt > 0)
        val retryDelay = retryDelaysMs.getOrNull(attempt)
        if (retryDelay != null) {
            scheduler.post(retryDelay) { attempt(token, displayId, attempt + 1) }
        } else {
            scheduler.post(finalVerifyDelayMs) { verifyFinal(token, displayId) }
        }
    }

    private fun verifyFinal(
        token: Long,
        displayId: Int,
    ) {
        if (!isCurrent(token, displayId)) return
        if (effects.isHealthy(displayId)) {
            finishRecovered(displayId)
        } else {
            effects.log("exhausted displayId=$displayId attempts=${retryDelaysMs.size + 1}")
            effects.onExhausted(displayId)
            cancel("exhausted")
        }
    }

    private fun finishRecovered(displayId: Int) {
        effects.log("success displayId=$displayId")
        generation += 1
        activeDisplayId = null
        activeReason = null
        effects.onRecovered(displayId)
    }

    private fun isCurrent(
        token: Long,
        displayId: Int,
    ): Boolean = token == generation && activeDisplayId == displayId
}
