package com.shiny.music.discover

import com.music.innertube.SharedHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ChartError { Network, RateLimited, Unavailable, Server, Malformed, Empty }

class ChartFetchException(val error: ChartError, val retryAfterMs: Long = 0, cause: Throwable? = null) :
    Exception(error.name, cause)

interface AppleChartSource {
    suspend fun fetch(storefront: String, kind: ChartKind): AppleChart
}

interface ChartStore {
    fun read(storefront: String, kind: ChartKind): AppleChart?
    fun write(chart: AppleChart)
}

data class ChartResult(val chart: AppleChart?, val error: ChartError? = null)

class AppleRssSource(
    private val client: OkHttpClient = SharedHttp.client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val clock: () -> Long = System::currentTimeMillis,
) : AppleChartSource {
    companion object {
        const val BASE = "https://rss.applemarketingtools.com/api/v2"
        private val RETRY_DELAYS_MS = longArrayOf(700L, 2_000L)

        fun url(storefront: String, kind: ChartKind) =
            "$BASE/$storefront/music/most-played/${kind.limit}/${kind.path}.json"
    }

    override suspend fun fetch(storefront: String, kind: ChartKind): AppleChart {
        val request = Request.Builder()
            .url(url(storefront, kind))
            .header("Accept", "application/json")
            .build()
        var attempt = 0
        while (true) {
            val body = try {
                client.newCall(request).await().use { response ->
                    when {
                        response.isSuccessful -> response.body.string()
                        response.code == 429 -> throw ChartFetchException(
                            ChartError.RateLimited,
                            retryAfterMs = response.header("Retry-After")?.toLongOrNull()?.times(1000) ?: 0,
                        )
                        response.code >= 500 -> throw ChartFetchException(ChartError.Server)
                        else -> throw ChartFetchException(ChartError.Unavailable)
                    }
                }
            } catch (e: ChartFetchException) {
                if (e.error == ChartError.Server && attempt < RETRY_DELAYS_MS.size) {
                    delay(RETRY_DELAYS_MS[attempt++])
                    continue
                }
                throw e
            } catch (e: IOException) {
                if (attempt < RETRY_DELAYS_MS.size) {
                    delay(RETRY_DELAYS_MS[attempt++])
                    continue
                }
                throw ChartFetchException(ChartError.Network, cause = e)
            }
            return withContext(Dispatchers.Default) {
                try {
                    AppleChartParser.parse(body, storefront, kind, clock())
                } catch (e: ChartFormatException) {
                    throw ChartFetchException(ChartError.Malformed, cause = e)
                }
            }
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        })
    }
}

class FileChartStore(private val dir: File) : ChartStore {
    @Serializable
    private data class Stored(val version: Int, val chart: AppleChart)

    private companion object {
        const val VERSION = 1
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(storefront: String, kind: ChartKind) = File(dir, "apple_${storefront}_${kind.path}.json")

    override fun read(storefront: String, kind: ChartKind): AppleChart? {
        val f = file(storefront, kind)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString(Stored.serializer(), f.readText()) }
            .onFailure { Timber.tag("Discover").w(it, "unreadable chart cache, ignored") }
            .getOrNull()
            ?.takeIf { it.version == VERSION && it.chart.storefront == storefront && it.chart.kind == kind }
            ?.chart
    }

    override fun write(chart: AppleChart) {
        var tmp: File? = null
        runCatching {
            dir.mkdirs()
            tmp = File.createTempFile("chart", ".tmp", dir).also {
                it.writeText(json.encodeToString(Stored.serializer(), Stored(VERSION, chart)))
                check(it.renameTo(file(chart.storefront, chart.kind))) { "rename failed" }
            }
        }.onFailure {
            tmp?.delete()
            Timber.tag("Discover").w(it, "chart not saved")
        }
    }
}

class AppleChartsRepository(
    private val source: AppleChartSource,
    private val store: ChartStore,
    private val clock: () -> Long = System::currentTimeMillis,
    val freshForMs: Long = DEFAULT_FRESH_MS,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        const val DEFAULT_FRESH_MS = 10L * 60 * 60 * 1000
        const val MIN_FORCED_INTERVAL_MS = 2L * 60 * 1000
        const val RATE_LIMIT_BACKOFF_MS = 15L * 60 * 1000
    }

    private data class Key(val storefront: String, val kind: ChartKind)

    private val scope = CoroutineScope(SupervisorJob() + io)
    private val memory = ConcurrentHashMap<Key, AppleChart>()
    private val inFlight = HashMap<Key, CompletableDeferred<AppleChart>>()
    private val lock = Mutex()

    @Volatile
    private var blockedUntil = 0L

    fun isFresh(chart: AppleChart): Boolean = clock() - chart.fetchedAt in 0 until freshForMs

    suspend fun cached(storefront: String, kind: ChartKind): AppleChart? {
        val key = Key(storefront, kind)
        memory[key]?.let { return it }
        return withContext(io) { store.read(storefront, kind) }?.also { memory.putIfAbsent(key, it) }
    }

    suspend fun get(storefront: String, kind: ChartKind, force: Boolean = false): ChartResult {
        val held = cached(storefront, kind)
        if (held != null) {
            val age = clock() - held.fetchedAt
            if (!force && isFresh(held)) return ChartResult(held)
            if (force && age in 0 until MIN_FORCED_INTERVAL_MS) return ChartResult(held)
        }
        if (clock() < blockedUntil) return ChartResult(held, ChartError.RateLimited)
        return try {
            val fresh = fetchShared(Key(storefront, kind))
            if (fresh.entries.isEmpty() && held?.entries?.isNotEmpty() == true) {
                ChartResult(held, ChartError.Empty)
            } else {
                ChartResult(fresh, if (fresh.entries.isEmpty()) ChartError.Empty else null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChartFetchException) {
            Timber.tag("Discover").i(e.cause, "%s %s chart failed: %s", storefront, kind, e.error)
            if (e.error == ChartError.RateLimited) {
                blockedUntil = clock() + maxOf(e.retryAfterMs, RATE_LIMIT_BACKOFF_MS)
            }
            ChartResult(held, e.error)
        } catch (e: Exception) {
            Timber.tag("Discover").w(e, "chart fetch failed")
            ChartResult(held, ChartError.Network)
        }
    }

    private suspend fun fetchShared(key: Key): AppleChart {
        val (deferred, owner) = lock.withLock {
            inFlight[key]?.let { it to false } ?: CompletableDeferred<AppleChart>().also { inFlight[key] = it } to true
        }
        if (owner) {
            scope.launch {
                try {
                    val chart = source.fetch(key.storefront, key.kind)
                    val held = memory[key]
                    if (chart.entries.isNotEmpty() || held == null || held.entries.isEmpty()) {
                        memory[key] = chart
                        store.write(chart)
                    }
                    deferred.complete(chart)
                } catch (e: Throwable) {
                    deferred.completeExceptionally(e)
                } finally {
                    lock.withLock { if (inFlight[key] === deferred) inFlight.remove(key) }
                }
            }
        }
        return deferred.await()
    }
}
