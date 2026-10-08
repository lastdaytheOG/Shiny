package com.shiny.music.applecanvas

import com.shiny.music.canvas.CanvasArtwork
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

private object AppleCanvasLogger {
    fun d(msg: String) = println("AppleMusicCanvas: D: $msg")
    fun w(msg: String) = println("AppleMusicCanvas: W: $msg")
    fun e(t: Throwable, msg: String) {
        println("AppleMusicCanvas: E: $msg")
        t.printStackTrace()
    }
}

/**
 * Fetches Apple Music album motion artwork (HLS canvas) for the album screen.
 *
 * Two extraction strategies are tried in order:
 *
 * 1. **editorialVideo** — present on albums that have Apple Motion artwork.
 *    Accessed via `?extend=editorialVideo` on the AMP albums endpoint.
 *
 * 2. **music-video tracks** — some albums embed a full-length music video as a track.
 *    Accessed via `?include=tracks`.
 *
 * Results are cached for 24 hours.
 */
object AppleMusicCanvasProvider {

    // Public read-only JWT used by the Apple Music web player for unauthenticated catalog reads.


    private const val ITUNES_SEARCH_URL = "https://itunes.apple.com/search"
    private const val AMP_BASE_URL = "https://amp-api.music.apple.com"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
                // iTunes returns text/javascript for JSON responses
                register(ContentType.Text.JavaScript, KotlinxSerializationConverter(json))
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 25_000
                socketTimeoutMillis = 25_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(HttpCache)
            expectSuccess = false
        }
    }

    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 1000L * 60 * 60 * 24 // 24 hours

    suspend fun getByAlbumArtist(
        album: String,
        artist: String,
        storefront: String = "us",
    ): CanvasArtwork? {
        AppleCanvasLogger.d("getByAlbumArtist: album='$album', artist='$artist'")
        val key = cacheKey("sa", album, artist, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }

        val result = searchAndFetchMotion(album, artist, album, storefront, "albums")
        if (result != null) {
            cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        }
        return result
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String? = null,
        storefront: String = "us",
    ): CanvasArtwork? {
        val key = cacheKey("song", song, artist, album ?: "", storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }

        // Use searchAndFetchMotion which can handle song searches by resolving to albums
        val result = searchAndFetchMotion(song, artist, album, storefront, "songs")
        if (result != null) {
            cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        }
        return result
    }

    suspend fun getByAlbumId(
        albumId: String,
        storefront: String = "us",
    ): CanvasArtwork? {
        val key = cacheKey("id", albumId, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }

        val result = fetchMotionArtwork(albumId, storefront, null)
        cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        return result
    }

    /**
     * Searches via AMP API and tries to fetch motion artwork.
     * This is faster than iTunes search + AMP lookup.
     */
    private suspend fun searchAndFetchMotion(
        term: String,
        artist: String,
        album: String?,
        storefront: String,
        type: String, // "albums" or "songs"
    ): CanvasArtwork? {
        return runCatching {
            AppleCanvasLogger.d("searching for $type: $term (album: $album) in $storefront")
            var query = if (term.contains(artist, ignoreCase = true)) term else "$artist $term"
            if (!album.isNullOrBlank() && !query.contains(album, ignoreCase = true)) {
                query = "$query $album"
            }
            val url = "$AMP_BASE_URL/v1/catalog/$storefront/search"
            val token = com.shiny.music.canvas.AppleMusicToken.get() ?: error("No Apple Music token")
            val response = client.get(url) {
                header("Authorization", "Bearer $token")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                parameter("term", query)
                parameter("types", type)
                parameter("limit", "10")
                parameter("extend", "editorialVideo")
                parameter("include", "albums")
            }
            if (response.status != HttpStatusCode.OK) {
                AppleCanvasLogger.w("search failed with status ${response.status}")
                return@runCatching null
            }

            val root = response.body<JsonObject>()
            val results = root["results"]?.jsonObject?.get(type)?.jsonObject?.get("data")?.jsonArray ?: return@runCatching null
            
            // Score results for quality and edition matching
            val scoredResults = results.mapNotNull { item ->
                val obj = item.jsonObject
                val attributes = obj["attributes"]?.jsonObject ?: return@mapNotNull null
                val resultArtistName = attributes["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
                val resultName = attributes["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val resultCollectionName = attributes["collectionName"]?.jsonPrimitive?.contentOrNull ?: ""
                
                // --- Playlist/Set List Filtering ---
                // We should never use playlist animations as album canvas.
                val nameLower = resultName.lowercase(Locale.ROOT)
                val collectionLower = resultCollectionName.lowercase(Locale.ROOT)
                val isBlacklisted = nameLower.contains("playlist") || nameLower.contains("set list") ||
                        collectionLower.contains("playlist") || collectionLower.contains("set list") ||
                        nameLower.contains("essentials") || collectionLower.contains("essentials") ||
                        collectionLower.contains("dj mix") || collectionLower.contains("mixed") ||
                        collectionLower.contains("apple music") || collectionLower.contains("today's hits") ||
                        nameLower.contains("session") || collectionLower.contains("session")
                
                if (isBlacklisted) {
                    AppleCanvasLogger.d("  - Skipping blacklisted result: '$resultName' (Album: '$resultCollectionName')")
                    return@mapNotNull null
                }

                // Strict artist check: result must contain requested artist or vice versa
                val artistMatch = resultArtistName.equals(artist, ignoreCase = true)
                val artistFuzzy = resultArtistName.contains(artist, ignoreCase = true) || artist.contains(resultArtistName, ignoreCase = true)
                
                if (!artistFuzzy) return@mapNotNull null
                
                var score = 0
                if (artistMatch) score += 10
                else score += 5
                
                // Name matching (Song or Album title)
                val nameMatch = resultName.equals(term, ignoreCase = true)
                val nameFuzzy = resultName.contains(term, ignoreCase = true) || term.contains(resultName, ignoreCase = true)
                
                if (nameMatch) {
                    score += 15
                } else if (nameFuzzy) {
                    score += 7
                } else {
                    // If name doesn't match at all, this is likely a different song by the same artist
                    score -= 10
                }

                // Special editions handling (Deluxe, Expanded, etc)
                val editionWords = listOf("deluxe", "expanded", "remastered", "remix", "version", "edit", "mix", "bonus")
                for (word in editionWords) {
                    val inTerm = term.contains(word, ignoreCase = true)
                    val inResult = resultName.contains(word, ignoreCase = true)
                    if (inTerm && inResult) score += 5
                    else if (inTerm != inResult && inResult) score -= 3 // Penalty for unexpected "Deluxe" etc.
                }

                // Album matching - very strong signal
                if (!album.isNullOrBlank() && resultCollectionName.isNotBlank()) {
                    val albumMatch = resultCollectionName.equals(album, ignoreCase = true)
                    val albumFuzzy = resultCollectionName.contains(album, ignoreCase = true) || album.contains(resultCollectionName, ignoreCase = true)
                    
                    if (albumMatch) score += 20
                    else if (albumFuzzy) score += 10
                }
                
                AppleCanvasLogger.d("  - Result: '$resultName' by '$resultArtistName' (Album: '$resultCollectionName', ID: ${obj["id"]}) -> Score: $score")
                score to item
            }.sortedByDescending { it.first }
            
            AppleCanvasLogger.d("Found ${scoredResults.size} scored results for term '$term'")
            
            // Try results until we find motion or exhaustion
            for ((score, item) in scoredResults) {
                if (score < 12) {
                    AppleCanvasLogger.d("skipping result with low score: $score")
                    continue
                }
                val obj = item.jsonObject
                val attributes = obj["attributes"]?.jsonObject ?: continue
                val resultName = attributes["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val resultArtistName = attributes["artistName"]?.jsonPrimitive?.contentOrNull ?: ""

                // 1. Resolve Album ID
                var targetAlbumId: String? = null
                val type = obj["type"]?.jsonPrimitive?.contentOrNull
                if (type == "songs") {
                    val relationships = obj["relationships"]?.jsonObject
                    targetAlbumId = relationships?.get("albums")?.jsonObject?.get("data")?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
                        ?: attributes["collectionId"]?.jsonPrimitive?.contentOrNull
                    
                    // Fallback: Parse from URL if possible
                    if (targetAlbumId == null) {
                        val url = attributes["url"]?.jsonPrimitive?.contentOrNull
                        if (url != null) {
                            // URL format: https://music.apple.com/region/album/name/ID?i=songId
                            val albumPart = url.substringAfter("/album/", "").substringBefore("?")
                            val id = albumPart.substringAfterLast("/", "")
                            if (id.isNotBlank() && id.all { it.isDigit() }) {
                                targetAlbumId = id
                            }
                        }
                    }
                    
                    if (targetAlbumId == null) {
                        AppleCanvasLogger.d("relationships keys for $resultName: ${relationships?.keys}")
                    }
                } else if (type == "albums") {
                    targetAlbumId = obj["id"]?.jsonPrimitive?.contentOrNull
                }

                if (targetAlbumId == null || targetAlbumId.startsWith("pl.")) {
                    AppleCanvasLogger.d("skipping null or playlist albumId ($targetAlbumId) for $resultName ($resultArtistName)")
                    continue
                }

                AppleCanvasLogger.d("trying resolve for $targetAlbumId (from ${obj["type"]?.jsonPrimitive?.contentOrNull})")

                // 2. Check for immediate motion in search result
                val ev = attributes["editorialVideo"]?.jsonObject
                if (ev != null) {
                    val name = attributes["name"]?.jsonPrimitive?.contentOrNull
                    val collName = attributes["collectionName"]?.jsonPrimitive?.contentOrNull
                    // If this is a song result, use song name as name and collection as albumName
                    // If this is an album result, use album name as both name and albumName
                    val resolvedAlbumName = if (type == "songs") collName else name
                    val found = CanvasArtwork(name, resultArtistName, targetAlbumId, albumName = resolvedAlbumName, animated = squareVideo(ev)).withTall(ev)
                    if (found.hasMotion) {
                        AppleCanvasLogger.d("Found direct editorialVideo for $name (ID: $targetAlbumId)")
                        return@runCatching found
                    }
                }

                // 3. Full lookup with metadata preservation
                val fetched = fetchMotionArtwork(
                    albumId = targetAlbumId,
                    storefront = storefront,
                    fallbackArtist = resultArtistName,
                    titleOverride = if (type == "songs") attributes["name"]?.jsonPrimitive?.contentOrNull else null,
                    artistOverride = if (type == "songs") resultArtistName else null
                )
                if (fetched != null) return@runCatching fetched
            }
            AppleCanvasLogger.d("no canvas found in resolution/lookup for $term after ${scoredResults.size} results")
            null
        }.onFailure {
            if (it is CancellationException) throw it
            AppleCanvasLogger.e(it, "error in searchAndFetchMotion for $term")
        }.getOrNull()
    }

    private suspend fun fetchMotionArtwork(
        albumId: String,
        storefront: String,
        fallbackArtist: String?,
        titleOverride: String? = null,
        artistOverride: String? = null,
    ): CanvasArtwork? {
        if (albumId.startsWith("pl.")) {
            AppleCanvasLogger.d("fetchMotionArtwork: ignoring playlist id $albumId")
            return null
        }
        return runCatching {
            AppleCanvasLogger.d("fetching album $albumId")
            val url = "$AMP_BASE_URL/v1/catalog/$storefront/albums/$albumId"
            val token = com.shiny.music.canvas.AppleMusicToken.get() ?: error("No Apple Music token")
            val response = client.get(url) {
                header("Authorization", "Bearer $token")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                parameter("extend", "editorialVideo")
                parameter("include", "tracks")
            }
            if (response.status != HttpStatusCode.OK) {
                AppleCanvasLogger.w("album fetch failed for $albumId: ${response.status}")
                return@runCatching null
            }

            val root = response.body<JsonObject>()
            val data = root["data"]?.jsonArray
            if (data.isNullOrEmpty()) return@runCatching null
            
            val albumObj = data.firstOrNull()?.jsonObject ?: return@runCatching null
            val attributes = albumObj["attributes"]?.jsonObject
            val albumName = attributes?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
            val artistName = attributes?.get("artistName")?.jsonPrimitive?.contentOrNull ?: fallbackArtist
            
            val nameLower = albumName.lowercase(Locale.ROOT)
            val isBlacklisted = nameLower.contains("playlist") || nameLower.contains("set list") ||
                    nameLower.contains("essentials") || nameLower.contains("dj mix") ||
                    nameLower.contains("mixed") || nameLower.contains("apple music") ||
                    nameLower.contains("today's hits") || nameLower.contains("session")
            
            if (isBlacklisted) {
                AppleCanvasLogger.d("fetchMotionArtwork: ignoring blacklisted album '$albumName' ($albumId)")
                return@runCatching null
            }

            // titleOverride is the song name (when searching by song), albumName is always the album name
            val finalTitle = titleOverride ?: albumName
            val finalArtist = artistOverride ?: artistName

            // Strategy 1: editorialVideo
            val ev = attributes?.get("editorialVideo")?.jsonObject
            if (ev != null) {
                val found = CanvasArtwork(finalTitle, finalArtist, albumId, albumName = albumName, animated = squareVideo(ev)).withTall(ev)
                if (found.hasMotion) {
                    AppleCanvasLogger.d("found editorialVideo for $finalTitle (album: $albumName, id: $albumId)")
                    return@runCatching found
                }
            }

            AppleCanvasLogger.d("no editorialVideo for $albumId (available keys: ${attributes?.keys})")
            null
        }.onFailure {
            if (it is CancellationException) throw it
            AppleCanvasLogger.e(it, "error in fetchMotionArtwork for $albumId")
        }.getOrNull()
    }

    /**
     * The album's square motion artwork, under its current name and then its older one. Only
     * the square one: a few albums have the portrait clip alone, and that is not put in the
     * square's place, where it could only be shown cut down to a square.
     */
    private fun squareVideo(ev: JsonObject): String? = videoOf(ev, "motionSquareVideo1x1", "motionDetailSquare")

    /** A value's text, or null when it is not there or is not a plain value. Never throws. */
    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    /** The address of the video in the first of the assets named by [keys] that has one. */
    private fun videoOf(ev: JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> (ev[key] as? JsonObject)?.text("video")?.takeIf { it.isNotBlank() } }

    /**
     * Adds the album's portrait motion artwork, if it has one: the video, a still frame of it
     * and its shape. The catalogue names it `motionTallVideo3x4` (or, before that,
     * `motionDetailTall`), and gives the still as an address with `{w}x{h}` in it.
     */
    private fun CanvasArtwork.withTall(ev: JsonObject): CanvasArtwork {
        val key = listOf("motionTallVideo3x4", "motionDetailTall").firstOrNull { videoOf(ev, it) != null } ?: return this
        val video = videoOf(ev, key) ?: return this
        val frame = (ev[key] as? JsonObject)?.get("previewFrame") as? JsonObject
        val width = frame?.text("width")?.toFloatOrNull()
        val height = frame?.text("height")?.toFloatOrNull()
        return copy(
            tallAnimated = video,
            tallStill = frame?.text("url")?.takeIf { "{w}" in it },
            tallAspect = if (width != null && height != null && height > 0f) width / height else null,
        )
    }

    /**
     * The motion artwork of the album with this catalogue id: the direct question, with no
     * searching and no guessing which album was meant. Null means the album has none, which is
     * the ordinary case. Throws when the catalogue could not be asked (no token, no
     * connection, a refusal), so that "could not ask" is never mistaken for "has none".
     */
    suspend fun albumMotion(albumId: String, storefront: String): CanvasArtwork? {
        suspend fun ask(token: String) = client.get("$AMP_BASE_URL/v1/catalog/$storefront/albums/$albumId") {
            header("Authorization", "Bearer $token")
            header("Origin", "https://music.apple.com")
            header("Referer", "https://music.apple.com/")
            parameter("extend", "editorialVideo")
        }
        val token = com.shiny.music.canvas.AppleMusicToken.get() ?: error("No Apple Music token")
        var response = ask(token)
        if (response.status == HttpStatusCode.Unauthorized) {
            // The token was withdrawn before its own expiry: the web player's current one is
            // fetched and the question put once more.
            AppleCanvasLogger.w("album $albumId: token refused (401); fetching the web player's current one")
            com.shiny.music.canvas.AppleMusicToken.refused(token)
            response = ask(com.shiny.music.canvas.AppleMusicToken.get() ?: error("Apple Music token refused"))
        }
        if (response.status == HttpStatusCode.NotFound) return null
        check(response.status == HttpStatusCode.OK) { "Album $albumId: ${response.status}" }
        // An answer in a shape this does not know is "could not ask", not "has none": if Apple
        // changes the reply, nothing is remembered wrongly and nothing else is affected.
        val data = response.body<JsonObject>()["data"] as? JsonArray ?: error("Album $albumId: no data in the reply")
        val attributes = (data.firstOrNull() as? JsonObject)?.get("attributes") as? JsonObject ?: return null
        val ev = attributes["editorialVideo"] as? JsonObject ?: return null
        val name = attributes.text("name")
        val found = CanvasArtwork(
            name = name,
            artist = attributes.text("artistName"),
            albumId = albumId,
            albumName = name,
            animated = squareVideo(ev),
        ).withTall(ev)
        if (!found.hasMotion) return null
        // The plain file behind one size of each clip: what plays if the stream will not.
        return found.copy(
            videoUrl = found.animated?.let { runCatching { directVideo(it, sharp = false) }.getOrNull() },
            tallVideoUrl = found.tallAnimated?.let { runCatching { directVideo(it, sharp = true) }.getOrNull() },
        )
    }

    /**
     * The MP4 behind one H.264 size of an HLS motion clip, or null: the size nearest a cover on
     * a phone, or with [sharp] the size that fills a phone's width (the portrait clip is drawn
     * that wide).
     */
    private suspend fun directVideo(hlsUrl: String, sharp: Boolean): String? {
        val master = client.get(hlsUrl).takeIf { it.status == HttpStatusCode.OK }?.bodyAsText() ?: return null
        val variants = com.shiny.music.canvas.HlsMotion.variants(hlsUrl, master)
        val variant = (if (sharp) com.shiny.music.canvas.HlsMotion.sharp(variants) else com.shiny.music.canvas.HlsMotion.standard(variants))
            ?: return com.shiny.music.canvas.HlsMotion.directVideoUrl(hlsUrl, master)
        val playlist = client.get(variant.playlistUrl).takeIf { it.status == HttpStatusCode.OK }?.bodyAsText() ?: return null
        return com.shiny.music.canvas.HlsMotion.directVideoUrl(variant.playlistUrl, playlist)
    }

    /** A song as Apple Music's own search lists it. */
    data class SongHit(
        val songId: String,
        /** The album it is on: what the album's motion artwork is asked for by. */
        val albumId: String?,
        val name: String,
        val artistName: String,
        val albumName: String,
        val durationMillis: Long,
        /** The cover's address, with `{w}x{h}` to fill in. */
        val artworkTemplate: String,
        val isrc: String?,
        val explicit: Boolean,
        /** Who the album is by: "Various Artists" for a compilation the song was only gathered onto. */
        val albumArtistName: String = "",
    )

    /**
     * The songs Apple Music's own search finds for [term]: the search the web player uses,
     * which lists every release there is. The public iTunes search does not: it leaves out
     * every explicit song and album, so a song with swearing in it is either not found there
     * at all or found only on the "clean" copy of its album, which is often the copy Apple
     * gave no motion artwork to.
     *
     * Null when it could not be asked (no token, a refusal, no connection); never throws.
     */
    suspend fun searchSongs(term: String, storefront: String): List<SongHit>? = runCatching {
        suspend fun ask(token: String) = client.get("$AMP_BASE_URL/v1/catalog/$storefront/search") {
            header("Authorization", "Bearer $token")
            header("Origin", "https://music.apple.com")
            header("Referer", "https://music.apple.com/")
            parameter("term", term)
            parameter("types", "songs")
            parameter("limit", "25")
            // Each song with the album it is on: its id, and who the album is by.
            parameter("include[songs]", "albums")
        }
        val token = com.shiny.music.canvas.AppleMusicToken.get() ?: return@runCatching null
        var response = ask(token)
        if (response.status == HttpStatusCode.Unauthorized) {
            AppleCanvasLogger.w("search: token refused (401); fetching the web player's current one")
            com.shiny.music.canvas.AppleMusicToken.refused(token)
            response = ask(com.shiny.music.canvas.AppleMusicToken.get() ?: return@runCatching null)
        }
        if (response.status != HttpStatusCode.OK) return@runCatching null
        val results = response.body<JsonObject>()["results"] as? JsonObject ?: return@runCatching null
        // No `songs` at all is how the search says it found none.
        val songs = ((results["songs"] as? JsonObject)?.get("data") as? JsonArray).orEmpty()
        songs.mapNotNull { item ->
            val song = item as? JsonObject ?: return@mapNotNull null
            val attributes = song["attributes"] as? JsonObject ?: return@mapNotNull null
            val template = (attributes["artwork"] as? JsonObject)?.text("url") ?: return@mapNotNull null
            val album = (((song["relationships"] as? JsonObject)?.get("albums") as? JsonObject)?.get("data") as? JsonArray)
                ?.firstOrNull() as? JsonObject
            SongHit(
                songId = song.text("id").orEmpty(),
                // Failing the album itself, a song's address is `…/album/<name>/<album id>?i=<song id>`.
                albumId = album?.text("id")?.takeIf { it.isNotBlank() }
                    ?: attributes.text("url")?.substringAfter("/album/", "")?.substringBefore('?')?.substringAfterLast('/')
                        ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) },
                albumArtistName = (album?.get("attributes") as? JsonObject)?.text("artistName").orEmpty(),
                name = attributes.text("name").orEmpty(),
                artistName = attributes.text("artistName").orEmpty(),
                albumName = attributes.text("albumName").orEmpty(),
                durationMillis = attributes.text("durationInMillis")?.toLongOrNull() ?: 0,
                artworkTemplate = template,
                isrc = attributes.text("isrc"),
                explicit = attributes.text("contentRating") == "explicit",
            )
        }
    }.onFailure {
        if (it is CancellationException) throw it
    }.getOrNull()

    /** A song in the catalogue, as far as its cover goes, and the album it is on. */
    data class CatalogSong(
        val songId: String,
        val artworkTemplate: String,
        val name: String,
        val artistName: String,
        val albumId: String? = null,
        val albumName: String? = null,
    )

    /**
     * The catalogue's song for a recording's [isrc]: an exact identity, with no searching or
     * guessing. Null when there is no token, no such recording, or no connection.
     */
    suspend fun songByIsrc(isrc: String, storefront: String = "us"): CatalogSong? = runCatching {
        val token = com.shiny.music.canvas.AppleMusicToken.get() ?: return@runCatching null
        val response = client.get("$AMP_BASE_URL/v1/catalog/$storefront/songs") {
            header("Authorization", "Bearer $token")
            header("Origin", "https://music.apple.com")
            header("Referer", "https://music.apple.com/")
            parameter("filter[isrc]", isrc)
            // With the album it is on: that id is what its motion artwork is asked for by.
            parameter("include", "albums")
        }
        if (response.status != HttpStatusCode.OK) return@runCatching null
        val song = response.body<JsonObject>()["data"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@runCatching null
        val attributes = song["attributes"]?.jsonObject ?: return@runCatching null
        val template = attributes["artwork"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: return@runCatching null
        val album = (((song["relationships"] as? JsonObject)?.get("albums") as? JsonObject)?.get("data") as? JsonArray)
            ?.firstOrNull() as? JsonObject
        CatalogSong(
            songId = song["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            artworkTemplate = template,
            name = attributes["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            artistName = attributes["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            albumId = album?.text("id")?.takeIf { it.isNotBlank() },
            albumName = attributes.text("albumName"),
        )
    }.onFailure {
        if (it is CancellationException) throw it
    }.getOrNull()

    private fun cacheKey(prefix: String, vararg parts: String): String {
        return "$prefix|" + parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }
    }
}
