package com.shiny.music.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class KuromojiXzTest {
    @Test
    fun romanizesKanjiFromCompressedDictionary() = runBlocking {
        val out = LyricsUtils.romanizeJapanese("東京に行きます")
        assertEquals(true, out.lowercase().contains("toukyou") || out.lowercase().contains("tokyo"))
    }
}
