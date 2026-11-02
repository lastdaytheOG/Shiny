package com.shiny.music.ui.liquid.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.shiny.music.R
import com.shiny.music.db.entities.Song
import com.shiny.music.home.HomeLive
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.utils.resize

private fun line(title: String, artists: String) = if (artists.isBlank()) title else "$title - $artists"

private fun Song.card(key: String, label: String, actions: LiquidActions) = HeroCard(
    key = key,
    label = label,
    labelCover = null,
    labelName = null,
    roundCover = false,
    title = line(title, artists.joinToString { it.name }),
    art = thumbnailUrl?.resize(1200, 1200),
    mediaId = id,
    play = { actions.open(this) },
)

private fun YTItem.card(key: String, label: String, cover: String?, name: String?, round: Boolean, actions: LiquidActions) = HeroCard(
    key = key,
    label = label,
    labelCover = cover,
    labelName = name,
    roundCover = round,
    title = line(title, (this as? SongItem)?.artists?.joinToString { it.name }.orEmpty()),
    art = thumbnail?.resize(1200, 1200),
    mediaId = id,
    play = { actions.open(this) },
)

// the slides, in order: the last song, then picks grown from your songs taking turns with your
// own artists, albums and favourites, topped up with what you played lately. a new install
// starts from the artists in the library
@Composable
internal fun rememberHeroCards(live: HomeLive, actions: LiquidActions): List<HeroCard> {
    val sContinue = stringResource(R.string.home_hero_continue)
    val sRecent = stringResource(R.string.home_hero_recent)
    val sRepeat = stringResource(R.string.home_kicker_on_repeat)
    val sBack = stringResource(R.string.home_hero_back)
    val sMayLike = stringResource(R.string.home_hero_may_like)
    val sLike = stringResource(R.string.home_kicker_because_like)
    val sPlayed = stringResource(R.string.home_hero_played)
    val sInspired = stringResource(R.string.home_hero_inspired)
    val sListen = stringResource(R.string.home_kicker_listen)
    val sSimilar = stringResource(R.string.home_kicker_similar)
    return remember(live.lastPlayed, live.discover, live.similar, live.mostPlayed.firstOrNull(), live.forgotten.firstOrNull(), live.keepListening, live.starter) {
        buildList {
            live.lastPlayed?.let { add(it.card("last_${it.id}", sContinue, actions)) }

            val discover = live.discover.take(4).mapIndexed { i, d ->
                val seed = d.seed
                val (label, name) = when (i % 4) {
                    0 -> sLike to seed.title
                    1 -> sMayLike to null
                    2 -> sPlayed to seed.title
                    else -> sInspired to seed.title
                }
                d.pick.card("discover_${d.pick.id}", label, if (name != null) seed.thumbnailUrl else null, name, false, actions)
            }
            val extras = buildList {
                live.similar.sortedBy { if (it.seedArtist != null) 0 else 1 }.take(4).forEach { row ->
                    val pick = row.items.firstOrNull { it is SongItem && !it.thumbnail.isNullOrEmpty() } ?: row.items.firstOrNull { !it.thumbnail.isNullOrEmpty() } ?: return@forEach
                    val card = when {
                        row.seedArtist != null -> pick.card("similar_${pick.id}", sListen, row.seedArtist.thumbnailUrl, row.seedArtist.title, true, actions)
                        row.seedAlbum != null -> pick.card("similar_${pick.id}", sPlayed, row.seedAlbum.thumbnailUrl, row.seedAlbum.title, false, actions)
                        else -> pick.card("similar_${pick.id}", sSimilar, row.seedSong?.thumbnailUrl, row.seedSong?.title, false, actions)
                    }
                    add(card)
                }
                live.mostPlayed.firstOrNull { !it.thumbnailUrl.isNullOrEmpty() }?.let { add(it.card("repeat_${it.id}", sRepeat, actions)) }
                live.forgotten.firstOrNull { !it.thumbnailUrl.isNullOrEmpty() }?.let { add(it.card("back_${it.id}", sBack, actions)) }
            }
            // a pick already on the hero isn't put forward twice
            val shown = HashSet<String>()
            live.lastPlayed?.id?.let(shown::add)
            val a = ArrayDeque(discover)
            val b = ArrayDeque(extras)
            while (a.isNotEmpty() || b.isNotEmpty()) {
                listOfNotNull(a.removeFirstOrNull(), b.removeFirstOrNull()).forEach { c -> if (c.mediaId == null || shown.add(c.mediaId)) add(c) }
            }
            if (size < 5) {
                live.keepListening.filter { s -> none { it.mediaId == s.id } }.take(5 - size).forEach { add(it.card("recent_${it.id}", sRecent, actions)) }
            }
            if (isEmpty()) {
                live.starter.take(5).forEach { st ->
                    add(st.song.card("starter_${st.song.id}", sListen, st.artistPhoto, st.artistName, true, actions))
                }
            }
        }
    }
}

// what the random plays stack spins through: your own songs and the hero's picks
@Composable
internal fun rememberGachaPool(live: HomeLive, actions: LiquidActions): List<HeroCard> =
    remember(live.mostPlayed, live.forgotten, live.keepListening, live.history, live.discover) {
        val own = (live.mostPlayed + live.keepListening + live.forgotten + live.history).distinctBy { it.id }.filter { !it.thumbnailUrl.isNullOrEmpty() }
        val cards = own.map { it.card("gacha_${it.id}", "", actions) } +
            live.discover.map { it.pick.card("gacha_${it.pick.id}", "", null, null, false, actions) }
        cards.distinctBy { it.mediaId }.shuffled().take(10)
    }
