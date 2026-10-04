package com.shiny.music.db.entities

/** One month of a song's counted plays, as the `playCount` table keeps them (device calendar months). */
data class SongMonthPlays(
    val songId: String,
    val year: Int,
    val month: Int,
    val count: Int,
)

/**
 * When a song was last counted as played, from the `event` table: the device's wall-clock
 * time stored as if it were UTC, as the timestamp converter writes `LocalDateTime.now()`.
 */
data class SongLastPlayed(
    val songId: String,
    val lastPlayed: Long,
)
