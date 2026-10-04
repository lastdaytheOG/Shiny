package com.shiny.music.ui.liquid

import android.content.Context
import android.os.Process
import android.os.SystemClock
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The covers of the first screen, decoded before that screen asks for them.
 *
 * After a process start Coil's memory cache is empty, so every cover on the first screen is
 * read from disk, decoded and faded in only once its tile has been laid out — after the page
 * itself is up. So for the first seconds of a process [Artwork] reports the covers it showed
 * inside the window, at the pixel size it laid them out at, and the next cold start decodes
 * exactly those into the memory cache while the activity is still starting. The screen's
 * requests then hit memory, and Coil draws memory hits at once, with no fade.
 *
 * The request has to match the one `AsyncImage` makes for the hit to count: same data (Coil's
 * memory key is the URL alone when there are no transformations), and a bitmap at least as
 * large as the laid-out size, which the same size with `Scale.FILL` (Crop) and
 * `Precision.INEXACT` guarantees. Disk only: a cover that was evicted is left to the screen,
 * so the warmup never puts a download on the startup path.
 */
object CoverWarmup {
    private const val FILE = "cover_warmup"
    /** A first screen shows well under this; the rest is a margin for a busier layout. */
    private const val MAX_COVERS = 20
    /** How long after the process starts a shown cover still counts as the first screen. */
    private const val FIRST_SCREEN_MS = 5_000L

    private val recorded = LinkedHashMap<String, Pair<Int, Int>>()
    private val started = AtomicBoolean(false)

    /** True while covers laid out now belong to the first screen of this process. */
    fun isRecording(): Boolean =
        SystemClock.uptimeMillis() - Process.getStartUptimeMillis() < FIRST_SCREEN_MS

    /** One [Artwork]'s report: recorded once it has both loaded and been placed in view. */
    class Note(private val data: String) {
        var width = 0
        var height = 0
        var loaded = false
        /** Recorded, or past the first screen: nothing more to do for this cover. */
        var done = false
            private set

        fun offer() {
            if (!isRecording()) done = true
            if (done || !loaded || width <= 0) return
            record(data, width, height)
            done = true
        }
    }

    /** A cover [data] was shown inside the window at [width]x[height] px. Main thread. */
    fun record(data: String, width: Int, height: Int) {
        if (width <= 0 || height <= 0 || !isRecording()) return
        // One URL can be on screen twice (a chart row and the hero): the larger bitmap serves
        // both, since a request accepts a cached one at least as large as it needs.
        val known = recorded[data]
        if (known == null && recorded.size >= MAX_COVERS) return
        if (known == null || width * height > known.first * known.second) recorded[data] = width to height
    }

    /**
     * Decodes the covers the last first screen showed, and saves this one's once its window
     * has closed. A process started only for playback composes nothing and keeps the old list.
     */
    fun start(context: Context, scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        val file = File(app.filesDir, FILE)
        scope.launch(Dispatchers.IO) {
            val entries = runCatching { file.readLines() }.getOrDefault(emptyList())
            if (entries.isNotEmpty()) {
                val loader = SingletonImageLoader.get(app)
                for (line in entries) {
                    val (w, h, url) = line.split(' ', limit = 3).takeIf { it.size == 3 } ?: continue
                    val width = w.toIntOrNull() ?: continue
                    val height = h.toIntOrNull() ?: continue
                    // `execute`, not `enqueue`: an enqueued request starts on the main thread,
                    // which is busy with the activity's first frame exactly now.
                    val request = ImageRequest.Builder(app)
                        .data(url)
                        .size(width, height)
                        .scale(Scale.FILL)
                        .precision(Precision.INEXACT)
                        .networkCachePolicy(CachePolicy.DISABLED)
                        // A hardware bitmap is uploaded through the GPU, which is busy creating
                        // the first frame's EGL context right now. A plain one decodes on the CPU
                        // alone and still satisfies the screen's (hardware-allowed) request.
                        .allowHardware(false)
                        .build()
                    launch { loader.execute(request) }
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            val left = FIRST_SCREEN_MS - (SystemClock.uptimeMillis() - Process.getStartUptimeMillis())
            delay(left.coerceAtLeast(0) + 500)
            val lines = recorded.map { (url, size) -> "${size.first} ${size.second} $url" }
            if (lines.isEmpty()) return@launch
            launch(Dispatchers.IO) {
                runCatching { file.writeText(lines.joinToString("\n")) }
                    .onFailure { Timber.w(it, "cover warmup list not saved") }
            }
        }
    }
}
