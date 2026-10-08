package com.music.innertube.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountTextTest {
    private fun runs(vararg texts: String) = Runs(texts.map { Run(text = it, navigationEndpoint = null) })

    @Test
    fun `the count is read from a single run`() {
        assertEquals("653K", runs("653K subscribers").extractCountText())
        assertEquals("1.2M", runs("1.2M subscribers").extractCountText())
        assertEquals("12", runs("12 subscribers").extractCountText())
    }

    @Test
    fun `the count is read when the label is split across runs`() {
        assertEquals("653K", runs("653K", " subscribers").extractCountText())
        assertEquals("12.2M", runs("12.2M", " monthly audience").extractCountText())
    }

    @Test
    fun `a label that comes before the count is skipped`() {
        assertEquals("653K", runs("チャンネル登録者数", "653K").extractCountText())
        assertEquals("12.2M", runs("月間リスナー: ", "12.2M").extractCountText())
    }

    @Test
    fun `east asian units stay with the number and the counter word is dropped`() {
        assertEquals("12.3万", runs("月間リスナー 12.3万人").extractCountText())
        assertEquals("123万", runs("月間リスナー 123万").extractCountText())
        assertEquals("1.2만", runs("구독자 1.2만명").extractCountText())
        assertEquals("3.4億", countIn("3.4億 回"))
    }

    @Test
    fun `a unit set off by a space is joined to the number`() {
        assertEquals("653K", countIn("653 K subscribers"))
        assertEquals("1,2M", countIn("1,2 M d’abonnés"))
    }

    @Test
    fun `a word that only starts with a unit letter is not a unit`() {
        assertEquals("1,234", countIn("1,234 monthly listeners"))
        assertEquals("987", countIn("987 total plays"))
        assertEquals("45", countIn("45 Beiträge"))
    }

    @Test
    fun `grouping separators and spaces inside the number are kept`() {
        assertEquals("1,234,567", countIn("1,234,567 subscribers"))
        assertEquals("12 345", countIn("12 345 abonnés"))
        assertEquals("1.234", countIn("1.234 Abonnenten."))
        assertEquals("7", countIn("7, and counting"))
    }

    @Test
    fun `no number gives null`() {
        assertNull(runs("チャンネル登録者数").extractCountText())
        assertNull(runs("subscribers").extractCountText())
        assertNull(runs().extractCountText())
        assertNull(Runs(null).extractCountText())
        assertNull((null as Runs?).extractCountText())
    }
}
