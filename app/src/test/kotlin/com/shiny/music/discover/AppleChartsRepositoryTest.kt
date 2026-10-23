package com.shiny.music.discover

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AppleChartsRepositoryTest {

    private var now = 1_000_000_000L

    private class MemoryStore : ChartStore {
        val charts = HashMap<Pair<String, ChartKind>, AppleChart>()
        override fun read(storefront: String, kind: ChartKind) = charts[storefront to kind]
        override fun write(chart: AppleChart) {
            charts[chart.storefront to chart.kind] = chart
        }
    }

    private inner class FakeSource : AppleChartSource {
        val calls = AtomicInteger()
        var gate: CompletableDeferred<Unit>? = null
        var failWith: ChartFetchException? = null
        var entries = listOf(entry(1))

        override suspend fun fetch(storefront: String, kind: ChartKind): AppleChart {
            calls.incrementAndGet()
            gate?.await()
            failWith?.let { throw it }
            return AppleChart(storefront, kind, entries = entries, fetchedAt = now)
        }
    }

    private fun entry(position: Int) = ChartEntry(position, "id$position", "Song $position", "Artist")

    private fun chart(fetchedAt: Long, vararg positions: Int) =
        AppleChart("in", ChartKind.Songs, entries = positions.map(::entry), fetchedAt = fetchedAt)

    private val store = MemoryStore()
    private val source = FakeSource()
    private val repo = AppleChartsRepository(source, store, clock = { now }, io = Dispatchers.Unconfined)

    @Test
    fun `a fresh cached chart is served without touching the network`() = runBlocking {
        store.write(chart(now - 60_000, 1, 2))
        val result = repo.get("in", ChartKind.Songs)
        assertEquals(2, result.chart!!.entries.size)
        assertNull(result.error)
        assertEquals(0, source.calls.get())
    }

    @Test
    fun `a stale chart is refetched and the new one is stored`() = runBlocking {
        store.write(chart(now - repo.freshForMs - 1, 1, 2))
        val result = repo.get("in", ChartKind.Songs)
        assertEquals(1, source.calls.get())
        assertEquals(now, result.chart!!.fetchedAt)
        assertEquals(now, store.read("in", ChartKind.Songs)!!.fetchedAt)
    }

    @Test
    fun `the freshness window is configurable`() = runBlocking {
        val short = AppleChartsRepository(source, store, clock = { now }, freshForMs = 1_000, io = Dispatchers.Unconfined)
        store.write(chart(now - 2_000, 1))
        short.get("in", ChartKind.Songs)
        assertEquals(1, source.calls.get())
    }

    @Test
    fun `a failed refresh keeps the saved chart and reports why`() = runBlocking {
        val held = chart(now - repo.freshForMs - 1, 1, 2, 3)
        store.write(held)
        source.failWith = ChartFetchException(ChartError.Network)
        val result = repo.get("in", ChartKind.Songs)
        assertEquals(held, result.chart)
        assertEquals(ChartError.Network, result.error)
    }

    @Test
    fun `with nothing saved a failure is an error with no chart`() = runBlocking {
        source.failWith = ChartFetchException(ChartError.Unavailable)
        val result = repo.get("in", ChartKind.Songs)
        assertNull(result.chart)
        assertEquals(ChartError.Unavailable, result.error)
    }

    @Test
    fun `an empty feed never replaces a chart that had songs`() = runBlocking {
        val held = chart(now - repo.freshForMs - 1, 1, 2)
        store.write(held)
        source.entries = emptyList()
        val result = repo.get("in", ChartKind.Songs)
        assertEquals(held, result.chart)
        assertEquals(ChartError.Empty, result.error)
        assertEquals(held, store.read("in", ChartKind.Songs))
    }

    @Test
    fun `concurrent requests for the same chart share one fetch`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val shared = AppleChartsRepository(source, store, clock = { now }, io = Dispatchers.Default)
        val results = List(5) { async(Dispatchers.Default) { shared.get("in", ChartKind.Songs) } }
        withTimeout(5_000) { while (source.calls.get() == 0) kotlinx.coroutines.delay(5) }
        kotlinx.coroutines.delay(50)
        gate.complete(Unit)
        val charts = results.awaitAll().map { it.chart }
        assertEquals(1, source.calls.get())
        charts.forEach { assertSame(charts.first(), it) }
    }

    @Test
    fun `regions and categories are cached separately`() = runBlocking {
        repo.get("in", ChartKind.Songs)
        repo.get("in", ChartKind.Albums)
        repo.get("us", ChartKind.Songs)
        assertEquals(3, source.calls.get())
        repo.get("in", ChartKind.Songs)
        assertEquals(3, source.calls.get())
    }

    @Test
    fun `a forced refresh right after a fetch does not ask again`() = runBlocking {
        repo.get("in", ChartKind.Songs)
        now += 30_000
        repo.get("in", ChartKind.Songs, force = true)
        assertEquals(1, source.calls.get())
        now += AppleChartsRepository.MIN_FORCED_INTERVAL_MS
        repo.get("in", ChartKind.Songs, force = true)
        assertEquals(2, source.calls.get())
    }

    @Test
    fun `rate limiting backs off every chart for a while`() = runBlocking {
        source.failWith = ChartFetchException(ChartError.RateLimited, retryAfterMs = 0)
        assertEquals(ChartError.RateLimited, repo.get("in", ChartKind.Songs).error)
        source.failWith = null
        assertEquals(ChartError.RateLimited, repo.get("in", ChartKind.Albums).error)
        assertEquals(1, source.calls.get())
        now += AppleChartsRepository.RATE_LIMIT_BACKOFF_MS
        assertNull(repo.get("in", ChartKind.Albums).error)
    }

    @Test
    fun `the disk copy is remembered after the first read`() = runBlocking {
        store.write(chart(now, 1))
        val first = repo.cached("in", ChartKind.Songs)
        store.charts.clear()
        assertSame(first, repo.cached("in", ChartKind.Songs))
    }
}
