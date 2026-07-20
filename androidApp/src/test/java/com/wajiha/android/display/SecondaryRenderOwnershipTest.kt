package com.wajiha.android.display

import org.junit.Assert.assertEquals
import org.junit.Test

class SecondaryRenderOwnershipTest {
    @Test
    fun claimsExactlyOneRenderSurfaceAtATime() {
        val ownership = SecondaryRenderOwnership()

        assertEquals(SecondaryRenderSurface.Activity, ownership.surface.value)

        ownership.claim(SecondaryRenderSurface.Overlay)
        assertEquals(SecondaryRenderSurface.Overlay, ownership.surface.value)

        ownership.claim(SecondaryRenderSurface.Activity)
        assertEquals(SecondaryRenderSurface.Activity, ownership.surface.value)
    }
}
