package com.shiny.music.discord

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Publishes SHINY's Discord activity over a Gateway session opened with the user's OAuth2 access
 * token. Remembers what should be showing, so a session that drops comes back with it; clearing
 * or closing forgets it.
 */
object DiscordSocialPresenceClient {
    private const val CONNECT_TIMEOUT_MS = 20_000L

    /** Discord allows 5 presence updates per 20 s on a session; one every 4 s stays under it. */
    private const val MIN_UPDATE_INTERVAL_MS = 4_000L

    /** A resend of the same activity whose timestamps moved by less than this is skipped. */
    private const val TIMESTAMP_TOLERANCE_MS = 2_000L
    private const val RECONNECT_BASE_MS = 2_000L
    private const val RECONNECT_MAX_MS = 5 * 60_000L

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var gateway: GatewayClient? = null
    private var gatewayToken: String? = null

    /**
     * Whether Discord took IDENTIFY's token with the `Bearer ` prefix OAuth2 tokens use elsewhere.
     * Null until a session reaches READY; until then both forms are tried, Bearer first.
     */
    private var bearerPrefix: Boolean? = null

    /** The presence that should be showing: sent again after a reconnect. */
    private var wanted: JSONObject? = null

    /** The presence the current session last sent. */
    private var sent: JSONObject? = null
    private var sentAt = 0L

    @Volatile private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    /**
     * Shows [activity], connecting first if needed.
     *
     * @throws GatewayAuthenticationException when Discord's Gateway refuses [accessToken]
     */
    suspend fun updatePresence(accessToken: String, activity: DiscordPresenceActivity) {
        reconnectJob?.cancel()
        withContext(Dispatchers.IO) {
            val payload = buildPresencePayload(activity)
            mutex.withLock {
                wanted = payload
                publish(accessToken.trim(), payload)
            }
        }
    }

    /** Removes the activity. Doesn't connect just for that: without a session nothing is showing. */
    suspend fun clearPresence() {
        reconnectJob?.cancel()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                wanted = null
                clearLocked()
            }
        }
    }

    /** Removes the activity and ends the session. */
    suspend fun close() {
        reconnectJob?.cancel()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                wanted = null
                clearLocked()
                dropGateway()
                gatewayToken = null
                reconnectAttempts = 0
            }
        }
    }

    private fun clearLocked() {
        val session = gateway?.takeIf { it.isConnected } ?: return
        val last = sent ?: return
        if (last.optJSONArray("activities")?.length() == 0) return
        val empty = emptyPresence()
        if (session.sendPresence(empty)) {
            sent = empty
            sentAt = System.currentTimeMillis()
            DiscordLog.i("Activity cleared")
        } else {
            DiscordLog.w("Activity clear could not be sent")
        }
    }

    /** Sends [payload] on a READY session for [token]. Called with [mutex] held. */
    private suspend fun publish(token: String, payload: JSONObject) {
        require(token.isNotEmpty()) { "Discord access token is missing" }
        var session = connectedGateway(token)
        if (sameAsSent(payload)) return

        val wait = sentAt + MIN_UPDATE_INTERVAL_MS - System.currentTimeMillis()
        if (wait > 0L) delay(wait)

        if (!session.sendPresence(payload)) {
            // The session ended between READY and this send: one fresh session.
            dropGateway()
            session = connectedGateway(token)
            check(session.sendPresence(payload)) { "Discord Gateway did not take the presence update" }
        }
        sent = payload
        sentAt = System.currentTimeMillis()
        DiscordLog.i("Presence update sent: $payload")
    }

    private suspend fun connectedGateway(token: String): GatewayClient {
        gateway?.let { current ->
            if (current.isConnected && gatewayToken == token) return current
        }
        dropGateway()

        val forms = bearerPrefix?.let { listOf(it) } ?: listOf(true, false)
        var rejection: GatewayAuthenticationException? = null
        for (bearer in forms) {
            val client = GatewayClient(if (bearer) "Bearer $token" else token)
            try {
                client.connect(CONNECT_TIMEOUT_MS)
            } catch (e: GatewayAuthenticationException) {
                DiscordLog.w("IDENTIFY rejected with the ${tokenForm(bearer)} token")
                rejection = e
                continue
            }
            if (bearerPrefix == null) DiscordLog.i("Gateway accepted the ${tokenForm(bearer)} token")
            bearerPrefix = bearer
            client.onClose = { info -> scope.launch { onSessionLost(client, info) } }
            gateway = client
            gatewayToken = token
            reconnectAttempts = 0
            return client
        }
        throw checkNotNull(rejection)
    }

    private fun tokenForm(bearer: Boolean) = if (bearer) "Bearer-prefixed" else "bare"

    private fun dropGateway() {
        gateway?.let {
            it.onClose = null
            it.disconnect()
        }
        gateway = null
        sent = null
        sentAt = 0L
    }

    private suspend fun onSessionLost(client: GatewayClient, info: GatewayCloseInfo) {
        mutex.withLock {
            if (gateway !== client) return
            gateway = null
            sent = null
            val token = gatewayToken ?: return
            when {
                info.fatal -> DiscordLog.w("Not reconnecting after close ${info.code}")
                wanted == null -> DiscordLog.i("Session ended with nothing showing; the next update reconnects")
                else -> scheduleReconnect(token)
            }
        }
    }

    /** Called with [mutex] held. */
    private fun scheduleReconnect(token: String) {
        val delayMs = min(RECONNECT_MAX_MS, RECONNECT_BASE_MS shl min(reconnectAttempts, 8)) + Random.nextLong(1_000L)
        reconnectAttempts++
        DiscordLog.i("Reconnecting in ${delayMs / 1000} s (attempt $reconnectAttempts)")
        reconnectJob = scope.launch {
            delay(delayMs)
            mutex.withLock {
                val presence = wanted ?: return@withLock
                if (gatewayToken != token) return@withLock
                try {
                    publish(token, presence)
                    DiscordLog.i("Presence restored after reconnect")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: GatewayAuthenticationException) {
                    DiscordLog.w("Reconnect refused; waiting for the next update", e)
                } catch (e: Exception) {
                    DiscordLog.w("Reconnect failed", e)
                    scheduleReconnect(token)
                }
            }
        }
    }

    private fun sameAsSent(payload: JSONObject): Boolean {
        val previous = sent ?: return false
        val (previousText, previousTimes) = splitTimestamps(previous)
        val (text, times) = splitTimestamps(payload)
        if (previousText != text) return false
        if (previousTimes == null || times == null) return previousTimes == null && times == null
        return listOf("start", "end").all { key ->
            previousTimes.has(key) == times.has(key) && abs(previousTimes.optLong(key) - times.optLong(key)) < TIMESTAMP_TOLERANCE_MS
        }
    }

    /** The payload as text without its activity's timestamps, and those timestamps. */
    private fun splitTimestamps(payload: JSONObject): Pair<String, JSONObject?> {
        val copy = JSONObject(payload.toString())
        val timestamps = copy.optJSONArray("activities")?.optJSONObject(0)?.remove("timestamps") as? JSONObject
        return copy.toString() to timestamps
    }

    private fun emptyPresence(): JSONObject =
        JSONObject()
            .put("since", JSONObject.NULL)
            .put("activities", JSONArray())
            .put("status", "online")
            .put("afk", false)

    private suspend fun buildPresencePayload(activity: DiscordPresenceActivity): JSONObject {
        val json = JSONObject()
            .put("name", activity.name ?: "SHINY")
            .put("type", activity.type.nativeValue)
            .put("application_id", activity.applicationId.toString())
            .put("platform", "android")
        activity.details?.let { json.put("details", it) }
        activity.state?.let { json.put("state", it) }

        val timestamps = JSONObject()
        activity.timestamps.startMs?.let { timestamps.put("start", it) }
        activity.timestamps.endMs?.let { timestamps.put("end", it) }
        if (timestamps.length() > 0) json.put("timestamps", timestamps)

        val assets = JSONObject()
        activity.assets.largeImage?.let { DiscordArtAssets.resolve(it) }?.let { assets.put("large_image", it) }
        activity.assets.largeText?.let { assets.put("large_text", it) }
        activity.assets.smallImage?.let { DiscordArtAssets.resolve(it) }?.let { assets.put("small_image", it) }
        activity.assets.smallText?.let { assets.put("small_text", it) }
        if (assets.length() > 0) json.put("assets", assets)

        val buttons = activity.buttons.take(2)
        if (buttons.isNotEmpty()) {
            json.put("buttons", JSONArray(buttons.map { it.label }))
            json.put("metadata", JSONObject().put("button_urls", JSONArray(buttons.map { it.url })))
        }

        return JSONObject()
            .put("since", JSONObject.NULL)
            .put("activities", JSONArray().put(json))
            .put(
                "status",
                when (activity.onlineStatus) {
                    DiscordOnlineStatus.Idle -> "idle"
                    DiscordOnlineStatus.Dnd -> "dnd"
                    else -> "online"
                },
            )
            .put("afk", false)
    }
}
