package com.shiny.music.ui.liquid.settings

data class Release(
    val version: String,
    val date: String?,
    val summary: String?,
    val featured: List<Highlight> = emptyList(),
    val new: List<String> = emptyList(),
    val improved: List<String> = emptyList(),
    val fixed: List<String> = emptyList(),
)

data class Highlight(val title: String, val text: String)

/**
 * Release history shipped with the app. The list is sorted here so adding a future release
 * cannot accidentally put an older release above it in What's New.
 */
val ShinyReleases: List<Release> = sortReleaseHistory(listOf(
    Release(
        version = "1.2.7",
        date = null,
        summary = "A calmer, roomier queue, and playlists that play all the way through.",
        new = listOf(
            "Redesigned Up Next: one list with the song playing now highlighted, songs you've played above it and what's next below.",
            "Queue mode gives the list the whole screen on a dark background, with play and pause in the header.",
            "Every song in the queue has a menu with Play Next, Remove from Queue and Clear upcoming songs, and you can undo a removal or a clear.",
        ),
        improved = listOf(
            "Long playlists now load in full, even with Autoplay off, instead of stopping after 50 songs.",
            "Shuffle keeps its order when songs are added, and stays in sync as more of a playlist loads.",
            "Shuffle, Repeat and Autoplay sit together at the top of the queue, with clear labels.",
            "Long queues show their songs a page at a time, so the queue opens quickly.",
            "Reorder songs by long-pressing and dragging them.",
        ),
        fixed = listOf(
            "Songs were sometimes skipped or played twice when you started a playlist while it was still loading.",
            "The queue could show the same songs twice for a moment after you picked a song.",
        ),
    ),
    Release(
        version = "1.2.6",
        date = null,
        summary = "A cleaner Home and more dependable playback controls.",
        improved = listOf(
            "Cleaner Home: the Your Week summary, Surprise Me card, and mood-selection section are gone.",
            "Improved Shuffle: shuffle behavior and queue synchronization are more reliable.",
            "Better Repeat Controls: repeat-mode handling is more consistent while tracks finish or change.",
            "Autoplay Improvements: automatic track selection and playback behavior are more dependable.",
            "Queue Improvements: playback modes and queue controls stay synchronized.",
        ),
    ),
    Release(
        version = "1.2.5",
        date = null,
        summary = "A rebuilt Shiny: a new design on every screen, friends and Discord, and an appearance you can make your own.",
        featured = listOf(
            Highlight(
                title = "A new look, everywhere",
                text = "Home, Library, Search, Now Playing and Settings were rebuilt with one design, " +
                    "from the type to the way pages move.",
            ),
            Highlight(
                title = "Listen with friends",
                text = "Add friends, see what they're playing, share a profile page, and let people " +
                    "listen along from your Discord status.",
            ),
            Highlight(
                title = "Appearance, your way",
                text = "Atmosphere, artwork glow, accent colour, material, page transitions and " +
                    "interface size, with a live preview as you change them.",
            ),
        ),
        new = listOf(
            "Home learns from your very first play, instead of waiting until you've listened to 12 songs.",
            "Charts follow your country, taken from your mobile network. Pick another country or the global chart in Settings → Library & Home.",
            "Smart Shuffle for your downloaded songs.",
            "Music sources: choose which folders count as music. Voice notes, recordings and system sounds are left out.",
            "Choose a download folder, so downloads stop taking up Shiny's own storage.",
            "Export songs on your device as MP3, then share them with any app.",
        ),
        improved = listOf(
            "Shiny opens faster, and songs start playing sooner.",
            "Crossfade waits until the next song is ready, so a transition never stalls part-way.",
            "Song details only show what a song actually has.",
            "Privacy, Downloads & Storage and About are simpler and clearer.",
        ),
        fixed = listOf(
            "Lyrics stay in time after you seek.",
            "Songs you'd already played no longer download again every time Shiny restarts.",
            "Opening a song from another app no longer gets replaced by your previous queue.",
            "The first song of a new queue now gets the next one ready in time.",
        ),
    ),
))

internal fun sortReleaseHistory(releases: List<Release>): List<Release> =
    releases.sortedWith(Comparator { left, right ->
        compareVersionKeys(right.version.releaseVersionKey(), left.version.releaseVersionKey())
    })

private fun String.releaseVersionKey(): List<Int> =
    removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }

private fun compareVersionKeys(left: List<Int>, right: List<Int>): Int {
    val length = maxOf(left.size, right.size)
    for (index in 0 until length) {
        val comparison = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
        if (comparison != 0) return comparison
    }
    return 0
}
