package com.shiny.music.ui.liquid.settings

/**
 * Settings that still work but are not shown.
 *
 * Hiding a row only takes it off screen and out of search: its preference keeps whatever
 * value it has, its default still applies, and everything that reads it behaves as before.
 * Flip a flag back to `true` to bring the rows back exactly as they were.
 */
object SettingsVisibility {
    /**
     * Every lyrics option: Now Playing → Lyrics (and the pages it leads to), Content → Lyrics
     * providers, and Playback → Prepare its lyrics too. Lyrics themselves are unaffected.
     */
    const val LYRICS = false

    /** Content → Logs → Playback logs. Playback is still logged; only the viewer is hidden. */
    const val PLAYBACK_LOGS = false
}
