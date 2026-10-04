package com.shiny.music.ui.liquid.appearance

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.shiny.music.BuildConfig
import com.shiny.music.constants.CropAlbumArtKey
import com.shiny.music.constants.DarkModeKey
import com.shiny.music.constants.DensityScaleKey
import com.shiny.music.constants.DynamicThemeKey
import com.shiny.music.constants.LiquidGlassGlobalEnabledKey
import com.shiny.music.constants.PureBlackKey
import com.shiny.music.constants.SelectedThemeColorKey
import com.shiny.music.ui.liquid.LiquidPrefs
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.rememberEnumPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The appearance settings the renderers read, gathered once at the theme root.
 *
 * Provided through a *dynamic* composition local: changing one of these recomposes the
 * handful of composables that read it — the glass, the Now Playing background, the page
 * transitions — and nothing else. Nothing here changes while music plays.
 */
@Immutable
data class ShinyAppearance(
    val atmosphere: Atmosphere = Atmosphere.Balanced,
    val glow: ArtworkGlow = ArtworkGlow.Off,
    /** How Now Playing presents the cover. Card is the square stage Shiny has always drawn. */
    val artwork: ArtworkPresentation = ArtworkPresentation.Card,
    val glassFinish: GlassFinish = GlassFinish.Adaptive,
    val transitions: PageTransitions = PageTransitions.Slide,
    /** AMOLED chosen *and* the dark theme showing. */
    val amoled: Boolean = false,
) {
    companion object {
        /** Today's Shiny. What every reader sees when nothing has been provided. */
        val Baseline = ShinyAppearance()
    }
}

val LocalShinyAppearance = compositionLocalOf { ShinyAppearance.Baseline }

private const val LogTag = "Appearance"

/**
 * Reads the appearance preferences and provides them to [content].
 *
 * Reads go through the shared preference snapshot, so this costs one derived read per
 * key; a value that fails to decode falls back to the baseline for that key alone.
 */
@Composable
fun ProvideShinyAppearance(
    amoled: Boolean,
    content: @Composable () -> Unit,
) {
    val atmosphere by rememberEnumPreference(LiquidPrefs.Atmosphere, Atmosphere.Balanced)
    val glow by rememberEnumPreference(LiquidPrefs.ArtworkGlow, ArtworkGlow.Off)
    val artwork by rememberEnumPreference(LiquidPrefs.ArtworkPresentation, ArtworkPresentation.Card)
    val finish by rememberEnumPreference(LiquidPrefs.GlassFinish, GlassFinish.Adaptive)
    val transitions by rememberEnumPreference(LiquidPrefs.PageTransitions, PageTransitions.Slide)

    val appearance = remember(atmosphere, glow, artwork, finish, transitions, amoled) {
        ShinyAppearance(
            atmosphere = atmosphere,
            glow = glow,
            artwork = artwork,
            glassFinish = finish,
            transitions = transitions,
            amoled = amoled,
        )
    }

    if (BuildConfig.DEBUG) {
        // One line per change, never per frame.
        LaunchedEffect(atmosphere) { Log.d(LogTag, "[Appearance] Atmosphere = $atmosphere") }
        LaunchedEffect(glow) { Log.d(LogTag, "[Appearance] Glow = $glow") }
        LaunchedEffect(artwork) { Log.d(LogTag, "[Appearance] Artwork presentation = $artwork") }
        LaunchedEffect(finish) { Log.d(LogTag, "[Appearance] Material finish = $finish") }
        LaunchedEffect(transitions) { Log.d(LogTag, "[Appearance] Transitions = $transitions") }
        LaunchedEffect(amoled) { Log.d(LogTag, "[Appearance] AMOLED = $amoled") }
    }

    CompositionLocalProvider(LocalShinyAppearance provides appearance, content = content)
}

internal fun logAppearance(message: String) {
    if (BuildConfig.DEBUG) Log.d(LogTag, "[Appearance] $message")
}

/**
 * Every preference "Reset appearance" returns to its default.
 *
 * Only things that change how Shiny looks: theme, accent, surfaces, the Now Playing
 * environment and its motion, page transitions, artwork cropping and interface size.
 * Haptics and refresh rate (how the device behaves), animated covers (a network choice),
 * and everything about playback, lyrics, library, downloads and accounts are not here.
 */
private val AppearanceKeys: List<Preferences.Key<*>> = listOf(
    DarkModeKey,
    PureBlackKey,
    SelectedThemeColorKey,
    DynamicThemeKey,
    LiquidGlassGlobalEnabledKey,
    LiquidPrefs.GlassFinish,
    LiquidPrefs.Atmosphere,
    LiquidPrefs.ArtworkGlow,
    LiquidPrefs.ArtworkPresentation,
    LiquidPrefs.PageTransitions,
    LiquidPrefs.PlayerMotion,
    LiquidPrefs.ArtworkBreathe,
    LiquidPrefs.LivingArtwork,
    CropAlbumArtKey,
)

/** The plain preference file `com.dpi.DensityScaler` reads before DataStore exists. */
internal const val DensityPrefsFile = "shiny_settings"
internal const val DensityPrefsKey = "density_scale_factor"

/**
 * Puts every appearance preference back to Shiny's default in one write.
 *
 * Removing a key is what restores its default, since every reader supplies the default
 * itself. Density is written rather than removed: it is mirrored into a second file the
 * process-start provider reads, and both copies have to agree.
 *
 * Returns true when the interface size changed, which only takes effect on restart.
 */
suspend fun resetAppearance(context: Context): Boolean {
    val densityChanged = withContext(Dispatchers.IO) {
        val plain = context.getSharedPreferences(DensityPrefsFile, Context.MODE_PRIVATE)
        val changed = plain.getFloat(DensityPrefsKey, 1f) != 1f
        // Committed, not applied: a restart may follow within the second.
        plain.edit(commit = true) { putFloat(DensityPrefsKey, 1f) }
        changed
    }
    context.dataStore.edit { prefs ->
        AppearanceKeys.forEach { prefs.remove(it) }
        prefs[DensityScaleKey] = 1f
    }
    logAppearance("Reset to baseline (restart needed: $densityChanged)")
    return densityChanged
}
