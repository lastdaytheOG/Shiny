package com.shiny.music.artwork

import com.shiny.music.canvas.CanvasArtwork
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

/*
 * One place that knows where a song's artwork comes from.
 *
 *                      Apple catalogue
 *                            │
 *              ┌─────────────┴─────────────┐
 *        static artwork              editorial video
 *       (master: 3000²)              (motion artwork)
 *              │                    ┌──────┴──────┐
 *              │                  square         tall
 *              └────────────────────┴─────────────┘
 *                                   │
 *                          AppleArtworkResult
 *                                   │
 *                              Now Playing
 *
 *   the song's own artwork   a file's embedded cover, or the picture YouTube / Spotify gave.
 *                            Always shown first and at once; nothing waits on anything else.
 *   Apple's cover            the catalogue's own cover, at any size. Used where the song's own
 *                            picture is not a cover at all (a music video's still) or is
 *                            missing. Found through Apple's catalogue search.
 *   Apple's motion artwork   the animated cover some albums have, square and portrait.
 *                            Optional, and entirely behind [AppleMotionArtworkProvider].
 *
 * The song is first identified in Apple's catalogue (by ISRC where Shiny knows it, otherwise
 * by a checked match on artist, title and album). That one identification serves both the
 * cover and the motion artwork, which is asked for by the album's catalogue id rather than by
 * searching a second time.
 *
 * What was found, and what was looked for and not found, is remembered in memory and on
 * disk under a key that identifies the recording, so nothing is asked twice. Callers asking
 * for the same song at the same moment share one request. Nothing here throws.
 */

/** Where the still artwork in an [ArtworkResult] came from. */
enum class ArtworkSource {
    /** Apple's catalogue cover. */
    APPLE,

    /** The cover inside a file on the phone. */
    EMBEDDED,

    /** The artwork the song came with (YouTube Music's, Spotify's): what Shiny has always shown. */
    FALLBACK,

    NONE,
}

/** How a motion artwork address is played. */
enum class MotionArtworkType { HLS, MP4 }

/**
 * A song, as far as finding its artwork needs it: what it is called and who made it. Nothing
 * else about the listener or their library is part of a lookup.
 */
data class ArtworkQuery(
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationSeconds: Int = 0,
    /** The recording's ISRC, when Shiny knows it. Identifies it exactly. */
    val isrc: String? = null,
    /** The artwork the song already has: a file's own cover, or the one its service gave. */
    val ownArtwork: String? = null,
    /** A file on the phone. Its name is never sent anywhere. */
    val local: Boolean = false,
) {
    /**
     * What identifies this recording in the cache: its ISRC, or failing that its artist, album
     * and track together. Null when there is not enough to say which song it is, in which case
     * nothing is looked up at all. A title on its own is never a key.
     */
    val key: String?
        get() {
            val code = isrc?.trim()?.uppercase()?.takeIf { it.length >= 8 }
            if (code != null) return "isrc:$code"
            val artist = artists.firstOrNull()?.let(ArtworkMatcher::key).orEmpty()
            val track = ArtworkMatcher.key(ArtworkMatcher.songTitle(title, artists))
            if (artist.length < 2 || track.isEmpty()) return null
            return "n:$artist|${album?.let(ArtworkMatcher::albumKey).orEmpty()}|$track"
        }

    /** Whether the song's own picture is a music video's still: 720 pixels across at best, and not a cover. */
    val ownArtworkIsVideoStill: Boolean
        get() = ownArtwork != null && "i.ytimg.com" in ownArtwork
}

/** What is known about one recording's artwork. Kept on disk as it is. */
@Serializable
data class ArtworkEntry(
    val key: String,
    /** Apple's address for the cover, or null if the catalogue has no such song (or was never asked). */
    val appleArtwork: String? = null,
    /** Apple's id for the song. */
    val appleSongId: String? = null,
    /** Apple's id for the album it is on: what motion artwork is asked for by. */
    val appleAlbumId: String? = null,
    /** When the catalogue was last asked to identify the song; 0 for never. */
    val catalogueCheckedAt: Long = 0,
    val motion: CanvasArtwork? = null,
    /** When motion artwork was last looked for; 0 for never. */
    val motionCheckedAt: Long = 0,
    val usedAt: Long = 0,
    /** The catalogue's name for that album. */
    val appleAlbumName: String? = null,
    /** The storefront the song was found in. Ids are a storefront's own: the album is asked for there. */
    val appleStorefront: String? = null,
    /** The same release under its other catalogue ids, asked when [appleAlbumId] has no motion artwork. */
    val appleSiblingAlbumIds: List<String> = emptyList(),
    /** The [ArtworkMatcher.Version] that identified the song; an older one's answer is looked up again. */
    val matcher: Int = 0,
    /** For a song that names no album: the other releases it is on; see [ArtworkMatcher.alternates]. */
    val appleAlternates: List<CatalogRelease> = emptyList(),
) {
    /** What the catalogue has for this recording, or null when it has (or was asked) nothing. */
    fun apple(): AppleArtworkResult? {
        if (appleArtwork == null && appleSongId == null && appleAlbumId == null && motion == null) return null
        return AppleArtworkResult(
            songId = appleSongId,
            albumId = appleAlbumId ?: motion?.albumId,
            staticArtwork = appleArtwork,
            albumName = appleAlbumName,
            squareMotion = motion?.let { motionOf(listOfNotNull(it.animated, it.videoUrl), it.static, 1f) },
            tallMotion = motion?.let { motionOf(listOfNotNull(it.tallAnimated, it.tallVideoUrl), it.tallStill, it.tallAspect ?: 0.75f) },
        )
    }
}

private fun motionOf(addresses: List<String>, still: String?, aspect: Float): AppleMotionArtwork? {
    val real = addresses.filter { it.isNotBlank() }
    if (real.isEmpty()) return null
    return AppleMotionArtwork(
        hlsUri = real.firstOrNull { motionTypeOf(it) == MotionArtworkType.HLS },
        mp4Uri = real.firstOrNull { motionTypeOf(it) == MotionArtworkType.MP4 },
        still = still,
        aspect = aspect,
    )
}

/** One shape of an album's motion artwork. */
data class AppleMotionArtwork(
    /** The stream, in every size Apple made of it; the player takes the one that fits the screen. */
    val hlsUri: String?,
    /** One plain file of it, for where the stream cannot be played. */
    val mp4Uri: String?,
    /** A still frame of it: an address, or one with `{w}` and `{h}` to fill in. */
    val still: String?,
    /** Width over height: 1 for the square, about 3:4 for the tall. */
    val aspect: Float,
) {
    val uri: String? get() = hlsUri ?: mp4Uri
    val type: MotionArtworkType? get() = uri?.let(::motionTypeOf)
}

/** What Apple's catalogue has for one recording: its cover, and its album's motion artwork in both shapes. */
data class AppleArtworkResult(
    val songId: String?,
    val albumId: String?,
    /** The cover's address as the catalogue gave it. [staticAt] gives it at a size. */
    val staticArtwork: String?,
    /** The catalogue's name for the album the cover is of. */
    val albumName: String?,
    val squareMotion: AppleMotionArtwork?,
    val tallMotion: AppleMotionArtwork?,
) {
    /** The cover at the size of Apple's master (3000 × 3000 at most): all there is of it. */
    val staticArtworkUri: String? get() = staticAt(ArtworkMatcher.MasterPx)

    /**
     * The cover [px] pixels square. A screen cannot show more pixels than it has, so what is
     * drawn is asked for at the size it is drawn; [best] asks for it all but uncompressed.
     */
    fun staticAt(px: Int, best: Boolean = false): String? = staticArtwork?.let { ArtworkMatcher.sized(it, px, best) }
}

/** What the interface is given for a song. */
data class ArtworkResult(
    /** `apple:<id>` for a song found in Apple's catalogue, otherwise the recording's cache key. */
    val identifier: String?,
    /** The still artwork to show: Apple's at its master size, or the song's own. */
    val staticArtworkUri: String?,
    val motion: CanvasArtwork?,
    val source: ArtworkSource,
    /** Everything the catalogue has for the song, whichever still is shown. */
    val apple: AppleArtworkResult? = null,
) {
    val squareMotionArtworkUri: String? get() = motion?.preferredAnimationUrl
    val tallMotionArtworkUri: String? get() = motion?.tallAnimated

    /** The motion artwork to play where nothing says which shape is wanted: the square one. */
    val motionArtworkUri: String? get() = squareMotionArtworkUri ?: tallMotionArtworkUri
    val motionArtworkType: MotionArtworkType? get() = motionArtworkUri?.let(::motionTypeOf)
}

/** Apple's motion artwork is an HLS stream; the plain file behind one size of it is an MP4. */
fun motionTypeOf(uri: String): MotionArtworkType {
    val path = uri.substringBefore('?').lowercase()
    return if (path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".mov")) MotionArtworkType.MP4 else MotionArtworkType.HLS
}

/** Asks Apple's public catalogue for songs matching some words. */
fun interface CatalogSearch {
    /** The songs found, empty when there are none, or null when the catalogue could not be asked at all. */
    suspend fun songs(term: String, country: String): List<CatalogCandidate>?
}

/** A song found in Apple's catalogue. */
data class CatalogSong(
    val artwork: String?,
    val songId: String?,
    val albumId: String?,
    val albumName: String? = null,
    /** The storefront it was found in. */
    val storefront: String? = null,
    /** The same release under other ids; see [ArtworkMatcher.siblings]. */
    val siblingAlbumIds: List<String> = emptyList(),
    /** Other releases the song is on, for one that names no album; see [ArtworkMatcher.alternates]. */
    val alternates: List<CatalogRelease> = emptyList(),
)

/** One release a song is on in Apple's catalogue: the album, the song on it, and its cover. */
@Serializable
data class CatalogRelease(
    val albumId: String,
    val songId: String? = null,
    val artwork: String? = null,
    val albumName: String? = null,
)

/**
 * Apple's motion artwork, and the one other catalogue read that needs the same web-player
 * token: a song by its ISRC. Everything that depends on that token (it is not a documented
 * interface, and Apple can change it) is behind this, so it can be replaced, or stop working,
 * without touching covers or anything else.
 */
interface AppleMotionArtworkProvider {
    /**
     * The motion artwork of the album with this catalogue id, or null when it has none.
     * Throws when it could not be looked for (no token, no connection).
     */
    suspend fun motionForAlbum(albumId: String, country: String): CanvasArtwork?

    /**
     * Motion artwork for a song the catalogue search could not identify, found some other
     * way, or null. Throws when it could not be looked for.
     */
    suspend fun motionFor(query: ArtworkQuery): CanvasArtwork? = null

    /** The catalogue's song for the recording with this ISRC, or null. */
    suspend fun songByIsrc(isrc: String, country: String): CatalogSong? = null
}

/** Where [ArtworkEntry]s are kept between runs. */
interface ArtworkIndex {
    fun load(): Map<String, ArtworkEntry>
    fun save(entries: Collection<ArtworkEntry>)
}

class ArtworkResolver(
    private val index: ArtworkIndex,
    private val catalog: CatalogSearch,
    private val motionProvider: AppleMotionArtworkProvider,
    private val countries: () -> List<String> = { listOf("us") },
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val maxEntries: Int = 1500,
    /** Told each step of a lookup: what went in, what came out, and why not. */
    private val log: (String) -> Unit = {},
) {
    private val entries = ConcurrentHashMap<String, ArtworkEntry>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Any?>>()

    @Volatile
    private var loaded = false

    /** Reads the index from disk, once. Everything that needs it calls this first. */
    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching { index.load() }.getOrNull()?.let(entries::putAll)
            loaded = true
        }
    }

    /**
     * What is already in memory for [query]. Asks no one and reads nothing: before [load] has
     * run (it runs off the main thread, with the first lookup) this is simply null.
     */
    fun known(query: ArtworkQuery): ArtworkEntry? = query.key?.let { entries[it] }

    /** The result as it stands for [query], from what is already known. Never touches the network. */
    fun result(query: ArtworkQuery): ArtworkResult {
        val entry = known(query)
        val apple = entry?.apple()
        val identifier = entry?.appleSongId?.let { "apple:$it" } ?: query.key
        return when {
            query.local -> ArtworkResult(query.key, query.ownArtwork, null, if (query.ownArtwork != null) ArtworkSource.EMBEDDED else ArtworkSource.NONE)
            apple?.staticArtworkUri != null && needsAppleCover(query) ->
                ArtworkResult(identifier, apple.staticArtworkUri, entry.motion, ArtworkSource.APPLE, apple)
            query.ownArtwork != null -> ArtworkResult(identifier, query.ownArtwork, entry?.motion, ArtworkSource.FALLBACK, apple)
            else -> ArtworkResult(identifier, null, entry?.motion, ArtworkSource.NONE, apple)
        }
    }

    /**
     * The whole of it in one call: the song is found in the catalogue, its album's motion
     * artwork is asked for, and what there is comes back as one result. Each step is
     * remembered and shared exactly as when asked for on its own, and none of them throws.
     */
    suspend fun resolve(query: ArtworkQuery, withMotion: Boolean = true): ArtworkResult {
        if (!query.local && query.key != null) {
            identify(query)
            if (withMotion) motion(query)
        }
        return result(query)
    }

    /**
     * Whether Apple's cover takes the place of the song's own: the song has no artwork, or
     * what it has is a video's still. A song that already has a real cover keeps it; a file on
     * the phone is never looked up.
     */
    fun needsAppleCover(query: ArtworkQuery): Boolean =
        !query.local && (query.ownArtwork == null || query.ownArtworkIsVideoStill)

    /**
     * Apple's address for [query]'s cover, or null: when the song keeps its own, when there is
     * not enough to identify it, when the catalogue has no such song, or when it could not be
     * asked.
     */
    suspend fun appleCover(query: ArtworkQuery): String? =
        if (needsAppleCover(query)) identify(query)?.appleArtwork else null

    /**
     * [query]'s motion artwork, or null when it has none or it could not be looked for. A song
     * with none is the ordinary case, and is remembered so it is not asked about again.
     */
    suspend fun motion(query: ArtworkQuery): CanvasArtwork? {
        if (query.local) return null
        val key = query.key ?: return null
        load()
        entries[key]?.let { entry ->
            val age = now() - entry.motionCheckedAt
            if (entry.motionCheckedAt > 0) {
                if (entry.motion != null && age < MotionHitTtl) return entry.motion.also { touch(key) }
                // "None" is only believed of the album today's matcher puts the song on.
                if (entry.motion == null && age < MotionMissTtl && entry.matcher >= ArtworkMatcher.Version) return null
            }
        }
        return shared("m:$key") {
            val found = identify(query)
            val region = found?.appleStorefront ?: countries().firstOrNull() ?: "us"
            // The album the song is on, then the same release under its other ids: Apple
            // attaches motion artwork to some ids of an album and not to the rest.
            val albums = listOfNotNull(found?.appleAlbumId) + found?.appleSiblingAlbumIds.orEmpty()
            // For a song that names no album, the other releases it is on come after those.
            val others = found?.appleAlternates.orEmpty()
            var moved: CatalogRelease? = null
            // A failure here means "could not look", which is not remembered: it is tried again.
            val looked = runCatching {
                if (albums.isEmpty()) {
                    Looked(motionProvider.motionFor(query))
                } else {
                    Looked(
                        albums.firstNotNullOfOrNull { albumMotion(it, region) }
                            ?: others.firstNotNullOfOrNull { release -> albumMotion(release.albumId, region)?.also { moved = release } }
                    )
                }
            }.onFailure { log("motion    $key  albums=$albums others=${others.map { it.albumId }} storefront=$region -> COULD NOT ASK: $it") }
                .getOrNull() ?: return@shared null
            // A search by name that finds nothing only means "none" when the catalogue itself
            // was reached. With no connection (or the search refusing) it says nothing, and
            // remembering it as "none" would keep the still cover on the song for days.
            val identified = found != null && found.matcher >= ArtworkMatcher.Version && found.catalogueCheckedAt > 0
            if (looked.motion == null && albums.isEmpty() && !identified) {
                log("motion    $key  the catalogue could not be asked and a search by name found nothing -> NOT REMEMBERED, asked again next time")
                return@shared null
            }
            log(
                "motion    $key  albums=${albums.ifEmpty { "none, searched by name" }} others=${others.map { it.albumId }} storefront=$region -> " +
                    (looked.motion?.let { "album ${it.albumId} square=${it.animated ?: it.videoUrl} tall=${it.tallAnimated}" } ?: "NO MOTION ARTWORK")
            )
            update(key) { entry ->
                // The release with the motion artwork becomes the song's release, cover and all:
                // a cover from one release under a clip from another would be two pictures.
                val placed = moved?.let { release ->
                    entry.copy(
                        appleAlbumId = release.albumId,
                        appleSongId = release.songId ?: entry.appleSongId,
                        appleArtwork = release.artwork ?: entry.appleArtwork,
                        appleAlbumName = release.albumName,
                        appleSiblingAlbumIds = emptyList(),
                        appleAlternates = emptyList(),
                    )
                } ?: entry
                placed.copy(motion = looked.motion, motionCheckedAt = now())
            }
            looked.motion
        } as CanvasArtwork?
    }

    private class Looked(val motion: CanvasArtwork?, val at: Long = 0)

    /** What each album asked about this run has, so the songs of one album make one request between them. */
    private val albumAnswers = ConcurrentHashMap<String, Looked>()

    /** The motion artwork of one album, asked for once however many of its songs want it. Throws when it could not be asked. */
    private suspend fun albumMotion(albumId: String, region: String): CanvasArtwork? {
        val id = "$region/$albumId"
        albumAnswers[id]?.let { known ->
            if (now() - known.at < (if (known.motion != null) MotionHitTtl else MotionMissTtl)) return known.motion
        }
        val answer = shared("a:$id") {
            try {
                Looked(motionProvider.motionForAlbum(albumId, region), now()).also { albumAnswers[id] = it }
            } catch (failure: Throwable) {
                failure
            }
        }
        if (answer is Looked) return answer.motion
        throw (answer as? Throwable) ?: IllegalStateException("Album $albumId could not be asked")
    }

    /**
     * The song in Apple's catalogue: found once, by ISRC where there is one and otherwise by
     * a checked match, and remembered. Null when it cannot be said which song it is.
     */
    private suspend fun identify(query: ArtworkQuery): ArtworkEntry? {
        if (query.local) return null
        val key = query.key ?: return null
        load()
        // What an older matcher picked is not believed: it is looked up again, once.
        entries[key]?.takeIf { it.matcher >= ArtworkMatcher.Version }?.let { entry ->
            if (entry.appleArtwork != null || entry.appleAlbumId != null) return entry.also { touch(key) }
            if (entry.catalogueCheckedAt > 0 && now() - entry.catalogueCheckedAt < CatalogueMissTtl) return entry
        }
        return shared("c:$key") {
            // Null from the search means "could not ask": nothing is recorded, so it is tried again.
            val found = runCatching { findInCatalogue(query) }.getOrNull() ?: return@shared entries[key]
            update(key) {
                // Motion artwork belongs to the album. If the song is now placed on another one,
                // what was found (or not found) for the old one says nothing about it.
                val sameAlbum = found.albumId == null ||
                    (it.appleAlbumId == found.albumId && it.appleSiblingAlbumIds == found.siblingAlbumIds)
                it.copy(
                    appleArtwork = found.artwork,
                    appleSongId = found.songId,
                    appleAlbumId = found.albumId,
                    appleAlbumName = found.albumName,
                    appleStorefront = found.storefront,
                    appleSiblingAlbumIds = found.siblingAlbumIds,
                    appleAlternates = found.alternates,
                    matcher = ArtworkMatcher.Version,
                    catalogueCheckedAt = now(),
                    motion = if (sameAlbum) it.motion else null,
                    motionCheckedAt = if (sameAlbum) it.motionCheckedAt else 0,
                )
            }
        } as ArtworkEntry?
    }

    /** The song, an empty [CatalogSong] when the catalogue has no such song, or null when it could not be asked. */
    private suspend fun findInCatalogue(query: ArtworkQuery): CatalogSong? {
        val regions = countries().ifEmpty { listOf("us") }
        val song = "'${query.title}' by ${query.artists} album=${query.album} ${query.durationSeconds}s isrc=${query.isrc}"
        query.isrc?.trim()?.takeIf { it.length >= 8 }?.let { isrc ->
            runCatching { motionProvider.songByIsrc(isrc, regions.first()) }.getOrNull()?.let {
                log("catalogue ${query.key}  $song -> by ISRC [${regions.first()}]: song ${it.songId} album ${it.albumId}")
                return it.copy(storefront = it.storefront ?: regions.first())
            }
        }
        val name = ArtworkMatcher.songTitle(query.title, query.artists)
        val artist = query.artists.firstOrNull().orEmpty()
        if (name.isBlank() || artist.isBlank()) return CatalogSong(null, null, null)
        // A storefront that cannot be asked (the phone's country is not one of Apple's, or the
        // search is refusing for a while) does not stop the next one being asked.
        var asked = false
        for (country in regions) {
            val candidates = catalog.songs("$artist $name", country)
            if (candidates == null) {
                log("catalogue ${query.key}  $song -> [$country] COULD NOT ASK")
                continue
            }
            asked = true
            val chosen = ArtworkMatcher.choose(candidates, query.title, query.artists, query.album, query.durationSeconds)
            if (chosen == null) {
                log("catalogue ${query.key}  $song -> [$country] ${candidates.size} candidates, NONE IS THIS SONG")
                continue
            }
            val siblings = ArtworkMatcher.siblings(candidates, chosen, query.title, query.artists)
            // A song that says which album it is from stays on it. One that does not (or names
            // one the catalogue does not have) may be on any of its releases.
            val named = !query.album.isNullOrBlank() &&
                ArtworkMatcher.albumKey(chosen.collectionName) == ArtworkMatcher.albumKey(query.album)
            val others = if (named) emptyList() else ArtworkMatcher.alternates(candidates, chosen, query.title, query.artists)
            log(
                "catalogue ${query.key}  $song -> [$country] ${candidates.size} candidates, chose song ${chosen.trackId} on album " +
                    "${chosen.collectionId} '${chosen.collectionName}'; same release also under ${siblings.map { it.collectionId }}" +
                    if (named) "" else "; names no album, also on ${others.map { "${it.collectionId} '${it.collectionName}'" }}"
            )
            return CatalogSong(
                artwork = chosen.artworkUrl100,
                songId = chosen.trackId.takeIf { id -> id > 0 }?.toString(),
                albumId = chosen.collectionId.takeIf { id -> id > 0 }?.toString(),
                albumName = chosen.collectionName.takeIf { name -> name.isNotBlank() },
                storefront = country,
                siblingAlbumIds = siblings.map { it.collectionId.toString() },
                alternates = others.map {
                    CatalogRelease(
                        albumId = it.collectionId.toString(),
                        songId = it.trackId.takeIf { id -> id > 0 }?.toString(),
                        artwork = it.artworkUrl100,
                        albumName = it.collectionName.takeIf { name -> name.isNotBlank() },
                    )
                },
            )
        }
        return if (asked) CatalogSong(null, null, null) else null
    }

    /** Runs [work] once however many callers ask for [id] at the same time; all of them get its answer. */
    private suspend fun shared(id: String, work: suspend () -> Any?): Any? {
        val mine = CompletableDeferred<Any?>()
        val running = inFlight.putIfAbsent(id, mine)
        if (running != null) return running.await()
        // On the resolver's own scope: a caller that goes away does not take the answer with it.
        scope.launch {
            val answer = runCatching { work() }.getOrNull()
            inFlight.remove(id)
            mine.complete(answer)
        }
        return mine.await()
    }

    private fun touch(key: String) {
        entries.computeIfPresent(key) { _, entry -> entry.copy(usedAt = now()) }
    }

    private fun update(key: String, change: (ArtworkEntry) -> ArtworkEntry): ArtworkEntry {
        val updated = entries.compute(key) { _, old -> change(old ?: ArtworkEntry(key)).copy(usedAt = now()) }!!
        if (entries.size > maxEntries) {
            // The least recently used tenth goes with it, so this does not run again on the next song.
            entries.values.sortedBy { it.usedAt }.take(entries.size - maxEntries + maxEntries / 10)
                .forEach { entries.remove(it.key) }
        }
        val snapshot = entries.values.toList()
        scope.launch { runCatching { index.save(snapshot) } }
        return updated
    }

    companion object {
        /** How long "the catalogue has no such song" is believed before asking again. */
        const val CatalogueMissTtl = 7L * 24 * 60 * 60 * 1000

        /** How long "this has no motion artwork" is believed. Albums gain it after release. */
        const val MotionMissTtl = 3L * 24 * 60 * 60 * 1000

        /** How long a motion artwork address is used before it is looked up afresh. */
        const val MotionHitTtl = 30L * 24 * 60 * 60 * 1000
    }
}
