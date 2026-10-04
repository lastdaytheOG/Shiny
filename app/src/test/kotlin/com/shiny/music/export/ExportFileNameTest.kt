package com.shiny.music.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFileNameTest {
    @Test
    fun `artist and title make the name`() {
        assertEquals("Daft Punk - One More Time.mp3", exportFileName("Daft Punk", "One More Time"))
    }

    @Test
    fun `reserved characters are replaced`() {
        assertEquals("AC_DC - What_ Why_.mp3", exportFileName("AC/DC", "What? Why*"))
    }

    @Test
    fun `missing artist falls back to the title alone`() {
        assertEquals("Intro.mp3", exportFileName(null, "Intro"))
        assertEquals("Intro.mp3", exportFileName("  ", "Intro"))
    }

    @Test
    fun `trailing dots and control characters are removed`() {
        assertEquals("A - B_C.mp3", exportFileName("A", "B\u0007C..."))
    }

    @Test
    fun `an empty name still produces a file name`() {
        assertEquals("Song.mp3", exportFileName("", ""))
    }

    @Test
    fun `long names fit the filesystem limit`() {
        val name = exportFileName("É".repeat(200), "Ü".repeat(200))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 255)
        assertTrue(name.endsWith(".mp3"))
    }
}
