package com.shiny.music.home

import com.music.innertube.pages.MoodAndGenres
import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.Song
import com.shiny.music.home.HomeRanking.capPerArtist
import com.shiny.music.home.HomeRanking.primaryArtistKey
import com.shiny.music.home.HomeRanking.rotateHead
import com.shiny.music.home.HomeRanking.spreadArtists
import java.time.ZoneOffset
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Assembles Home from real signals. A pure function of the listen history, the cached
 * network content, what the listener reaches for and the moment — no I/O — so every
 * listener shape can be tested.
 *
 * Three rules run through all of it:
 *
 * - **A section exists only when it has enough real content.** Nothing is padded, nothing
 *   is invented, and an empty shelf is never drawn because the layout expects one.
 *
 * - **Personalisation is a slope, not a door.** There is no play count at which Home
 *   becomes personal. Every threshold below is scaled by [HomeTasteProfile.confidence],
 *   which moves from the first counted play, so a section appears at the moment it has
 *   something true to say and then grows into its full shape. What changes between a new
 *   listener and a long-standing one is the *balance* of Home — editorial content leading
 *   at first, personal content leading later — not whether personal content is allowed.
 *
 * - **The order is chosen, not fixed.** [HomeOrder] scores every section from the
 *   listener's own behaviour: album listeners see releases earlier, chart readers see
 *   charts earlier, people who mostly play downloads see those first.
 */
object HomeFeedBuilder {

    // Section minimums. Each pair is (while Shiny is still learning, once it knows them):
    // the low number is what it takes for a section to be worth showing at all, the high
    // number is the shape it settles into. See HomeTaste.minimum.
    const val MIN_ROTATION_LOW = 2
    const val MIN_ROTATION_HIGH = 4
    const val MIN_AROUND_NOW_LOW = 5
    const val MIN_AROUND_NOW_HIGH = 8
    const val MIN_DAYPART_DAYS_LOW = 2
    const val MIN_DAYPART_DAYS_HIGH = 4
    const val MIN_DISCOVER_LOW = 3
    const val MIN_DISCOVER_HIGH = 5
    const val MIN_REDISCOVER = 5
    const val MIN_SPOTIFY_MIX_SONGS = 5

    /** Saved songs by one artist before they count as one of the listener's artists. */
    const val MIN_SAVED_ARTIST_SONGS = 3
    const val MIN_HERO_LOW = 6
    const val MIN_HERO_HIGH = 10
    const val MIN_CONTINUE = 4
    const val HERO_SIZE = 24

    /** Above this the listener has better sections than "more of what you just played". */
    const val CONTINUE_RETIRES_AT = 0.45

    /**
     * A name for the whole listener, derived from the same weights everything else uses.
     * Kept for the New tab and for describing Home's own shape — nothing is gated on it.
     */
    fun profile(signals: HomeSignals): ListenerProfile =
        profileOf(HomeTaste.of(signals, HomeInterest.Empty, homeWallNowMs()), signals)

    private fun profileOf(taste: HomeTasteProfile, signals: HomeSignals): ListenerProfile {
        if (taste.confidence < 0.20) return ListenerProfile.New
        if (signals.monthPlays >= 10 && taste.offlineLean >= 0.6) return ListenerProfile.LocalHeavy
        return ListenerProfile.Established
    }

    fun build(
        signals: HomeSignals,
        remote: HomeRemote?,
        ctx: HomeContext,
        interest: HomeInterest = HomeInterest.Empty,
        spotifyMixes: List<HomeSpotifyMix> = emptyList(),
    ): HomeFeed {
        val taste = HomeTaste.of(signals, interest, ctx.nowMs, remote?.artistIds.orEmpty())
        val confidence = taste.confidence
        val profile = profileOf(taste, signals)
        val stats = signals.songStats
        val now = ctx.nowMs

        fun playable(song: Song) = ctx.online || song.song.isLocal || song.song.isDownloaded
        fun allowed(song: Song) = !(ctx.hideVideoSongs && song.song.isVideo) && playable(song)
        fun resolve(ids: List<String>) = ids.mapNotNull { signals.songs[it] }.filter(::allowed)
        fun ranked(song: Song) = stats[song.id].let {
            RankedSong(song, it?.plays ?: 0, it?.monthPlays ?: 0, it?.weekPlays ?: 0, it?.lastPlayed ?: 0L)
        }

        val rotationSongs = resolve(signals.rotationIds)
            .sortedByDescending { song ->
                val st = stats.getValue(song.id)
                HomeRanking.rotationScore(st) * HomeRanking.completionFactor(song, st)
            }
            .let { capPerArtist(it, 2, ::primaryArtistKey) }
        val rotation = rotationSongs.take(10).map(::ranked)

        val minDaypartDays = HomeTaste.minimum(confidence, MIN_DAYPART_DAYS_LOW, MIN_DAYPART_DAYS_HIGH)
        val daypartSongs = if (signals.daypartDays >= minDaypartDays) {
            resolve(signals.daypartIds)
                .let { capPerArtist(it, 3, ::primaryArtistKey) }
                .let { rotateHead(it, 12, ctx.seed) }
                .take(25)
        } else {
            emptyList()
        }

        val rediscoverAll = resolve(signals.rediscoverIds)
            .let { rotateHead(it, 24, ctx.seed) }
            .let { capPerArtist(it, 2, ::primaryArtistKey) }
        val rediscover = rediscoverAll.take(10)

        val discoverAll = if (!ctx.online) emptyList() else {
            signals.discovery.mapNotNull { link ->
                val song = signals.songs[link.songId] ?: return@mapNotNull null
                val because = signals.songs[link.seedId] ?: return@mapNotNull null
                DiscoverPick(song, because)
            }.filter {
                !(ctx.hideVideoSongs && it.song.song.isVideo) && !(ctx.hideExplicit && it.song.song.explicit)
            }
        }

        val continueSection = if (confidence < CONTINUE_RETIRES_AT) {
            keepListening(signals, discoverAll, taste, ctx, ::allowed)
        } else {
            null
        }
        val continueIds = continueSection?.songs?.map { it.id }.orEmpty().toSet()

        val discover = run {
            val pool = discoverAll.filterNot { it.song.id in continueIds }
            // Breadth first: one song per artist unless that leaves too little to show.
            val onePerArtist = capPerArtist(pool, 1) { primaryArtistKey(it.song) }
            val chosen = if (onePerArtist.size >= 8) onePerArtist else capPerArtist(pool, 2) { primaryArtistKey(it.song) }
            rotateHead(chosen, 16, ctx.seed).take(12)
        }

        val hero = buildHero(
            ctx = ctx,
            confidence = confidence,
            rotation = rotationSongs,
            daypart = daypartSongs,
            rediscover = rediscoverAll,
            recent = resolve(signals.recentIds),
            offline = signals.offlineSongs.filter(::allowed),
            unexplored = signals.unexplored.filter(::allowed),
            fresh = discoverAll.map { it.song }.filterNot { s -> discover.any { it.song.id == s.id } }
                .ifEmpty { discover.map { it.song } },
        )

        val sections = linkedMapOf<HomeSlot, HomeSection>()

        if (!ctx.online) sections[HomeSlot.OfflineNotice] = OfflineNoticeSection(signals.counts.localSongs + signals.counts.downloadedSongs)

        if (hero != null) {
            sections[HomeSlot.Opening] = hero
        } else {
            // Nothing of the listener's own to open on. Show them the best real music
            // Shiny has instead of an empty state: the chart for where they are, or what
            // is already on the device.
            val chart = remote?.takeIf { ctx.online }?.let { chartsFor(it, ctx).firstOrNull() }
            val device = signals.offlineSongs.filter(::allowed)
            if (chart != null || device.isNotEmpty() || !ctx.online) {
                sections[HomeSlot.Opening] = StartHereSection(
                    chart = chart,
                    deviceSongs = device.take(30),
                    localSongs = signals.counts.localSongs,
                    downloadedSongs = signals.counts.downloadedSongs,
                    online = ctx.online,
                )
            }
        }

        continueSection?.let { sections[HomeSlot.Continue] = it }

        // ---- Each connected service's own row: its liked songs, then its mixes -----------
        // Everything in them streams from YouTube, so like the charts they need the network.
        val spotify = if (ctx.online) spotifySection(signals, spotifyMixes) else null
        val youtube = if (ctx.online) youtubeSection(signals, remote, ctx) else null

        // Lately leaves out what a service row already leads with.
        lately(
            signals, remote, ctx,
            likedShown = youtube?.liked != null,
            playlistsShown = setOfNotNull(signals.spotifyLiked?.id?.takeIf { spotify?.liked != null }),
        )?.let { sections[HomeSlot.Lately] = it }

        if (rotation.size >= HomeTaste.minimum(confidence, MIN_ROTATION_LOW, MIN_ROTATION_HIGH)) {
            sections[HomeSlot.Rotation] = RotationSection(rotation)
        }

        val moods = if (ctx.online) moodsFor(ctx.daypart, remote?.moods.orEmpty()) else emptyList()
        if (daypartSongs.size >= HomeTaste.minimum(confidence, MIN_AROUND_NOW_LOW, MIN_AROUND_NOW_HIGH)) {
            sections[HomeSlot.AroundNow] = AroundNowSection(ctx.daypart, daypartSongs, moods.take(6))
        } else if (moods.isNotEmpty()) {
            // No pattern at this hour yet: offer the choice instead of guessing.
            sections[HomeSlot.Moods] = MoodsSection(ctx.daypart, moods.take(8))
        }

        if (discover.size >= HomeTaste.minimum(confidence, MIN_DISCOVER_LOW, MIN_DISCOVER_HIGH)) {
            sections[HomeSlot.Discover] = discoverSection(discover)
        }

        if (ctx.online && remote != null) {
            deepDive(signals, remote, ctx, taste)?.let { sections[HomeSlot.DeepDive] = it }
            charts(remote, ctx)?.let { sections[HomeSlot.Charts] = it }
        }

        spotify?.let { sections[HomeSlot.Spotify] = it }
        youtube?.let { sections[HomeSlot.YouTube] = it }

        if (rediscover.size >= MIN_REDISCOVER) sections[HomeSlot.Rediscover] = RediscoverSection(rediscover.map(::ranked))

        albumsInProgress(signals, ctx)?.let { sections[HomeSlot.AlbumsInProgress] = it }
        recentlyAdded(signals, ctx, ::allowed)?.let { sections[HomeSlot.RecentlyAdded] = it }
        readyOffline(signals, ctx, taste, ::allowed)?.let { sections[HomeSlot.ReadyOffline] = it }
        insights(signals, remote, ctx)?.let { sections[HomeSlot.Insights] = it }

        val surprise = SurpriseSection(
            listOf(
                SurprisePool(SurpriseKind.Forgotten, 0.30, rediscoverAll.drop(rediscover.size).ifEmpty { rediscoverAll }),
                SurprisePool(SurpriseKind.Unexplored, 0.30, signals.unexplored.filter(::allowed)),
                SurprisePool(
                    SurpriseKind.NewToYou, 0.25,
                    if (ctx.online) discoverAll.drop(discover.size).map { it.song }.ifEmpty { discover.map { it.song } } else emptyList(),
                ),
                SurprisePool(
                    SurpriseKind.OnDevice, 0.15,
                    signals.offlineSongs.filter { song ->
                        allowed(song) && (stats[song.id]?.lastPlayed?.let { now - it > 14 * HOME_DAY_MS } ?: true)
                    },
                ),
            ).filter { it.songs.isNotEmpty() }
        )
        if (surprise.size >= 5) sections[HomeSlot.Surprise] = surprise

        val feed = HomeOrder.order(sections.keys, ctx, taste).mapNotNull { sections[it] }
        return HomeFeed(feed, profile, ctx.daypart, ctx.online, taste)
    }

    /**
     * The day's mix.
     *
     * The familiar pool is filled from the strongest evidence down: the rotation and the
     * hour's pattern first, then old favourites, then simply what was played lately, then
     * the listener's own downloads and device files, then their library. A listener with
     * one counted play falls through to the last rungs and still gets a mix; a listener
     * with none at all but a full phone gets a mix of their phone. Only someone with
     * nothing at all gets no mix, and they get [StartHereSection] instead.
     */
    private fun buildHero(
        ctx: HomeContext,
        confidence: Double,
        rotation: List<Song>,
        daypart: List<Song>,
        rediscover: List<Song>,
        recent: List<Song>,
        offline: List<Song>,
        unexplored: List<Song>,
        fresh: List<Song>,
    ): HeroMixSection? {
        val nightish = ctx.daypart == Daypart.Evening || ctx.daypart == Daypart.Night
        var familiarPool = (if (nightish) daypart + rotation else rotation + daypart).distinctBy { it.id }
        fun topUp(more: List<Song>) {
            if (familiarPool.size < 16) familiarPool = (familiarPool + more).distinctBy { it.id }
        }
        topUp(rediscover)
        // A young history has no rotation yet; what was played lately is still the listener's own.
        topUp(recent)
        // And so is everything they downloaded or put on the phone, network or not.
        topUp(offline)
        topUp(unexplored)

        val daypartShare = if (!ctx.online) 0.0 else when (ctx.daypart) {
            Daypart.Morning -> 0.3
            Daypart.Afternoon -> 0.5
            Daypart.Evening -> 0.4
            Daypart.Night -> 0.2
        }
        // Knowing less is a reason to offer more, not less (see HomeTaste.freshShare).
        val freshShare = HomeTaste.freshShare(daypartShare, confidence)
        val freshPool = fresh.filterNot { f -> familiarPool.any { it.id == f.id } }
        val freshCount = (HERO_SIZE * freshShare).roundToInt().coerceAtMost(freshPool.size)
        val familiar = rotateHead(familiarPool, familiarPool.size, ctx.seed).take(HERO_SIZE - freshCount)
        val freshPicks = rotateHead(freshPool, freshPool.size, ctx.seed + 1).take(freshCount)

        val minHero = HomeTaste.minimum(confidence, MIN_HERO_LOW, MIN_HERO_HIGH)
        // At least one song has to be theirs, or this is not a mix, it is a recommendation.
        if (familiar.isEmpty() || familiar.size + freshPicks.size < minHero) return null

        // Weave: two familiar, one new, so discovery arrives between songs the listener knows.
        val woven = ArrayList<Song>(familiar.size + freshPicks.size)
        val f = familiar.iterator()
        val n = freshPicks.iterator()
        while (f.hasNext() || n.hasNext()) {
            repeat(2) { if (f.hasNext()) woven += f.next() }
            if (n.hasNext()) woven += n.next()
        }
        return HeroMixSection(
            daypart = ctx.daypart,
            songs = spreadArtists(woven, artistOf = ::primaryArtistKey),
            familiar = familiar.size,
            fresh = freshPicks.size,
            offlineOnly = !ctx.online,
            confidence = confidence,
        )
    }

    /**
     * More of whatever was played last — the first personal section any listener sees.
     *
     * It prefers the artist, because "more by this artist" is the strongest claim one play
     * supports, and falls back to the related-song graph, which `MusicService` fills the
     * first time each song plays. The [AffinityTier] it carries is what stops the copy
     * from overclaiming: one listen is framed as one listen.
     */
    private fun keepListening(
        signals: HomeSignals,
        discoverAll: List<DiscoverPick>,
        taste: HomeTasteProfile,
        ctx: HomeContext,
        allowed: (Song) -> Boolean,
    ): ContinueSection? {
        val seed = signals.recentIds.firstNotNullOfOrNull { signals.songs[it] }?.takeIf(allowed) ?: return null
        val seedArtist = seed.artists.firstOrNull()
        val played = signals.songStats.keys

        // Songs by the same artist that are not the seed itself.
        val byArtist = if (seedArtist == null) emptyList() else {
            signals.songs.values
                .filter { song ->
                    song.id != seed.id && allowed(song) &&
                        song.artists.any { it.id == seedArtist.id || it.name.equals(seedArtist.name, ignoreCase = true) }
                }
                // What they have not heard leads; what they have heard follows.
                .sortedBy { it.id in played }
                .take(12)
        }
        if (byArtist.size >= MIN_CONTINUE) {
            val affinity = taste.artists.firstOrNull { it.artistId == seedArtist?.id }
            return ContinueSection(
                seed = seed,
                kind = ContinueKind.Artist,
                songs = byArtist,
                throughName = seedArtist?.name,
                throughId = seedArtist?.id,
                tier = affinity?.tier ?: AffinityTier.Exploring,
            )
        }

        val related = discoverAll.filter { it.because.id == seed.id }.map { it.song }
            .let { capPerArtist(it, 2, ::primaryArtistKey) }
            .take(12)
        if (related.size >= MIN_CONTINUE) {
            return ContinueSection(
                seed = seed,
                kind = ContinueKind.Related,
                songs = related,
                throughName = seedArtist?.name,
                throughId = seedArtist?.id,
                tier = AffinityTier.Exploring,
            )
        }
        return null
    }

    /** The shelf, plus the one song or artist most of it came from, when there is one. */
    private fun discoverSection(picks: List<DiscoverPick>): DiscoverSection {
        val bySeed = picks.groupingBy { it.because.id }.eachCount()
        val dominant = bySeed.maxByOrNull { it.value }
            ?.takeIf { it.value * 2 >= picks.size }
            ?.let { entry -> picks.first { it.because.id == entry.key }.because }
        val byArtist = picks.groupingBy { primaryArtistKey(it.because) }.eachCount()
        val dominantArtist = byArtist.maxByOrNull { it.value }
            ?.takeIf { it.value * 2 >= picks.size }
            ?.let { entry -> picks.first { primaryArtistKey(it.because) == entry.key }.because.artists.firstOrNull()?.name }
        return DiscoverSection(picks, lead = dominant, leadArtist = dominantArtist)
    }

    /** An artist's photo from the library, else from their cached artist page. */
    private fun artistImage(signals: HomeSignals, remote: HomeRemote?, artistId: String): String? =
        signals.artists[artistId]?.artist?.thumbnailUrl ?: remote?.artists?.get(artistId)?.thumbnail

    private fun lately(
        signals: HomeSignals,
        remote: HomeRemote?,
        ctx: HomeContext,
        likedShown: Boolean,
        playlistsShown: Set<String>,
    ): LatelySection? {
        val now = ctx.nowMs
        val window = 21 * HOME_DAY_MS
        val tiles = mutableListOf<LatelyTile>()

        // A pinned library playlist is that playlist, marked pinned — one tile that opens its
        // songs, and it plays offline. Other pins are YouTube items; offline they would open
        // to nothing. The library playlists added further down share its key, so the
        // distinctBy at the end keeps just this one.
        if (ctx.showPinned) {
            signals.pinned.take(4).forEach { pin ->
                val local = signals.pinnedPlaylists[pin.id]
                when {
                    local != null -> tiles += PlaylistTile(local, pinned = true)
                    ctx.online -> tiles += PinnedTile(pin)
                }
            }
        }
        val pinnedIds = signals.pinned.map { it.id }.toSet()

        if (!ctx.online) {
            if (signals.counts.downloadedSongs > 0) tiles += DownloadsTile(signals.counts.downloadedSongs)
            if (signals.counts.localSongs > 0) tiles += DeviceTile(signals.counts.localSongs)
        }

        // Albums and artists interleaved by when they were last played.
        val recent = buildList<Pair<Long, LatelyTile>> {
            signals.albumStats.forEach { st ->
                if (now - st.lastPlayed > window || st.albumId in pinnedIds) return@forEach
                val album = signals.albums[st.albumId] ?: return@forEach
                if (!ctx.online && !album.album.isLocal) return@forEach
                add(st.lastPlayed to AlbumTile(album, st.songsPlayed))
            }
            signals.artistStats.forEach { st ->
                if (now - st.lastPlayed > window || st.songs < 2 || st.artistId in pinnedIds) return@forEach
                val artist = signals.artists[st.artistId] ?: return@forEach
                if (!ctx.online && !artist.artist.isLocal) return@forEach
                add(st.lastPlayed to ArtistTile(artist, st.plays, artistImage(signals, remote, st.artistId)))
            }
        }.sortedByDescending { it.first }.map { it.second }

        // At most two artists, so the grid stays mostly things that play.
        var artists = 0
        recent.forEach { tile ->
            if (tiles.size >= 6) return@forEach
            if (tile is ArtistTile) {
                if (artists >= 2) return@forEach
                artists++
            }
            tiles += tile
        }
        signals.playlists.filterNot { it.id in playlistsShown }.take(2).forEach { if (tiles.size < 7) tiles += PlaylistTile(it) }
        if (ctx.online && !likedShown && signals.counts.likedSongs >= 5) tiles += LikedTile(signals.counts.likedSongs)
        if (ctx.online && signals.counts.downloadedSongs > 0) tiles += DownloadsTile(signals.counts.downloadedSongs)
        if (ctx.online && signals.counts.localSongs > 0) tiles += DeviceTile(signals.counts.localSongs)

        return tiles.distinctBy { it.key }.take(8).takeIf { it.size >= 2 }?.let(::LatelySection)
    }

    private fun deepDive(
        signals: HomeSignals,
        remote: HomeRemote,
        ctx: HomeContext,
        taste: HomeTasteProfile,
    ): DeepDiveSection? {
        // Not a permanent fixture: it skips mornings and one daypart in three.
        if (ctx.daypart == Daypart.Morning || Math.floorMod(ctx.seed, 3L) == 2L) return null
        // An artist heard once is not one to build a whole section around. Below Warming,
        // "keep listening" says the same thing without claiming a preference.
        val strong = taste.favourites.map { it.artistId }.toSet()
        val candidates = topArtists(signals, artistIds = remote.artistIds)
            .filter { it.artistId in strong }
            .mapNotNull { st -> remote.artists[st.artistId]?.let { st to it } }
        if (candidates.isEmpty()) return null
        val (st, digest) = candidates[Math.floorMod(ctx.seed / 4, candidates.size.toLong()).toInt()]

        val heardAlbums = signals.albumStats.map { it.albumId }.toSet()
        val listened = signals.artistStats.map { it.artistId }.toSet()
        val albums = (listOfNotNull(digest.latest) + digest.albums)
            .distinctBy { it.id }
            .filter { it.id !in heardAlbums && !(ctx.hideExplicit && it.explicit) }
            .take(8)
        val related = digest.related.filter { it.id !in listened }.take(10)
        if (albums.size + related.size < 4) return null
        return DeepDiveSection(digest, st.plays, st.songs, albums, related, fromLibrary = st.plays == 0)
    }

    /**
     * The artists the listener plays most this month, most first, topped up with the artists
     * their saved music is strongest in. A saved artist comes as a stat with no plays, and
     * `songs` counting their saved songs — callers tell the two apart by `plays == 0`.
     */
    fun topArtists(signals: HomeSignals, limit: Int = 8, artistIds: Map<String, String> = emptyMap()): List<HomeArtistStat> {
        val played = signals.artistStats
            .filter { HomeRanking.isStreamableId(it.artistId) && it.monthPlays > 0 }
            .sortedByDescending { it.monthPlays * 2.0 + ln(1.0 + it.plays) }
            .take(limit)
        if (played.size >= limit) return played
        val have = played.map { it.artistId }.toSet()
        val saved = savedArtistStats(signals, artistIds)
            .filter { it.artistId !in have && it.songs >= MIN_SAVED_ARTIST_SONGS }
        return played + saved.take(limit - played.size)
    }

    /** The saved artists as play-less stats, strongest first (see [topArtists]). */
    fun savedArtistStats(signals: HomeSignals, artistIds: Map<String, String>): List<HomeArtistStat> =
        HomeTaste.savedArtists(signals, artistIds).map {
            HomeArtistStat(artistId = it.artistId, plays = 0, weekPlays = 0, monthPlays = 0, lastPlayed = 0, songs = it.songs)
        }

    /**
     * Saved artists whose names a device file gave but whose YouTube channel is not known
     * yet: what `HomeRemoteRepository.resolveArtists` should look up next.
     */
    fun unresolvedSavedArtists(signals: HomeSignals, artistIds: Map<String, String>, limit: Int): List<String> =
        signals.savedArtists
            .filter { !HomeRanking.isStreamableId(it.artistId) && it.songs >= MIN_SAVED_ARTIST_SONGS }
            .map { it.name.trim() }
            .filter { it.isNotBlank() && it.lowercase() !in artistIds && !it.equals("unknown artist", ignoreCase = true) && !it.equals("<unknown>", ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(limit)

    /** Spotify's row: the imported Liked Songs, then the mixes whose songs are matched. */
    private fun spotifySection(signals: HomeSignals, mixes: List<HomeSpotifyMix>): SpotifySection? {
        val liked = signals.spotifyLiked?.takeIf { it.songCount > 0 }
            ?.let { HomeLikedSongs(it.songCount, it.songThumbnails.filterNotNull().distinct().take(4)) }
        val ready = mixes.filter { it.songCount >= MIN_SPOTIFY_MIX_SONGS }
        if (liked == null && ready.isEmpty()) return null
        return SpotifySection(liked, ready)
    }

    /**
     * YouTube Music's row, only while signed in: the account's liked songs (synced into
     * Shiny's likes), then the mixes YouTube Music made for it.
     */
    private fun youtubeSection(signals: HomeSignals, remote: HomeRemote?, ctx: HomeContext): YouTubeSection? {
        if (!ctx.youtubeSignedIn) return null
        val liked = signals.counts.likedSongs.takeIf { it > 0 }?.let { HomeLikedSongs(it, signals.likedCovers) }
        val mixes = remote?.youtubeMixes.orEmpty()
        if (liked == null && mixes.isEmpty()) return null
        return YouTubeSection(liked, mixes)
    }

    private fun charts(remote: HomeRemote, ctx: HomeContext): ChartsSection? {
        val charts = chartsFor(remote, ctx)
        if (charts.isEmpty()) return null
        return ChartsSection(charts, remote.chartsAt)
    }

    private fun albumsInProgress(signals: HomeSignals, ctx: HomeContext): AlbumsInProgressSection? {
        val albums = signals.albumStats.asSequence()
            .filter { ctx.nowMs - it.lastPlayed <= 60 * HOME_DAY_MS && it.songsPlayed >= 2 }
            .mapNotNull { st ->
                val album = signals.albums[st.albumId] ?: return@mapNotNull null
                val total = album.album.songCount
                if (total < 5 || st.songsPlayed >= total || st.songsPlayed > total * 0.8) return@mapNotNull null
                if (!ctx.online && !album.album.isLocal) return@mapNotNull null
                AlbumProgress(album, st.songsPlayed, total)
            }
            .take(8)
            .toList()
        return albums.takeIf { it.size >= 2 }?.let(::AlbumsInProgressSection)
    }

    /** When a song arrived: saved to the library, downloaded, or (for a file) last written. */
    fun addedAt(song: Song): Long? = listOfNotNull(
        song.song.inLibrary,
        song.song.dateDownload,
        song.song.dateModified.takeIf { song.song.isLocal },
    ).maxOrNull()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()

    private fun recentlyAdded(signals: HomeSignals, ctx: HomeContext, allowed: (Song) -> Boolean): RecentlyAddedSection? {
        val songs = signals.recentlyAdded
            .filter { song -> allowed(song) && (addedAt(song)?.let { ctx.nowMs - it <= 30 * HOME_DAY_MS } ?: false) }
            .take(12)
        return songs.takeIf { it.size >= 3 }?.let(::RecentlyAddedSection)
    }

    private fun readyOffline(
        signals: HomeSignals,
        ctx: HomeContext,
        taste: HomeTasteProfile,
        allowed: (Song) -> Boolean,
    ): ReadyOfflineSection? {
        val local = signals.counts.localSongs
        val downloaded = signals.counts.downloadedSongs
        if (local + downloaded < 5) return null
        // Relevant when it is all that plays, when it is most of what they play, when they
        // have barely any history yet, or when they have simply downloaded a lot.
        val relevant = !ctx.online || taste.offlineLean >= 0.5 || taste.confidence < 0.25 || downloaded >= 10
        if (!relevant) return null
        val songs = signals.offlineSongs.filter(allowed)
        if (songs.isEmpty()) return null
        return ReadyOfflineSection(local, downloaded, songs)
    }

    private fun insights(signals: HomeSignals, remote: HomeRemote?, ctx: HomeContext): InsightsSection? {
        val week = signals.songStats.values.filter { it.weekPlays > 0 }
        val weekPlays = week.sumOf { it.weekPlays }
        if (weekPlays < 5) return null
        val topArtist = signals.artistStats.maxByOrNull { it.weekPlays }?.takeIf { it.weekPlays >= 3 }
        val replayed = week.maxByOrNull { it.weekPlays }?.takeIf { it.weekPlays >= 3 }
        return InsightsSection(
            weekPlays = weekPlays,
            weekSongs = week.size,
            weekArtists = signals.artistStats.count { it.weekPlays > 0 },
            weekMinutes = (week.sumOf { it.weekPlayTime } / 60_000L).toInt(),
            newSongs = week.count { ctx.nowMs - it.firstPlayed <= HomeRanking.WEEK_DAYS * HOME_DAY_MS },
            topArtist = topArtist?.let { signals.artists[it.artistId] },
            topArtistImage = topArtist?.let { artistImage(signals, remote, it.artistId) },
            topArtistPlays = topArtist?.weekPlays ?: 0,
            replayed = replayed?.let { signals.songs[it.songId] },
            replayedPlays = replayed?.weekPlays ?: 0,
        )
    }

    private val daypartMoodWords = mapOf(
        Daypart.Morning to listOf("energ", "commute", "workout", "feel good", "focus"),
        Daypart.Afternoon to listOf("focus", "workout", "energ", "party", "feel good"),
        Daypart.Evening to listOf("chill", "feel good", "romance", "party", "relax"),
        Daypart.Night to listOf("sleep", "chill", "romance", "sad", "relax"),
    )

    /**
     * YouTube Music's moods, the ones that suit this part of the day first. The listener
     * picks; nothing here claims to know their mood. Titles that do not match (another
     * language, say) keep YouTube's own order after the matches.
     */
    fun moodsFor(daypart: Daypart, moods: List<MoodAndGenres.Item>): List<MoodAndGenres.Item> {
        val words = daypartMoodWords.getValue(daypart)
        val unique = moods.distinctBy { it.title }
        val matched = words.flatMap { word -> unique.filter { it.title.contains(word, ignoreCase = true) } }.distinct()
        return (matched + unique).distinct()
    }
}
