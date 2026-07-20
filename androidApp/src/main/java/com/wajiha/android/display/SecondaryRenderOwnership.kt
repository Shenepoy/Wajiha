package com.wajiha.android.display

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SecondaryRenderSurface {
    Activity,
    Overlay,
}

internal class SecondaryRenderOwnership {
    private val _surface = MutableStateFlow(SecondaryRenderSurface.Activity)
    val surface: StateFlow<SecondaryRenderSurface> = _surface.asStateFlow()

    fun claim(surface: SecondaryRenderSurface) {
        _surface.value = surface
    }
}
