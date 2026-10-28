package com.shiny.music.search

import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_PODCAST_EPISODE
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK
import com.music.innertube.models.YTItem

enum class SearchKind(val weight: Double, val official: Boolean, val needsTitleEvidence: Boolean) {
    Song(1.0, official = true, needsTitleEvidence = false),
    MusicVideo(0.8, official = true, needsTitleEvidence = false),
    Video(0.4, official = false, needsTitleEvidence = true),
    Album(0.8, official = true, needsTitleEvidence = false),
    Artist(1.0, official = true, needsTitleEvidence = false),
    Playlist(0.5, official = false, needsTitleEvidence = true),
    Unsupported(0.0, official = false, needsTitleEvidence = true);

    val shelf: SearchShelf?
        get() = when (this) {
            Song -> SearchShelf.Songs
            MusicVideo, Video -> SearchShelf.Videos
            Album -> SearchShelf.Albums
            Artist -> SearchShelf.Artists
            Playlist -> SearchShelf.Playlists
            Unsupported -> null
        }
}

enum class SearchShelf { Songs, Videos, Albums, Artists, Playlists }

fun YTItem.searchKind(): SearchKind = when (this) {
    is SongItem -> when (musicVideoType) {
        null, MUSIC_VIDEO_TYPE_ATV, MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK -> SearchKind.Song
        MUSIC_VIDEO_TYPE_OMV, MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC -> SearchKind.MusicVideo
        MUSIC_VIDEO_TYPE_PODCAST_EPISODE -> SearchKind.Unsupported
        else -> SearchKind.Video
    }
    is AlbumItem -> SearchKind.Album
    is ArtistItem -> SearchKind.Artist
    is PlaylistItem -> SearchKind.Playlist
}

fun YouTube.SearchFilter.kinds(): Set<SearchKind> = when (value) {
    YouTube.SearchFilter.FILTER_SONG.value -> setOf(SearchKind.Song)
    YouTube.SearchFilter.FILTER_VIDEO.value -> setOf(SearchKind.MusicVideo, SearchKind.Video)
    YouTube.SearchFilter.FILTER_ALBUM.value -> setOf(SearchKind.Album)
    YouTube.SearchFilter.FILTER_ARTIST.value -> setOf(SearchKind.Artist)
    YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST.value,
    YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST.value -> setOf(SearchKind.Playlist)
    else -> SearchKind.entries.toSet() - SearchKind.Unsupported
}
