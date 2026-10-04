package com.shiny.music.utils

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One always-current copy of the whole preference file, held in Compose snapshot state.
 *
 * This replaces two things at once.
 *
 * Reads used to go through [get], which is `runBlocking(Dispatchers.IO)` — and
 * `rememberPreference` passed that call as the `initial` argument of `collectAsState`,
 * an argument Kotlin evaluates on *every* recomposition rather than only the first. The
 * player reads 22 preferences and recomposes off a 100 ms position tick, so that was
 * hundreds of blocking main-thread hops per second on the one screen where the frame
 * budget matters most.
 *
 * Separately, every call site opened its own collector on `dataStore.data`. DataStore
 * re-emits the entire [Preferences] object on any write, so a single toggle woke roughly
 * eighty independent flows.
 *
 * Here a single collector fills [preferences], and call sites read it through a
 * `derivedStateOf` (see `rememberPreference`). That derivation recomputes whenever any
 * preference changes but only invalidates its own readers when *its* key changed, which
 * is exactly the `distinctUntilChanged` the per-key flows used to provide — without the
 * per-key flow.
 */
object PreferencesSnapshot {

    private var preferences by mutableStateOf<Preferences?>(null)

    /**
     * Whether the file has been read at least once.
     *
     * A plain field rather than snapshot state: the splash screen polls this from
     * outside composition, and there is no reason to make that reader depend on
     * snapshot-apply timing.
     */
    @Volatile
    var isLoaded: Boolean = false
        private set

    /**
     * The same fact as [isLoaded], but read through snapshot state so composition
     * re-runs when it flips.
     *
     * Both exist deliberately. [isLoaded] is polled from a pre-draw listener outside
     * composition, where a snapshot read would be pointless; this one is read *by*
     * composition, which is how the app tree knows it can compose against real values.
     */
    val hasValue: Boolean
        get() = preferences != null

    private var job: Job? = null

    /**
     * Process-lifetime fallback for callers with no scope of their own to lend. Never an
     * Activity scope: a collector cancelled by a configuration change would freeze the
     * snapshot at whatever it last held, and every preference in the app would quietly
     * stop updating.
     */
    private val fallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Begins mirroring the preference file. Idempotent, so it is safe to call from every
     * process entry point; a call made while a live collector already exists does
     * nothing, and one made after a previous collector died replaces it.
     *
     * Collected on the main dispatcher on purpose. The file read itself happens on
     * DataStore's own IO scope, so nothing blocks here; keeping the *write* to snapshot
     * state on the main thread just removes any question about when [isLoaded] becomes
     * visible to the splash callback that gates the first frame.
     */
    fun start(context: Context, scope: CoroutineScope = fallbackScope) {
        if (job?.isActive == true) return
        val appContext = context.applicationContext
        job = scope.launch(Dispatchers.Main.immediate) {
            appContext.dataStore.data.collect { prefs ->
                preferences = prefs
                isLoaded = true
            }
        }
    }

    /**
     * Reads [key], yielding [defaultValue] until the first emission lands.
     *
     * Callers get the default for a short window on a cold start rather than blocking
     * for the real value; [isLoaded] exists so the launch path can wait instead.
     */
    fun <T> read(key: Preferences.Key<T>, defaultValue: T): T {
        val prefs = preferences ?: return defaultValue
        val stored = try {
            prefs[key]
        } catch (e: Exception) {
            null
        }
        return coerce(stored, defaultValue)
    }

    /**
     * Reads a string-backed key without a default, for enum preferences that decode the
     * raw name themselves.
     */
    fun readString(key: Preferences.Key<String>): String? {
        val prefs = preferences ?: return null
        // Widened to Any? deliberately. `Preferences.get` is an unchecked cast, so an
        // entry stored under this name with a different type arrives here typed as
        // String without ever having been one. Left at the declared type the compiler
        // would consider the check below redundant and drop it; going through Any?
        // forces the instanceof that actually catches the mismatch.
        val stored: Any? = try {
            prefs[key]
        } catch (e: Exception) {
            null
        }
        return stored as? String
    }

    /**
     * Falls back to [defaultValue] when the stored value's type no longer matches the
     * declared one.
     *
     * Preferences here have changed type across releases, and a mismatched entry would
     * otherwise reach an unchecked cast and crash. Sets are exempt because the stored
     * implementation class legitimately differs from whatever the default was built as.
     */
    private fun <T> coerce(stored: T?, defaultValue: T): T =
        if (stored != null &&
            defaultValue != null &&
            defaultValue !is Set<*> &&
            stored::class != defaultValue::class
        ) {
            defaultValue
        } else {
            stored ?: defaultValue
        }
}
