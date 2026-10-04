package com.shiny.music.ui.screens.settings

/**
 * Settings values shared across the app.
 *
 * These used to live at the bottom of the old Appearance screen; they outlived it, so
 * they live here now — MainActivity, the theme plumbing and the Liquid settings screens
 * all read them.
 */

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

/** The tab Shiny opens on. */
enum class NavigationTab {
    HOME,
    SEARCH,
    LIBRARY,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}
