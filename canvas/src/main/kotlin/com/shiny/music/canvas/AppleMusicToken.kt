package com.shiny.music.canvas

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Base64

/**
 * The developer token Apple Music's own web player uses for catalogue reads.
 *
 * The web player ships the token inside its JavaScript; there is no endpoint that hands it out.
 * So this loads the player's page, follows it to its scripts and takes the token from there.
 * One provider serves the whole app (canvas, artist backgrounds, album notes).
 */
object AppleMusicToken {
    private const val PAGE = "https://music.apple.com/us/browse"
    private const val ORIGIN = "https://music.apple.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
        }
    }

    private val source = AppleMusicTokenSource(
        page = PAGE,
        origin = ORIGIN,
        fetch = { url ->
            val response = client.get(url) { header("User-Agent", USER_AGENT) }
            if (response.status.isSuccess()) response.bodyAsText() else null
        },
    )

    /** The current token, or null if it could not be obtained. */
    suspend fun get(): String? = source.token()
}

/**
 * Finds the token and remembers it until it expires.
 *
 * @param fetch returns the text at a URL, or null if it could not be loaded. It may throw.
 */
class AppleMusicTokenSource(
    private val page: String,
    private val origin: String,
    private val fetch: suspend (url: String) -> String?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Held(val token: String, val staleAt: Long)

    private val mutex = Mutex()
    private var held: Held? = null
    private var retryAt = 0L

    suspend fun token(): String? = mutex.withLock {
        held?.takeIf { now() < it.staleAt }?.let { return@withLock it.token }
        // A failed look-up is not repeated for every song that asks.
        if (now() < retryAt) return@withLock null
        val fresh = runCatching { load() }.getOrNull()
        if (fresh == null) {
            retryAt = now() + RETRY_PAUSE_MS
            return@withLock null
        }
        val expiry = expiryMillis(fresh)
        held = Held(fresh, if (expiry != null) expiry - EXPIRY_MARGIN_MS else now() + UNDATED_LIFETIME_MS)
        fresh
    }

    private suspend fun load(): String? {
        val html = fetch(page) ?: return null
        for (script in scriptPaths(html).take(MAX_SCRIPTS)) {
            val text = fetch(if (script.startsWith("http")) script else origin + script) ?: continue
            tokenIn(text)?.let { return it }
        }
        return null
    }

    companion object {
        private const val EXPIRY_MARGIN_MS = 5 * 60_000L
        private const val UNDATED_LIFETIME_MS = 60 * 60_000L
        private const val RETRY_PAUSE_MS = 60_000L
        private const val MAX_SCRIPTS = 4

        private val scriptSrc = Regex("""<script\b[^>]*\bsrc\s*=\s*["']([^"']+\.js)["']""", RegexOption.IGNORE_CASE)
        private val jwt = Regex("""eyJ[A-Za-z0-9_-]{8,}\.eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{16,}""")
        private val expClaim = Regex(""""exp"\s*:\s*(\d+)""")

        /**
         * The page's scripts, most likely token carrier first: the player's entry script is named
         * `index…`, and the `legacy` build is only a fallback for old browsers.
         */
        fun scriptPaths(html: String): List<String> =
            scriptSrc.findAll(html).map { it.groupValues[1] }.distinct().toList().sortedBy { path ->
                val name = path.substringAfterLast('/').lowercase()
                when {
                    name.startsWith("index") && "legacy" !in name -> 0
                    name.startsWith("index") -> 1
                    else -> 2
                }
            }

        /** The first signed token in [text]; the web player lists the catalogue token first. */
        fun tokenIn(text: String): String? = jwt.findAll(text).map { it.value }.firstOrNull { payload(it) != null }

        /** When [token] expires, in epoch milliseconds, or null if it does not say. */
        fun expiryMillis(token: String): Long? =
            payload(token)?.let { expClaim.find(it) }?.groupValues?.get(1)?.toLongOrNull()?.times(1000)

        private fun payload(token: String): String? = runCatching {
            String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.startsWith("{") }
    }
}
