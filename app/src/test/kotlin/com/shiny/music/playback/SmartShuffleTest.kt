package com.shiny.music.playback

import com.shiny.music.playback.SmartShuffle.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SmartShuffleTest {

    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    private fun track(id: String, plays: Double = 0.0, lastPlayed: Long? = null, artist: String? = null) =
        Track(id, artist, plays, lastPlayed)

    private fun library(size: Int, plays: (Int) -> Double = { 0.0 }) =
        List(size) { track("s$it", plays(it), artist = "artist $it") }

    private fun order(tracks: List<Track>, seed: Long, avoidFirst: String? = null) =
        SmartShuffle.order(tracks, now, Random(seed), avoidFirst)

    private fun assertPermutation(tracks: List<Track>, result: List<String>) {
        val ids = tracks.map { it.id }.distinct()
        assertEquals(ids.size, result.size)
        assertEquals(ids.toSet(), result.toSet())
    }

    /** Mean position of each id over [runs] shuffles. */
    private fun meanPositions(tracks: List<Track>, runs: Int = 3000): Map<String, Double> {
        val sums = HashMap<String, Double>()
        repeat(runs) { seed ->
            order(tracks, seed.toLong()).forEachIndexed { index, id -> sums[id] = (sums[id] ?: 0.0) + index }
        }
        return sums.mapValues { it.value / runs }
    }

    @Test
    fun `empty and single-song libraries`() {
        assertEquals(emptyList<String>(), order(emptyList(), 1))
        assertEquals(listOf("only"), order(listOf(track("only", plays = 50.0)), 1))
        assertEquals(listOf("only"), order(listOf(track("only")), 1, avoidFirst = "only"))
    }

    @Test
    fun `two songs come out both, in either order`() {
        val tracks = listOf(track("a", plays = 20.0), track("b"))
        val seen = (0 until 200).map { order(tracks, it.toLong()) }.toSet()
        assertEquals(setOf(listOf("a", "b"), listOf("b", "a")), seen)
    }

    @Test
    fun `every size gives each song exactly once`() {
        for (size in listOf(2, 3, 5, 10, 100, 1000)) {
            val tracks = library(size) { (it % 7) * 13.0 }
            repeat(20) { seed -> assertPermutation(tracks, order(tracks, seed.toLong())) }
        }
    }

    @Test
    fun `a duplicated song is kept once, so it can never play twice in a row`() {
        val tracks = listOf(track("a", 90.0), track("a", 90.0), track("b"), track("a", 90.0), track("c"))
        repeat(100) { seed ->
            val result = order(tracks, seed.toLong())
            assertEquals(listOf("a", "b", "c").sorted(), result.sorted())
            result.zipWithNext().forEach { (x, y) -> assertNotEquals(x, y) }
        }
    }

    @Test
    fun `a brand new library is a fair shuffle`() {
        val tracks = library(10)
        val firsts = IntArray(10)
        repeat(5000) { seed -> firsts[order(tracks, seed.toLong()).first().removePrefix("s").toInt()]++ }
        // Each song opens the queue about 500 times in 5000.
        firsts.forEach { assertTrue("first-place counts $it", it in 380..620) }
    }

    @Test
    fun `more plays come earlier on average, in order of plays`() {
        val tracks = listOf(track("a", 100.0), track("b", 30.0), track("c", 5.0), track("d", 1.0)) +
            List(16) { track("new$it") }
        val mean = meanPositions(tracks)
        val newMean = (0 until 16).map { mean.getValue("new$it") }.average()
        assertTrue(mean.getValue("a") < mean.getValue("b"))
        assertTrue(mean.getValue("b") < mean.getValue("c"))
        assertTrue(mean.getValue("c") < mean.getValue("d"))
        assertTrue(mean.getValue("d") < newMean)
        // A plain shuffle would put every song at 9.5 on average.
        assertTrue("a at ${mean["a"]}", mean.getValue("a") in 4.0..7.0)
    }

    @Test
    fun `one very popular song does not take over the start`() {
        val tracks = listOf(track("hit", plays = 1000.0)) + library(49)
        var first = 0
        var inFirstFive = 0
        repeat(4000) { seed ->
            val result = order(tracks, seed.toLong())
            if (result[0] == "hit") first++
            if ("hit" in result.take(5)) inFirstFive++
        }
        // Plain shuffle: 2% first, 10% in the first five.
        assertTrue("first $first of 4000", first in 160..480)
        assertTrue("first five $inFirstFive of 4000", inFirstFive in 800..2000)
    }

    @Test
    fun `favourites are mixed in, not used up first`() {
        val tracks = library(100) { if (it < 20) 100.0 else 0.0 }
        val favourites = (0 until 20).map { "s$it" }.toSet()
        var inFirstTen = 0
        var inSecondHalf = 0
        val runs = 2000
        repeat(runs) { seed ->
            val result = order(tracks, seed.toLong())
            inFirstTen += result.take(10).count { it in favourites }
            inSecondHalf += result.drop(50).count { it in favourites }
        }
        // Plain shuffle: 2 of the first ten, 10 in the second half.
        val firstTen = inFirstTen.toDouble() / runs
        val secondHalf = inSecondHalf.toDouble() / runs
        assertTrue("first ten $firstTen", firstTen in 3.5..5.0)
        assertTrue("second half $secondHalf", secondHalf in 3.5..6.0)
        // Every shuffle still opens with some songs never played.
        repeat(200) { seed -> assertTrue(order(tracks, seed.toLong()).take(10).any { it !in favourites }) }
    }

    @Test
    fun `never-played songs appear and can open the queue`() {
        val tracks = library(10) { if (it == 0) 0.0 else 200.0 }
        var neverPlayedFirst = 0
        repeat(3000) { seed ->
            val result = order(tracks, seed.toLong())
            assertTrue("s0" in result)
            if (result[0] == "s0") neverPlayedFirst++
        }
        assertTrue("never-played first $neverPlayedFirst of 3000", neverPlayedFirst > 40)
    }

    @Test
    fun `a song played in the last day is held back, and recovers`() {
        assertEquals(SmartShuffle.COOLDOWN_FLOOR, SmartShuffle.cooldown(now, now), 1e-9)
        assertEquals(0.65, SmartShuffle.cooldown(now - 12 * hour, now), 1e-9)
        assertEquals(1.0, SmartShuffle.cooldown(now - day, now), 1e-9)
        assertEquals(1.0, SmartShuffle.cooldown(null, now), 1e-9)
        // A clock that went backwards is treated as just played, not as a bonus.
        assertEquals(SmartShuffle.COOLDOWN_FLOOR, SmartShuffle.cooldown(now + hour, now), 1e-9)

        val tracks = listOf(
            track("justNow", plays = 50.0, lastPlayed = now - 10 * 60 * 1000),
            track("lastWeek", plays = 50.0, lastPlayed = now - 7 * day),
        ) + List(18) { track("new$it") }
        val mean = meanPositions(tracks)
        assertTrue(mean.getValue("lastWeek") < mean.getValue("justNow"))
        val newMean = (0 until 18).map { mean.getValue("new$it") }.average()
        assertTrue("just played ${mean["justNow"]} vs new $newMean", mean.getValue("justNow") > newMean)
    }

    @Test
    fun `the song playing now does not start the new queue`() {
        val tracks = library(8) { if (it == 3) 500.0 else 0.0 }
        repeat(500) { seed ->
            val result = order(tracks, seed.toLong(), avoidFirst = "s3")
            assertNotEquals("s3", result[0])
            assertPermutation(tracks, result)
        }
        val pair = listOf(track("a"), track("b"))
        repeat(50) { seed -> assertEquals("b", order(pair, seed.toLong(), avoidFirst = "a")[0]) }
    }

    @Test
    fun `the same artist is kept apart when another is close by`() {
        // Spelled two ways each: the comparison ignores case and surrounding spaces.
        val tracks = List(12) { track("x$it", artist = if (it % 2 == 0) "Artist X" else " artist x ") } +
            List(12) { track("y$it", artist = if (it % 2 == 0) "Artist Y" else "ARTIST Y") }
        var adjacent = 0
        repeat(300) { seed ->
            val result = order(tracks, seed.toLong())
            assertPermutation(tracks, result)
            adjacent += result.zipWithNext().count { (a, b) -> a[0] == b[0] }
        }
        // A plain shuffle of this library has about 11 same-artist neighbours per queue.
        assertTrue("same-artist neighbours $adjacent in 300 queues", adjacent < 150)

        val oneArtist = List(10) { track("z$it", artist = "Solo") }
        assertPermutation(oneArtist, order(oneArtist, 4))
        val unknown = List(10) { track("u$it", artist = null) }
        assertPermutation(unknown, order(unknown, 4))
    }

    @Test
    fun `the same seed gives the same order, new seeds give new ones`() {
        val tracks = library(10) { it * 3.0 }
        assertEquals(order(tracks, 42), order(tracks, 42))
        val distinct = (0 until 30).map { order(tracks, it.toLong()) }.toSet()
        assertTrue("distinct orders ${distinct.size}", distinct.size >= 28)
    }

    @Test
    fun `old plays count for less`() {
        assertEquals(10.0, SmartShuffle.agedPlays(listOf(Triple(2026, 9, 10)), 2026, 9), 1e-9)
        assertEquals(5.0, SmartShuffle.agedPlays(listOf(Triple(2026, 3, 10)), 2026, 9), 1e-9)
        assertEquals(2.5, SmartShuffle.agedPlays(listOf(Triple(2025, 9, 10)), 2026, 9), 1e-9)
        assertEquals(15.0, SmartShuffle.agedPlays(listOf(Triple(2026, 9, 10), Triple(2026, 3, 10)), 2026, 9), 1e-9)
        // A month ahead of the clock counts as this month; empty rows count nothing.
        assertEquals(4.0, SmartShuffle.agedPlays(listOf(Triple(2026, 11, 4), Triple(2026, 9, 0)), 2026, 9), 1e-9)
        assertEquals(0.0, SmartShuffle.agedPlays(emptyList(), 2026, 9), 1e-9)
    }

    @Test
    fun `the weight grows slowly with plays and the boost fades as the queue fills`() {
        assertEquals(1.0, SmartShuffle.weight(0.0, null, now), 1e-9)
        val w1 = SmartShuffle.weight(1.0, null, now)
        val w100 = SmartShuffle.weight(100.0, null, now)
        val w10000 = SmartShuffle.weight(10_000.0, null, now)
        assertTrue(w1 > 1.0 && w100 > w1 && w10000 > w100)
        assertTrue("100 plays weigh $w100", w100 < 3.5)
        assertTrue("10000 plays weigh $w10000", w10000 < 6.0)
        assertEquals(1.0, SmartShuffle.weight(100.0, null, now, filled = 1.0), 1e-9)
        assertEquals(1.0, SmartShuffle.weight(-5.0, null, now), 1e-9)
    }

    @Test
    fun `a large library shuffles quickly`() {
        val tracks = List(5000) { track("s$it", plays = (it % 50).toDouble(), artist = "a${it % 300}") }
        order(tracks, 0)
        val started = System.nanoTime()
        val result = order(tracks, 1)
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertPermutation(tracks, result)
        assertTrue("5000 songs took $tookMs ms", tookMs < 1500)
    }
}
