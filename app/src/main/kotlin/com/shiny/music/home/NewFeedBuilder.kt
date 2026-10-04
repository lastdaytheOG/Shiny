package com.shiny.music.home

import com.music.innertube.models.AlbumItem
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.ln

// ---------------------------------------------------------------------------------------
// What New is made of
// ---------------------------------------------------------------------------------------

/**
 * Why a release is on the page.
 *
 * Every case is something Shiny can point at in the listener's own history or in a cached
 * artist page. There is no "editorial pick" case, because Shiny has no editors.
 */
sealed interface NewReason {
    /** An artist the listener plays, with their counted plays. */
    data class Plays(val artist: String, val plays: Int) : NewReason

    /** An artist listed as similar to [to] on [to]'s own page, whom the listener has never played. */
    data class Like(val artist: String, val to: String) : NewReason

    /** Nothing personal is known. It is simply out. */
    data object Fresh : NewReason
}

/** One release with the reason it is being shown. */
data class NewRelease(val album: AlbumItem, val reason: NewReason)

/** One block of New. A section exists only when it has real content. */
sealed interface NewSection {
    val key: String
}

/** The one release Shiny is actually recommending, and why. */
data class FeaturedSection(val release: NewRelease) : NewSection {
    override val key get() = "featured"
}

/** New music by artists in the listener's rotation, their strongest first. */
data class YourArtistsSection(val releases: List<NewRelease>) : NewSection {
    override val key get() = "your_artists"
}

/** New music by artists YouTube lists next to the listener's, whom they have never played. */
data class AdjacentSection(val releases: List<NewRelease>) : NewSection {
    override val key get() = "adjacent"
}

/** Everything else that is out, in YouTube's own order. */
data class FreshSection(val albums: List<AlbumItem>) : NewSection {
    override val key get() = "fresh"
}

/** The charts, exactly as the listener's Charts setting asks for them. */
data class NewChartsSection(val charts: List<HomeChart>, val refreshedAt: Long) : NewSection {
    override val key get() = "charts"
}

data class NewFeed(
    val sections: List<NewSection>,
    val profile: ListenerProfile,
    val online: Boolean,
    /** When the cached release block was fetched, or 0 if it never has been. */
    val fetchedAt: Long,
) {
    val isEmpty: Boolean get() = sections.isEmpty()

    companion object {
        val Empty = NewFeed(emptyList(), ListenerProfile.New, true, 0)
    }
}

// ---------------------------------------------------------------------------------------
// The builder
// ---------------------------------------------------------------------------------------

/**
 * New, assembled from the same signals Home uses: the listen history read by
 * [HomeSignalsLoader], and the one cached network block held by [HomeRemoteRepository].
 *
 * A pure function of those two and the moment — no I/O — so every listener shape can be
 * tested on the JVM.
 *
 * The page answers "what is worth hearing that I have not heard", not "what came out". Three
 * distances run through it, in this order and never mixed:
 *
 * 1. **Yours** — artists the listener actually plays, weighted by counted plays.
 * 2. **Adjacent** — artists listed as similar to theirs, on those artists' own pages, whom
 *    they have never played. Real edges from a real graph, not a guess.
 * 3. **Fresh** — everything else, in YouTube's order, so the page cannot close into an echo
 *    of what is already known.
 *
 * How much of each depends on who is listening. A listener with no history gets no personal
 * sections at all rather than fabricated ones; the whole page is the catalogue, and says so.
 */
object NewFeedBuilder {
    /** Releases from the listener's own artists, at most. */
    const val MAX_YOURS = 8

    /** Releases from adjacent artists, at most. */
    const val MAX_ADJACENT = 10

    /** How wide the broad shelf opens for a listener Shiny cannot personalise for. */
    const val MAX_FRESH_UNKNOWN = 24

    /** How wide it opens once the page already has personal sections above it. */
    const val MAX_FRESH_PERSONAL = 12

    /** Most candidates a tie for the lead is ever broken between. */
    const val FEATURE_POOL = 4

    /**
     * How close two releases have to score before the daypart, rather than the ranking,
     * decides which of them leads. Smaller than the gap any real affinity opens up, so a
     * release by somebody the listener actually plays is never rotated out by a stranger.
     */
    const val FEATURE_TIE = 0.35

    fun build(signals: HomeSignals, remote: HomeRemote?, ctx: HomeContext): NewFeed {
        val profile = HomeFeedBuilder.profile(signals)
        if (remote == null) return NewFeed(emptyList(), profile, ctx.online, 0)

        val releases = classify(signals, remote, ctx)
        val sections = mutableListOf<NewSection>()

        val featured = feature(releases, ctx)
        featured?.let { sections += FeaturedSection(it) }

        val used = mutableSetOf<String>()
        featured?.let { used += it.album.id }

        val yours = releases.asSequence()
            .filter { it.reason is NewReason.Plays && it.album.id !in used }
            .sortedByDescending { (it.reason as NewReason.Plays).plays }
            .take(MAX_YOURS)
            .toList()
        if (yours.isNotEmpty()) {
            sections += YourArtistsSection(yours)
            used += yours.map { it.album.id }
        }

        val adjacent = releases.asSequence()
            .filter { it.reason is NewReason.Like && it.album.id !in used }
            .take(MAX_ADJACENT)
            .toList()
        if (adjacent.isNotEmpty()) {
            sections += AdjacentSection(adjacent)
            used += adjacent.map { it.album.id }
        }

        // The broad shelf opens wider when nothing above it was personal — including the lead,
        // which may itself be the listener's only release — so a page Shiny cannot personalise
        // is still a full page rather than a short apology.
        val hasPersonal = yours.isNotEmpty() || adjacent.isNotEmpty() ||
            (featured != null && featured.reason != NewReason.Fresh)
        val freshLimit = if (hasPersonal) MAX_FRESH_PERSONAL else MAX_FRESH_UNKNOWN
        val fresh = releases.asSequence()
            .filter { it.album.id !in used }
            .map { it.album }
            .take(freshLimit)
            .toList()
        if (fresh.isNotEmpty()) sections += FreshSection(fresh)

        chartsFor(remote, ctx).takeIf { it.isNotEmpty() }?.let {
            sections += NewChartsSection(it, remote.chartsAt)
        }

        return NewFeed(sections, profile, ctx.online, remote.exploreAt)
    }

    /**
     * Every release Shiny can show, each tagged with its distance from the listener, in
     * YouTube's own order.
     *
     * Albums the listener has already heard are dropped: New is for what they have *not*
     * heard. An artist's own newest release is folded in from their cached page, so an album
     * that never reached the global Explore shelf still surfaces for the person who plays them.
     */
    private fun classify(signals: HomeSignals, remote: HomeRemote, ctx: HomeContext): List<NewRelease> {
        val byId = signals.artistStats.associateBy { it.artistId }
        val byName = signals.artistStats.mapNotNull { st ->
            signals.artists[st.artistId]?.artist?.name?.lowercase()?.let { it to st }
        }.toMap()
        val heardAlbums = signals.albumStats.map { it.albumId }.toSet()
        val listened = signals.artistStats.map { it.artistId }.toSet()
        val year = LocalDateTime.ofInstant(Instant.ofEpochMilli(ctx.nowMs), ZoneOffset.UTC).year

        // Artists YouTube lists beside the listener's own, whom they have never played, each
        // remembering which of their artists pulled them in.
        val adjacentById = HashMap<String, String>()
        val adjacentByName = HashMap<String, String>()
        HomeFeedBuilder.topArtists(signals).forEach { st ->
            val digest = remote.artists[st.artistId] ?: return@forEach
            digest.related.forEach { related ->
                if (related.id in listened) return@forEach
                adjacentById.putIfAbsent(related.id, digest.name)
                adjacentByName.putIfAbsent(related.title.lowercase(), digest.name)
            }
        }

        fun showable(album: AlbumItem) = album.id !in heardAlbums && !(ctx.hideExplicit && album.explicit)

        fun reasonFor(album: AlbumItem): NewReason {
            album.artists.orEmpty().forEach { a ->
                val stat = a.id?.let(byId::get) ?: byName[a.name.lowercase()]
                if (stat != null) return NewReason.Plays(a.name, stat.plays)
            }
            album.artists.orEmpty().forEach { a ->
                val seed = a.id?.let(adjacentById::get) ?: adjacentByName[a.name.lowercase()]
                if (seed != null) return NewReason.Like(a.name, seed)
            }
            return NewReason.Fresh
        }

        val global = remote.newReleases.filter(::showable).map { NewRelease(it, reasonFor(it)) }

        // An artist's newest release from their own page, when it came out this year. The
        // global shelf is one page of the catalogue; this is how a release by somebody the
        // listener plays reaches them even when it never made that page.
        // Only artists actually played: a "Plays" reason for a saved-only artist would say 0.
        val fromDigests = HomeFeedBuilder.topArtists(signals).filter { it.plays > 0 }.mapNotNull { st ->
            val digest = remote.artists[st.artistId] ?: return@mapNotNull null
            val latest = digest.latest?.takeIf { showable(it) && it.year == year } ?: return@mapNotNull null
            NewRelease(latest, NewReason.Plays(digest.name, st.plays))
        }

        return (fromDigests + global).distinctBy { it.album.id }
    }

    /**
     * The one release the page leads with.
     *
     * Not the newest, and not the first of a list: the best of three things Shiny can measure
     * — how much the listener plays the artist, whether the release is actually of this year,
     * and how prominently YouTube itself is placing it.
     *
     * Only a *tie* is broken by the daypart. Releases within [FEATURE_TIE] of the best score
     * are rotated by the seed, so a page where several releases are equally good does not
     * open on the same album every time; a release that clearly wins always leads, because a
     * lead that changed while the reason for it did not would be a lie about the ranking.
     */
    private fun feature(releases: List<NewRelease>, ctx: HomeContext): NewRelease? {
        if (releases.isEmpty()) return null
        val year = LocalDateTime.ofInstant(Instant.ofEpochMilli(ctx.nowMs), ZoneOffset.UTC).year
        val total = releases.size
        val ranked = releases
            .mapIndexed { index, release ->
                release to featureScore(
                    rank = index,
                    total = total,
                    reason = release.reason,
                    thisYear = release.album.year == year,
                )
            }
            .sortedByDescending { it.second }
        val best = ranked.first().second
        val tied = ranked.takeWhile { it.second >= best - FEATURE_TIE }.take(FEATURE_POOL).map { it.first }
        return HomeRanking.rotateHead(tied, tied.size, ctx.seed).firstOrNull()
    }

    /**
     * How strongly one release deserves the top of the page.
     *
     * Affinity dominates — a release by somebody the listener plays daily should beat a
     * heavily promoted one by a stranger — but prominence still separates the strangers from
     * each other, so a page with no history is not ordered by nothing.
     */
    fun featureScore(rank: Int, total: Int, reason: NewReason, thisYear: Boolean): Double {
        val prominence = if (total <= 1) 1.0 else 1.0 - rank.toDouble() / total
        val affinity = when (reason) {
            is NewReason.Plays -> 1.6 * ln(1.0 + reason.plays.coerceAtLeast(0))
            is NewReason.Like -> 0.7
            NewReason.Fresh -> 0.0
        }
        return affinity + (if (thisYear) 0.6 else 0.0) + prominence * 0.9
    }
}
