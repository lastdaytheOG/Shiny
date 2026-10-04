package com.shiny.music.playback

import com.shiny.music.utils.PlaybackHealth
import com.shiny.music.utils.StreamResolveException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PlaybackHealthTest {

    @After
    fun clearSink() {
        PlaybackHealth.sink = null
    }

    @Test
    fun `finds a resolve failure however deep Media3 wrapped it`() {
        val failure = StreamResolveException("playabilityNotOk", permanent = true, status = "UNPLAYABLE")
        // The loader wraps it (UnexpectedLoaderException), then the player wraps that again.
        val wrapped = RuntimeException("player", IOException("loader", failure))

        assertSame(failure, PlaybackHealth.resolveFailureIn(wrapped))
        assertTrue(PlaybackHealth.resolveFailureIn(wrapped)!!.permanent)
    }

    @Test
    fun `an ordinary network error is not a resolve failure`() {
        assertNull(PlaybackHealth.resolveFailureIn(IOException("reset", java.net.SocketException())))
        assertNull(PlaybackHealth.resolveFailureIn(null))
    }

    @Test
    fun `a cause chain that loops back on itself does not hang`() {
        val a = IOException("a")
        val b = IOException("b", a)
        a.initCause(b)

        assertNull(PlaybackHealth.resolveFailureIn(a))
    }

    @Test
    fun `the message falls back to why when YouTube gave no reason`() {
        assertEquals("noStreamUrl", StreamResolveException("noStreamUrl", permanent = false).message)
        assertEquals(
            "Video unavailable",
            StreamResolveException("playabilityNotOk", permanent = true, reason = "Video unavailable").message,
        )
    }

    @Test
    fun `events reach the sink and a failing sink never reaches playback`() {
        val seen = mutableListOf<PlaybackHealth.Event>()
        PlaybackHealth.sink = { seen += it }
        val event = PlaybackHealth.Event.Resolved(source = "play", client = "VISIONOS", ms = 420)

        PlaybackHealth.record(event)
        assertEquals(listOf<PlaybackHealth.Event>(event), seen)

        PlaybackHealth.sink = { throw IllegalStateException("analytics down") }
        PlaybackHealth.record(event) // must not throw
    }
}
