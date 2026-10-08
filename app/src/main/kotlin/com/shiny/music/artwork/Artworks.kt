package com.shiny.music.artwork

import android.content.Context
import com.music.innertube.SharedHttp
import com.shiny.music.applecanvas.AppleMusicCanvasProvider
import com.shiny.music.canvas.CanvasArtwork
import com.shiny.music.canvas.TidalCanvasProvider
import com.shiny.music.models.MediaMetadata
import com.shiny.music.ui.player.normalizeCanvasArtistName
import com.shiny.music.ui.player.normalizeCanvasSongTitle
import com.shiny.music.utils.isLocalMediaId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.Locale

/** Shiny's artwork resolver, and how a playing song is put to it. */
object Artworks {
    @Volatile
    private var instance: ArtworkResolver? = null

    fun resolver(context: Context): ArtworkResolver =
        instance ?: synchronized(this) {
            // The web player's token is kept beside the index, so it is fetched when it expires
            // and not at every start of the app.
            com.shiny.music.canvas.AppleMusicToken.file = File(context.applicationContext.filesDir, "artwork/token")
            instance ?: ArtworkResolver(
                index = FileArtworkIndex(File(context.applicationContext.filesDir, "artwork/index.json")),
                catalog = AppleCatalog,
                motionProvider = DefaultAppleMotionArtworkProvider,
                countries = { listOf(storefront(), "us").distinct() },
                log = ::log,
            ).also { instance = it }
        }

    /**
     * One line for each step a song's artwork goes through, from the catalogue to the screen:
     * `adb logcat -s Artwork` reads the whole of it, and where a picture stopped on the way.
     */
    fun log(line: String) = Timber.tag("Artwork").d(line)

    /** The catalogue region to ask first: the phone's own. */
    fun storefront(): String =
        Locale.getDefault().country.takeIf { it.length == 2 }?.lowercase(Locale.ROOT) ?: "us"

    fun query(metadata: MediaMetadata): ArtworkQuery = ArtworkQuery(
        title = metadata.title,
        artists = metadata.artists.map { it.name }.filter { it.isNotBlank() },
        album = metadata.album?.title,
        durationSeconds = metadata.duration,
        ownArtwork = metadata.thumbnailUrl,
        local = metadata.id.isLocalMediaId(),
    )
}

/**
 * How a song is looked for in Apple's catalogue: in Apple Music's own search first, and in the
 * public one when that cannot be asked or finds nothing.
 *
 * The public search (iTunes) needs no token, which is why it was the only one used; but it
 * lists no explicit song or album at all (measured 6 October 2026, from two countries). A
 * song with swearing in it was therefore never found, or was placed on the "clean" copy of its
 * album, and Apple's motion artwork is usually on the other copy. Apple Music's own search
 * lists both. It needs the web player's token; without one the public search still answers,
 * so a cover never depends on that token.
 */
internal val AppleCatalog = CatalogSearch { term, country ->
    AppleMusicCatalogSearch.songs(term, country)?.takeIf { it.isNotEmpty() } ?: ItunesCatalogSearch.songs(term, country)
}

/** Apple Music's own search: every release, explicit ones included. Only the song's name and artist go out. */
internal object AppleMusicCatalogSearch : CatalogSearch {
    override suspend fun songs(term: String, country: String): List<CatalogCandidate>? =
        AppleMusicCanvasProvider.searchSongs(term, country)?.map {
            CatalogCandidate(
                trackId = it.songId.toLongOrNull() ?: 0,
                collectionId = it.albumId?.toLongOrNull() ?: 0,
                trackName = it.name,
                artistName = it.artistName,
                collectionName = it.albumName,
                collectionArtistName = it.albumArtistName,
                trackTimeMillis = it.durationMillis,
                artworkUrl100 = it.artworkTemplate,
            )
        }
}

/**
 * Apple's public catalogue search (the iTunes Search API): no key, no account, and only the
 * song's name and artist go out. It allows about twenty requests a minute; when it says it has
 * been asked too often it is left alone for a while.
 */
internal object ItunesCatalogSearch : CatalogSearch {
    private const val SearchUrl = "https://itunes.apple.com/search"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Response(val results: List<CatalogCandidate> = emptyList())

    @Volatile
    private var quietUntil = 0L

    override suspend fun songs(term: String, country: String): List<CatalogCandidate>? = runCatching {
        if (System.currentTimeMillis() < quietUntil) return@runCatching null
        val url = SearchUrl.toHttpUrl().newBuilder()
            .addQueryParameter("term", term)
            .addQueryParameter("media", "music")
            .addQueryParameter("entity", "song")
            .addQueryParameter("limit", "15")
            .addQueryParameter("country", country)
            .build()
        SharedHttp.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 403 || response.code == 429) quietUntil = System.currentTimeMillis() + 90_000
                return@use null
            }
            json.decodeFromString<Response>(response.body?.string().orEmpty()).results
        }
    }.onFailure { Timber.tag("Artwork").d("catalogue the public search could not be asked: %s", it.toString()) }.getOrNull()
}

/**
 * Motion artwork from Apple's web-player catalogue: the album's `editorialVideo`, square and
 * portrait, as HLS with the plain MP4 behind one size of it as a second way in. The token
 * those reads need is found in the web player's own script each time it expires
 * (`AppleMusicToken`); none is stored in the app, and nothing outside this object depends
 * on it.
 *
 * For a song the catalogue search could not place, the providers Shiny already had are asked
 * by name instead (Apple's own search, then Tidal's), and their answer is checked against the
 * song's artist and title or album so an unrelated video never plays over the wrong cover.
 */
internal object DefaultAppleMotionArtworkProvider : AppleMotionArtworkProvider {

    override suspend fun motionForAlbum(albumId: String, country: String): CanvasArtwork? =
        AppleMusicCanvasProvider.albumMotion(albumId, country)

    override suspend fun songByIsrc(isrc: String, country: String): CatalogSong? =
        AppleMusicCanvasProvider.songByIsrc(isrc, country)?.let {
            CatalogSong(
                artwork = it.artworkTemplate,
                songId = it.songId.ifBlank { null },
                albumId = it.albumId,
                albumName = it.albumName,
                storefront = country,
            )
        }

    override suspend fun motionFor(query: ArtworkQuery): CanvasArtwork? {
        val storefront = Artworks.storefront()
        val album = query.album
        val titleRaw = query.title
        val artistRaw = query.artists.firstOrNull().orEmpty()
        val title = normalizeCanvasSongTitle(titleRaw)
        val artist = normalizeCanvasArtistName(artistRaw)

        val fetched = linkedSetOf(title to artist, titleRaw to artist, title to artistRaw, titleRaw to artistRaw)
            .filter { (s, a) -> s.isNotBlank() && a.isNotBlank() }
            .firstNotNullOfOrNull { (s, a) ->
                if (!album.isNullOrBlank()) {
                    AppleMusicCanvasProvider.getByAlbumArtist(album = album, artist = a, storefront = storefront)
                        ?.takeIf { it.hasMotion }
                        ?.let { return@firstNotNullOfOrNull it }
                }
                TidalCanvasProvider.getBySongArtist(song = s, artist = a, album = album)?.takeIf { it.hasMotion }
                    ?: AppleMusicCanvasProvider.getBySongArtist(song = s, artist = a, album = album, storefront = storefront)
                        ?.takeIf { it.hasMotion }
            } ?: return null

        fun loose(a: String, b: String): Boolean {
            if (a.isBlank() || b.isBlank()) return true
            val na = normalizeCanvasSongTitle(a)
            val nb = normalizeCanvasSongTitle(b)
            return a.contains(b, true) || b.contains(a, true) || na.contains(nb, true) || nb.contains(na, true)
        }

        val artistOk = fetched.artist?.let { found ->
            val nf = normalizeCanvasArtistName(found)
            found.contains(artistRaw, true) || artistRaw.contains(found, true) || nf.contains(artist, true) || artist.contains(nf, true)
        } ?: true
        val titleOk = when {
            fetched.albumName != null && !album.isNullOrBlank() -> loose(fetched.albumName!!, album)
            fetched.name != null -> loose(fetched.name!!, titleRaw) || (!album.isNullOrBlank() && loose(fetched.name!!, album))
            else -> true
        }
        return fetched.takeIf { artistOk && titleOk }
    }
}

/**
 * The index as one small JSON file: a few hundred bytes a song, written whole after a change.
 * A file that cannot be read is an empty index, not an error.
 */
class FileArtworkIndex(private val file: File) : ArtworkIndex {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Stored(val entries: List<ArtworkEntry> = emptyList())

    override fun load(): Map<String, ArtworkEntry> = runCatching {
        if (!file.exists()) return@runCatching emptyMap()
        json.decodeFromString<Stored>(file.readText()).entries.associateBy { it.key }
    }.getOrDefault(emptyMap())

    @Synchronized
    override fun save(entries: Collection<ArtworkEntry>) {
        file.parentFile?.mkdirs()
        // Written beside and moved over, so a kill half-way leaves the old index, not half a new one.
        val pending = File(file.parentFile, file.name + ".tmp")
        pending.writeText(json.encodeToString(Stored.serializer(), Stored(entries.toList())))
        if (!pending.renameTo(file)) {
            file.delete()
            pending.renameTo(file)
        }
    }
}
