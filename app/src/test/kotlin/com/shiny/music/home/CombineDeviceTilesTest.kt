package com.shiny.music.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CombineDeviceTilesTest {
    @Test
    fun `downloads fold into the device tile`() {
        assertEquals(listOf(DeviceTile(8)), combineDeviceTiles(listOf(DownloadsTile(5), DeviceTile(3))))
    }

    @Test
    fun `downloads alone become an On This Device tile`() {
        assertEquals(listOf(DeviceTile(5)), combineDeviceTiles(listOf(DownloadsTile(5))))
    }

    @Test
    fun `without downloads nothing changes`() {
        assertEquals(listOf(DeviceTile(3)), combineDeviceTiles(listOf(DeviceTile(3))))
    }
}
