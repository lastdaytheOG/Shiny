package com.shiny.music.home

import android.content.Context
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ArtistPage
import com.music.innertube.pages.ArtistSection
import com.music.innertube.pages.ChartsPage
import com.shiny.music.constants.ArtistSortType
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.Album
import com.shiny.music.db.entities.Artist
import com.shiny.music.db.entities.Playlist
import com.shiny.music.db.entities.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

// a hero card grown from one of your songs
data class HeroDiscoverPick(val seed: Song, val pick: SongItem)

// one of your artists, albums or songs and what grew out of it
data class SimilarRow(val seedArtist: Artist?, val seedAlbum: Album?, val seedSong: Song?, val items: List<YTItem>) {
    val seedId: String get() = seedArtist?.id ?: seedAlbum?.id ?: seedSong?.id.orEmpty()
}

@Serializable
data class CommunityPlaylist(val playlist: PlaylistItem, val songs: List<SongItem>)

@Serializable
data class HomeVideo(val item: SongItem, val title: String, val subtitle: String)

@Serializable
data class HomeShelf(val title: String, val items: List<YTItem>, val browseId: String? = null, val params: String? = null)

// one artist and up to five things around them, for a box in the artist list
data class ArtistGroup(val artistId: String?, val artistName: String, val artistPhoto: String?, val items: List<YTItem>)

// what a box showed, handed to the similar page it opens so that page leads with the same things
object SimilarPicks {
    private val picks = ConcurrentHashMap<String, ArtistGroup>()
    fun put(id: String, group: ArtistGroup) {
        picks[id] = group
    }
    fun get(id: String): ArtistGroup? = picks[id]
}

// the similar rows regrouped by artist: an artist's own row stays theirs, a song's or an
// album's picks go to whoever made each pick
fun artistGroups(rows: List<SimilarRow>): List<ArtistGroup> {
    class Acc(val id: String?, val name: String, var photo: String?, val items: MutableList<YTItem> = mutableListOf())
    val groups = LinkedHashMap<String, Acc>()
    fun add(key: String, name: String, photo: String?, id: String?, item: YTItem?) {
        val g = groups.getOrPut(key) { Acc(id, name, photo) }
        if (photo != null && g.photo == null) g.photo = photo
        if (item != null && g.items.none { it.id == item.id }) g.items += item
    }
    rows.forEach { row ->
        row.items.forEach { item ->
            val seed = row.seedArtist
            when {
                item is ArtistItem -> add(item.id, item.title, item.thumbnail, item.id, null)
                seed != null -> add(seed.id, seed.title, seed.thumbnailUrl, seed.id, item)
                else -> {
                    val a = when (item) {
                        is SongItem -> item.artists.firstOrNull()
                        is AlbumItem -> item.artists?.firstOrNull()
                        else -> null
                    } ?: return@forEach
                    add(a.id ?: a.name, a.name, null, a.id, item)
                }
            }
        }
    }
    return groups.values.filter { it.items.isNotEmpty() }.map { ArtistGroup(it.id, it.name, it.photo, it.items.take(5)) }.shuffled().take(15)
}

// a song by one of the artists in the library, for a new install's hero
data class StarterPick(val song: SongItem, val artistName: String, val artistPhoto: String?)

// everything home shows that isn't the old local feed. empty lists mean not loaded or nothing yet
data class HomeLive(
    val lastPlayed: Song? = null,
    val mostPlayed: List<Song> = emptyList(),
    val history: List<Song> = emptyList(),
    val forgotten: List<Song> = emptyList(),
    val keepListening: List<Song> = emptyList(),
    val localPlaylists: List<Playlist> = emptyList(),
    val discover: List<HeroDiscoverPick> = emptyList(),
    val similar: List<SimilarRow> = emptyList(),
    val artistGroups: List<ArtistGroup> = emptyList(),
    val community: List<CommunityPlaylist> = emptyList(),
    val liveShows: List<HomeVideo> = emptyList(),
    val musicVideos: List<HomeVideo> = emptyList(),
    val categories: List<RecommendationRowSection> = emptyList(),
    val accountPlaylists: List<PlaylistItem> = emptyList(),
    val shelves: List<HomeShelf> = emptyList(),
    val hits: List<YTItem> = emptyList(),
    val starter: List<StarterPick> = emptyList(),
    val loaded: Boolean = false,
)

// home's network rows, kept so the next launch shows the last feed straight away
class HomeFeedStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("home_feed_store", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun restore(into: HomeLive): HomeLive = into.copy(
        community = load("community") ?: into.community,
        liveShows = load("live") ?: into.liveShows,
        musicVideos = load("videos") ?: into.musicVideos,
        categories = load("categories") ?: into.categories,
        shelves = load("shelves") ?: into.shelves,
        hits = load("hits") ?: into.hits,
    )

    fun saveCommunity(v: List<CommunityPlaylist>) = save("community", v)
    fun saveLive(v: List<HomeVideo>) = save("live", v)
    fun saveVideos(v: List<HomeVideo>) = save("videos", v)
    fun saveCategories(v: List<RecommendationRowSection>) = save("categories", v)
    fun saveShelves(v: List<HomeShelf>) = save("shelves", v)
    fun saveHits(v: List<YTItem>) = save("hits", v)

    private inline fun <reified T> load(key: String): T? =
        prefs.getString(key, null)?.let { raw -> runCatching { json.decodeFromString<T>(raw) }.getOrNull() }

    private inline fun <reified T> save(key: String, value: T) {
        runCatching { prefs.edit().putString(key, json.encodeToString(value)).apply() }
    }
}

// fetches each home row. every artist page is asked for once per load and shared
class HomeSources(private val database: MusicDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pages = ConcurrentHashMap<String, Deferred<ArtistPage?>>()
    val recommender = HomeRecommender(database, ::artistPage)

    suspend fun artistPage(id: String): ArtistPage? =
        pages.computeIfAbsent(id) { scope.async { runCatching { YouTube.artist(id).getOrNull() }.getOrNull() } }.await()

    fun forgetPages() = pages.clear()

    suspend fun local(into: HomeLive): HomeLive {
        val history = database.libraryDao.recentHistory(15).first()
        val mostPlayed = database.mostPlayedSongs(fromTimeStamp = daysAgo(30), limit = 20).first().distinctBy { it.id }.take(20)
        val forgotten = database.forgottenFavorites().first().distinctBy { it.id }.shuffled().take(20)
        val keep = database.mostPlayedSongs(fromTimeStamp = daysAgo(14), limit = 15, offset = 5).first().shuffled().take(10)
        val playlists = database.playlistsByUpdatedDateAsc().first().distinctBy { it.id }
        return into.copy(
            lastPlayed = history.firstOrNull(),
            history = history,
            mostPlayed = mostPlayed,
            forgotten = forgotten,
            keepListening = keep,
            localPlaylists = playlists,
        )
    }

    // seeds from three directions so the cards don't all orbit one song: something you liked,
    // something you play all the time, something you played just now. one per artist
    suspend fun discover(): List<HeroDiscoverPick> {
        val events = database.events().first()
        val liked = database.likedSongsByCreateDateAsc().first()
        val recent = events.map { it.song }.distinctBy { it.id }
        if (recent.isEmpty() && liked.isEmpty()) return emptyList()
        val counts = events.groupingBy { it.song.id }.eachCount()
        val most = recent.sortedByDescending { counts[it.id] ?: 0 }
        val seeds = buildList<Song> {
            fun takeFrom(songs: List<Song>, n: Int) {
                songs.shuffled()
                    .filter { s -> none { it.id == s.id } && none { it.artists.firstOrNull()?.id == s.artists.firstOrNull()?.id } }
                    .take(n)
                    .forEach(::add)
            }
            takeFrom(liked, 2)
            takeFrom(most.take(20), 2)
            takeFrom(recent.take(15), 2)
            if (size < 5) takeFrom(recent, 5 - size)
        }
        val heard = recent.mapTo(HashSet()) { it.id }
        val found = coroutineScope {
            seeds.map { seed ->
                async(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint ?: return@async null
                    val page = YouTube.related(endpoint).getOrNull() ?: return@async null
                    val candidates = page.songs.filter { it.id != seed.id }
                    val pick = candidates.filter { it.id !in heard }.shuffled().firstOrNull() ?: candidates.shuffled().firstOrNull()
                    pick?.let { HeroDiscoverPick(seed, it) }
                }
            }.awaitAll().filterNotNull()
        }
        // one card per artist, so one act can't take over the hero
        return found.distinctBy { it.pick.id }.distinctBy { it.pick.artists.firstOrNull()?.name ?: it.pick.id }.shuffled()
    }

    suspend fun similar(): List<SimilarRow> = coroutineScope {
        val since = daysAgo(14)
        val artists = database.mostPlayedArtists(fromTimeStamp = since, limit = 15).first()
            .filter { it.artist.isYouTubeArtist }.shuffled().take(4)
            .map { artist ->
                async(Dispatchers.IO) {
                    val items = artistPage(artist.id)?.sections.orEmpty().takeLast(3).flatMap { it.items }
                    items.distinctBy { it.id }.shuffled().take(12).takeIf { it.isNotEmpty() }?.let { SimilarRow(artist, null, null, it) }
                }
            }
        val songs = database.mostPlayedSongs(fromTimeStamp = since, limit = 15).first()
            .filter { it.album != null }.shuffled().take(3)
            .map { song ->
                async(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint ?: return@async null
                    val page = YouTube.related(endpoint).getOrNull() ?: return@async null
                    val items = (page.songs.shuffled().take(10) + page.albums.shuffled().take(5) + page.artists.shuffled().take(3) + page.playlists.shuffled().take(3))
                        .distinctBy { it.id }.shuffled()
                    items.takeIf { it.isNotEmpty() }?.let { SimilarRow(null, null, song, it) }
                }
            }
        val albums = database.mostPlayedAlbums(fromTimeStamp = since, limit = 10).first()
            .filter { it.album.thumbnailUrl != null }.shuffled().take(2)
            .map { album ->
                async(Dispatchers.IO) {
                    val items = ArrayList<YTItem>()
                    YouTube.album(album.id).getOrNull()?.let { items += it.otherVersions }
                    album.artists.firstOrNull()?.id?.let { id -> artistPage(id)?.sections?.lastOrNull()?.items?.let { items += it } }
                    items.distinctBy { it.id }.shuffled().take(10).takeIf { it.isNotEmpty() }?.let { SimilarRow(null, album, null, it) }
                }
            }
        (artists + songs + albums).awaitAll().filterNotNull().shuffled()
    }

    suspend fun community(): List<CommunityPlaylist> {
        val since = daysAgo(28)
        val artistSeeds = database.mostPlayedArtists(fromTimeStamp = since, limit = 10).first().filter { it.artist.isYouTubeArtist }.shuffled().take(3)
        val songSeeds = database.mostPlayedSongs(fromTimeStamp = since, limit = 5).first().shuffled().take(2)
        fun ok(p: PlaylistItem, artistName: String? = null) =
            p.author?.name !in setOf("YouTube Music", "YouTube", "Playlist") && p.author?.name != artistName &&
                !p.id.startsWith("RD") && !p.id.startsWith("OLAK")
        val candidates = coroutineScope {
            val a = artistSeeds.map { seed ->
                async(Dispatchers.IO) {
                    artistPage(seed.id)?.sections.orEmpty().flatMap { it.items.filterIsInstance<PlaylistItem>() }.filter { ok(it, seed.artist.name) }
                }
            }
            val b = songSeeds.map { seed ->
                async(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint ?: return@async emptyList()
                    YouTube.related(endpoint).getOrNull()?.playlists.orEmpty().filter { ok(it) }
                }
            }
            (a + b).awaitAll().flatten()
        }.distinctBy { it.id }.shuffled().take(5)
        return coroutineScope {
            candidates.map { p ->
                async(Dispatchers.IO) {
                    val page = YouTube.playlist(p.id).getOrNull() ?: return@async null
                    val songs = page.songs.take(10)
                    if (songs.isEmpty()) null else CommunityPlaylist(p.copy(songCountText = page.playlist.songCountText ?: p.songCountText), songs)
                }
            }.awaitAll().filterNotNull().shuffled()
        }
    }

    // "live shows" and "music videos": the video shelves off the pages of who you play most this
    // month, a few each, dealt out one artist at a time
    suspend fun artistVideos(): Pair<List<HomeVideo>, List<HomeVideo>> {
        val artists = database.mostPlayedArtists(fromTimeStamp = daysAgo(30), limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .sortedByDescending { it.timeListened ?: 0 }
            .take(6)
        if (artists.isEmpty()) return emptyList<HomeVideo>() to emptyList()
        val per = coroutineScope {
            artists.map { artist ->
                async(Dispatchers.IO) {
                    val shelves = ArtistVideos.shelves(artistPage(artist.id)?.sections.orEmpty())
                    fun pick(v: List<SongItem>) = v.take(6).shuffled().take(3).map { ArtistVideos.video(it, artist.artist.name) }
                    pick(shelves.live) to pick(shelves.videos)
                }
            }.awaitAll()
        }
        val live = ArtistVideos.interleave(per.map { it.first }, 12).distinctBy { it.item.id }
        val liveIds = live.mapTo(HashSet()) { it.item.id }
        val videos = ArtistVideos.interleave(per.map { (_, v) -> v.filterNot { it.item.id in liveIds } }, 12).distinctBy { it.item.id }
        return live to videos
    }

    suspend fun hits(): List<YTItem> {
        fun top(page: ChartsPage?) = page?.sections?.firstOrNull { it.chartType == ChartsPage.ChartType.TOP } ?: page?.sections?.firstOrNull { it.items.isNotEmpty() }
        var section = top(YouTube.getChartsPage().onFailure { android.util.Log.w("HomeFeed", "charts failed", it) }.getOrNull())
        if (section == null) {
            delay(1500)
            section = top(YouTube.getChartsPage().getOrNull())
        }
        return section?.items?.distinctBy { it.id }?.take(20).orEmpty()
    }

    // youtube's own home shelves, minus trending and community ones
    suspend fun youtubeHome(): List<HomeShelf> {
        val first = YouTube.home().onFailure { android.util.Log.w("HomeFeed", "youtube home failed", it) }.getOrNull() ?: return emptyList()
        // a signed-out first page can come back as chips only, the shelves follow in the continuation
        var sections = first.sections
        var cont = first.continuation
        var pages = 0
        while (sections.size < 4 && cont != null && pages < 2) {
            val more = YouTube.home(continuation = cont).getOrNull() ?: break
            sections = sections + more.sections
            cont = more.continuation
            pages++
        }
        android.util.Log.d("HomeFeed", "youtube home: ${first.sections.size} then ${sections.size} sections")
        return sections.mapNotNull { s ->
            val t = s.title.lowercase()
            if ("trending" in t || "community" in t) return@mapNotNull null
            val items = s.items.distinctBy { it.id }
            if (items.isEmpty()) null else HomeShelf(s.title, items, s.endpoint?.browseId, s.endpoint?.params)
        }
    }

    suspend fun accountPlaylists(): List<PlaylistItem> =
        YouTube.library("FEmusic_liked_playlists").getOrNull()?.items.orEmpty()
            .filterIsInstance<PlaylistItem>().distinctBy { it.id }.filterNot { it.id == "SE" }

    suspend fun starter(): List<StarterPick> {
        if (database.eventCount().first() > 0) return emptyList()
        val picked = database.artists(ArtistSortType.CREATE_DATE, true).first().filter { it.artist.isYouTubeArtist }.take(5)
        return coroutineScope {
            picked.map { artist ->
                async(Dispatchers.IO) {
                    val top = artistPage(artist.id)?.sections.orEmpty().firstNotNullOfOrNull { s ->
                        s.items.filterIsInstance<SongItem>().takeIf { it.isNotEmpty() }
                    } ?: return@async null
                    StarterPick(top.take(3).random(), artist.artist.name, artist.artist.thumbnailUrl)
                }
            }.awaitAll().filterNotNull()
        }
    }
}

object ArtistVideos {
    class Shelves(val videos: List<SongItem>, val live: List<SongItem>)

    // audio-only tracks are ATV, podcasts have their own type
    private val VideoTypes = setOf("MUSIC_VIDEO_TYPE_OMV", "MUSIC_VIDEO_TYPE_UGC", "MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC")
    private val LiveTitle = Regex("\\b(live|langsung|en vivo|ao vivo|en directo|en direct|dal vivo|konser|concerts?)\\b|라이브|ライブ|现场|現場", RegexOption.IGNORE_CASE)
    private val NotAVideo = Regex("\\b(lyrics?|lirik|audio|visuali[sz]er|teaser|trailer|behind the scenes|making of|shorts|dance practice)\\b", RegexOption.IGNORE_CASE)
    private val Tags = Regex("\\s*[(\\[][^)\\]]*\\b(official|mv|m/v|music video|video|4k|hd)\\b[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE)

    private fun isVideo(s: SongItem) = s.musicVideoType in VideoTypes || (s.musicVideoType == null && s.album == null)

    // shelf titles come in the user's language, so shelves are found by what's in them: rows of
    // videos. the live one reads "live", or else is the second, since videos always comes first
    fun shelves(sections: List<ArtistSection>): Shelves {
        val videoShelves = sections.filter { s ->
            val n = s.items.count { it is SongItem && isVideo(it) && it.album == null }
            n > 0 && n * 4 >= s.items.size * 3
        }
        val live = videoShelves.firstOrNull { LiveTitle.containsMatchIn(it.title) } ?: videoShelves.getOrNull(1)
        val videos = videoShelves.firstOrNull { it !== live }
        val liveShows = live?.items.orEmpty().filterIsInstance<SongItem>().filter(::isVideo).distinctBy { it.title.lowercase() }
        val liveIds = liveShows.mapTo(HashSet()) { it.id }
        val musicVideos = videos?.items.orEmpty().filterIsInstance<SongItem>()
            .filter { isVideo(it) && it.id !in liveIds && !NotAVideo.containsMatchIn(it.title) }
            .distinctBy { it.title.lowercase() }
        return Shelves(musicVideos, liveShows)
    }

    fun video(item: SongItem, artistName: String): HomeVideo {
        val title = item.title.replace(Tags, "").trim().removePrefix("$artistName - ").ifBlank { item.title }
        return HomeVideo(item, title, item.artists.joinToString { it.name }.ifBlank { artistName })
    }

    // one from each list in turn
    fun <T> interleave(lists: List<List<T>>, limit: Int): List<T> = buildList {
        var i = 0
        while (size < limit && lists.any { i < it.size }) {
            lists.forEach { l -> if (i < l.size && size < limit) add(l[i]) }
            i++
        }
    }
}
