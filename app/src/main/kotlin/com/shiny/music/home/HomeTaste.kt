package com.shiny.music.home

import com.shiny.music.db.entities.HomeAlbumStat
import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.HomeSavedArtist
import kotlin.math.exp
import kotlin.math.ln

/**
 * What Shiny knows about the listener, as continuous weights rather than thresholds.
 *
 * Home used to ask one boolean question — *are there twelve plays yet?* — and answer it
 * with either nothing personal at all or the whole personal feed at once. Everything here
 * exists to replace that question with a measured one: **how much is known, and about
 * what?** Every value moves from the first counted play and keeps moving; none of them
 * ever snaps.
 *
 * Two properties matter and are tested:
 *
 * - **Monotonic and smooth.** More listening never lowers [HomeTaste.confidence], and no
 *   single play moves it by a visible step. Sections fade in as their evidence arrives
 *   rather than unlocking together.
 * - **Repetition outranks reach.** One play of an artist is exploration and is scored as
 *   such ([AffinityTier.Exploring]); the same artist across several songs and several days
 *   is a preference. This is what stops one Bollywood song from turning Home Bollywood.
 *
 * Pure functions over [HomeSignals] and [HomeInterest] — no I/O, cheap enough to run on
 * every rebuild, and testable for every listener shape.
 */
object HomeTaste {

    /**
     * A saturating curve: 0 at 0, [half] at [k], approaching 1. The shape behind every
     * weight here — it has no cliff anywhere, which is the entire point.
     */
    private fun sat(x: Double, k: Double): Double = if (x <= 0.0) 0.0 else x / (x + k)

    private fun sat(x: Int, k: Double): Double = sat(x.toDouble(), k)

    /** Falls from 1 to ~0.37 over [halfLifeDays] · ln2, so "a while ago" fades rather than expires. */
    private fun decay(ageMs: Long, halfLifeDays: Double): Double =
        if (ageMs <= 0L) 1.0 else exp(-ageMs.toDouble() / (halfLifeDays * HOME_DAY_MS))

    // -----------------------------------------------------------------------------------
    // Confidence
    // -----------------------------------------------------------------------------------

    /**
     * How much history there is to personalise from, 0..1.
     *
     * Five kinds of evidence, weighted by how much each one actually tells us. Repetition
     * carries the most per play because a song played twice is the cheapest honest proof
     * of a preference; reach carries less because playing forty songs once each says only
     * that someone is exploring.
     *
     * Roughly: one play ≈ 0.04, a first session of five ≈ 0.15, a dozen plays ≈ 0.29,
     * a couple of months of daily listening ≈ 0.63, a long history ≈ 0.85. It never
     * reaches 1 — there is always more to learn.
     */
    fun confidence(signals: HomeSignals): Double {
        val stats = signals.songStats.values
        val plays = signals.counts.events
        val distinctSongs = stats.size
        val distinctArtists = signals.artistStats.size
        val repeated = stats.count { it.plays >= 2 }

        val depth = sat(plays, 30.0)
        val breadth = sat(distinctSongs, 18.0)
        val variety = sat(distinctArtists, 10.0)
        val repetition = sat(repeated, 8.0)
        val habit = sat(signals.daypartDays, 6.0)

        val played = (0.30 * depth + 0.20 * breadth + 0.18 * variety + 0.22 * repetition + 0.10 * habit)
            .coerceIn(0.0, 1.0)
        // Combined as independent evidence, so the prior only fills the room listening has
        // not yet taken and can never make listening count for less.
        return 1.0 - (1.0 - played) * (1.0 - prior(signals))
    }

    /**
     * What the listener's own music says before they play anything: liked and saved songs,
     * their playlists and Spotify mixes, and the files on the device. Worth at most
     * [MAX_PRIOR] — enough that someone arriving with a full library does not get the Home
     * of someone with nothing, never enough to pass for real listening. A library of 40
     * songs gives half of it.
     */
    fun prior(signals: HomeSignals): Double =
        MAX_PRIOR * sat(signals.savedSongs + signals.counts.localSongs, 40.0)

    const val MAX_PRIOR = 0.25

    // -----------------------------------------------------------------------------------
    // Artists
    // -----------------------------------------------------------------------------------

    /**
     * How strongly the listener is attached to one artist, 0..1, and what that is worth
     * saying out loud ([AffinityTier]).
     *
     * A single play cannot clear [AffinityTier.Warming] whatever else is true of it: the
     * depth and spread terms are both near zero at one play of one song, so the ceiling
     * for a first listen is about 0.26. Reaching [AffinityTier.Favourite] takes repeats
     * across more than one song, and the whole score fades with a 45-day half-life, so an
     * artist the listener has moved on from stops shaping Home without being deleted.
     */
    fun artistAffinity(stat: HomeArtistStat, nowMs: Long): ArtistAffinity {
        val depth = sat(stat.plays, 6.0)
        val spread = sat(stat.songs, 3.0)
        val recent = sat(stat.monthPlays * 2 + stat.weekPlays * 3, 8.0)
        val recency = decay(nowMs - stat.lastPlayed, halfLifeDays = 45.0)
        val strength = ((0.40 * depth + 0.25 * spread + 0.35 * recent) * (0.45 + 0.55 * recency))
            .coerceIn(0.0, 1.0)
        return ArtistAffinity(
            artistId = stat.artistId,
            strength = strength,
            plays = stat.plays,
            songs = stat.songs,
            tier = when {
                strength >= 0.55 -> AffinityTier.Favourite
                strength >= 0.30 -> AffinityTier.Warming
                else -> AffinityTier.Exploring
            },
        )
    }

    /**
     * How strongly the listener's saved music ties them to an artist they may never have
     * played. Capped below [AffinityTier.Favourite]: a library says "likes", only listening
     * says "loves". Around eight saved songs (likes count twice) reach [AffinityTier.Warming].
     */
    fun savedAffinity(artist: HomeSavedArtist): ArtistAffinity {
        val strength = MAX_SAVED_STRENGTH * sat(artist.songs + artist.liked, 6.0)
        return ArtistAffinity(
            artistId = artist.artistId,
            strength = strength,
            plays = 0,
            songs = artist.songs,
            tier = if (strength >= 0.30) AffinityTier.Warming else AffinityTier.Exploring,
        )
    }

    private const val MAX_SAVED_STRENGTH = 0.54

    /**
     * The saved artists under streamable ids, strongest first. An artist known only from
     * device files is found through [artistIds] (name → YouTube channel, resolved by
     * `HomeRemoteRepository`), and merged with the same artist met on YouTube.
     */
    fun savedArtists(signals: HomeSignals, artistIds: Map<String, String>): List<HomeSavedArtist> =
        signals.savedArtists
            .mapNotNull { a ->
                val id = if (HomeRanking.isStreamableId(a.artistId)) a.artistId
                else artistIds[a.name.lowercase()]?.takeIf { it.isNotBlank() }
                id?.let { a.copy(artistId = it) }
            }
            .groupBy { it.artistId }
            .map { (id, same) -> HomeSavedArtist(id, same.first().name, same.sumOf { it.songs }, same.sumOf { it.liked }) }
            .sortedByDescending { it.liked * 2 + it.songs }

    /**
     * Every artist the listener has played or saved, strongest first. Where both are known
     * the stronger reading wins, and the play counts stay those of real listening.
     */
    fun artists(signals: HomeSignals, nowMs: Long, artistIds: Map<String, String> = emptyMap()): List<ArtistAffinity> {
        val played = signals.artistStats
            .filter { HomeRanking.isStreamableId(it.artistId) }
            .map { artistAffinity(it, nowMs) }
            .associateBy { it.artistId }
        val saved = savedArtists(signals, artistIds).map(::savedAffinity).associateBy { it.artistId }
        return (played.keys + saved.keys).map { id ->
            val p = played[id]
            val s = saved[id]
            when {
                p == null -> s!!
                s == null || p.strength >= s.strength -> p
                else -> p.copy(strength = s.strength, tier = maxOf(p.tier, s.tier))
            }
        }.sortedByDescending { it.strength }
    }

    // -----------------------------------------------------------------------------------
    // Leans — how this listener behaves, each 0..1
    // -----------------------------------------------------------------------------------

    /** Share of listening that happens inside albums rather than across singles. */
    private fun albumLean(albumStats: List<HomeAlbumStat>, plays: Int): Double {
        if (plays <= 0 || albumStats.isEmpty()) return 0.0
        val deep = albumStats.filter { it.songsPlayed >= 2 }.sumOf { it.plays }
        return (deep.toDouble() / plays).coerceIn(0.0, 1.0)
    }

    /**
     * Concentration: how much of the listening the top three artists hold. High means a
     * loyalist, who wants their artists surfaced; low means a grazer, who wants breadth.
     */
    private fun artistLean(artistStats: List<HomeArtistStat>, plays: Int): Double {
        if (plays <= 0 || artistStats.isEmpty()) return 0.0
        val top = artistStats.sortedByDescending { it.plays }.take(3).sumOf { it.plays }
        return (top.toDouble() / plays).coerceIn(0.0, 1.0)
    }

    /**
     * Breadth per play: many artists across few plays is exploration, few artists across
     * many plays is not. Normalised so a listener hearing a new artist every other play
     * reads as fully exploratory.
     */
    private fun exploreLean(artistStats: List<HomeArtistStat>, plays: Int): Double {
        if (plays <= 0) return 0.0
        return ((artistStats.size.toDouble() / plays) / 0.5).coerceIn(0.0, 1.0)
    }

    /** How much listening happened in the last week against the last month. */
    private fun activeLean(signals: HomeSignals): Double {
        val month = signals.monthPlays
        if (month <= 0) return 0.0
        val week = signals.songStats.values.sumOf { it.weekPlays }
        return (week.toDouble() / month).coerceIn(0.0, 1.0)
    }

    /**
     * Everything Home needs to know about this listener, in one pass over the aggregates
     * already loaded. [interest] carries what they have reached for on Home itself —
     * charts, moods, releases, search — which the listen history cannot see.
     */
    fun of(
        signals: HomeSignals,
        interest: HomeInterest,
        nowMs: Long,
        artistIds: Map<String, String> = emptyMap(),
    ): HomeTasteProfile {
        val plays = signals.counts.events
        val monthPlays = signals.monthPlays
        return HomeTasteProfile(
            confidence = confidence(signals),
            artists = artists(signals, nowMs, artistIds),
            albumLean = albumLean(signals.albumStats, plays),
            artistLean = artistLean(signals.artistStats, plays),
            offlineLean = if (monthPlays > 0) {
                (signals.monthOfflinePlays.toDouble() / monthPlays).coerceIn(0.0, 1.0)
            } else {
                // No plays this month: the library itself is the only evidence of how this
                // listener listens, so a device full of files still leans offline.
                val offline = signals.counts.localSongs + signals.counts.downloadedSongs
                if (offline >= 5) sat(offline, 40.0) else 0.0
            },
            exploreLean = exploreLean(signals.artistStats, plays),
            activeLean = activeLean(signals),
            habitLean = sat(signals.daypartDays, 6.0),
            chartLean = interest.lean(HomeInterest.Kind.Charts),
            moodLean = interest.lean(HomeInterest.Kind.Moods),
            releaseLean = interest.lean(HomeInterest.Kind.Releases),
            discoveryLean = interest.lean(HomeInterest.Kind.Discovery),
            // Real data, not a tap counter: how many different things they have looked for.
            searchLean = sat(signals.counts.searches, 12.0),
        )
    }

    // -----------------------------------------------------------------------------------
    // Progressive minimums
    // -----------------------------------------------------------------------------------

    /**
     * A section's minimum, scaled by confidence: [low] while Shiny is still learning,
     * [high] once it knows the listener.
     *
     * This is what replaced the cliff. A shelf that used to need four songs and a profile
     * flag now needs two at first and four later — it appears the moment it has something
     * true to say, and grows into its full shape instead of switching on.
     */
    fun minimum(confidence: Double, low: Int, high: Int): Int =
        (low + (high - low) * confidence).toInt().coerceIn(minOf(low, high), maxOf(low, high))

    /**
     * The share of the day's mix that should be music the listener has never played.
     *
     * Deliberately *inverted* against confidence: knowing little is a reason to offer
     * more, not less, and a first mix that is two-thirds discovery is honest about what
     * it is. As the rotation becomes real the mix leans back towards it, but never below
     * the floor — Home is never only what is already known.
     */
    fun freshShare(daypartShare: Double, confidence: Double): Double =
        (daypartShare * (1.0 + (1.0 - confidence) * 0.7)).coerceIn(daypartShare, 0.7)
}

enum class AffinityTier {
    /** Heard once or twice: interesting, not yet a preference. */
    Exploring,

    /** Coming back to them. Worth surfacing, not worth claiming. */
    Warming,

    /** Sustained listening across songs and days. */
    Favourite,
}

data class ArtistAffinity(
    val artistId: String,
    /** 0..1 (see [HomeTaste.artistAffinity]). */
    val strength: Double,
    val plays: Int,
    val songs: Int,
    val tier: AffinityTier,
)

/**
 * The listener as Home reads them. Every field is 0..1 and every field moves from the
 * first interaction.
 */
data class HomeTasteProfile(
    val confidence: Double,
    val artists: List<ArtistAffinity>,
    /** Listens to albums rather than singles. */
    val albumLean: Double,
    /** Returns to the same few artists. */
    val artistLean: Double,
    /** Plays downloads and device files rather than streams. */
    val offlineLean: Double,
    /** Reaches for artists they have not heard before. */
    val exploreLean: Double,
    /** Listening this week against this month. */
    val activeLean: Double,
    /** Has a pattern at this time of day. */
    val habitLean: Double,
    /** Opens the charts. */
    val chartLean: Double,
    /** Opens moods and genres. */
    val moodLean: Double,
    /** Opens new releases. */
    val releaseLean: Double,
    /** Plays from Discover and Surprise Me. */
    val discoveryLean: Double,
    /** Searches. */
    val searchLean: Double,
) {
    /** Artists worth building a section around: repeat listening, strongest first. */
    val favourites: List<ArtistAffinity> get() = artists.filter { it.tier != AffinityTier.Exploring }

    /** The strongest artist of any tier — what "more from" can honestly point at. */
    val leadArtist: ArtistAffinity? get() = artists.firstOrNull()

    companion object {
        /** A listener Shiny has never heard play anything. */
        val Unknown = HomeTasteProfile(
            confidence = 0.0,
            artists = emptyList(),
            albumLean = 0.0,
            artistLean = 0.0,
            offlineLean = 0.0,
            exploreLean = 0.0,
            activeLean = 0.0,
            habitLean = 0.0,
            chartLean = 0.0,
            moodLean = 0.0,
            releaseLean = 0.0,
            discoveryLean = 0.0,
            searchLean = 0.0,
        )
    }
}

/** Natural log of 1+x, for callers that want a gentler curve than [HomeTaste] exposes. */
internal fun ln1p(x: Int): Double = ln(1.0 + x)
