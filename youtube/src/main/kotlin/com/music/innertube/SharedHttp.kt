package com.music.innertube

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * One set of HTTP threads and one connection pool for Shiny's clients.
 *
 * Every `OkHttpClient.Builder()`, and every Ktor OkHttp engine (which builds its own
 * `Dispatcher()`), used to bring its own thread pool and its own connections. Measured on a
 * phone, a minute of background playback started 24 dispatcher threads that each lived a
 * minute and died, and no client reused another's TLS connection to the same host.
 *
 * - OkHttp clients derive from [client] with `newBuilder()`, keeping their own timeouts and
 *   interceptors while sharing [dispatcher] and the pool.
 * - Ktor engines set `config { dispatcher(SharedHttp.ktorDispatcher()) }`: their own
 *   dispatcher (Ktor cancels and shuts its dispatcher down when the client closes, which
 *   must not stop anyone else's requests) running on the shared threads.
 */
object SharedHttp {
    private val threadCount = AtomicInteger()

    /** A few threads stay warm for the steady trickle of requests; a burst still gets more. */
    private val threads = ThreadPoolExecutor(
        4, Int.MAX_VALUE, 60, TimeUnit.SECONDS, SynchronousQueue(),
        ThreadFactory { runnable ->
            Thread(runnable, "OkHttp Shared ${threadCount.incrementAndGet()}").apply { isDaemon = true }
        },
    )

    /** The shared threads behind an executor nobody can shut down. */
    private val unclosable = object : AbstractExecutorService() {
        override fun execute(command: Runnable) = threads.execute(command)
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
    }

    val dispatcher: Dispatcher = Dispatcher(unclosable)

    val connectionPool = ConnectionPool(10, 5, TimeUnit.MINUTES)

    val client: OkHttpClient = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectionPool(connectionPool)
        .build()

    /** For a Ktor OkHttp engine: its own queue and limits, on the shared threads. */
    fun ktorDispatcher(): Dispatcher = Dispatcher(unclosable)
}
