package com.shiny.music.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.EOFException
import java.io.IOException

class RangeChunkerTest {
    /** A resource of [size] bytes that records every range it is asked for. */
    private class FakeSource(
        private val size: Int,
        private val reportsLength: Boolean = true,
        /** The most bytes one range hands out, to model a server that answers short. */
        private val rangeCap: Int = Int.MAX_VALUE,
    ) : RangeSource {
        val data = ByteArray(size) { (it % 251).toByte() }
        val ranges = mutableListOf<Pair<Long, Long>>()
        var open = false
        var closes = 0
        private var cursor = 0
        private var rangeEnd = 0

        override fun open(position: Long, length: Long): Long {
            check(!open) { "previous range was not closed" }
            ranges += position to length
            if (position > size || (position == size.toLong() && size > 0)) {
                throw PastEndException(IOException("416"))
            }
            open = true
            cursor = position.toInt()
            val wanted = if (length == RangeChunker.UNSET) size - cursor else length.toInt()
            rangeEnd = minOf(size, cursor + minOf(wanted, rangeCap))
            return if (reportsLength) (rangeEnd - cursor).toLong() else RangeChunker.UNSET
        }

        override fun read(target: ByteArray, offset: Int, length: Int): Int {
            check(open)
            if (cursor >= rangeEnd) return RangeChunker.END
            // Hand out at most 7 bytes at a time: a read may always return less than asked.
            val count = minOf(length, rangeEnd - cursor, 7)
            data.copyInto(target, offset, cursor, cursor + count)
            cursor += count
            return count
        }

        override fun close() {
            open = false
            closes++
        }
    }

    private fun RangeChunker.readAll(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16)
        while (true) {
            val count = read(buffer, 0, buffer.size)
            if (count == RangeChunker.END) break
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    @Test
    fun `a length that is an exact multiple is split into equal ranges`() {
        val source = FakeSource(300)
        val chunker = RangeChunker(source, 100)
        assertEquals(300, chunker.open(0, 300))
        assertArrayEquals(source.data, chunker.readAll())
        assertEquals(listOf(0L to 100L, 100L to 100L, 200L to 100L), source.ranges)
    }

    @Test
    fun `the last range is the remainder`() {
        val source = FakeSource(250)
        val chunker = RangeChunker(source, 100)
        assertEquals(250, chunker.open(0, 250))
        assertArrayEquals(source.data, chunker.readAll())
        assertEquals(listOf(0L to 100L, 100L to 100L, 200L to 50L), source.ranges)
    }

    @Test
    fun `a read starts at the requested position`() {
        val source = FakeSource(250)
        val chunker = RangeChunker(source, 100)
        assertEquals(120, chunker.open(30, 120))
        assertArrayEquals(source.data.copyOfRange(30, 150), chunker.readAll())
        assertEquals(listOf(30L to 100L, 130L to 20L), source.ranges)
    }

    @Test
    fun `an unknown length ends at the first short range`() {
        val source = FakeSource(250)
        val chunker = RangeChunker(source, 100)
        assertEquals(RangeChunker.UNSET, chunker.open(0, RangeChunker.UNSET))
        assertArrayEquals(source.data, chunker.readAll())
        assertEquals(listOf(0L to 100L, 100L to 100L, 200L to 100L), source.ranges)
    }

    @Test
    fun `an unknown length that fits in one range is reported at open`() {
        val source = FakeSource(40)
        val chunker = RangeChunker(source, 100)
        assertEquals(40, chunker.open(0, RangeChunker.UNSET))
        assertArrayEquals(source.data, chunker.readAll())
        assertEquals(listOf(0L to 100L), source.ranges)
    }

    @Test
    fun `an unknown length that is an exact multiple ends when the next range is past the end`() {
        val source = FakeSource(200)
        val chunker = RangeChunker(source, 100)
        assertEquals(RangeChunker.UNSET, chunker.open(0, RangeChunker.UNSET))
        assertArrayEquals(source.data, chunker.readAll())
        assertEquals(listOf(0L to 100L, 100L to 100L, 200L to 100L), source.ranges)
        assertEquals(false, source.open)
    }

    @Test
    fun `an unknown length works when the source never reports a length`() {
        val source = FakeSource(250, reportsLength = false)
        val chunker = RangeChunker(source, 100)
        assertEquals(RangeChunker.UNSET, chunker.open(0, RangeChunker.UNSET))
        assertArrayEquals(source.data, chunker.readAll())
    }

    @Test
    fun `a zero length read opens nothing`() {
        val source = FakeSource(250)
        val chunker = RangeChunker(source, 100)
        assertEquals(0, chunker.open(10, 0))
        assertEquals(RangeChunker.END, chunker.read(ByteArray(8), 0, 8))
        assertEquals(emptyList<Pair<Long, Long>>(), source.ranges)
    }

    @Test
    fun `a range that comes back short is continued where it stopped`() {
        val source = FakeSource(250, rangeCap = 60)
        val chunker = RangeChunker(source, 100)
        assertEquals(250, chunker.open(0, 250))
        assertArrayEquals(source.data, chunker.readAll())
        assertEquals(listOf(0L to 100L, 60L to 100L, 120L to 100L, 180L to 70L, 240L to 10L), source.ranges)
    }

    @Test
    fun `a known length the source cannot supply fails instead of looping`() {
        val source = FakeSource(150)
        val chunker = RangeChunker(source, 100)
        assertEquals(400, chunker.open(0, 400))
        val buffer = ByteArray(64)
        var total = 0
        val failure = assertThrows(IOException::class.java) {
            while (true) {
                val count = chunker.read(buffer, 0, buffer.size)
                check(count != RangeChunker.END) { "ended quietly after $total bytes" }
                total += count
            }
        }
        assertEquals(150, total)
        assertEquals(true, failure is EOFException || failure.message == "416")
    }

    @Test
    fun `a first range past the end is the caller's error`() {
        val original = IOException("out of range")
        val source = object : RangeSource {
            override fun open(position: Long, length: Long): Long = throw PastEndException(original)
            override fun read(target: ByteArray, offset: Int, length: Int): Int = error("not opened")
            override fun close() = Unit
        }
        val thrown = assertThrows(IOException::class.java) { RangeChunker(source, 100).open(500, RangeChunker.UNSET) }
        assertSame(original, thrown)
    }

    @Test
    fun `every opened range is closed, and the chunker can be reused after close`() {
        val source = FakeSource(250)
        val chunker = RangeChunker(source, 100)
        chunker.open(0, 250)
        chunker.read(ByteArray(16), 0, 16)
        chunker.close()
        assertEquals(false, source.open)
        assertEquals(RangeChunker.END, chunker.read(ByteArray(16), 0, 16))

        assertEquals(50, chunker.open(200, 50))
        assertArrayEquals(source.data.copyOfRange(200, 250), chunker.readAll())
        chunker.close()
        assertEquals(false, source.open)
        assertEquals(source.ranges.size, source.closes)
    }

    @Test
    fun `a read of zero bytes returns zero`() {
        val chunker = RangeChunker(FakeSource(10), 100)
        chunker.open(0, 10)
        assertEquals(0, chunker.read(ByteArray(4), 0, 0))
    }
}
