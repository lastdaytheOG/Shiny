package com.shiny.music.home

import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ArtistPage
import com.music.innertube.pages.MoodAndGenres
import com.shiny.music.constants.ArtistSortType
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.Artist
import com.shiny.music.db.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

enum class RecRowKind { BecauseYouLike, MoreLike, Genre, NewGenre, NewReleases }

// one titled row of picks, songs and playlists side by side
@Serializable
data class RecommendationRowSection(
    val kind: RecRowKind,
    val subject: String,
    val photo: String?,
    val items: List<YTItem>,
    val id: String = "${kind.name}_$subject",
    // where the title goes: an artist page, or a genre page
    val browseId: String? = null,
    val params: String? = null,
) : HomeSection {
    @Transient
    override val key: String = "rec_$id"
}

data class HistorySection(val songs: List<Song>) : HomeSection {
    override val key get() = "history"
}

data class ArtistListSection(val artists: List<DeepDiveSection>) : HomeSection {
    override val key get() = "artist_list"
}

// what you've been playing, read once per load and shared by every row
class TasteProfile(
    val heard: Set<String>,
    val avoidSongs: Set<String>,
    val avoidArtists: Set<String>,
    val songSeeds: List<Song>,
    val topArtists: List<Artist>,
)

internal fun epochOf(time: LocalDateTime): Long = time.toInstant(ZoneOffset.UTC).toEpochMilli()

// event times are local wall time stored as utc, so "n days ago" is counted the same way
internal fun daysAgo(days: Long): Long = epochOf(LocalDateTime.now().minusDays(days))

// rows grown from a song, from an artist, from a genre, and new releases from who you play. all
// of them lean on what you play now, keep out what you skipped, keep songs you know to a few per
// row and spread a row across artists
class HomeRecommender(
    private val database: MusicDatabase,
    private val artistPage: suspend (String) -> ArtistPage?,
) {
    private val gate = Semaphore(4)

    suspend fun profile(): TasteProfile {
        val now = LocalDateTime.now()
        val events = database.events().first()
        val liked = database.likedSongsByCreateDateAsc().first()

        val scores = HashMap<String, Double>()
        val songs = HashMap<String, Song>()
        events.forEach { e ->
            val ageDays = ChronoUnit.MINUTES.between(e.event.timestamp, now) / 1440.0
            if (ageDays > 90) return@forEach
            scores.merge(e.song.id, TasteMath.decay(ageDays), Double::plus)
            songs[e.song.id] = e.song
        }
        liked.takeLast(50).forEach { song ->
            scores.merge(song.id, 1.5, Double::plus)
            songs.putIfAbsent(song.id, song)
        }

        val skips = ListeningSignals.recentSkips()
        val avoidSongs = skips.mapTo(HashSet()) { it.songId }
        val avoidArtists = TasteMath.avoidArtists(skips.map { it.artists })

        val ranked = scores.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, score) -> songs[id]?.let { it to score } }
            .filter { (song, _) -> !song.song.isLocal && song.id !in avoidSongs && song.artists.none { it.name.lowercase() in avoidArtists } }
            .distinctBy { (song, _) -> song.artists.firstOrNull()?.name ?: song.id }
            .take(14)
        val seeds = TasteMath.weightedSample(ranked, 8, Random.Default)

        val topArtists = database.mostPlayedArtists(fromTimeStamp = daysAgo(30), limit = 14).first()
            .filter { it.artist.isYouTubeArtist && it.artist.name.lowercase() !in avoidArtists }
            .sortedByDescending { it.timeListened ?: 0 }
            .ifEmpty {
                // nothing played yet: the artists saved to the library stand in
                database.artists(ArtistSortType.CREATE_DATE, true).first()
                    .filter { it.artist.isYouTubeArtist && it.artist.name.lowercase() !in avoidArtists }
                    .take(14)
            }
        return TasteProfile(scores.keys, avoidSongs, avoidArtists, seeds, topArtists)
    }

    suspend fun becauseYouLike(profile: TasteProfile, seeds: List<Song>): List<RecommendationRowSection> = coroutineScope {
        seeds.map { seed ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint ?: return@withPermit null
                    val page = YouTube.related(endpoint).getOrNull() ?: return@withPermit null
                    val songs = TasteMath.pick(page.songs.filter { it.id != seed.id }, profile.heard, profile.avoidSongs, profile.avoidArtists, limit = 10, perArtist = 2)
                    RecommendationRowSection(
                        kind = RecRowKind.BecauseYouLike,
                        subject = seed.title,
                        photo = seed.thumbnailUrl,
                        items = TasteMath.weave(songs, page.playlists.take(3)),
                        id = "because_${seed.id}",
                    )
                }
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun moreLike(profile: TasteProfile, seeds: List<Artist>): List<RecommendationRowSection> = coroutineScope {
        seeds.map { seed ->
            async(Dispatchers.IO) {
                val page = artistPage(seed.id) ?: return@async null
                val radio = page.artist.radioEndpoint ?: return@async null
                val queue = gate.withPermit { YouTube.next(radio).getOrNull()?.items }.orEmpty()
                val name = seed.artist.name
                val others = queue.filter { song -> song.artists.none { it.id == seed.id || it.name.equals(name, ignoreCase = true) } }
                val songs = TasteMath.pick(others, profile.heard, profile.avoidSongs, profile.avoidArtists, limit = 12, perArtist = 1)
                // "featured on" comes before "playlists by", both all playlists
                val playlists = page.sections
                    .firstOrNull { s -> s.items.isNotEmpty() && s.items.all { it is PlaylistItem } }
                    ?.items.orEmpty()
                    .take(2)
                RecommendationRowSection(
                    kind = RecRowKind.MoreLike,
                    subject = name,
                    photo = seed.artist.thumbnailUrl ?: page.artist.thumbnail,
                    items = TasteMath.weave(songs, playlists),
                    id = "more_like_${seed.id}",
                    browseId = seed.id,
                )
            }
        }.awaitAll().filterNotNull()
    }

    // up to three genres your top artists are filed under, weighted by time listened, and the
    // rest ones you don't play yet
    suspend fun genres(profile: TasteProfile): List<RecommendationRowSection> {
        val tiles = YouTube.moodAndGenres().onFailure { android.util.Log.w("HomeFeed", "genres failed", it) }.getOrNull()
            ?.maxByOrNull { group -> HomeGenres.genreScore(group.items.map { it.title }) }
            ?.items
            ?.takeIf { it.isNotEmpty() }
            ?: return emptyList()
        val artistGenres = coroutineScope {
            profile.topArtists.take(12).map { a -> async(Dispatchers.IO) { a to gate.withPermit { HomeGenres.itunesGenre(a.artist.name) } } }.awaitAll()
        }
        val weights = HashMap<MoodAndGenres.Item, Long>()
        artistGenres.forEach { (artist, genre) ->
            val tile = genre?.let { g -> HomeGenres.match(g, tiles) { it.title } } ?: return@forEach
            weights[tile] = (weights[tile] ?: 0L) + (artist.timeListened ?: 0).toLong().coerceAtLeast(1)
        }
        val yours = weights.entries.sortedByDescending { it.value }.take(3).map { it.key }
        val fresh = tiles.filter { it !in yours && HomeGenres.isGenre(it.title) }.shuffled().take(5 - yours.size)
        return coroutineScope {
            (yours.map { it to RecRowKind.Genre } + fresh.map { it to RecRowKind.NewGenre }).map { (tile, kind) ->
                async(Dispatchers.IO) {
                    val page = gate.withPermit { YouTube.browse(tile.endpoint.browseId, tile.endpoint.params).getOrNull() } ?: return@async null
                    val shelves = page.items.map { it.items }
                    val songs = TasteMath.pick(
                        shelves.flatten().filterIsInstance<SongItem>().take(40).shuffled(),
                        profile.heard, profile.avoidSongs, profile.avoidArtists, limit = 9, perArtist = 2,
                    )
                    // some regions' genre pages are all playlists, so the row is filled with more of them
                    val playlists = if (songs.size >= 4) {
                        shelves.firstOrNull { s -> s.isNotEmpty() && s.all { it is PlaylistItem } }.orEmpty().shuffled().take(3)
                    } else {
                        shelves.flatten().filterIsInstance<PlaylistItem>().distinctBy { it.id }.shuffled().take(9)
                    }
                    RecommendationRowSection(
                        kind = kind,
                        subject = tile.title,
                        photo = null,
                        items = TasteMath.weave(songs, playlists),
                        id = "genre_${tile.endpoint.browseId}_${tile.endpoint.params}",
                        browseId = tile.endpoint.browseId,
                        params = tile.endpoint.params,
                    )
                }
            }.awaitAll().filterNotNull()
        }
    }

    // the newest singles and albums of the artists you play most, this year and last
    suspend fun newFromArtists(profile: TasteProfile): RecommendationRowSection? {
        val pages = coroutineScope { profile.topArtists.take(8).map { async(Dispatchers.IO) { artistPage(it.id) } }.awaitAll() }.filterNotNull()
        val since = LocalDate.now().year - 1
        val releases = pages.flatMap { page ->
            page.sections.filter { s -> s.items.isNotEmpty() && s.items.all { it is AlbumItem } }.flatMap { it.items.take(2) }
        }
            .filterIsInstance<AlbumItem>()
            .filter { (it.year ?: 0) >= since }
            .distinctBy { it.id }
            .sortedByDescending { it.year }
            .take(12)
        if (releases.size < 3) return null
        return RecommendationRowSection(RecRowKind.NewReleases, "", null, releases, id = "new_from_artists")
    }
}

object TasteMath {
    // a play today counts 1, two weeks ago about a third, three months ago next to nothing
    fun decay(ageDays: Double): Double = exp(-ageDays / 14.0)

    fun avoidArtists(skippedArtists: List<List<String>>): Set<String> =
        skippedArtists.flatten().map { it.lowercase() }.groupingBy { it }.eachCount().filterValues { it >= 3 }.keys

    // picks where a stronger score is likelier to win but never certain, so a refresh doesn't grow
    // every row from the same few songs
    fun <T> weightedSample(candidates: List<Pair<T, Double>>, count: Int, random: Random): List<T> =
        candidates
            .map { (item, w) -> item to ln(random.nextDouble(1e-9, 1.0)) / w.coerceAtLeast(1e-6) }
            .sortedByDescending { it.second }
            .take(count)
            .map { it.first }

    // mostly songs you haven't heard plus a few you have, nothing skipped, no videos, and only
    // perArtist from any one artist
    fun pick(
        songs: List<SongItem>,
        heard: Set<String>,
        avoidSongs: Set<String>,
        avoidArtists: Set<String>,
        limit: Int,
        perArtist: Int,
        random: Random = Random.Default,
    ): List<SongItem> {
        val usable = songs
            .filter { s -> !s.isVideoSong && s.id !in avoidSongs && s.artists.none { it.name.lowercase() in avoidArtists } }
            .distinctBy { it.id }
        val (known, unknown) = usable.partition { it.id in heard }
        val familiar = (limit / 4).coerceAtLeast(1)
        val chosen = unknown.take(limit * 2).shuffled(random).take(limit - minOf(familiar, known.size)) + known.shuffled(random).take(familiar)
        val count = HashMap<String, Int>()
        return chosen.shuffled(random).filter { s ->
            val artist = s.artists.firstOrNull()?.name?.lowercase() ?: return@filter true
            (count.merge(artist, 1, Int::plus) ?: 1) <= perArtist
        }
    }

    // a song row, an artist row and a genre row in turn, new releases after the first round, the
    // new genres last. a song only shows in the first row that has it, rows under four cards go
    fun arrange(parts: Map<RecRowKind, List<RecommendationRowSection>>): List<RecommendationRowSection> {
        val rounds = listOf(RecRowKind.BecauseYouLike, RecRowKind.MoreLike, RecRowKind.Genre).map { ArrayDeque(parts[it].orEmpty()) }
        val ordered = buildList {
            var first = true
            while (rounds.any { it.isNotEmpty() }) {
                rounds.forEach { q -> q.removeFirstOrNull()?.let(::add) }
                if (first) addAll(parts[RecRowKind.NewReleases].orEmpty())
                first = false
            }
            if (first) addAll(parts[RecRowKind.NewReleases].orEmpty())
            addAll(parts[RecRowKind.NewGenre].orEmpty())
        }
        val seen = HashSet<String>()
        return ordered.mapNotNull { row ->
            val items = row.items.filter { seen.add(it.id) }
            if (items.size >= 4) row.copy(items = items) else null
        }
    }

    // a playlist after every three songs, the rest at the end
    fun weave(songs: List<SongItem>, playlists: List<YTItem>): List<YTItem> = buildList {
        var next = 0
        songs.forEachIndexed { i, s ->
            add(s)
            if (i % 3 == 2 && next < playlists.size) add(playlists[next++])
        }
        addAll(playlists.drop(next))
    }
}
