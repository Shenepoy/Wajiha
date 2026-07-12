package com.wajiha.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

object WajihaMotion {
    const val fadeInMs = 280
    const val fadeOutMs = 220
    const val focusMs = 120

    fun <T> fadeInSpec(): TweenSpec<T> = tween(fadeInMs, easing = FastOutSlowInEasing)

    fun <T> fadeOutSpec(): TweenSpec<T> = tween(fadeOutMs, easing = FastOutSlowInEasing)

    fun <T> focusSpec(): TweenSpec<T> = tween(focusMs, easing = FastOutSlowInEasing)
}
