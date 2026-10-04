package com.shiny.music.ui.liquid.settings

/**
 * Shiny's release notes, shipped inside the app.
 *
 * What's New used to download `changelog.json` from the GitHub release for the running tag.
 * That repository is not publicly reachable, so the screen could only ever show its error.
 * The notes now travel with the build, so they are always there, offline included.
 *
 * Every line has to describe something that is in the build. 1.2.4 is written from the
 * commits since 1.2.2 (appearance settings, the foundation rebuild with social features, the
 * startup and playback work) and the changes made on top of them; 1.2.2 is RELEASE_INFO.md.
 */
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

val ShinyReleases: List<Release> = listOf(
    Release(
        version = "1.2.4",
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
    Release(
        version = "1.2.2",
        date = "August 28, 2026",
        summary = null,
        improved = listOf(
            "Input fields and dialog buttons, including in Spotify Import, have a more rounded, modern look.",
            "Some app components were updated to their latest stable versions for better reliability.",
        ),
        fixed = listOf(
            "A crash when adding a song to a playlist, album or artist before it had fully loaded.",
            "A crash caused by outdated saved settings after an update. Shiny now falls back to a safe default.",
            "Spotify sign-in with Google, Apple or Facebook could show a black screen or fail to finish.",
            "The Update Available dialog didn't match the rest of the app.",
        ),
    ),
)
