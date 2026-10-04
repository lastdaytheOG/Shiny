package com.shiny.music.home

import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.spotifyimport.SpotifyImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Reads everything Home is built from in one pass on IO.
 *
 * The history is read as aggregates (one row per song, artist or album), ranked in memory
 * by [HomeRanking], and only then are full `Song` rows loaded — for the few hundred ids that
 * can actually appear. A listener with 100 000 counted plays costs a handful of GROUP BY
 * scans, not 100 000 relation loads.
 */
class HomeSignalsLoader(private val database: MusicDatabase) {
    private companion object {
        /** Saved songs read per load; the strongest 25 seeds are drawn from these. */
        const val SAVED_SEED_POOL = 200

        /** Covers on the Liked Songs tile. */
        const val LIKED_COVERS = 4
    }


    suspend fun load(ctx: HomeContext): HomeSignals = withContext(Dispatchers.IO) {
        val now = ctx.nowMs
        val weekFrom = now - HomeRanking.WEEK_DAYS * HOME_DAY_MS
        val monthFrom = now - HomeRanking.MONTH_DAYS * HOME_DAY_MS
        val daypartFrom = now - HomeRanking.DAYPART_WINDOW_DAYS * HOME_DAY_MS
        val hours = ctx.daypart.hours

        val counts = database.homeLibraryCounts()
        val pinned = database.speedDialDao.getAll().first()
        val pinnedPlaylists = pinned.filter { it.type == "PLAYLIST" }
            .mapNotNull { database.getPlaylistById(it.id) }
            .associateBy { it.id }
        val playlists = database.playlistsByUpdatedDateAsc().first().asReversed().take(3)
        // What leads each service's row: the imported Spotify Liked Songs and the likes' covers.
        val spotifyLiked = database.getPlaylistById(SpotifyImportRepository.LIKED_SONGS_PLAYLIST_ID)
        val likedCovers = if (counts.likedSongs > 0) database.homeLikedCovers(limit = LIKED_COVERS) else emptyList()

        // What the listener chose without playing: the taste Shiny can read on day one.
        // Read before the first-launch check, since a Spotify or YouTube import can bring
        // playlists and nothing else.
        val saved = database.homeSavedSongs(limit = SAVED_SEED_POOL)

        if (counts.events == 0 && counts.localSongs == 0 && counts.downloadedSongs == 0 &&
            counts.librarySongs == 0 && counts.likedSongs == 0 && saved.isEmpty()
        ) {
            // A first launch: nothing to aggregate.
            return@withContext HomeSignals.Empty.copy(
                counts = counts,
                pinned = pinned,
                pinnedPlaylists = pinnedPlaylists,
                playlists = playlists,
                likedCovers = likedCovers,
                spotifyLiked = spotifyLiked,
            )
        }

        val statList = if (counts.events > 0) database.homeSongStats(weekFrom, monthFrom, daypartFrom, hours) else emptyList()
        val stats = statList.associateBy { it.songId }
        val artistStats = if (counts.events > 0) database.homeArtistStats(weekFrom, monthFrom, limit = 300) else emptyList()
        val albumStats = if (counts.events > 0) database.homeAlbumStats(limit = 60) else emptyList()
        val daypartDays = if (counts.events > 0) database.homeDaypartDays(daypartFrom, hours) else 0
        val monthOfflinePlays = if (counts.events > 0) database.homeOfflinePlays(monthFrom) else 0

        val savedSeeds = HomeRanking.savedSeedWeights(saved)
        val savedArtists = database.homeSavedArtists(limit = 40)
        val seeds = HomeRanking.mergeSeeds(HomeRanking.seedWeights(statList), savedSeeds)

        val rotationIds = HomeRanking.rotation(statList, now)
        val daypartIds = HomeRanking.daypart(statList)
        val rediscoverIds = HomeRanking.rediscover(statList, now)
        val recentIds = HomeRanking.recent(statList)
        val discovery = discovery(statList, seeds, ctx).filterNot { it.songId in savedSeeds }
        val replayedId = statList.filter { it.weekPlays >= 3 }.maxByOrNull { it.weekPlays }?.songId

        val songIds = buildSet {
            addAll(rotationIds)
            addAll(daypartIds)
            addAll(rediscoverIds)
            addAll(recentIds)
            discovery.forEach { add(it.songId); add(it.seedId) }
            replayedId?.let(::add)
        }
        val songs = songIds.chunked(500).flatMap { database.getSongsByIds(it) }.associateBy { it.id }

        val artistIds = buildSet {
            artistStats.take(40).forEach { add(it.artistId) }
            artistStats.sortedByDescending { it.monthPlays }.take(20).forEach { add(it.artistId) }
            artistStats.maxByOrNull { it.weekPlays }?.let { add(it.artistId) }
        }
        val artists = if (artistIds.isEmpty()) emptyMap() else database.homeArtists(artistIds.toList()).associateBy { it.id }
        val albums = if (albumStats.isEmpty()) emptyMap() else database.homeAlbums(albumStats.map { it.albumId }).associateBy { it.id }

        val hasOffline = counts.localSongs + counts.downloadedSongs > 0
        HomeSignals(
            counts = counts,
            songStats = stats,
            artistStats = artistStats,
            albumStats = albumStats,
            daypartDays = daypartDays,
            monthOfflinePlays = monthOfflinePlays,
            rotationIds = rotationIds,
            daypartIds = daypartIds,
            rediscoverIds = rediscoverIds,
            recentIds = recentIds,
            discovery = discovery,
            songs = songs,
            artists = artists,
            albums = albums,
            recentlyAdded = database.homeRecentlyAdded(limit = 24),
            offlineSongs = if (hasOffline) database.homeOfflineSongs(limit = 120) else emptyList(),
            unexplored = database.homeUnexploredLibrary(seed = unexploredSeed(ctx.seed), limit = 40),
            playlists = playlists,
            pinned = pinned,
            pinnedPlaylists = pinnedPlaylists,
            seeds = seeds,
            savedSongs = savedSeeds.size,
            savedArtists = savedArtists,
            likedCovers = likedCovers,
            spotifyLiked = spotifyLiked,
        )
    }

    /**
     * Discovery candidates from the related-song graph `MusicService` fills while songs play,
     * so Discover costs no request of its own. The "Quick picks" content setting chooses the
     * seeds: the listener's weighted favourites, or only the last song they heard (falling
     * back to favourites when that one song has too little related to it).
     */
    private suspend fun discovery(stats: List<HomeSongStat>, seeds: Map<String, Double>, ctx: HomeContext) =
        run {
            val favourites = seeds
            val last = HomeRanking.lastPlayed(stats)?.songId?.takeIf(HomeRanking::isStreamableId)
            val fromLast = if (ctx.discoverFromLastSong && last != null) {
                HomeRanking.discovery(database.homeRelatedLinks(listOf(last)), mapOf(last to 1.0))
            } else {
                emptyList()
            }
            if (fromLast.size >= 6 || favourites.isEmpty()) {
                fromLast
            } else {
                HomeRanking.discovery(database.homeRelatedLinks(favourites.keys.toList()), favourites)
            }
        }

    /** Seeds that need related songs fetched: favourites the graph knows nothing about yet. */
    suspend fun seedsWithoutRelated(signals: HomeSignals, limit: Int): List<String> = withContext(Dispatchers.IO) {
        signals.seeds.keys.take(12)
            .filterNot { database.hasRelatedSongs(it) }
            .take(limit)
    }

    private fun unexploredSeed(seed: Long): Long = 1L + Math.floorMod(seed * 7919L, 1_000_002L)
}
