package com.wajiha.input

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaLogKind
import com.wajiha.state.GamepadOwner

/** Stable, semantic identity for one actionable target inside a gamepad layer. */
data class FocusAnchor(
    val layerId: String,
    val targetId: Any,
)

enum class FocusClaimSource {
    Initial,
    Compose,
    Gamepad,
    Touch,
    Restore,
    Fallback,
}

enum class FocusRestorePolicy {
    PreserveAnchor,
    ResetToDefault,
    PushOverlay,
}

private object FocusContinuityMemory {
    private val targetByLayer = mutableMapOf<String, Any>()
    private val contextByKey = mutableMapOf<String, Any>()

    fun target(layerId: String): Any? = targetByLayer[layerId]

    fun remember(anchor: FocusAnchor) {
        targetByLayer[anchor.layerId] = anchor.targetId
    }

    fun clear(layerId: String) {
        targetByLayer.remove(layerId)
    }

    fun context(key: String): Any? = contextByKey[key]

    fun rememberContext(
        key: String,
        value: Any,
    ) {
        contextByKey[key] = value
    }
}

fun rememberedFocusTarget(layerId: String): Any? = FocusContinuityMemory.target(layerId)

fun rememberedFocusContext(key: String): Any? = FocusContinuityMemory.context(key)

fun rememberFocusContext(
    key: String,
    value: Any,
) {
    FocusContinuityMemory.rememberContext(key, value)
}

/**
 * Single logical focus authority for a [GamepadScreen].
 *
 * Compose focus remains an accessibility/input bridge, but visible chrome and
 * activation identity are anchored here. Layers freeze their last anchor while
 * covered, then restore it when the covering layer is removed.
 */
@Stable
class FocusContinuityController(
    rootLayerId: String,
    restorePolicy: FocusRestorePolicy = FocusRestorePolicy.PreserveAnchor,
) {
    private val layers = mutableListOf(rootLayerId)
    private val availableAnchors = mutableSetOf<FocusAnchor>()
    private val lastAnchorByLayer = mutableMapOf<String, FocusAnchor>()

    var activeLayerId by mutableStateOf(rootLayerId)
        private set

    var activeAnchor by
        mutableStateOf(
            if (restorePolicy == FocusRestorePolicy.PreserveAnchor) {
                FocusContinuityMemory.target(rootLayerId)?.let { FocusAnchor(rootLayerId, it) }
            } else {
                FocusContinuityMemory.clear(rootLayerId)
                null
            },
        )
        private set

    fun anchor(
        targetId: Any,
        layerId: String = activeLayerId,
    ): FocusAnchor = FocusAnchor(layerId = layerId, targetId = targetId)

    fun pushLayer(layerId: String) {
        activeAnchor?.let { lastAnchorByLayer[activeLayerId] = it }
        layers.removeAll { it == layerId }
        layers += layerId
        activeLayerId = layerId
        activeAnchor = lastAnchorByLayer[layerId]?.takeIf { it in availableAnchors }
        trace("layer push=$layerId restore=${activeAnchor?.targetId}")
    }

    fun popLayer(layerId: String) {
        if (activeLayerId == layerId) {
            activeAnchor?.let { lastAnchorByLayer[layerId] = it }
        }
        layers.removeAll { it == layerId }
        val restoredLayer = layers.lastOrNull() ?: return
        activeLayerId = restoredLayer
        activeAnchor = lastAnchorByLayer[restoredLayer]?.takeIf { it in availableAnchors }
        trace("layer pop=$layerId active=$restoredLayer restore=${activeAnchor?.targetId}")
    }

    fun register(anchor: FocusAnchor) {
        availableAnchors += anchor
        if (anchor.layerId == activeLayerId) {
            val current = activeAnchor
            if (current == anchor) {
                claim(anchor, FocusClaimSource.Restore)
            } else if (current == null) {
                claim(anchor, FocusClaimSource.Initial)
            }
        }
    }

    fun unregister(anchor: FocusAnchor) {
        availableAnchors -= anchor
        // Keep activeAnchor and the last painted geometry as a handoff sentinel.
        // The next registration in this layer atomically replaces it.
        if (activeAnchor == anchor) {
            trace("handoff pending layer=${anchor.layerId} from=${anchor.targetId}")
        }
    }

    fun claim(
        anchor: FocusAnchor,
        source: FocusClaimSource,
    ): Boolean {
        if (anchor.layerId != activeLayerId) {
            trace("claim rejected stale=${anchor.targetId} layer=${anchor.layerId} active=$activeLayerId")
            return false
        }
        val previous = activeAnchor
        activeAnchor = anchor
        lastAnchorByLayer[anchor.layerId] = anchor
        FocusContinuityMemory.remember(anchor)
        if (previous != anchor) {
            trace("claim source=$source layer=${anchor.layerId} ${previous?.targetId}->${anchor.targetId}")
        }
        return true
    }

    /**
     * Lets an active visual target become the fallback only when the current
     * anchor is absent or no longer registered. It never steals from a valid target.
     */
    fun offer(anchor: FocusAnchor): Boolean {
        register(anchor)
        val current = activeAnchor
        return when {
            anchor.layerId != activeLayerId -> {
                false
            }

            current == anchor -> {
                true
            }

            current == null || current !in availableAnchors -> {
                claim(anchor, FocusClaimSource.Fallback)
            }

            else -> {
                false
            }
        }
    }

    /** Resolve a missing remembered target after this frame's targets registered. */
    fun finishRestore(defaultTargetId: Any? = null) {
        val current = activeAnchor
        if (current != null && current in availableAnchors) return
        val fallback =
            defaultTargetId
                ?.let { FocusAnchor(activeLayerId, it) }
                ?.takeIf { it in availableAnchors }
                ?: availableAnchors.firstOrNull { it.layerId == activeLayerId }
                ?: return
        claim(fallback, FocusClaimSource.Fallback)
    }

    fun isActive(anchor: FocusAnchor): Boolean =
        anchor.layerId == activeLayerId &&
            (activeAnchor == anchor || (activeAnchor == null && offer(anchor)))

    private fun trace(message: String) {
        WajihaLog.d(WajihaLogKind.DEBUG, "focusContinuity: $message")
    }
}

val LocalFocusContinuityController = compositionLocalOf<FocusContinuityController?> { null }

val LocalFocusLayerId = compositionLocalOf { "" }

val LocalGamepadOwner = compositionLocalOf<GamepadOwner?> { null }
