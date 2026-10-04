package com.shiny.music.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverResizeTest {

    private val cover = "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj"
    private val bare = "https://lh3.googleusercontent.com/abc"

    @Test
    fun `a list row gets YouTube Music's row size`() {
        assertEquals("$bare=w226-h226", cover.resize(175, 175))
        assertEquals("$bare=w226-h226", cover.resize(240, 240))
        assertEquals("$bare=w226-h226", "$bare=s60".resize(144, 144))
    }

    @Test
    fun `tiles and full screen keep their sizes`() {
        assertEquals("$bare=w500-h500", cover.resize(241, 241))
        assertEquals("$bare=w500-h500", cover.resize(544, 544))
        assertEquals("$bare=w1200-h1200", cover.resize(1080, 1080))
    }

    @Test
    fun `video stills are left to their own variants`() {
        val still = "https://i.ytimg.com/vi/x/hqdefault.jpg"
        assertEquals("https://i.ytimg.com/vi/x/mqdefault.jpg", still.resize(175, 175))
    }
}
