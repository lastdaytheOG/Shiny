package com.shiny.music.discord

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * One Discord Gateway session, used only to publish presence (opcode 3). Single use: once it has
 * closed, open a new one.
 *
 * [identifyToken] goes into IDENTIFY's `token` field as is (see DiscordSocialPresenceClient for its
 * form). It is never logged.
 */
class GatewayClient(
    private val identifyToken: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<GatewayReadyEvent>()
    private val lock = Any()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var heartbeatJob: Job? = null
    @Volatile private var awaitingAck = false
    @Volatile private var sequence: Int? = null
    @Volatile private var readyReceived = false
    @Volatile private var closeInfo: GatewayCloseInfo? = null

    /** Called once, off the main thread, when a session that reached READY ends without [disconnect]. */
    @Volatile var onClose: ((GatewayCloseInfo) -> Unit)? = null

    /** READY arrived and nothing has closed the session since. */
    val isConnected: Boolean
        get() = readyReceived && closeInfo == null

    /**
     * Opens the socket and waits for READY.
     *
     * @throws GatewayAuthenticationException when Discord rejects the token (close 4004)
     * @throws GatewayClosedException on any other close, a network failure, or no READY in time
     */
    suspend fun connect(timeoutMs: Long): GatewayReadyEvent {
        check(socket == null) { "GatewayClient is single use" }
        DiscordLog.i("Gateway connecting")
        val webSocket = httpClient.newWebSocket(Request.Builder().url(GATEWAY_URL).build(), Listener())
        socket = webSocket
        try {
            return withTimeout(timeoutMs) { ready.await() }
        } catch (e: TimeoutCancellationException) {
            val info = GatewayCloseInfo(CLOSE_TIMEOUT, "no READY within ${timeoutMs / 1000} s")
            fail(webSocket, info)
            throw GatewayClosedException(info)
        } catch (e: CancellationException) {
            disconnect()
            throw e
        }
    }

    /** Queues a presence update. False when the session isn't READY or has closed. */
    fun sendPresence(payload: JSONObject): Boolean {
        val webSocket = socket ?: return false
        return isConnected && send(webSocket, GatewayOp.PRESENCE_UPDATE, payload)
    }

    /** Closes the session on purpose; [onClose] is not called. */
    fun disconnect() {
        synchronized(lock) {
            if (closeInfo != null) return
            closeInfo = GatewayCloseInfo(1000, "closed by SHINY")
        }
        scope.cancel()
        socket?.close(1000, null)
        if (!readyReceived) ready.completeExceptionally(CancellationException("Gateway disconnected"))
        DiscordLog.i("Gateway disconnected")
    }

    private inner class Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(webSocket, text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            handleClose(GatewayCloseInfo(code, reason))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleClose(GatewayCloseInfo(code, reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            response?.close()
            handleClose(GatewayCloseInfo(CLOSE_NETWORK_FAILURE, t.message ?: t.javaClass.simpleName))
        }
    }

    private fun handleMessage(webSocket: WebSocket, text: String) {
        if (closeInfo != null) return
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (!message.isNull("s")) sequence = message.optInt("s")

        when (message.optInt("op", -1)) {
            GatewayOp.HELLO -> {
                val interval = message.optJSONObject("d")?.optLong("heartbeat_interval") ?: 0L
                DiscordLog.i("Gateway HELLO (heartbeat every $interval ms)")
                if (interval > 0L) startHeartbeat(webSocket, interval)
                identify(webSocket)
            }

            GatewayOp.HEARTBEAT_ACK -> {
                awaitingAck = false
                DiscordLog.i("Heartbeat ACK")
            }

            // Discord asking for a heartbeat right now.
            GatewayOp.HEARTBEAT -> sendHeartbeat(webSocket)

            GatewayOp.RECONNECT -> fail(webSocket, GatewayCloseInfo(CLOSE_RECONNECT_REQUESTED, "Discord asked for a reconnect"))

            GatewayOp.INVALID_SESSION -> fail(webSocket, GatewayCloseInfo(CLOSE_INVALID_SESSION, "invalid session"))

            GatewayOp.DISPATCH -> if (message.optString("t") == "READY") {
                val event = GatewayReadyEvent.fromJson(message.optJSONObject("d") ?: JSONObject())
                readyReceived = true
                DiscordLog.i("Gateway READY" + event.user?.username?.let { " (@$it)" }.orEmpty())
                ready.complete(event)
            }
        }
    }

    private fun identify(webSocket: WebSocket) {
        val properties = JSONObject()
            .put("os", "Android")
            .put("browser", "SHINY")
            .put("device", "SHINY")
        val payload = JSONObject()
            .put("token", identifyToken)
            // Presence needs no Gateway events, so none are asked for (and none can be disallowed).
            .put("intents", 0)
            .put("properties", properties)
        DiscordLog.i(if (send(webSocket, GatewayOp.IDENTIFY, payload)) "IDENTIFY sent" else "IDENTIFY could not be queued")
    }

    private fun startHeartbeat(webSocket: WebSocket, intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            // Discord's jitter: the first beat lands somewhere inside the first interval.
            delay((intervalMs * Random.nextDouble()).toLong())
            while (isActive) {
                if (awaitingAck) {
                    fail(webSocket, GatewayCloseInfo(CLOSE_ZOMBIE, "no heartbeat ACK"))
                    return@launch
                }
                sendHeartbeat(webSocket)
                delay(intervalMs)
            }
        }
    }

    private fun sendHeartbeat(webSocket: WebSocket) {
        awaitingAck = true
        send(webSocket, GatewayOp.HEARTBEAT, sequence ?: JSONObject.NULL)
        DiscordLog.i("Heartbeat sent")
    }

    private fun send(webSocket: WebSocket, op: Int, d: Any): Boolean =
        closeInfo == null && webSocket.send(JSONObject().put("op", op).put("d", d).toString())

    /** A failure the client noticed itself: drop the socket now instead of waiting on a closing handshake. */
    private fun fail(webSocket: WebSocket, info: GatewayCloseInfo) {
        webSocket.cancel()
        handleClose(info)
    }

    private fun handleClose(info: GatewayCloseInfo) {
        synchronized(lock) {
            if (closeInfo != null) return
            closeInfo = info
        }
        scope.cancel()
        DiscordLog.w("Gateway closed (code ${info.code}: ${info.reason})")
        if (!readyReceived) {
            ready.completeExceptionally(
                if (info.code == CLOSE_AUTHENTICATION_FAILED) GatewayAuthenticationException(info) else GatewayClosedException(info),
            )
        } else {
            onClose?.invoke(info)
        }
    }

    private companion object {
        // Discord's heartbeats, not a socket read timeout, tell a quiet session from a dead one.
        val httpClient: OkHttpClient by lazy {
            com.music.innertube.SharedHttp.client.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        }
    }
}
