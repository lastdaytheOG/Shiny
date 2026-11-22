package com.shiny.music.ui.liquid.customize

import androidx.datastore.preferences.core.stringSetPreferencesKey

enum class HomePart(val title: String, private val library: Boolean = false) {
    Top("Top of Home"),
    LikedSongs("Liked Songs", library = true),
    Downloads("Downloads", library = true),
    OnDevice("On This Device", library = true),
    RecentlyAdded("Recently Added", library = true),
    RecentlyPlayed("Recently Played"),
    MostPlayed("Most Played"),
    Picks("Recommendations"),
    Community("From the community"),
    JumpBackIn("Jump Back In"),
    History("Listening history"),
    LiveShows("Live Shows"),
    Artists("Artist List"),
    Playlists("Your playlists"),
    DontForget("Don't forget these songs"),
    MusicVideos("Music Videos for You"),
    YouTube("YouTube Music shelves"),
    Hits("All-time hits");

    fun onByDefault(style: HomeStyle): Boolean = !library || style == HomeStyle.HopIn

    fun shownWith(changed: Set<String>, style: HomeStyle): Boolean = onByDefault(style) != (name in changed)

    fun label(style: HomeStyle): String = when {
        this == Top -> style.title
        this == History && style == HomeStyle.HopIn -> "History"
        else -> title
    }
}

object HomeLayoutPrefs {
    fun changed(style: HomeStyle) = stringSetPreferencesKey("customizeHomeChanged_${style.name}")
}
