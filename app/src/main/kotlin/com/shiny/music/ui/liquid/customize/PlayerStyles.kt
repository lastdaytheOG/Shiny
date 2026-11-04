package com.shiny.music.ui.liquid.customize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shiny.music.extensions.toEnum
import com.shiny.music.ui.liquid.LiquidPrefs
import com.shiny.music.ui.liquid.appearance.PlayerLook
import com.shiny.music.utils.PreferencesSnapshot

/**
 * The whole open player. Shiny and Soft are the two looks Appearance already had and are
 * still stored there; every other style is a complete player of its own, drawn instead of
 * Shiny's in the same sheet, with the same swipe down to close.
 */
enum class PlayerStyle(val title: String, val blurb: String) {
    Shiny("Shiny", "Shiny's own player: the cover, the controls, lyrics and queue a tap away"),
    Soft("Soft", "The cover's colours poured over the whole screen, white controls on top"),
    Turntable("Turntable", "The song on a spinning record; the arm moves across it as it plays"),
    Poster("Poster", "The cover as a great circle, the words sung big underneath"),
    Timeline("Timeline", "The song as time: what's played and what's left, split by the playhead"),
    Lyrics("Lyrics stage", "The line being sung, huge, over the timeline"),
    Video("Full video", "The song's video across the whole screen, its words over it"),
    BedsideTiles("Bedside", "Two tiles for the nightstand: the music on one, a clock on the other"),
    Bedside("Bedside clock", "A big quiet clock for the nightstand, the song a swipe away");

    /** Drawn by the alternate player host rather than Shiny's NowPlaying. */
    val alternate: Boolean get() = this != Shiny && this != Soft

    val bedside: Boolean get() = this == Bedside || this == BedsideTiles
}

/** Per-style choices, edited on Settings › Customise › Player styles. */
object PlayerStylePrefs {
    // absent (or Shiny/Soft) means the look Appearance holds; anything else is that style
    val Style = stringPreferencesKey("customizePlayerStyle")

    val TurntableSpin = booleanPreferencesKey("customizeTurntableSpin")
    val TurntableArmSeeks = booleanPreferencesKey("customizeTurntableArmSeeks")
    val PosterWords = booleanPreferencesKey("customizePosterWords")
    val TimelineCover = booleanPreferencesKey("customizeTimelineCover")
    val VideoWords = booleanPreferencesKey("customizeVideoWords")
    val Bedside24h = booleanPreferencesKey("customizeBedside24h")
    val BedsideSeconds = booleanPreferencesKey("customizeBedsideSeconds")
    val BedsideNight = booleanPreferencesKey("customizeBedsideNight")
    val BedsideAwake = booleanPreferencesKey("customizeBedsideAwake")
    val BedsideClockTile = booleanPreferencesKey("customizeBedsideClockTile")
}

/** The style the open player is drawn in, from its own key and Appearance's look. */
fun currentPlayerStyle(): PlayerStyle {
    val own = PreferencesSnapshot.readString(PlayerStylePrefs.Style).toEnum(PlayerStyle.Shiny)
    if (own.alternate) return own
    val look = PreferencesSnapshot.readString(LiquidPrefs.PlayerLook).toEnum(PlayerLook.Shiny)
    return if (look == PlayerLook.Soft) PlayerStyle.Soft else PlayerStyle.Shiny
}

/** [currentPlayerStyle] as state that only changes when the style does. */
@Composable
fun rememberPlayerStyle(): State<PlayerStyle> = remember { derivedStateOf { currentPlayerStyle() } }

/**
 * Writes [style]. Shiny and Soft go back to Appearance's own look key, so Appearance and this
 * picker never disagree; the Poster artwork presentation is Appearance's and left as it is.
 */
fun MutablePreferences.choosePlayerStyle(style: PlayerStyle) {
    when (style) {
        PlayerStyle.Shiny, PlayerStyle.Soft -> {
            remove(PlayerStylePrefs.Style)
            this[LiquidPrefs.PlayerLook] = if (style == PlayerStyle.Soft) PlayerLook.Soft.name else PlayerLook.Shiny.name
        }
        else -> this[PlayerStylePrefs.Style] = style.name
    }
}

