package com.shiny.music.canvas

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class AppleMusicTokenTest {
    private val page = "https://music.example/us/browse"
    private val origin = "https://music.example"

    private fun b64(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())

    private fun token(expSeconds: Long?, id: String = "a"): String {
        val claims = if (expSeconds == null) """{"iss":"TEAM$id"}""" else """{"iss":"TEAM$id","exp":$expSeconds}"""
        return b64("""{"alg":"ES256","typ":"JWT","kid":"KEY"}""") + "." + b64(claims) + "." + "s".repeat(40)
    }

    private val html = """
        <!doctype html><html><head>
        <script type="module" crossorigin src="/assets/vendor~abc.js"></script>
        <script nomodule src="/assets/index-legacy~1f2e.js"></script>
        <script type="module" crossorigin src="/assets/index~9d8c7b.js"></script>
        </head><body></body></html>
    """.trimIndent()

    private fun bundle(token: String) = """const a=1;var cfg={developerToken:"$token",other:"eyJnot.a.token"};"""

    /** A site with [pages], counting how often each URL is asked for. */
    private class Site(val pages: MutableMap<String, String?>) {
        val hits = mutableListOf<String>()
        suspend fun fetch(url: String): String? {
            hits += url
            return pages[url]
        }
    }

    @Test
    fun `the entry script is tried first and the legacy build after it`() {
        assertEquals(
            listOf("/assets/index~9d8c7b.js", "/assets/index-legacy~1f2e.js", "/assets/vendor~abc.js"),
            AppleMusicTokenSource.scriptPaths(html),
        )
    }

    @Test
    fun `the token is found in the script and its expiry read`() {
        val jwt = token(expSeconds = 2_000_000_000)
        assertEquals(jwt, AppleMusicTokenSource.tokenIn(bundle(jwt)))
        assertEquals(2_000_000_000_000, AppleMusicTokenSource.expiryMillis(jwt))
        assertNull(AppleMusicTokenSource.tokenIn("var x = 'no token here'"))
    }

    @Test
    fun `the token is fetched once and reused until it expires`() = runBlocking {
        var clock = 1_000_000L
        val first = token(expSeconds = 2_000, id = "a") // expires at 2,000,000 ms
        val site = Site(mutableMapOf(page to html, "$origin/assets/index~9d8c7b.js" to bundle(first)))
        val source = AppleMusicTokenSource(page, origin, site::fetch) { clock }

        assertEquals(first, source.token())
        assertEquals(first, source.token())
        assertEquals(listOf(page, "$origin/assets/index~9d8c7b.js"), site.hits)

        // Inside the safety margin before expiry, a new one is fetched.
        val second = token(expSeconds = 9_000, id = "b")
        site.pages["$origin/assets/index~9d8c7b.js"] = bundle(second)
        clock = 2_000_000L - 60_000L
        assertEquals(second, source.token())
        assertEquals(4, site.hits.size)
    }

    @Test
    fun `a script without the token falls through to the next one`() = runBlocking {
        val jwt = token(expSeconds = 2_000_000_000)
        val site = Site(
            mutableMapOf(
                page to html,
                "$origin/assets/index~9d8c7b.js" to "console.log('nothing')",
                "$origin/assets/index-legacy~1f2e.js" to null,
                "$origin/assets/vendor~abc.js" to bundle(jwt),
            ),
        )
        assertEquals(jwt, AppleMusicTokenSource(page, origin, site::fetch).token())
    }

    @Test
    fun `a failure gives null and is not retried at once`() = runBlocking {
        var clock = 0L
        val site = Site(mutableMapOf(page to null))
        val source = AppleMusicTokenSource(page, origin, site::fetch) { clock }

        assertNull(source.token())
        assertNull(source.token())
        assertEquals(1, site.hits.size)

        val jwt = token(expSeconds = 2_000_000_000)
        site.pages[page] = html
        site.pages["$origin/assets/index~9d8c7b.js"] = bundle(jwt)
        clock = 61_000L
        assertEquals(jwt, source.token())
    }

    @Test
    fun `an error while fetching gives null`() = runBlocking {
        val source = AppleMusicTokenSource(page, origin, { error("offline") })
        assertNull(source.token())
    }

    @Test
    fun `a token without an expiry is kept for an hour`() = runBlocking {
        var clock = 0L
        val jwt = token(expSeconds = null)
        val site = Site(mutableMapOf(page to html, "$origin/assets/index~9d8c7b.js" to bundle(jwt)))
        val source = AppleMusicTokenSource(page, origin, site::fetch) { clock }
        assertEquals(jwt, source.token())
        clock = 59 * 60_000L
        assertEquals(jwt, source.token())
        assertEquals(2, site.hits.size)
        clock = 61 * 60_000L
        assertEquals(jwt, source.token())
        assertEquals(4, site.hits.size)
    }

    @Test
    fun `callers that ask together share one fetch`() = runBlocking {
        val jwt = token(expSeconds = 2_000_000_000)
        val site = Site(mutableMapOf(page to html, "$origin/assets/index~9d8c7b.js" to bundle(jwt)))
        val source = AppleMusicTokenSource(page, origin, site::fetch)
        val results = List(8) { async { source.token() } }.awaitAll()
        assertEquals(List(8) { jwt }, results)
        assertEquals(2, site.hits.size)
    }
}
