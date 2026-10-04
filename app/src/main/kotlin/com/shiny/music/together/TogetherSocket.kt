package com.shiny.music.together

import android.content.Context
import com.shiny.music.BuildConfig
import com.shiny.music.constants.TogetherServerUrlKey
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

internal val TogetherJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    coerceInputValues = true
}

/** Where the Together server lives: the user's override, the build's, or the dev machine. */
object TogetherServer {
    /** The emulator's view of the computer running `npm run together:dev`. */
    private const val EmulatorDevServer = "http://10.0.2.2:8788"

    fun base(context: Context): String? {
        val override = context.dataStore.get(TogetherServerUrlKey, "").trim().trimEnd('/')
        if (override.startsWith("http://") || override.startsWith("https://")) return override
        if (BuildConfig.TOGETHER_SERVER_URL.isNotBlank()) return BuildConfig.TOGETHER_SERVER_URL
        // Only an emulator can see the computer's dev server at 10.0.2.2; on a real phone that
        // address leads nowhere, so a phone without a server says so instead of timing out.
        if (BuildConfig.DEBUG && isEmulator()) return EmulatorDevServer
        return null
    }

    private fun isEmulator(): Boolean {
        val fingerprint = android.os.Build.FINGERPRINT.orEmpty()
        val hardware = android.os.Build.HARDWARE.orEmpty()
        return fingerprint.startsWith("generic") || fingerprint.contains("emulator") ||
            hardware == "ranchu" || hardware == "goldfish" ||
            android.os.Build.PRODUCT.orEmpty().contains("sdk_gphone")
    }

    fun socketUrl(base: String, code: String) =
        base.replaceFirst(Regex("^http"), "ws") + "/v1/rooms/$code/socket"

    fun inviteUrl(base: String, code: String) = "$base/j/$code"
}

/** The two plain requests: start a session, and look at one before joining. */
class TogetherApi(private val http: OkHttpClient) {
    suspend fun create(base: String): TogetherCreated = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base/v1/rooms")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Server answered ${response.code}")
            TogetherJson.decodeFromString(TogetherCreated.serializer(), response.body?.string().orEmpty())
        }
    }

    /** Null when there is no live session with that code. */
    suspend fun preview(base: String, code: String): TogetherPreview? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$base/v1/rooms/$code").get().build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return@use null
            if (!response.isSuccessful) throw IOException("Server answered ${response.code}")
            TogetherJson.decodeFromString(TogetherPreview.serializer(), response.body?.string().orEmpty())
        }
    }
}

/**
 * One session's connection: reconnects on its own with backoff, says hello every time it
 * opens (the session supplies the words, so a reconnect presents the member's token), and
 * keeps [clock] honest with pings — five quick ones after opening, then a slow pulse.
 */
class TogetherSocket(
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val clock: TogetherClock,
) {
    sealed interface Event {
        data class Opened(val reconnect: Boolean) : Event
        data class Message(val type: String, val body: JsonObject) : Event
        /** The connection dropped; [retrying] says whether it will come back by itself. */
        data class Dropped(val code: Int, val retrying: Boolean) : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 256)
    val events: SharedFlow<Event> = _events

    @Volatile private var socket: WebSocket? = null
    @Volatile private var url: String? = null
    @Volatile private var wanted = false
    @Volatile private var opened = false
    private var attempts = 0
    private var everOpened = false
    private var retryJob: Job? = null
    private var pingJob: Job? = null

    /** Called on every open; its result is the hello frame. */
    var hello: (() -> JsonObject)? = null

    val isOpen: Boolean get() = opened

    fun open(url: String) {
        close()
        this.url = url
        wanted = true
        attempts = 0
        everOpened = false
        connect()
    }

    fun close(code: Int = 1000) {
        wanted = false
        retryJob?.cancel()
        pingJob?.cancel()
        socket?.close(code, null)
        socket = null
        opened = false
    }

    fun send(message: JsonObject): Boolean = socket?.takeIf { opened }?.send(message.toString()) ?: false

    private fun connect() {
        val target = url ?: return
        val request = Request.Builder().url(target).build()
        socket = http.newWebSocket(request, Listener())
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (webSocket !== socket) return
            opened = true
            val reconnect = everOpened
            everOpened = true
            hello?.invoke()?.let { webSocket.send(it.toString()) }
            startPings(webSocket)
            _events.tryEmit(Event.Opened(reconnect))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== socket) return
            val body = runCatching { TogetherJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            val type = body["t"]?.jsonPrimitive?.content ?: return
            if (type == "pong") {
                val sent = body["c"]?.jsonPrimitive?.long ?: return
                val server = body["s"]?.jsonPrimitive?.long ?: return
                clock.onPong(sent, server, System.currentTimeMillis())
                // A connection that answers is a good one; later drops start the backoff over.
                attempts = 0
                return
            }
            _events.tryEmit(Event.Message(type, body))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== socket) return
            dropped(code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== socket) return
            Timber.tag(TAG).d("socket failure: ${t.message}")
            dropped(-1)
        }
    }

    private fun dropped(code: Int) {
        opened = false
        pingJob?.cancel()
        // 44xx: the server meant it (removed, declined, no such session). Don't knock again.
        val terminal = code in 4400..4499 || code == 1000
        val retrying = wanted && !terminal
        _events.tryEmit(Event.Dropped(code, retrying))
        if (!retrying) {
            wanted = false
            socket = null
            return
        }
        attempts++
        val backoff = min(15_000L, 500L shl min(attempts, 5)) + Random.nextLong(0, 400)
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(backoff)
            if (wanted) connect()
        }
    }

    private fun startPings(webSocket: WebSocket) {
        pingJob?.cancel()
        pingJob = scope.launch {
            var sent = 0
            while (isActive && webSocket === socket && opened) {
                webSocket.send(buildJsonObject {
                    put("t", "ping")
                    put("c", System.currentTimeMillis())
                }.toString())
                sent++
                delay(if (sent < 5) 250L else 10_000L)
            }
        }
    }

    companion object {
        private const val TAG = "ShinyTogether"

        fun httpClient(): OkHttpClient = com.music.innertube.SharedHttp.client.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
