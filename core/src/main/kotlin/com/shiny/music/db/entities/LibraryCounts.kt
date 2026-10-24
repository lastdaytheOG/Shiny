package com.shiny.music.db.entities

data class LibraryCounts(
    val playlists: Int = 0,
    val artists: Int = 0,
    val albums: Int = 0,
    val songs: Int = 0,
    val liked: Int = 0,
    val downloaded: Int = 0,
)
