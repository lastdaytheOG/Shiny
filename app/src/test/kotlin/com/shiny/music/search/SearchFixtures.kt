package com.shiny.music.search

import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_PODCAST_EPISODE
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import com.music.innertube.models.YTItem
import com.music.innertube.pages.SearchSummary
import com.music.innertube.pages.SearchSummaryPage

internal fun artistId(name: String) = "UC-" + name.lowercase().replace(' ', '-')

internal fun song(
    title: String,
    vararg artists: String,
    id: String = "v-$title-${artists.joinToString()}",
    type: String? = MUSIC_VIDEO_TYPE_ATV,
    album: String? = null,
) = SongItem(
    id = id,
    title = title,
    artists = artists.map { Artist(name = it, id = artistId(it)) },
    album = album?.let { Album(name = it, id = "MPRE-$it") },
    musicVideoType = type,
    thumbnail = "https://i.ytimg.com/$id.jpg",
)

internal fun video(title: String, uploader: String, id: String = "u-$title-$uploader") =
    SongItem(id = id, title = title, artists = listOf(Artist(uploader, null)), musicVideoType = MUSIC_VIDEO_TYPE_UGC, thumbnail = "t")

internal fun musicVideo(title: String, vararg artists: String) = song(title, *artists, id = "m-$title", type = MUSIC_VIDEO_TYPE_OMV)

internal fun episode(title: String, published: String = "Feb 6, 2019") =
    SongItem(id = "e-$title", title = title, artists = listOf(Artist(published, null)), musicVideoType = MUSIC_VIDEO_TYPE_PODCAST_EPISODE, thumbnail = "t")

internal fun album(title: String, vararg artists: String, list: String = "OLAK-$title") = AlbumItem(
    browseId = "MPRE-$title",
    playlistId = list,
    title = title,
    artists = artists.map { Artist(it, artistId(it)) },
    thumbnail = "t",
)

internal fun artist(name: String) = ArtistItem(id = artistId(name), title = name, thumbnail = "t", shuffleEndpoint = null, radioEndpoint = null)

internal fun playlist(title: String, author: String?, id: String = "PL-$title") = PlaylistItem(
    id = id,
    title = title,
    author = author?.let { Artist(it, null) },
    songCountText = null,
    thumbnail = null,
    playEndpoint = null,
    shuffleEndpoint = null,
    radioEndpoint = null,
)

internal fun page(vararg sections: Pair<String, List<YTItem>>, corrected: String? = null) =
    SearchSummaryPage(sections.map { (title, items) -> SearchSummary(title, items) }, corrected)

internal fun RankedSummary.all(): List<YTItem> = listOfNotNull(top) + sections.flatMap { it.items }

internal fun RankedSummary.shelf(shelf: SearchShelf): List<YTItem> = sections.firstOrNull { it.shelf == shelf }?.items.orEmpty()

internal fun List<YTItem>.titles() = map { it.title }

const val TERRANOVA = "Terranova (Hardtekk) (Slowed)"

internal val terranovaPage = page(
    "Top result" to listOf(song("terranova (Hardtekk) (super slowed)", "treit")),
    "Songs" to listOf(
        song("Terranova (Original Radio Mix)", "Janina"),
        song("HERO HARDTEKK (Ultra Slowed)", "Riversmelt", "Skyper", "H2HELL", "DJ FYP"),
        song("terranova (Hardtekk) (slowed)", "treit"),
        song("Terranova (Slowed Down Version)", "Janina"),
        song("Terranova (Hardstyle) (Super Slowed)", "puremind"),
        song("MONTAGEM NOCHE", "LUMIX", "KXRSED"),
    ),
    "Videos" to listOf(
        video("terranova x hardtekk (slowed)", "Real_F22"),
        video("Terranova (hardtekk) Slowed", "not my main"),
        video("Last summer - Hardtekk (𝙨𝙪𝙥𝙚𝙧 𝙨𝙡𝙤𝙬𝙚𝙙)", "Reverb"),
        episode("What Aphria Financials Mean for Investors"),
        episode("Two of the largest gold projects in Canada are moving ahead", "Jul 19, 2022"),
        video("Hard Techno Is Not A Crime ØTJE · GEWOONRAVES", "Stern Radio Rochlitz"),
        video("What Aphria Financials Mean for Investors", "Money Professor"),
        video("Two of the largest gold projects in Canada", "Terranova Resources"),
    ),
    "Albums" to listOf(
        album("terranova (Hardtekk)", "treit"),
        album("Terranova Hardtekk", "OneLastLight"),
        album("TERRANOVA (HARDTEKK)", "AvantTEKK"),
    ),
    "Playlists" to listOf(playlist("Ultra slowed/cool/funk", "trey"), playlist("HARDTEKK LARP", "L3X3R")),
    "Artists" to listOf(artist("treit"), artist("Janina")),
)
