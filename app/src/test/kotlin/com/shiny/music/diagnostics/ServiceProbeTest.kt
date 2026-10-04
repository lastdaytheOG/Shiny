package com.shiny.music.diagnostics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class ServiceProbeTest {
    private fun endpoint(name: String, vararg urls: String) = ServiceEndpoint(name, ServiceGroup.OTHER, urls.toList())

    /** A clock that moves only when a URL "takes" time, so latencies are exact. */
    private class Clock {
        var nanos = 0L
        fun spend(millis: Long) { nanos += millis * 1_000_000 }
    }

    @Test
    fun `a server that answers is up, with its latency`() = runBlocking {
        val clock = Clock()
        val probe = ServiceProbe(reach = { clock.spend(120); true }, nanoTime = { clock.nanos })
        val result = probe.probe(endpoint("a", "https://a"))
        assertEquals(ProbeResult(endpoint("a", "https://a"), ProbeState.UP, 120, "https://a"), result)
    }

    @Test
    fun `an answer slower than the threshold is slow`() = runBlocking {
        val clock = Clock()
        val probe = ServiceProbe(reach = { clock.spend(1_501); true }, nanoTime = { clock.nanos })
        assertEquals(ProbeState.SLOW, probe.probe(endpoint("a", "https://a")).state)
        val atThreshold = ServiceProbe(reach = { clock.spend(1_500); true }, nanoTime = { clock.nanos })
        assertEquals(ProbeState.UP, atThreshold.probe(endpoint("a", "https://a")).state)
    }

    @Test
    fun `no answer, an error and a timeout are all down`() = runBlocking {
        val refused = ServiceProbe(reach = { false })
        assertEquals(ProbeState.DOWN, refused.probe(endpoint("a", "https://a")).state)

        val broken = ServiceProbe(reach = { throw java.io.IOException("unreachable") })
        val result = broken.probe(endpoint("a", "https://a"))
        assertEquals(ProbeState.DOWN, result.state)
        assertNull(result.millis)
        assertNull(result.url)

        val hanging = ServiceProbe(reach = { delay(10_000); true }, timeoutMs = 40)
        assertEquals(ProbeState.DOWN, hanging.probe(endpoint("a", "https://a")).state)
    }

    @Test
    fun `a fallback URL that answers makes the service up, timed on its own`() = runBlocking {
        val clock = Clock()
        val asked = mutableListOf<String>()
        val probe = ServiceProbe(
            reach = { url ->
                asked += url
                clock.spend(if (url.endsWith("1")) 900 else 80)
                url.endsWith("3")
            },
            nanoTime = { clock.nanos },
        )
        val result = probe.probe(endpoint("mirrors", "https://m1", "https://m2", "https://m3", "https://m4"))
        assertEquals(listOf("https://m1", "https://m2", "https://m3"), asked)
        assertEquals(ProbeState.UP, result.state)
        assertEquals(80L, result.millis)
        assertEquals("https://m3", result.url)
    }

    @Test
    fun `every endpoint gets a result and a slow one does not hold the others up`() = runBlocking {
        val order = Collections.synchronizedList(mutableListOf<String>())
        val probe = ServiceProbe(
            reach = { url ->
                if (url == "https://slow") delay(150)
                order += url
                url != "https://down"
            },
        )
        val endpoints = listOf(endpoint("slow", "https://slow"), endpoint("fast", "https://fast"), endpoint("down", "https://down"))
        val results = probe.check(endpoints).toList()
        assertEquals(setOf("slow", "fast", "down"), results.map { it.endpoint.name }.toSet())
        assertEquals("https://slow", order.last())
        assertEquals("slow", results.last().endpoint.name)
        assertEquals(ProbeState.DOWN, results.first { it.endpoint.name == "down" }.state)
    }

    @Test
    fun `cancelling the check cancels the probes`() = runBlocking {
        var cancelled = false
        val probe = ServiceProbe(
            reach = {
                try {
                    delay(10_000)
                    true
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                }
            },
        )
        val job = launch { probe.check(listOf(endpoint("a", "https://a"))).toList() }
        repeat(5) { yield() }
        delay(30)
        job.cancel()
        job.join()
        assertTrue(cancelled)
    }

    @Test
    fun `the service list has no duplicate names and only https URLs`() {
        assertEquals(ShinyServices.size, ShinyServices.map { it.name }.toSet().size)
        assertTrue(ShinyServices.all { service -> service.urls.isNotEmpty() && service.urls.all { it.startsWith("https://") } })
        assertTrue(ServiceGroup.entries.all { group -> ShinyServices.any { it.group == group } })
    }
}
