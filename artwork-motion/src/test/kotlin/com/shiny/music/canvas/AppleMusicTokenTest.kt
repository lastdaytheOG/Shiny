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
    fun `a token apple refuses before its time is replaced by the one the web player has now`() = runBlocking {
        var clock = 1_000_000L
        val withdrawn = token(expSeconds = 2_000_000_000, id = "a")
        val script = "$origin/assets/index~9d8c7b.js"
        val site = Site(mutableMapOf(page to html, script to bundle(withdrawn)))
        val source = AppleMusicTokenSource(page, origin, site::fetch) { clock }
        assertEquals(withdrawn, source.token())

        // Refused, and the web player has a new one: that is what the next ask gets.
        val current = token(expSeconds = 2_000_000_000, id = "b")
        site.pages[script] = bundle(current)
        source.refused(withdrawn)
        assertEquals(current, source.token())
        assertEquals(current, source.token())
        assertEquals(4, site.hits.size)
        // Being told again about a token no longer held changes nothing.
        source.refused(withdrawn)
        assertEquals(current, source.token())
        assertEquals(4, site.hits.size)
    }

    @Test
    fun `a refused token the web player still carries is not fetched over and over`() = runBlocking {
        var clock = 1_000_000L
        val jwt = token(expSeconds = 2_000_000_000)
        val site = Site(mutableMapOf(page to html, "$origin/assets/index~9d8c7b.js" to bundle(jwt)))
        val source = AppleMusicTokenSource(page, origin, site::fetch) { clock }
        assertEquals(jwt, source.token())
        source.refused(jwt)
        // Looked for once more, found to be the same one, and then left alone for a while.
        assertNull(source.token())
        assertNull(source.token())
        clock += 10 * 60_000L
        assertNull(source.token())
        assertEquals(4, site.hits.size)
        // Half an hour on it is given another chance: the refusal may not have been about the token.
        clock += 21 * 60_000L
        assertEquals(jwt, source.token())
        assertEquals(6, site.hits.size)
    }

    private class Shelf(var token: String? = null) : AppleMusicTokenSource.Kept {
        override fun read(): String? = token
        override fun write(token: String?) {
            this.token = token
        }
    }

    @Test
    fun `a token found on one run is used on the next without fetching, until it expires`() = runBlocking {
        var clock = 1_000_000L
        val jwt = token(expSeconds = 5_000) // expires at 5,000,000 ms
        val script = "$origin/assets/index~9d8c7b.js"
        val site = Site(mutableMapOf(page to html, script to bundle(jwt)))
        val shelf = Shelf()
        assertEquals(jwt, AppleMusicTokenSource(page, origin, site::fetch, shelf) { clock }.token())
        assertEquals(jwt, shelf.token)
        assertEquals(2, site.hits.size)

        // The next run of the app: nothing is fetched.
        val next = AppleMusicTokenSource(page, origin, site::fetch, shelf) { clock }
        assertEquals(jwt, next.token())
        assertEquals(2, site.hits.size)

        // A run after it has expired fetches the one the web player has by then.
        val later = token(expSeconds = 9_000, id = "b")
        site.pages[script] = bundle(later)
        clock = 5_000_000L
        assertEquals(later, AppleMusicTokenSource(page, origin, site::fetch, shelf) { clock }.token())
        assertEquals(later, shelf.token)
        assertEquals(4, site.hits.size)
    }

    @Test
    fun `a kept token apple refuses is thrown away, and one that says no expiry is never kept`() = runBlocking {
        val jwt = token(expSeconds = 2_000_000_000)
        val script = "$origin/assets/index~9d8c7b.js"
        val site = Site(mutableMapOf(page to html, script to bundle(token(expSeconds = 2_000_000_000, id = "b"))))
        val shelf = Shelf(jwt)
        val source = AppleMusicTokenSource(page, origin, site::fetch, shelf) { 1_000_000L }
        assertEquals(jwt, source.token())
        source.refused(jwt)
        assertNull(shelf.token)
        assertEquals(token(expSeconds = 2_000_000_000, id = "b"), source.token())

        val undated = Shelf()
        site.pages[script] = bundle(token(expSeconds = null))
        AppleMusicTokenSource(page, origin, site::fetch, undated) { 1_000_000L }.token()
        assertNull(undated.token)
        // And something on the shelf that is not a token at all is simply not used.
        val junk = Shelf("not a token")
        site.pages[script] = bundle(jwt)
        assertEquals(jwt, AppleMusicTokenSource(page, origin, site::fetch, junk) { 1_000_000L }.token())
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

    @Test
    fun `of several signed tokens the one issued to the web player is the catalogue token`() {
        fun signed(issuer: String) =
            b64("""{"alg":"ES256","typ":"JWT","kid":"KEY"}""") + "." + b64("""{"iss":"$issuer","exp":4102444800}""") + "." + "s".repeat(40)
        val other = signed("SomethingElse")
        val webPlay = signed("AMPWebPlay")
        assertEquals(webPlay, AppleMusicTokenSource.tokenIn("""var a="$other",b="$webPlay";"""))
        // With none that says so, the first signed token, as before.
        assertEquals(other, AppleMusicTokenSource.tokenIn("""var a="$other";"""))
    }
}
