package com.wajiha.android.input

import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore

/**
 * Blocks launcher gamepad dispatch while a game/emulator is foreground and
 * applies a short debounce when the launcher regains focus (NeoStation pattern).
 */
class GamepadGate(
    private val store: DualScreenStore,
) {
    private var launcherForegroundedAtMs: Long = 0L

    /** Call when MainActivity or SecondaryHomeActivity resumes. */
    fun onLauncherForegrounded() {
        launcherForegroundedAtMs = System.currentTimeMillis()
    }

    /** Call when launcher pauses (game likely took focus). */
    fun onLauncherBackgrounded() {
        launcherForegroundedAtMs = 0L
    }

    /**
     * @return true when the launcher should not consume gamepad keys.
     */
    fun shouldBlockGamepad(): Boolean {
        val state = store.state.value
        if (state == DualScreenState.SingleDisplay) return false
        // Thor dual-display: game on top, launcher grid on bottom — keep gamepad live.
        if (state == DualScreenState.GameRunning ||
            state == DualScreenState.BlackoutSecondary
        ) {
            return false
        }
        val nowPlaying = store.nowPlaying.value
        if (nowPlaying == null) return false
        val elapsed = System.currentTimeMillis() - launcherForegroundedAtMs
        if (elapsed in 1 until RETURN_DEBOUNCE_MS) {
            WajihaLog.d(WajihaTags.GAMEPAD, "gate: debounce ${RETURN_DEBOUNCE_MS - elapsed}ms")
            return true
        }
        return false
    }

    companion object {
        const val RETURN_DEBOUNCE_MS = 2_000L
    }
}
