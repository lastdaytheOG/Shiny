package com.shiny.music.home

enum class HomeSlot {
    OfflineNotice,
    Opening,
    Lately,
    Continue,
    Rotation,
    AroundNow,
    Discover,
    DeepDive,
    Spotify,
    YouTube,
    Rediscover,
    PickArtists,
    DiscoveryMix,
    Shortcuts,
    MoreLikeFirst,
    MoreLikeSecond,
    DeepDiveSecond,
    Pinned,
    RecentArtists,
}

private enum class Band { Opening, Shortcuts, Pinned, Discoveries, Immediate, Services, Core, Library, Artists }

/**
 * Sections move only within a band, so Home always opens on something to play, then the
 * listener's shortcuts and pins, then Fresh Discoveries, then what was just playing, then their
 * own services, then the body of the feed, with the artists they played last at the foot.
 * Within a band, `+ c` terms earn their place as Shiny learns and `- c` terms hold one until
 * something personal can take it.
 */
object HomeOrder {

    private fun band(slot: HomeSlot): Band = when (slot) {
        HomeSlot.OfflineNotice, HomeSlot.Opening, HomeSlot.DiscoveryMix -> Band.Opening
        HomeSlot.Shortcuts -> Band.Shortcuts
        HomeSlot.Pinned -> Band.Pinned
        HomeSlot.Discover -> Band.Discoveries
        HomeSlot.Lately, HomeSlot.Continue, HomeSlot.PickArtists -> Band.Immediate
        HomeSlot.Rotation, HomeSlot.AroundNow,
        HomeSlot.DeepDive, HomeSlot.DeepDiveSecond, HomeSlot.MoreLikeFirst, HomeSlot.MoreLikeSecond,
        -> Band.Core
        HomeSlot.Spotify, HomeSlot.YouTube -> Band.Services
        HomeSlot.Rediscover -> Band.Library
        HomeSlot.RecentArtists -> Band.Artists
    }

    private fun score(slot: HomeSlot, ctx: HomeContext, taste: HomeTasteProfile): Double {
        val c = taste.confidence
        val base = when (slot) {
            HomeSlot.OfflineNotice -> 100.0
            HomeSlot.Opening -> 90.0
            HomeSlot.DiscoveryMix -> 80.0
            HomeSlot.Shortcuts, HomeSlot.Pinned, HomeSlot.Discover, HomeSlot.RecentArtists -> 0.0
            HomeSlot.Lately -> 60.0
            HomeSlot.Continue -> 50.0
            HomeSlot.PickArtists -> 40.0
            HomeSlot.Rotation -> 34.0 + 40.0 * c + 22.0 * taste.artistLean
            HomeSlot.AroundNow -> 25.0 + 38.0 * c + 30.0 * taste.habitLean
            HomeSlot.DeepDive -> 18.0 + 45.0 * c + 25.0 * taste.artistLean
            HomeSlot.DeepDiveSecond -> 10.0 + 45.0 * c + 25.0 * taste.artistLean
            HomeSlot.MoreLikeFirst -> 39.0 + 15.0 * c + 24.0 * taste.exploreLean + 28.0 * taste.discoveryLean
            HomeSlot.MoreLikeSecond -> 30.0 + 15.0 * c + 24.0 * taste.exploreLean + 28.0 * taste.discoveryLean
            HomeSlot.Spotify -> 70.0
            HomeSlot.YouTube -> 60.0
            HomeSlot.Rediscover -> 40.0 + 25.0 * c
        }
        return base + daypartNudge(slot, ctx.daypart) + jitter(slot, ctx.seed)
    }

    private fun daypartNudge(slot: HomeSlot, daypart: Daypart): Double = when (daypart) {
        Daypart.Morning -> when (slot) {
            HomeSlot.Rotation -> 12.0
            HomeSlot.AroundNow -> 10.0
            HomeSlot.DeepDive -> -12.0
            else -> 0.0
        }

        Daypart.Afternoon -> when (slot) {
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
            HomeSlot.Rediscover -> 12.0
            else -> 0.0
        }
    }

    private fun jitter(slot: HomeSlot, seed: Long): Double =
        (Math.floorMod(seed * 31 + slot.ordinal * 7919L, 41L) - 20) / 10.0

    fun order(present: Collection<HomeSlot>, ctx: HomeContext, taste: HomeTasteProfile): List<HomeSlot> =
        present.distinct()
            .sortedWith(
                compareBy<HomeSlot> { band(it).ordinal }
                    .thenByDescending { score(it, ctx, taste) }
                    .thenBy { it.ordinal }
            )
}
