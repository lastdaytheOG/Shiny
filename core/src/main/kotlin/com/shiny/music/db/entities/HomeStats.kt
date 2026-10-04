package com.shiny.music.db.entities

/*
 * Plain projections of the listen history for Home. They carry ids and numbers only; Home
 * loads the full `Song` relations for the handful of rows it actually shows.
 *
 * Every timestamp here comes from the `event` table, which stores the device's wall-clock
 * time as if it were UTC (see `SongLastPlayed`). Windows are therefore computed from
 * `LocalDateTime.now()` read the same way, and the hour of day of a play is simply
 * `(timestamp / 3_600_000) % 24`.
 */

/** One song's listening across all of its counted plays. */
data class HomeSongStat(
    val songId: String,
    val plays: Int,
    val playTime: Long,
    val firstPlayed: Long,
    val lastPlayed: Long,
    val weekPlays: Int,
    val weekPlayTime: Long,
    val monthPlays: Int,
    /** Plays inside the current part of the day, over the daypart window. */
    val daypartPlays: Int,
)

/** One artist's listening, over every song credited to them. */
data class HomeArtistStat(
    val artistId: String,
    val plays: Int,
    val weekPlays: Int,
    val monthPlays: Int,
    val lastPlayed: Long,
    val songs: Int,
)

/** How much of an album has been heard: distinct songs played, and when last. */
data class HomeAlbumStat(
    val albumId: String,
    val songsPlayed: Int,
    val plays: Int,
    val lastPlayed: Long,
)

/** An edge of the related-song graph `MusicService` stores while songs play. */
data class HomeRelatedLink(
    val seedId: String,
    val songId: String,
)

/** What the library holds, in one read. */
data class HomeLibraryCounts(
    val localSongs: Int,
    val downloadedSongs: Int,
    val likedSongs: Int,
    val librarySongs: Int,
    val events: Int,
    val playlists: Int,
    /**
     * Distinct searches ever made. `search_history` is unique on the query text and holds
     * no timestamps, so this is the only thing it can honestly say: how much this person
     * goes looking. Home reads it as a second, play-independent measure of exploring —
     * someone who searches constantly and plays little is still exploring.
     */
    val searches: Int = 0,
)

/**
 * A song the listener chose without (necessarily) playing it: liked, saved to the library,
 * in one of their playlists, or in one of their Spotify mixes. Only streamable songs.
 */
data class HomeSavedSong(
    val songId: String,
    val liked: Boolean,
    val inLibrary: Boolean,
    val inPlaylist: Boolean,
    val inMix: Boolean,
    /** Among the listener's most played on Spotify (`SPOTIFY_TOP_…`). */
    val inTop: Boolean = false,
)

/** An artist across the songs the listener has chosen, played or not, local files included. */
data class HomeSavedArtist(
    val artistId: String,
    val name: String,
    val songs: Int,
    val liked: Int,
)
