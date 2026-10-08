package com.shiny.music.home

/** Every place a section can take on Home, in the order they are *declared*, not shown. */
enum class HomeSlot {
    OfflineNotice,
    Opening,
    Lately,
    Continue,
    Rotation,
    AroundNow,
    Moods,
    Discover,
    DeepDive,
    Charts,
    Spotify,
    YouTube,
    Rediscover,
    AlbumsInProgress,
    RecentlyAdded,
    ReadyOffline,
    Insights,
    Surprise,
}

/**
 * A band is a fixed piece of Home's editorial shape. Sections move freely *within* a
 * band and never between them, which is what keeps an adaptive page from becoming a
 * chaotic one: however the listener behaves, Home still opens on something to play,
 * then what was just playing, then the body of the feed, then the library, then the
 * quiet closing notes.
 */
private enum class Band { Opening, Immediate, Core, Library, Closing }

/**
 * The order of Home, scored rather than listed.
 *
 * The old Home chose between five hardcoded orders. This one gives every section a score
 * from the moment and the listener, and sorts. Two forces run through the scores:
 *
 * - **Confidence trades one kind of section for another.** The editorial sections — the
 *   charts and moods — start high and are *pushed down by* confidence, while
 *   the personal ones are *lifted by* it. Nothing unlocks: a new listener opens on the
 *   charts because the charts are the best thing Shiny can honestly show them, and the
 *   rotation overtakes the charts somewhere around the point where there is a rotation
 *   worth the name. The handover is continuous and takes weeks, not one play.
 *
 * - **Behaviour outranks assumptions.** Someone who opens the charts every day keeps them
 *   near the top forever, because [HomeTasteProfile.chartLean] does not fade while they
 *   keep doing it. The same is true of moods, albums and downloads.
 *
 * The result is deterministic for a given listener and moment — the same Home twice in a
 * row, not a reshuffle on every open — with one small seeded jitter that is far too small
 * to cross the gaps between bands.
 */
object HomeOrder {

    private fun band(slot: HomeSlot, taste: HomeTasteProfile): Band = when (slot) {
        HomeSlot.OfflineNotice, HomeSlot.Opening -> Band.Opening
        HomeSlot.Lately, HomeSlot.Continue -> Band.Immediate
        HomeSlot.Rotation, HomeSlot.AroundNow, HomeSlot.Moods, HomeSlot.Discover,
        HomeSlot.DeepDive, HomeSlot.Charts, HomeSlot.Spotify, HomeSlot.YouTube,
        -> Band.Core

        // Downloads and device files are the body of the feed for someone who mostly
        // plays them, and a footnote for everyone else.
        HomeSlot.ReadyOffline -> if (taste.offlineLean >= 0.55) Band.Core else Band.Library
        HomeSlot.Rediscover, HomeSlot.AlbumsInProgress, HomeSlot.RecentlyAdded -> Band.Library
        HomeSlot.Insights, HomeSlot.Surprise -> Band.Closing
    }

    /**
     * How much each section is worth here and now. Higher is earlier.
     *
     * `c` is confidence. Read the signs: a `+ c` term is a section that earns its place as
     * Shiny learns, a `- c` term is one that is holding a place until something personal
     * can take it.
     */
    private fun score(slot: HomeSlot, ctx: HomeContext, taste: HomeTasteProfile): Double {
        val c = taste.confidence
        val daypart = daypartNudge(slot, ctx.daypart)
        val base = when (slot) {
            // Band.Opening and Band.Immediate are ordered by their own logic, not scored.
            HomeSlot.OfflineNotice -> 100.0
            HomeSlot.Opening -> 90.0
            HomeSlot.Continue -> 60.0
            HomeSlot.Lately -> 50.0

            // ---- Core: the handover ----------------------------------------------------
            // What the listener keeps returning to, once there is such a thing.
            HomeSlot.Rotation -> 34.0 + 40.0 * c + 22.0 * taste.artistLean
            // Their pattern at this hour, which needs several days at this hour to exist.
            HomeSlot.AroundNow -> 25.0 + 38.0 * c + 30.0 * taste.habitLean
            // Discovery rises with how much they reach for it, and never falls to nothing.
            HomeSlot.Discover -> 42.0 + 15.0 * c + 24.0 * taste.exploreLean + 28.0 * taste.discoveryLean + 14.0 * taste.searchLean
            // One of their artists, opened up — only worth the room once an artist is real.
            HomeSlot.DeepDive -> 18.0 + 45.0 * c + 25.0 * taste.artistLean
            // The charts hold the top of a new Home, and stay for whoever keeps opening them.
            HomeSlot.Charts -> 58.0 - 30.0 * c + 45.0 * taste.chartLean
            // Somewhere to start when Shiny cannot say where; kept by mood-led listeners.
            HomeSlot.Moods -> 46.0 - 26.0 * c + 45.0 * taste.moodLean + 10.0 * taste.searchLean
            // The listener's own services — their liked songs, then the mixes each service made
            // for them. They lead while Shiny learns, and never sink far: the likes are the
            // listener's library, not a suggestion. Spotify and YouTube Music stay two rows.
            HomeSlot.Spotify -> 70.0 - 20.0 * c
            HomeSlot.YouTube -> 69.0 - 20.0 * c
            // Promoted into Core by offlineLean (see band()), so it is scored to lead there.
            HomeSlot.ReadyOffline -> 20.0 + 60.0 * taste.offlineLean

            HomeSlot.Rediscover -> 40.0 + 25.0 * c
            HomeSlot.AlbumsInProgress -> 35.0 + 40.0 * taste.albumLean
            HomeSlot.RecentlyAdded -> 38.0 + 20.0 * taste.offlineLean

            HomeSlot.Insights -> 40.0 + 20.0 * taste.activeLean
            HomeSlot.Surprise -> 30.0 + 30.0 * taste.discoveryLean
        }
        return base + daypart + jitter(slot, ctx.seed)
    }

    /**
     * The day's own shape, kept from the hand-written orders it replaces: mornings open on
     * the familiar, afternoons lean out into new music, evenings and nights return to what
     * the listener knows. Small enough that a strong habit always outweighs the hour.
     */
    private fun daypartNudge(slot: HomeSlot, daypart: Daypart): Double = when (daypart) {
        Daypart.Morning -> when (slot) {
            HomeSlot.Rotation -> 12.0
            HomeSlot.AroundNow -> 10.0
            HomeSlot.Discover -> -10.0
            HomeSlot.DeepDive -> -12.0
            else -> 0.0
        }

        Daypart.Afternoon -> when (slot) {
            HomeSlot.Discover -> 18.0
            HomeSlot.Rotation, HomeSlot.AroundNow -> -6.0
            else -> 0.0
        }

        Daypart.Evening -> when (slot) {
            HomeSlot.AroundNow -> 14.0
            HomeSlot.DeepDive -> 8.0
            else -> 0.0
        }

        Daypart.Night -> when (slot) {
            HomeSlot.AroundNow, HomeSlot.Rotation -> 10.0
            HomeSlot.Rediscover, HomeSlot.Surprise -> 12.0
            HomeSlot.Charts -> -6.0
            else -> 0.0
        }
    }

    /**
     * The controlled variety the feed has always had, as a number instead of a swap: ±2,
     * fixed per daypart, enough to trade places between two sections that were nearly
     * level and never enough to move one past a section that clearly outranks it.
     */
    private fun jitter(slot: HomeSlot, seed: Long): Double =
        (Math.floorMod(seed * 31 + slot.ordinal * 7919L, 41L) - 20) / 10.0

    /**
     * [present] ordered for this moment.
     *
     * Offline is not a listener type but a constraint, so it is applied here rather than
     * scored: nothing that needs the network can lead a Home that has none.
     */
    fun order(present: Collection<HomeSlot>, ctx: HomeContext, taste: HomeTasteProfile): List<HomeSlot> =
        present.distinct()
            .sortedWith(
                compareBy<HomeSlot> { band(it, taste).ordinal }
                    .thenByDescending { score(it, ctx, taste) }
                    .thenBy { it.ordinal }
            )
}
