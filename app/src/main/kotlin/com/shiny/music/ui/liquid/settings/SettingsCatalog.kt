package com.shiny.music.ui.liquid.settings

/**
 * What Shiny's settings search can find.
 *
 * One entry per setting that actually exists, naming the page it lives on. The list it
 * replaces was generated: it indexed dialog body text as if it were a setting, described
 * a third of its entries as "Manage <the setting's own name> settings", and sent fifteen
 * results to `settings/privacy`, a route that was never registered.
 *
 * The rule for this file: an entry may only name a row that is on screen somewhere, and
 * `route` must be a destination in `navigationBuilder`. If a row is removed, its entry
 * goes with it.
 */
data class SettingsEntry(
    val title: String,
    val page: String,
    val route: String,
    val keywords: String = "",
)

fun settingsCatalog(): List<SettingsEntry> = listOf(
    // Playback
    SettingsEntry("Data Saver", "Playback", "settings/player", "mobile data saving offline"),
    SettingsEntry("Volume levelling", "Playback", "settings/player", "normalisation normalization loudness gain"),
    SettingsEntry("Skip silence", "Playback", "settings/player", "quiet gaps"),
    SettingsEntry("Crossfade", "Playback", "settings/player", "fade transition mix"),
    SettingsEntry("Crossfade length", "Playback", "settings/player", "seconds duration"),
    SettingsEntry("Keep album tracks gapless", "Playback", "settings/player", "gapless album"),
    SettingsEntry("Audio offload", "Playback", "settings/player", "battery dsp"),
    SettingsEntry("Prepare the next song", "Playback", "settings/player", "preload buffer cache next"),
    SettingsEntry("Restore the queue", "Playback", "settings/player", "persistent queue resume"),
    SettingsEntry("Remember shuffle and repeat", "Playback", "settings/player", "shuffle repeat"),
    SettingsEntry("Keep shuffle on across queues", "Playback", "settings/player", "shuffle"),
    SettingsEntry("Shuffle the whole playlist", "Playback", "settings/player", "shuffle playlist album"),
    SettingsEntry("Never queue the same song twice", "Playback", "settings/player", "duplicate queue"),
    SettingsEntry("Line up similar music", "Playback", "settings/player", "autoplay similar radio"),
    SettingsEntry("Stop adding songs on Repeat All", "Playback", "settings/player", "repeat load more"),
    SettingsEntry("Skip tracks that fail", "Playback", "settings/player", "error skip"),
    SettingsEntry("Pause when muted", "Playback", "settings/player", "mute volume"),
    SettingsEntry("Resume when Bluetooth connects", "Playback", "settings/player", "bluetooth headphones"),
    SettingsEntry("Stop when Shiny is swiped away", "Playback", "settings/player", "task clear close recents"),
    SettingsEntry("Equaliser", "Playback", "settings/equalizer", "eq bass treble bands presets preamp"),
    SettingsEntry("Sound effects", "Equaliser", "settings/equalizer", "8d audio reverb slowed nightcore speed pitch effects spatial"),

    // Appearance
    SettingsEntry("Experience", "Appearance", "settings/appearance", "preset minimal balanced immersive style look"),
    SettingsEntry("Light or dark", "Appearance", "settings/appearance", "appearance light dark automatic theme mode"),
    SettingsEntry("AMOLED black", "Appearance", "settings/appearance", "amoled oled pure true black theme"),
    SettingsEntry("Accent colour", "Appearance", "settings/appearance", "accent tint colour color theme artwork dynamic monochrome mono graphite custom"),
    SettingsEntry("Artwork", "Appearance", "settings/appearance", "now playing cover square card immersive artwork presentation poster portrait edge to edge"),
    SettingsEntry("Atmosphere", "Appearance", "settings/appearance", "now playing background artwork colour color depth immersive soft"),
    SettingsEntry("Artwork glow", "Appearance", "settings/appearance", "glow bloom halo light ambient cover"),
    SettingsEntry("Material", "Appearance", "settings/appearance", "glass liquid blur frosted clear solid surfaces"),
    SettingsEntry("Artwork motion", "Appearance", "settings/appearance", "living breathing animation cover drift motion"),
    SettingsEntry("Page transitions", "Appearance", "settings/appearance", "animation navigation slide fade instant motion"),
    SettingsEntry("Interface size", "Appearance", "settings/appearance", "density compact dense large comfortable size scale zoom"),
    SettingsEntry("Crop artwork to square", "Appearance", "settings/appearance", "crop cover thumbnail"),
    SettingsEntry("High refresh rate", "Appearance", "settings/appearance", "120hz smooth display"),
    SettingsEntry("Haptics", "Appearance", "settings/appearance", "vibration feedback"),
    SettingsEntry("Reset appearance", "Appearance", "settings/appearance", "restore default original look"),

    // Now Playing
    SettingsEntry("Animated covers", "Now Playing", "settings/now_playing", "canvas motion video artwork"),
    SettingsEntry("Volume slider", "Now Playing", "settings/now_playing", "volume"),
    SettingsEntry("Keep the screen on", "Now Playing", "settings/now_playing", "screen awake"),

    // Library & Home
    SettingsEntry("Open Shiny on", "Library & Home", "settings/library", "default tab start home library"),
    SettingsEntry("Listen Together tab", "Library & Home", "settings/library", "tab bar together"),
    SettingsEntry("Pinned shelf", "Library & Home", "settings/library", "home speed dial pinned"),
    SettingsEntry("Charts", "Library & Home", "settings/library/charts", "home charts country region location top 100 global youtube"),
    SettingsEntry("Global chart too", "Library & Home", "settings/library/charts", "home charts global worldwide"),
    SettingsEntry("Top list length", "Library & Home", "settings/library", "my top songs count"),
    SettingsEntry("Grid size", "Library & Home", "settings/library", "grid tiles large small"),
    SettingsEntry("Swipe a song row", "Library & Home", "settings/library", "swipe queue play next gesture"),
    SettingsEntry("Music sources", "Library & Home", "settings/library/sources", "local device folders whatsapp recordings ringtones scan exclude include"),

    // Downloads & Storage
    SettingsEntry("Download folder", "Downloads & Storage", "settings/storage", "download location folder directory sd card external"),
    SettingsEntry("Downloaded songs", "Downloads & Storage", "settings/storage", "downloads offline size move"),
    SettingsEntry("Song cache", "Downloads & Storage", "settings/storage", "cache limit size"),
    SettingsEntry("Artwork cache", "Downloads & Storage", "settings/storage", "image cache covers"),
    SettingsEntry("Download liked songs", "Downloads & Storage", "settings/storage", "auto download like"),
    SettingsEntry("Remove all downloads", "Downloads & Storage", "settings/storage", "clear delete downloads"),
    SettingsEntry("Clear song cache", "Downloads & Storage", "settings/storage", "clear cache"),
    SettingsEntry("Clear artwork cache", "Downloads & Storage", "settings/storage", "clear image cache"),

    // Content
    SettingsEntry("Content language", "Content", "settings/content", "language region"),
    SettingsEntry("Content country", "Content", "settings/content", "country region"),
    SettingsEntry("App language", "Content", "settings/content", "language locale"),
    SettingsEntry("Proxy", "Content", "settings/content", "proxy network"),
    SettingsEntry("Quick picks", "Content", "settings/content", "home quick picks"),

    // Connections
    SettingsEntry("Account", "Account", "settings/account", "google youtube sign in"),
    SettingsEntry("ListenBrainz", "Account", "settings/account", "scrobble listenbrainz"),
    SettingsEntry("Import from Spotify", "Backup & Restore", "settings/spotify_import", "spotify import playlists"),
    SettingsEntry("Spotify mixes on Home", "Library & Home", "settings/library", "spotify daily mix discover weekly release radar home"),
    SettingsEntry("Backup & Restore", "Backup & Restore", "settings/backup_restore", "backup restore import m3u csv"),
    SettingsEntry("Shiny Social", "Social", "settings/social", "friends profile sharing"),
    SettingsEntry("Discord", "Social", "settings/discord", "discord rich presence listen along"),
    SettingsEntry("Listen Together", "Listen Together", "settings/together", "together session jam room sync party invite code"),
    SettingsEntry("Headphone delay", "Listen Together", "settings/together", "bluetooth latency sync delay together"),
    SettingsEntry("Listen Together server", "Listen Together", "settings/together", "together server self host workers"),

    // Privacy
    SettingsEntry("Pause listening history", "Privacy", "settings/privacy", "history pause private"),
    SettingsEntry("Count a listen after", "Privacy", "settings/privacy", "history duration threshold"),
    SettingsEntry("Clear listening history", "Privacy", "settings/privacy", "clear history"),
    SettingsEntry("Pause search history", "Privacy", "settings/privacy", "search history"),
    SettingsEntry("Clear search history", "Privacy", "settings/privacy", "clear search"),

    // Supported links
    SettingsEntry("Supported links", "Supported links", "settings/supported_links", "open by default youtube links deep link browser"),

    // Advanced & About
    SettingsEntry("Stream resolver", "Advanced", "settings/advanced", "playback engine newpipe decipher stream"),
    SettingsEntry("Block screenshots", "Advanced", "settings/advanced", "screenshot secure privacy app switcher"),
    SettingsEntry("Check for updates", "About", "settings/about", "update version upgrade"),
    SettingsEntry("Automatic updates", "About", "settings/about", "auto update check"),
    SettingsEntry("Update notifications", "About", "settings/about", "notify update"),
    SettingsEntry("What's New", "About", "settings/changelog", "changelog release notes version"),
) + if (SettingsVisibility.LYRICS) {
    listOf(
        SettingsEntry("Prepare its lyrics too", "Playback", "settings/player", "preload lyrics"),
        SettingsEntry("Fill word by word", "Lyrics", "settings/lyrics", "karaoke word timing sync"),
        SettingsEntry("Pronunciation", "Lyrics", "settings/content/romanization", "romanisation romaji pinyin cyrillic"),
        SettingsEntry("Lyrics sources", "Lyrics", "settings/content", "providers lrclib kugou"),
        SettingsEntry("Lyrics providers", "Content", "settings/content", "lrclib kugou lyrics source"),
    )
} else {
    emptyList()
} + if (SettingsVisibility.PLAYBACK_LOGS) {
    listOf(SettingsEntry("Playback logs", "Content", "settings/content", "logs debug"))
} else {
    emptyList()
} + if (com.shiny.music.BuildConfig.DEBUG) {
    // Endpoint diagnostics are only on screen in debug builds.
    listOf(SettingsEntry("Service status", "Content", "uptime", "uptime status servers diagnostics"))
} else {
    emptyList()
}

/** Matches a query against a setting's name, its page and its keywords. */
fun List<SettingsEntry>.search(query: String): List<SettingsEntry> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return filter {
        it.title.lowercase().contains(q) ||
            it.page.lowercase().contains(q) ||
            it.keywords.contains(q)
    }
}
