package com.shiny.music.home

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import kotlin.math.exp
import kotlin.math.min

/**
 * What the listener reaches for on Home itself.
 *
 * The listen history says what was *played*; it cannot say that someone opens the charts
 * every morning, or taps their way through moods, or lives in search. Those are the
 * signals Home needs to reorder itself around real behaviour rather than around a guess,
 * and nothing in the database records them.
 *
 * So this does, in the smallest form that works: one decayed number per kind of interest,
 * no ids, no queries, no timestamps of individual taps, no history that could be read
 * back as a record of what someone looked at. A tap adds one and the number halves every
 * [HALF_LIFE_DAYS] days, so a habit that stops is worth a quarter of its weight within a
 * month and nothing worth acting on within a season, while one that continues settles
 * near its ceiling. Nothing is ever deleted; it simply stops mattering.
 */
@Serializable
data class HomeInterest(
    val version: Int = VERSION,
    /** When the counts below were last decayed, in `System.currentTimeMillis()`. */
    val updatedAt: Long = 0,
    val charts: Double = 0.0,
    val moods: Double = 0.0,
    val releases: Double = 0.0,
    val discovery: Double = 0.0,
) {
    enum class Kind { Charts, Moods, Releases, Discovery }

    operator fun get(kind: Kind): Double = when (kind) {
        Kind.Charts -> charts
        Kind.Moods -> moods
        Kind.Releases -> releases
        Kind.Discovery -> discovery
    }

    /**
     * The count as a 0..1 weight. Saturating, like everything else Home ranks with: the
     * first tap is worth a lot more than the tenth, so one curious visit to the charts
     * does not outrank a month of rotation.
     */
    fun lean(kind: Kind): Double = this[kind].let { if (it <= 0.0) 0.0 else it / (it + 3.0) }

    /** The counts as they stand at [nowMs], having faded since [updatedAt]. */
    fun decayed(nowMs: Long): HomeInterest {
        val age = nowMs - updatedAt
        if (updatedAt == 0L || age <= 0L) return this
        val factor = exp(-age.toDouble() / (HALF_LIFE_DAYS * HOME_DAY_MS / LN2))
        if (factor >= 0.999) return this
        return copy(
            updatedAt = nowMs,
            charts = fade(charts, factor),
            moods = fade(moods, factor),
            releases = fade(releases, factor),
            discovery = fade(discovery, factor),
        )
    }

    /** [kind] noted once more, at [nowMs], on top of whatever has survived the decay. */
    fun noting(kind: Kind, nowMs: Long): HomeInterest {
        val base = decayed(nowMs).copy(updatedAt = nowMs)
        fun next(current: Double) = min(CEILING, current + 1.0)
        return when (kind) {
            Kind.Charts -> base.copy(charts = next(base.charts))
            Kind.Moods -> base.copy(moods = next(base.moods))
            Kind.Releases -> base.copy(releases = next(base.releases))
            Kind.Discovery -> base.copy(discovery = next(base.discovery))
        }
    }

    companion object {
        const val VERSION = 1

        /** An interest that stops being acted on is spent within about a month. */
        const val HALF_LIFE_DAYS = 14.0

        /** No single habit can grow without limit and pin a section to the top forever. */
        const val CEILING = 12.0

        private const val LN2 = 0.6931471805599453

        val Empty = HomeInterest()

        private fun fade(value: Double, factor: Double): Double =
            (value * factor).let { if (it < 0.01) 0.0 else it }
    }
}

/**
 * [HomeInterest] on disk. One small file beside Home's cached network block, written at
 * most once every [WRITE_DEBOUNCE_MS] however fast the taps arrive, so a listener flicking
 * through mood tiles costs one write rather than twenty.
 */
class HomeInterestStore(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, FILE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val _state = MutableStateFlow(HomeInterest.Empty)
    val state: StateFlow<HomeInterest> = _state.asStateFlow()
    private var pendingWrite: Job? = null

    /** Reads the file once, already faded to now. A missing or unreadable file is empty. */
    suspend fun restore() = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext
        val restored = runCatching { json.decodeFromString(HomeInterest.serializer(), file.readText()) }
            .onFailure { Timber.tag(TAG).w(it, "unreadable, ignored") }
            .getOrNull()
            ?.takeIf { it.version == HomeInterest.VERSION }
            ?: return@withContext
        mutex.withLock { _state.value = restored.decayed(System.currentTimeMillis()) }
    }

    /** Notes one interaction. Returns immediately; the file catches up. */
    fun note(kind: HomeInterest.Kind) {
        _state.value = _state.value.noting(kind, System.currentTimeMillis())
        if (pendingWrite?.isActive == true) return
        pendingWrite = scope.launch(Dispatchers.IO) {
            delay(WRITE_DEBOUNCE_MS)
            save(_state.value)
        }
    }

    private suspend fun save(interest: HomeInterest) = mutex.withLock {
        var tmp: File? = null
        runCatching {
            tmp = File.createTempFile(FILE, ".tmp", context.filesDir).also {
                it.writeText(json.encodeToString(HomeInterest.serializer(), interest))
                check(it.renameTo(file)) { "rename failed" }
            }
        }.onFailure {
            tmp?.delete()
            Timber.tag(TAG).w(it, "not saved")
        }
    }

    private companion object {
        const val FILE = "home_interest.json"
        const val TAG = "HomeInterest"
        const val WRITE_DEBOUNCE_MS = 4_000L
    }
}
