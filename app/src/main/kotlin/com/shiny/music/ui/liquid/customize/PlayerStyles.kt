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

enum class PlayerStyle(val title: String, val blurb: String) {
    Shiny("Shiny", "Shiny's own player: the cover, the controls, lyrics and queue a tap away"),
    Soft("Soft", "The cover's colours poured over the whole screen, white controls on top"),
    Turntable("Turntable", "The song on a spinning record; the arm moves across it as it plays"),
    Poster("Poster", "The cover as a great circle, the words sung big underneath"),
    PosterRing("Poster 2", "The cover swung in as a great circle with a seek ring on its rim, the words performed big, the controls in pills"),
    Dial("Dial", "The words in a card, the cover as a dial at the foot: drag its rim to seek"),
    Timeline("Timeline", "The song as time: what's played and what's left, split by the playhead"),
    Lyrics("Lyrics stage", "The line being sung, huge, over the timeline"),
    Video("Full video", "The song's video across the whole screen, its words over it"),
    BedsideTiles("Bedside", "Two tiles for the nightstand: the music on one, a clock on the other"),
    Bedside("Bedside clock", "A big quiet clock for the nightstand, the song a swipe away");

    val alternate: Boolean get() = this != Shiny && this != Soft

    val bedside: Boolean get() = this == Bedside || this == BedsideTiles
}

object PlayerStylePrefs {
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

fun currentPlayerStyle(): PlayerStyle {
    val own = PreferencesSnapshot.readString(PlayerStylePrefs.Style).toEnum(PlayerStyle.Shiny)
    if (own.alternate) return own
    val look = PreferencesSnapshot.readString(LiquidPrefs.PlayerLook).toEnum(PlayerLook.Shiny)
    return if (look == PlayerLook.Soft) PlayerStyle.Soft else PlayerStyle.Shiny
}

@Composable
fun rememberPlayerStyle(): State<PlayerStyle> = remember { derivedStateOf { currentPlayerStyle() } }

fun MutablePreferences.choosePlayerStyle(style: PlayerStyle) {
    when (style) {
        PlayerStyle.Shiny, PlayerStyle.Soft -> {
            remove(PlayerStylePrefs.Style)
            this[LiquidPrefs.PlayerLook] = if (style == PlayerStyle.Soft) PlayerLook.Soft.name else PlayerLook.Shiny.name
        }
        else -> this[PlayerStylePrefs.Style] = style.name
    }
}
