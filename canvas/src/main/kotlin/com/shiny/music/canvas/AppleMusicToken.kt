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

    /**
     * Where the token is kept between runs of the app, once the app has said. Finding it means
     * fetching the web player's page and script, about a megabyte, and it is good for some
     * two months: kept here, that megabyte is spent once and not at every start.
     */
    @Volatile
    var file: java.io.File? = null

    private val source = AppleMusicTokenSource(
        page = PAGE,
        origin = ORIGIN,
        fetch = { url ->
            val response = client.get(url) { header("User-Agent", USER_AGENT) }
            if (response.status.isSuccess()) response.bodyAsText() else null
        },
        kept = object : AppleMusicTokenSource.Kept {
            override fun read(): String? = runCatching { file?.takeIf { it.exists() }?.readText()?.trim() }.getOrNull()
            override fun write(token: String?) {
                runCatching {
                    val target = file ?: return
                    if (token == null) target.delete() else target.apply { parentFile?.mkdirs() }.writeText(token)
                }
            }
        },
    )

    /** The current token, or null if it could not be obtained. */
    suspend fun get(): String? = source.token()

    /** Says that Apple would not accept [token]; the next [get] looks for the one the web player has now. */
    suspend fun refused(token: String) = source.refused(token)
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
    private val kept: Kept? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Somewhere a token outlives the process. Reading and writing may fail quietly; neither may throw. */
    interface Kept {
        fun read(): String?
        fun write(token: String?)
    }

    private class Held(val token: String, val staleAt: Long)

    private val mutex = Mutex()
    private var held: Held? = null
    private var retryAt = 0L
    private var refused: String? = null
    private var refusedUntil = 0L
    private var recalled = false

    suspend fun token(): String? = mutex.withLock {
        if (!recalled) {
            // The one found on an earlier run, if it says when it expires and has not yet.
            recalled = true
            kept?.read()?.let { saved ->
                expiryMillis(saved)?.let { expiry -> held = Held(saved, expiry - EXPIRY_MARGIN_MS) }
            }
        }
        held?.takeIf { now() < it.staleAt }?.let { return@withLock it.token }
        // A failed look-up is not repeated for every song that asks.
        if (now() < retryAt) return@withLock null
        val fresh = runCatching { load() }.getOrNull()
        if (fresh == null) {
            retryAt = now() + RETRY_PAUSE_MS
            return@withLock null
        }
        if (fresh == refused && now() < refusedUntil) {
            // The web player still carries the token Apple just turned down. Asking again and
            // again would fetch its whole script each time, so it is left alone for a while,
            // and after that given another chance: the refusal may not have been about the token.
            retryAt = refusedUntil
            return@withLock null
        }
        val expiry = expiryMillis(fresh)
        held = Held(fresh, if (expiry != null) expiry - EXPIRY_MARGIN_MS else now() + UNDATED_LIFETIME_MS)
        // Only one that says when it expires is worth keeping for another run.
        if (expiry != null) kept?.write(fresh)
        fresh
    }

    /**
     * Apple would not accept [token], though by its own date it had not expired (they are
     * replaced in the web player from time to time). It is let go of, so the next [token]
     * reads the one the web player has now.
     */
    suspend fun refused(token: String) = mutex.withLock {
        if (held?.token != token) return@withLock
        held = null
        kept?.write(null)
        refused = token
        refusedUntil = now() + REFUSED_PAUSE_MS
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
        private const val REFUSED_PAUSE_MS = 30 * 60_000L
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

        /**
         * The catalogue token in [text]: the signed token issued to the web player
         * (`"iss":"AMPWebPlay"`). Failing one that says so, the first signed token, which is
         * where the web player has always listed it.
         */
        fun tokenIn(text: String): String? {
            val tokens = jwt.findAll(text).map { it.value }.filter { payload(it) != null }.toList()
            return tokens.firstOrNull { webPlayIssuer.containsMatchIn(payload(it).orEmpty()) } ?: tokens.firstOrNull()
        }

        private val webPlayIssuer = Regex(""""iss"\s*:\s*"AMPWebPlay"""")

        /** When [token] expires, in epoch milliseconds, or null if it does not say. */
        fun expiryMillis(token: String): Long? =
            payload(token)?.let { expClaim.find(it) }?.groupValues?.get(1)?.toLongOrNull()?.times(1000)

        private fun payload(token: String): String? = runCatching {
            String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.startsWith("{") }
    }
}
