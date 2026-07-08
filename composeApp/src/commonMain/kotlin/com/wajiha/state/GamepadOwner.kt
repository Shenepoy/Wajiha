package com.wajiha.state

/**
 * Which Wajiha activity should receive gamepad / confirm-key input while both
 * displays are alive. Android delivers keys only to the focused display's
 * window; [com.wajiha.android.input.GamepadKeyRouter] forwards events to the
 * owner activity in-process when they arrive on the other display.
 */
enum class GamepadOwner {
    /** [com.wajiha.android.MainActivity] — top / primary display. */
    Primary,

    /** [com.wajiha.android.SecondaryHomeActivity] — bottom / secondary display. */
    Secondary
}
