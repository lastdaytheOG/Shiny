package com.shiny.music.ui.screens.settings

import android.content.Context
import com.shiny.music.BuildConfig
import com.shiny.music.db.entities.Song
import com.shiny.music.discord.DiscordLog
import com.shiny.music.discord.DiscordOAuthRepository
import com.shiny.music.discord.DiscordSocialPresenceClient
import com.shiny.music.discord.DiscordTokenCheck
import com.shiny.music.discord.GatewayAuthenticationException
import com.shiny.music.utils.DiscordRPC
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Keeps the Discord activity in step with playback while MusicService says it should show. Every
 * update reads the song, position and pause state from the providers given to [start], and the
 * token from [DiscordOAuthRepository].
 */
object DiscordPresenceManager {
    private const val STOP_TIMEOUT_MS = 5_000L
    private const val RETRY_BASE_MS = 15_000L
    private const val RETRY_MAX_MS = 5 * 60_000L

    private val started = AtomicBoolean(false)
    private val generation = AtomicLong(0L)
    private val updateMutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var scope: CoroutineScope? = null
    private var updateJob: Job? = null
    private var rpc: DiscordRPC? = null
    private var appContext: Context? = null
    private var songProvider: (() -> Song?)? = null
    private var positionProvider: (() -> Long)? = null
    private var isPausedProvider: (() -> Boolean)? = null

    @Volatile private var failures = 0
    @Volatile private var retryAt = 0L

    /** A token Discord's API accepts but its Gateway refused: sending it again can't work. */
    @Volatile private var refusedToken: String? = null

    fun isRunning(): Boolean = started.get()

    /** Starts presence (or points it at new providers) and publishes the current state. Main thread. */
    fun start(
        context: Context,
        songProvider: () -> Song?,
        positionProvider: () -> Long,
        isPausedProvider: () -> Boolean,
    ) {
        appContext = context.applicationContext
        this.songProvider = songProvider
        this.positionProvider = positionProvider
        this.isPausedProvider = isPausedProvider
        if (!started.getAndSet(true)) {
            failures = 0
            retryAt = 0L
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            DiscordLog.i("Presence started")
        }
        requestUpdate()
    }

    /** Publishes the current state again (song change, pause, resume, seek). Main thread. */
    fun restart(): Boolean = started.get() && requestUpdate()

    /** Clears the activity and closes the Gateway session. */
    fun stop() {
        if (!started.getAndSet(false)) return
        generation.incrementAndGet()
        updateJob?.cancel()
        updateJob = null
        scope?.cancel()
        scope = null
        rpc = null
        cleanupScope.launch {
            updateMutex.withLock {
                // Started again while this waited: the new run owns the session.
                if (started.get()) return@withLock
                runCatching { withTimeout(STOP_TIMEOUT_MS) { DiscordSocialPresenceClient.close() } }
                    .onFailure { DiscordLog.w("Closing Discord presence failed", it) }
            }
        }
        DiscordLog.i("Presence stopped")
    }

    private fun requestUpdate(): Boolean {
        val activeScope = scope ?: return false
        val context = appContext ?: return false
        val readSong = songProvider ?: return false
        val readPosition = positionProvider ?: return false
        val readPaused = isPausedProvider ?: return false

        val id = generation.incrementAndGet()
        updateJob?.cancel()
        updateJob = activeScope.launch {
            val wait = retryAt - System.currentTimeMillis()
            if (wait > 0L) delay(wait)
            val (song, positionMs, isPaused) = withContext(Dispatchers.Main.immediate) {
                Triple(readSong(), readPosition(), readPaused())
            }
            if (update(context, song, positionMs, isPaused, id)) {
                withContext(Dispatchers.Main) { if (generation.get() == id) requestUpdate() }
            }
        }
        return true
    }

    /** @return true when it failed in a way worth trying again at [retryAt] */
    private suspend fun update(
        context: Context,
        song: Song?,
        positionMs: Long,
        isPaused: Boolean,
        id: Long,
    ): Boolean =
        updateMutex.withLock {
            if (id != generation.get()) return@withLock false

            val token = DiscordOAuthRepository.getValidAccessToken(context)
            if (token == null) {
                DiscordLog.w("No usable Discord session; nothing sent")
                return@withLock false
            }
            if (token == refusedToken) return@withLock false

            val presence = rpc?.takeIf { it.accessToken == token } ?: DiscordRPC(context, token).also { rpc = it }
            try {
                if (song == null) presence.stopActivity() else presence.updateSong(song, positionMs, isPaused)
                failures = 0
                retryAt = 0L
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: GatewayAuthenticationException) {
                onTokenRefused(context, token)
            } catch (e: Exception) {
                backOff("Presence update failed", e)
                true
            }
        }

    private suspend fun onTokenRefused(context: Context, token: String): Boolean =
        when (DiscordOAuthRepository.checkToken(context, token)) {
            DiscordTokenCheck.VALID -> {
                refusedToken = token
                DiscordLog.w(
                    "Discord's API accepts this session but its Gateway refused it. Check that application " +
                        "${BuildConfig.DISCORD_APPLICATION_ID} has the Social SDK enabled and that the granted scopes " +
                        "include sdk.social_layer_presence. Not retrying until Discord is reconnected.",
                )
                false
            }

            DiscordTokenCheck.REFRESHED -> {
                failures = 0
                retryAt = 0L
                true
            }

            // The session is cleared: settings ask to reconnect, and MusicService stops presence.
            DiscordTokenCheck.REVOKED -> false

            DiscordTokenCheck.UNKNOWN -> {
                backOff("Couldn't check the refused session", null)
                true
            }
        }

    private fun backOff(message: String, error: Throwable?) {
        failures++
        val delayMs = min(RETRY_MAX_MS, RETRY_BASE_MS shl min(failures - 1, 5))
        retryAt = System.currentTimeMillis() + delayMs
        DiscordLog.w("$message (attempt $failures); trying again in ${delayMs / 1000} s", error)
    }
}
