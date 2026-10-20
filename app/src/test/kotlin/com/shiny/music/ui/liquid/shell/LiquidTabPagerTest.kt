package com.shiny.music.ui.liquid.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidTabPagerTest {
    @Test
    fun `slow drag commits only after the distance threshold`() {
        assertEquals(0, resolveLiquidTabTarget(0, progress = 0.27f, velocityX = 0f, pageCount = 3))
        assertEquals(1, resolveLiquidTabTarget(0, progress = 0.29f, velocityX = 0f, pageCount = 3))
    }

    @Test
    fun `a quick flick commits in its direction before the distance threshold`() {
        assertEquals(1, resolveLiquidTabTarget(0, progress = 0.08f, velocityX = -1200f, pageCount = 3))
        assertEquals(0, resolveLiquidTabTarget(1, progress = 0.92f, velocityX = 1200f, pageCount = 3))
    }

    @Test
    fun `swipes clamp at the first and last page`() {
        assertEquals(0, resolveLiquidTabTarget(0, progress = -0.9f, velocityX = 1400f, pageCount = 3))
        assertEquals(2, resolveLiquidTabTarget(2, progress = 2.9f, velocityX = -1400f, pageCount = 3))
    }
}
