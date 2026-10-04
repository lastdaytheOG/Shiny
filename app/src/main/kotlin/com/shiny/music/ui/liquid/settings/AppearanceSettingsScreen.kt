package com.shiny.music.ui.liquid.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.shiny.music.constants.CropAlbumArtKey
import com.shiny.music.constants.DarkModeKey
import com.shiny.music.constants.DensityScale
import com.shiny.music.constants.DensityScaleKey
import com.shiny.music.constants.DynamicThemeKey
import com.shiny.music.constants.EnableHapticsKey
import com.shiny.music.constants.EnableHighRefreshRateKey
import com.shiny.music.constants.LiquidGlassGlobalEnabledKey
import com.shiny.music.constants.PureBlackKey
import com.shiny.music.constants.SelectedThemeColorKey
import com.shiny.music.ui.liquid.GlassSupported
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidAlert
import com.shiny.music.ui.liquid.LiquidPrefs
import com.shiny.music.ui.liquid.appearance.AccentMode
import com.shiny.music.ui.liquid.appearance.AppearanceColor
import com.shiny.music.ui.liquid.appearance.ArtworkGlow
import com.shiny.music.ui.liquid.appearance.ArtworkMotion
import com.shiny.music.ui.liquid.appearance.ArtworkPresentation
import com.shiny.music.ui.liquid.appearance.Atmosphere
import com.shiny.music.ui.liquid.appearance.DensityPrefsFile
import com.shiny.music.ui.liquid.appearance.DensityPrefsKey
import com.shiny.music.ui.liquid.appearance.Experience
import com.shiny.music.ui.liquid.appearance.ExperienceMix
import com.shiny.music.ui.liquid.appearance.GlassFinish
import com.shiny.music.ui.liquid.appearance.PageTransitions
import com.shiny.music.ui.liquid.appearance.SurfaceMaterial
import com.shiny.music.ui.liquid.appearance.accentModeOf
import com.shiny.music.ui.liquid.appearance.artworkMotionOf
import com.shiny.music.ui.liquid.appearance.experienceOf
import com.shiny.music.ui.liquid.appearance.finish
import com.shiny.music.ui.liquid.appearance.logAppearance
import com.shiny.music.ui.liquid.appearance.mix
import com.shiny.music.ui.liquid.appearance.resetAppearance
import com.shiny.music.ui.liquid.appearance.surfaceMaterialOf
import com.shiny.music.ui.liquid.appearance.switches
import com.shiny.music.ui.screens.settings.DarkMode
import com.shiny.music.ui.theme.DefaultThemeColor
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.rememberEnumPreference
import com.shiny.music.utils.rememberPreference
import kotlinx.coroutines.launch

/** The tints Shiny offers: Shiny Rose first, then a spectrum that works in both themes. */
internal val TintSwatches = listOf(
    DefaultThemeColor, // Shiny Rose, resolved per theme
    Color(0xFFFF453A),
    Color(0xFFFF9F0A),
    Color(0xFFFFD60A),
    Color(0xFF30D158),
    Color(0xFF64D2FF),
    Color(0xFF0A84FF),
    Color(0xFF5E5CE6),
    Color(0xFFBF5AF2),
)

/** The swatches the Custom accent picks from: the spectrum without Shiny Rose. */
private val CustomSwatches = TintSwatches.drop(1)

/**
 * How Shiny looks, as one system: a live stage at the top that every choice below redraws,
 * a one-tap Experience that sets the Now Playing environment together, then the parts —
 * theme, accent, the stage, surfaces, motion, size — and a way back to the original.
 *
 * Every row here changes something on screen, and every default is the Shiny people
 * already had.
 */
@Composable
fun AppearanceSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var darkMode by rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    var amoled by rememberPreference(PureBlackKey, false)
    val tint by rememberPreference(SelectedThemeColorKey, DefaultThemeColor.toArgb())
    val followArtwork by rememberPreference(DynamicThemeKey, false)
    val glass by rememberPreference(LiquidGlassGlobalEnabledKey, true)
    val finish by rememberEnumPreference(LiquidPrefs.GlassFinish, GlassFinish.Adaptive)
    var atmosphere by rememberEnumPreference(LiquidPrefs.Atmosphere, Atmosphere.Balanced)
    var glow by rememberEnumPreference(LiquidPrefs.ArtworkGlow, ArtworkGlow.Off)
    var artworkPresentation by rememberEnumPreference(LiquidPrefs.ArtworkPresentation, ArtworkPresentation.Card)
    var transitions by rememberEnumPreference(LiquidPrefs.PageTransitions, PageTransitions.Slide)
    val backdropDrift by rememberPreference(LiquidPrefs.PlayerMotion, true)
    val breathe by rememberPreference(LiquidPrefs.ArtworkBreathe, true)
    val living by rememberPreference(LiquidPrefs.LivingArtwork, true)
    var highRefresh by rememberPreference(EnableHighRefreshRateKey, true)
    var haptics by rememberPreference(EnableHapticsKey, false)
    var cropArtwork by rememberPreference(CropAlbumArtKey, false)

    // Density is applied by a ContentProvider at process start, so it also lives in the
    // plain SharedPreferences file that provider can read before DataStore exists.
    val sharedPreferences = remember { context.getSharedPreferences(DensityPrefsFile, Context.MODE_PRIVATE) }
    val storedDensity = remember(sharedPreferences) { sharedPreferences.getFloat(DensityPrefsKey, 1.0f) }
    var density by rememberPreference(DensityScaleKey, storedDensity)
    var pendingRestart by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    val motion = artworkMotionOf(backdropDrift, breathe, living)
    val experience = experienceOf(ExperienceMix(atmosphere, glow, motion, transitions))
    val accent = accentModeOf(followArtwork, tint)
    val material = surfaceMaterialOf(glass, finish)
    var lastCustomTint by rememberSaveable { mutableIntStateOf(CustomSwatches.first().toArgb()) }

    /** Several preferences in one write, so the page never shows a half-applied choice. */
    fun write(block: (MutablePreferences) -> Unit) {
        scope.launch { context.dataStore.edit { block(it) } }
    }

    fun MutablePreferences.putMotion(choice: ArtworkMotion) {
        val s = choice.switches
        this[LiquidPrefs.PlayerMotion] = s.backdropDrift
        this[LiquidPrefs.ArtworkBreathe] = s.breathe
        this[LiquidPrefs.LivingArtwork] = s.living
    }

    SettingsPage(title = "Appearance", navController = navController) {
        item(key = "preview") { AppearancePreview() }

        item(key = "experience") {
            SettingsSection(
                footer = "Sets the Now Playing atmosphere, glow, artwork motion and page transitions " +
                    "together. Change any of them below and this becomes your own mix.",
            ) {
                SettingsSegmentedRow(
                    title = "Experience",
                    subtitle = when (experience) {
                        Experience.Minimal -> "A quiet stage: soft colour, still artwork, dissolving pages"
                        Experience.Balanced -> "Shiny as it ships"
                        Experience.Immersive -> "The artwork fills the room, with light around the cover"
                        Experience.Custom -> "Custom — your own mix of the settings below"
                    },
                    options = listOf(Experience.Minimal, Experience.Balanced, Experience.Immersive),
                    selected = experience,
                    label = { it.name },
                    onSelect = { choice ->
                        val mix = choice.mix ?: return@SettingsSegmentedRow
                        logAppearance("Experience = $choice")
                        write { prefs ->
                            prefs[LiquidPrefs.Atmosphere] = mix.atmosphere.name
                            prefs[LiquidPrefs.ArtworkGlow] = mix.glow.name
                            prefs[LiquidPrefs.PageTransitions] = mix.transitions.name
                            prefs.putMotion(mix.motion)
                        }
                    },
                    divider = false,
                )
            }
        }

        item(key = "theme") {
            SettingsSection(title = "Theme") {
                SettingsSegmentedRow(
                    title = "Light or dark",
                    options = listOf(DarkMode.AUTO, DarkMode.OFF, DarkMode.ON),
                    selected = darkMode,
                    label = {
                        when (it) {
                            DarkMode.AUTO -> "Automatic"
                            DarkMode.OFF -> "Light"
                            DarkMode.ON -> "Dark"
                        }
                    },
                    onSelect = { darkMode = it },
                )
                SettingsToggleRow(
                    title = "AMOLED black",
                    subtitle = if (darkMode == DarkMode.OFF) {
                        "Applies whenever Shiny is dark"
                    } else {
                        "True black stage and deeper panels, so dark mode lights fewer pixels"
                    },
                    checked = amoled,
                    onCheckedChange = { amoled = it },
                    enabled = darkMode != DarkMode.OFF,
                    divider = false,
                )
            }
        }

        item(key = "accent") {
            SettingsSection(title = "Accent") {
                SettingsSegmentedRow(
                    title = "Accent colour",
                    subtitle = when (accent) {
                        AccentMode.Shiny -> "Shiny Rose"
                        AccentMode.Artwork -> "Follows the playing cover, adjusted to stay readable. Covers without colour use Shiny Rose"
                        AccentMode.Mono -> "Graphite controls, no colour"
                        AccentMode.Custom -> "A colour of your own"
                    },
                    options = AccentMode.entries,
                    selected = accent,
                    label = { if (it == AccentMode.Mono) "Mono" else it.name },
                    onSelect = { choice ->
                        logAppearance("Accent = $choice")
                        // Artwork leaves the chosen tint as it is underneath; Custom restores
                        // the last custom colour unless one is already chosen.
                        val newTint = when (choice) {
                            AccentMode.Shiny -> AppearanceColor.ShinyTint
                            AccentMode.Mono -> AppearanceColor.MonochromeTint
                            AccentMode.Custom -> if (accentModeOf(false, tint) == AccentMode.Custom) null else lastCustomTint
                            AccentMode.Artwork -> null
                        }
                        write { prefs ->
                            prefs[DynamicThemeKey] = choice == AccentMode.Artwork
                            if (newTint != null) prefs[SelectedThemeColorKey] = newTint
                        }
                    },
                    divider = accent == AccentMode.Custom,
                )
                if (accent == AccentMode.Custom) {
                    TintRow(
                        selected = tint,
                        onSelect = { argb ->
                            lastCustomTint = argb
                            write { prefs -> prefs[SelectedThemeColorKey] = argb }
                        },
                    )
                }
            }
        }

        item(key = "stage") {
            SettingsSection(
                title = "Now Playing",
                footer = "Atmosphere and glow follow each song and settle over a moment when it " +
                    "changes. Ambient Mode uses the same atmosphere. Full screen is portrait only; " +
                    "in landscape the cover keeps its own half of the window.",
            ) {
                SettingsChoiceRow(
                    title = "Artwork",
                    options = ArtworkPresentation.entries,
                    selected = artworkPresentation,
                    label = { if (it == ArtworkPresentation.FullScreen) "Full screen" else it.name },
                    optionSubtitle = {
                        when (it) {
                            ArtworkPresentation.Card -> "The cover on a stage of its own colour"
                            ArtworkPresentation.FullScreen -> "The cover fills Now Playing, behind the controls"
                        }
                    },
                    onSelect = { choice ->
                        logAppearance("Artwork presentation = $choice")
                        artworkPresentation = choice
                    },
                )
                SettingsChoiceRow(
                    title = "Atmosphere",
                    options = Atmosphere.entries,
                    selected = atmosphere,
                    label = { it.name },
                    optionSubtitle = {
                        when (it) {
                            Atmosphere.Off -> "A plain dark stage — true black with AMOLED"
                            Atmosphere.Soft -> "The cover's colour, quieted"
                            Atmosphere.Balanced -> "Shiny's stage as it ships"
                            Atmosphere.Immersive -> "The cover's colour, brought forward"
                        }
                    },
                    onSelect = { atmosphere = it },
                )
                SettingsSegmentedRow(
                    title = "Artwork glow",
                    subtitle = "Light from the cover spills onto the stage",
                    options = ArtworkGlow.entries,
                    selected = glow,
                    label = { it.name },
                    onSelect = { glow = it },
                    divider = false,
                )
            }
        }

        item(key = "material") {
            SettingsSection(
                title = "Surfaces",
                footer = if (GlassSupported) {
                    "Glass refracts what is behind the tab bar, the mini player and floating buttons. " +
                        "Solid draws them opaque, which is cheaper on older devices."
                } else {
                    "Glass needs Android 12 or later, so this device draws every surface solid."
                },
            ) {
                if (GlassSupported) {
                    SettingsChoiceRow(
                        title = "Material",
                        options = SurfaceMaterial.entries,
                        selected = material,
                        label = { it.name },
                        optionSubtitle = {
                            when (it) {
                                SurfaceMaterial.Adaptive -> "Clear over artwork, frosted over pages"
                                SurfaceMaterial.Clear -> "Thinner glass that shows more of what is behind"
                                SurfaceMaterial.Frosted -> "Softer, diffused glass that quiets what is behind"
                                SurfaceMaterial.Solid -> "Opaque surfaces, no refraction"
                            }
                        },
                        onSelect = { choice ->
                            logAppearance("Material = $choice")
                            // Solid only turns glass off; the finish is kept for when it returns.
                            val newFinish = choice.finish
                            write { prefs ->
                                prefs[LiquidGlassGlobalEnabledKey] = choice != SurfaceMaterial.Solid
                                if (newFinish != null) prefs[LiquidPrefs.GlassFinish] = newFinish.name
                            }
                        },
                        divider = false,
                    )
                } else {
                    SettingsValueRow(title = "Material", value = "Solid", divider = false)
                }
            }
        }

        item(key = "motion") {
            SettingsSection(
                title = "Motion",
                footer = "Artwork motion runs only while Now Playing is open and music is playing. " +
                    "Animated covers are in Now Playing settings.",
            ) {
                SettingsChoiceRow(
                    title = "Artwork motion",
                    options = ArtworkMotion.entries,
                    selected = motion,
                    label = { it.name },
                    optionSubtitle = {
                        when (it) {
                            ArtworkMotion.Off -> "A still cover on a still background"
                            ArtworkMotion.Subtle -> "The background drifts; the cover settles when paused"
                            ArtworkMotion.Living -> "The cover drifts and catches the light as it plays"
                        }
                    },
                    onSelect = { choice -> write { prefs -> prefs.putMotion(choice) } },
                )
                SettingsSegmentedRow(
                    title = "Page transitions",
                    subtitle = when (transitions) {
                        PageTransitions.Slide -> "Pages push in from the edge"
                        PageTransitions.Fade -> "Pages dissolve into each other"
                        PageTransitions.Instant -> "Pages change without animating"
                    },
                    options = PageTransitions.entries,
                    selected = transitions,
                    label = { it.name },
                    onSelect = { transitions = it },
                    divider = false,
                )
            }
        }

        item(key = "layout") {
            SettingsSection(
                title = "Layout",
                footer = "Interface size scales everything on screen, and Shiny restarts to apply it. " +
                    "On smaller screens the larger sizes are capped so Now Playing still fits.",
            ) {
                SettingsChoiceRow(
                    title = "Interface size",
                    options = DensityScale.bySize,
                    selected = DensityScale.fromValue(density),
                    label = { it.label },
                    optionSubtitle = {
                        when (it) {
                            DensityScale.NATIVE -> "Your device's own size"
                            DensityScale.COMFORTABLE -> "Slightly larger type and targets"
                            DensityScale.LARGE -> "Larger type and targets"
                            else -> null
                        }
                    },
                    onSelect = { scale ->
                        if (scale.value != density) {
                            density = scale.value
                            sharedPreferences.edit { putFloat(DensityPrefsKey, scale.value) }
                            logAppearance("Interface size = ${scale.name}")
                            pendingRestart = true
                        }
                    },
                )
                SettingsToggleRow(
                    title = "Crop artwork to square",
                    subtitle = "Fill the frame instead of fitting the whole cover",
                    checked = cropArtwork,
                    onCheckedChange = { cropArtwork = it },
                    divider = false,
                )
            }
        }

        item(key = "feel") {
            SettingsSection(title = "Feel") {
                SettingsToggleRow(
                    title = "High refresh rate",
                    subtitle = "Ask the display for its fastest mode",
                    checked = highRefresh,
                    onCheckedChange = { highRefresh = it },
                )
                SettingsToggleRow(
                    title = "Haptics",
                    subtitle = "A tick under taps and scrolling",
                    checked = haptics,
                    onCheckedChange = { haptics = it },
                    divider = false,
                )
            }
        }

        item(key = "reset") {
            SettingsSection(
                footer = "Returns theme, accent, surfaces, the Now Playing stage, motion and size to " +
                    "Shiny's defaults. Playback, lyrics, library, downloads and accounts are untouched.",
            ) {
                SettingsActionRow(
                    title = "Reset appearance",
                    destructive = true,
                    onClick = { confirmReset = true },
                    divider = false,
                )
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }

    if (confirmReset) {
        LiquidAlert(
            title = "Reset appearance?",
            message = "Shiny goes back to the look it ships with.",
            confirmLabel = "Reset",
            destructive = true,
            onConfirm = {
                confirmReset = false
                scope.launch {
                    if (resetAppearance(context)) pendingRestart = true
                }
            },
            onDismiss = { confirmReset = false },
        )
    }

    if (pendingRestart) {
        LiquidAlert(
            title = "Restart Shiny?",
            message = "The new interface size takes effect when Shiny starts again.",
            confirmLabel = "Restart",
            onConfirm = {
                pendingRestart = false
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                context.startActivity(intent)
                Runtime.getRuntime().exit(0)
            },
            onDismiss = { pendingRestart = false },
            dismissLabel = "Later",
        )
    }
}

/**
 * A single tint swatch, ringed when it is the chosen one.
 *
 * Also used by the welcome flow, which offers the same nine colours before there is a
 * settings screen to open.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TintSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(3.dp, Liquid.colors.label.copy(alpha = 0.9f), CircleShape)
                } else {
                    Modifier
                }
            )
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** The Custom accent picker: eight swatches, the current one ringed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TintRow(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = Liquid.colors
    Column(Modifier.fillMaxWidth()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = SettingsGutter),
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        ) {
            items(CustomSwatches) { swatch ->
                val isSelected = selected == swatch.toArgb()
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .then(
                            if (isSelected) {
                                Modifier.border(2.5.dp, colors.label.copy(alpha = 0.9f), CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSelect(swatch.toArgb()) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
