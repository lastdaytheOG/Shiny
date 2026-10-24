package com.shiny.music.localmedia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalArtworkTargetTest {
    @Test
    fun `a file's own cover address names the file`() {
        assertEquals(
            LocalArtworkFetcher.ArtTarget("external", 2764, null),
            LocalArtworkFetcher.artTarget("content://media/external/audio/media/2764/albumart"),
        )
    }

    @Test
    fun `old album addresses are still read, negative ids included`() {
        assertEquals(
            LocalArtworkFetcher.ArtTarget("external", null, -7880601406119258576),
            LocalArtworkFetcher.artTarget("content://media/external/audio/albumart/-7880601406119258576"),
        )
        assertEquals(
            LocalArtworkFetcher.ArtTarget("external_primary", null, 42),
            LocalArtworkFetcher.artTarget("content://media/external_primary/audio/albumart/42"),
        )
    }

    @Test
    fun `everything else is left to the other loaders`() {
        assertNull(LocalArtworkFetcher.artTarget("https://i.ytimg.com/vi/abc/hqdefault.jpg"))
        assertNull(LocalArtworkFetcher.artTarget("content://media/external/audio/media/2764"))
        assertNull(LocalArtworkFetcher.artTarget("content://media/external/images/media/5/albumart"))
    }
}
