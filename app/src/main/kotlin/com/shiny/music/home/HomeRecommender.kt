package com.shiny.music.home

import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ArtistPage
import com.shiny.music.db.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

enum class RecRowKind { BecauseYouLike, MoreLike, Genre, NewGenre, NewReleases }

data class RecommendationRowSection(
    val kind: RecRowKind,
    val subject: String,
    val photo: String?,
    val items: List<YTItem>,
) : HomeSection {
    override val key get() = "rec_${kind.name}_$subject"
}

data class TastePlay(
    val songId: String,
    val title: String,
    val artistIds: List<String>,
    val artistNames: List<String>,
    val thumbnail: String?,
    val at: Long,
    val playMs: Long,
)

data class TasteLike(
    val songId: String,
    val title: String,
    val artistId: String?,
    val artistName: String,
    val thumbnail: String?,
    val at: Long,
)

data class TasteSeed(
    val songId: String,
    val title: String,
    val artistId: String?,
    val artistName: String,
    val thumbnail: String?,
    val score: Double,
)

data class TasteArtist(val id: String?, val name: String, val weight: Double)

object TasteMath {
    const val DAY = 86_400_000L
    private const val SKIP_MS = 20_000L
    private const val HEARD_MS = 30_000L

    // a play today counts 1, two weeks ago about a third, three months ago next to nothing
    fun decay(ageMs: Long): Double = exp(-ageMs.coerceAtLeast(0) / (13.0 * DAY))

    fun skipped(plays: List<TastePlay>, now: Long): Set<String> =
        plays.filter { it.playMs < SKIP_MS && now - it.at < 14 * DAY }.mapTo(HashSet()) { it.songId }

    fun skippedArtists(plays: List<TastePlay>, now: Long): Set<String> =
        plays.filter { it.playMs < SKIP_MS && now - it.at < 14 * DAY }
            .flatMap { it.artistNames.map(String::lowercase) }
            .groupingBy { it }.eachCount()
            .filterValues { it >= 3 }.keys

    fun topArtists(plays: List<TastePlay>, now: Long): List<TasteArtist> {
        val weights = HashMap<String, TasteArtist>()
        plays.filter { it.playMs >= HEARD_MS }.forEach { play ->
            val w = decay(now - play.at) * (play.playMs / 60_000.0).coerceAtMost(6.0)
            play.artistNames.forEachIndexed { i, name ->
                val key = name.lowercase()
                val old = weights[key]
                weights[key] = TasteArtist(old?.id ?: play.artistIds.getOrNull(i), name, (old?.weight ?: 0.0) + w)
            }
        }
        return weights.values.sortedByDescending { it.weight }
    }

    // strongest songs, one per artist, drawn so a stronger score is likelier but never certain
    fun seeds(
        plays: List<TastePlay>,
        likes: List<TasteLike>,
        now: Long,
        skipped: Set<String>,
        random: Random,
        count: Int,
    ): List<TasteSeed> {
        val scores = HashMap<String, TasteSeed>()
        plays.filter { it.playMs >= HEARD_MS && it.songId !in skipped }.forEach { p ->
            val old = scores[p.songId]
            val add = decay(now - p.at)
            scores[p.songId] = TasteSeed(p.songId, p.title, p.artistIds.firstOrNull(), p.artistNames.firstOrNull().orEmpty(), p.thumbnail, (old?.score ?: 0.0) + add)
        }
        likes.filter { it.songId !in skipped }.forEach { l ->
            val old = scores[l.songId]
            val add = 1.5 * exp(-(now - l.at).coerceAtLeast(0) / (45.0 * DAY))
            scores[l.songId] = old?.copy(score = old.score + add) ?: TasteSeed(l.songId, l.title, l.artistId, l.artistName, l.thumbnail, add)
        }
        val ordered = scores.values
            .filter { it.score > 0.0 }
            .map { it to ln(random.nextDouble().coerceAtLeast(1e-12)) / it.score }
            .sortedByDescending { it.second }
            .map { it.first }
        val artists = HashSet<String>()
        return ordered.filter { artists.add(it.artistName.lowercase()) }.take(count)
    }

    // mostly songs you haven't heard plus a couple you have, nothing skipped, a few per artist
    fun pickSongs(
        candidates: List<SongItem>,
        heard: Set<String>,
        skipped: Set<String>,
        skippedArtists: Set<String>,
        size: Int = 12,
        perArtist: Int = 2,
        heardAllowed: Int = 3,
    ): List<SongItem> {
        val perArtistCount = HashMap<String, Int>()
        var heardCount = 0
        val out = ArrayList<SongItem>()
        for (song in candidates.distinctBy { it.id }) {
            if (out.size >= size) break
            if (song.isVideoSong || song.id in skipped) continue
            val artist = song.artists.firstOrNull()?.name?.lowercase().orEmpty()
            if (artist in skippedArtists) continue
            if ((perArtistCount[artist] ?: 0) >= perArtist) continue
            if (song.id in heard) {
                if (heardCount >= heardAllowed) continue
                heardCount++
            }
            perArtistCount[artist] = (perArtistCount[artist] ?: 0) + 1
            out += song
        }
        return out
    }

    // song row, artist row, genre row in turn; new releases after the first round, new genres last.
    // a song only shows in the first row that has it, and a row left with under four cards goes
    fun arrange(
        songRows: List<RecommendationRowSection>,
        artistRows: List<RecommendationRowSection>,
        genreRows: List<RecommendationRowSection>,
        newReleases: RecommendationRowSection?,
        newGenres: List<RecommendationRowSection>,
        minCards: Int = 4,
    ): List<RecommendationRowSection> {
        val ordered = ArrayList<RecommendationRowSection>()
        val rounds = maxOf(songRows.size, artistRows.size, genreRows.size)
        for (i in 0 until rounds) {
            songRows.getOrNull(i)?.let(ordered::add)
            artistRows.getOrNull(i)?.let(ordered::add)
            genreRows.getOrNull(i)?.let(ordered::add)
            if (i == 0 && newReleases != null) ordered += newReleases
        }
        if (rounds == 0 && newReleases != null) ordered += newReleases
        ordered += newGenres
        val seen = HashSet<String>()
        return ordered.mapNotNull { row ->
            val items = row.items.filter { it !is SongItem || seen.add(it.id) }
            if (items.size < minCards) null else row.copy(items = items)
        }
    }

    // a playlist after every three songs, the rest of the playlists at the end
    fun weave(songs: List<SongItem>, playlists: List<PlaylistItem>): List<YTItem> {
        val out = ArrayList<YTItem>()
        val queue = ArrayDeque(playlists)
        songs.forEachIndexed { i, song ->
            out += song
            if ((i + 1) % 3 == 0 && queue.isNotEmpty()) out += queue.removeFirst()
        }
        out.addAll(queue)
        return out
    }

    fun genreMatches(itunesGenre: String, youtubeTitle: String): Boolean {
        val parts = itunesGenre.lowercase().split('/', '&', ',').map { it.replace("music", "").trim() }.filter { it.length > 2 }
        val title = youtubeTitle.lowercase()
        return parts.any { part -> title.contains(part) || part.contains(title) }
    }
}

class HomeRecommender(private val database: MusicDatabase) {
    private val gate = Semaphore(4)
    private val genreCache = HashMap<String, String?>()

    suspend fun build(random: Random = Random(System.nanoTime())): List<RecommendationRowSection> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val plays = database.libraryDao.eventsSince(now - 90 * TasteMath.DAY).mapNotNull { e ->
            val song = e.song
            TastePlay(
                songId = song.id,
                title = song.song.title,
                artistIds = song.artists.map { it.id },
                artistNames = song.artists.map { it.name },
                thumbnail = song.song.thumbnailUrl,
                at = e.event.timestamp.toInstant(ZoneOffset.UTC).toEpochMilli(),
                playMs = e.event.playTime,
            )
        }
        val likes = database.libraryDao.recentLikes(100).map { s ->
            TasteLike(
                songId = s.id,
                title = s.song.title,
                artistId = s.artists.firstOrNull()?.id,
                artistName = s.artists.firstOrNull()?.name.orEmpty(),
                thumbnail = s.song.thumbnailUrl,
                at = s.song.likedDate?.toInstant(ZoneOffset.UTC)?.toEpochMilli() ?: now,
            )
        }
        if (plays.isEmpty() && likes.isEmpty()) return@withContext emptyList()

        val heard = plays.mapTo(HashSet()) { it.songId }
        val skipped = TasteMath.skipped(plays, now)
        val skippedArtists = TasteMath.skippedArtists(plays, now)
        val seeds = TasteMath.seeds(plays, likes, now, skipped, random, 3)
        val artists = TasteMath.topArtists(plays, now).filter { it.id != null && it.name.lowercase() !in skippedArtists }.take(4)

        coroutineScope {
            val songRows = seeds.map { seed -> async { becauseYouLike(seed, heard, skipped, skippedArtists) } }
            val pages = artists.map { a -> async { gate.withPermit { runCatching { YouTube.artist(a.id!!).getOrNull() }.getOrNull() } } }.awaitAll()
            val artistRows = pages.take(3).map { page -> async { page?.let { moreLike(it, heard, skipped, skippedArtists) } } }
            val genres = async { genreRows(artists, random) }
            val releases = newReleases(pages.filterNotNull())
            val (personal, fresh) = genres.await()
            TasteMath.arrange(
                songRows = songRows.awaitAll().filterNotNull(),
                artistRows = artistRows.awaitAll().filterNotNull(),
                genreRows = personal,
                newReleases = releases,
                newGenres = fresh,
            )
        }
    }

    private suspend fun becauseYouLike(seed: TasteSeed, heard: Set<String>, skipped: Set<String>, skippedArtists: Set<String>): RecommendationRowSection? =
        gate.withPermit {
            runCatching {
                val next = YouTube.next(WatchEndpoint(videoId = seed.songId)).getOrNull() ?: return@runCatching null
                val related = next.relatedEndpoint?.let { YouTube.related(it).getOrNull() }
                val pool = related?.songs.orEmpty().ifEmpty { next.items.drop(1) }
                val picks = TasteMath.pickSongs(pool.filter { it.id != seed.songId }, heard, skipped, skippedArtists)
                RecommendationRowSection(RecRowKind.BecauseYouLike, seed.title, seed.thumbnail, picks)
            }.getOrNull()
        }

    private suspend fun moreLike(page: ArtistPage, heard: Set<String>, skipped: Set<String>, skippedArtists: Set<String>): RecommendationRowSection? {
        val radio = page.artist.radioEndpoint ?: return null
        val name = page.artist.title
        return gate.withPermit {
            runCatching {
                val queue = YouTube.next(radio).getOrNull()?.items.orEmpty()
                val others = queue.filter { song -> song.artists.none { it.id == page.artist.id || it.name.equals(name, true) } }
                val songs = TasteMath.pickSongs(others, heard, skipped, skippedArtists, perArtist = 1)
                val featured = page.sections
                    .filter { it.title.contains("featured", true) || it.title.contains("playlist", true) }
                    .flatMap { it.items }
                    .filterIsInstance<PlaylistItem>()
                    .take(3)
                RecommendationRowSection(RecRowKind.MoreLike, name, page.artist.thumbnail, TasteMath.weave(songs, featured))
            }.getOrNull()
        }
    }

    private fun newReleases(pages: List<ArtistPage>): RecommendationRowSection? {
        val year = LocalDate.now().year
        val albums = pages.flatMap { page ->
            page.sections
                .filter { it.title.contains("album", true) || it.title.contains("single", true) }
                .flatMap { it.items }
                .filterIsInstance<AlbumItem>()
                .filter { (it.year ?: 0) >= year - 1 }
                .take(4)
        }.distinctBy { it.id }.sortedByDescending { it.year ?: 0 }.take(12)
        if (albums.isEmpty()) return null
        return RecommendationRowSection(RecRowKind.NewReleases, "", null, albums)
    }

    // up to three genres your top artists are filed under, then two you don't play yet
    private suspend fun genreRows(artists: List<TasteArtist>, random: Random): Pair<List<RecommendationRowSection>, List<RecommendationRowSection>> {
        val groups = runCatching { YouTube.moodAndGenres().getOrNull() }.getOrNull().orEmpty()
        val catalogue = groups.lastOrNull { group -> group.title.contains("genre", true) }?.items ?: groups.getOrNull(1)?.items.orEmpty()
        if (catalogue.isEmpty()) return emptyList<RecommendationRowSection>() to emptyList()
        val weighted = HashMap<String, Double>()
        artists.take(3).forEach { a ->
            val genre = genreOf(a.name) ?: return@forEach
            weighted[genre] = (weighted[genre] ?: 0.0) + a.weight
        }
        val mine = weighted.entries.sortedByDescending { it.value }.mapNotNull { (genre, _) ->
            catalogue.firstOrNull { TasteMath.genreMatches(genre, it.title) }
        }.distinctBy { it.title }.take(3)
        val others = catalogue.filter { item -> mine.none { it.title == item.title } }.shuffled(random).take(5 - mine.size.coerceAtMost(3))
        suspend fun row(item: com.music.innertube.pages.MoodAndGenres.Item, kind: RecRowKind) = gate.withPermit {
            runCatching {
                val page = YouTube.browse(item.endpoint.browseId, item.endpoint.params).getOrNull() ?: return@runCatching null
                val items = page.items.flatMap { it.items }.filter { it !is SongItem || !it.isVideoSong }.distinctBy { it.id }.take(12)
                RecommendationRowSection(kind, item.title, null, items)
            }.getOrNull()
        }
        return coroutineScope {
            val a = mine.map { async { row(it, RecRowKind.Genre) } }
            val b = others.take(2).map { async { row(it, RecRowKind.NewGenre) } }
            a.awaitAll().filterNotNull() to b.awaitAll().filterNotNull()
        }
    }

    // iTunes knows an artist's genre, YouTube doesn't
    private fun genreOf(artist: String): String? {
        val key = artist.lowercase()
        if (genreCache.containsKey(key)) return genreCache[key]
        val genre = runCatching {
            val url = URL("https://itunes.apple.com/search?term=${URLEncoder.encode(artist, "UTF-8")}&entity=musicArtist&limit=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
            }
            conn.inputStream.bufferedReader().use { it.readText() }.let { body ->
                JSONObject(body).optJSONArray("results")?.optJSONObject(0)?.optString("primaryGenreName")?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
        genreCache[key] = genre
        return genre
    }
}

// where the engine's rows sit among the rest of Home: after recently and most played, a first
// round of rows, then the artist list and similar artists, then the rest
object HomeArrangement {
    fun arrange(feed: HomeFeed, rows: List<RecommendationRowSection>): HomeFeed {
        if (rows.isEmpty()) return feed
        val s = feed.sections
        val lately = s.filterIsInstance<LatelySection>()
        val rotation = s.filterIsInstance<RotationSection>()
        val artistList = s.filterIsInstance<RecentArtistsSection>()
        val deep = s.filterIsInstance<DeepDiveSection>()
        val others = s.filterNot {
            it is LatelySection || it is RotationSection || it is RecentArtistsSection || it is DeepDiveSection ||
                it is DiscoverSection || it is MoreLikeSection || it is ContinueSection
        }
        return feed.copy(sections = others + lately + rotation + rows.take(4) + artistList + deep + rows.drop(4))
    }
}
