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
import com.shiny.music.ui.liquid.appearance.PlayerLook
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
    var playerLook by rememberEnumPreference(LiquidPrefs.PlayerLook, PlayerLook.Shiny)
    var transitions by rememberEnumPreference(LiquidPrefs.PageTransitions, PageTransitions.Slide)
    val backdropDrift by rememberPreference(LiquidPrefs.PlayerMotion, true)
    val breathe by rememberPreference(LiquidPrefs.ArtworkBreathe, true)
    val living by rememberPreference(LiquidPrefs.LivingArtwork, LiquidPrefs.LivingArtworkDefault)
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

        item(key = "player_look") {
            SettingsSection(title = "Player") {
                SettingsSegmentedRow(
                    title = "Player style",
                    options = PlayerLook.entries,
                    selected = playerLook,
                    label = { if (it == PlayerLook.Soft) "Soft" else "Shiny" },
                    onSelect = { choice ->
                        logAppearance("Player look = $choice")
                        playerLook = choice
                    },
                    divider = false,
                )
            }
        }

        if (playerLook == PlayerLook.Shiny) {
            item(key = "experience") {
                SettingsSection {
                    SettingsSegmentedRow(
                        title = "Experience",
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

        if (playerLook == PlayerLook.Shiny) {
            item(key = "stage") {
                SettingsSection(title = "Now Playing") {
                    SettingsChoiceRow(
                        title = "Artwork",
                        options = ArtworkPresentation.entries,
                        selected = artworkPresentation,
                        label = { it.name },
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
                        onSelect = { atmosphere = it },
                    )
                    SettingsSegmentedRow(
                        title = "Artwork glow",
                        options = ArtworkGlow.entries,
                        selected = glow,
                        label = { it.name },
                        onSelect = { glow = it },
                        divider = false,
                    )
                }
            }

            item(key = "material") {
                SettingsSection(title = "Surfaces") {
                    if (GlassSupported) {
                        SettingsChoiceRow(
                            title = "Material",
                            options = SurfaceMaterial.entries,
                            selected = material,
                            label = { it.name },
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
                SettingsSection(title = "Motion") {
                    SettingsChoiceRow(
                        title = "Artwork motion",
                        options = ArtworkMotion.entries,
                        selected = motion,
                        label = { it.name },
                        onSelect = { choice -> write { prefs -> prefs.putMotion(choice) } },
                    )
                    SettingsSegmentedRow(
                        title = "Page transitions",
                        options = PageTransitions.entries,
                        selected = transitions,
                        label = { it.name },
                        onSelect = { transitions = it },
                        divider = false,
                    )
                }
            }

            item(key = "layout") {
                SettingsSection(title = "Layout") {
                    SettingsChoiceRow(
                        title = "Interface size",
                        options = DensityScale.bySize,
                        selected = DensityScale.fromValue(density),
                        label = { it.label },
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
                        checked = cropArtwork,
                        onCheckedChange = { cropArtwork = it },
                        divider = false,
                    )
                }
            }

        }

        item(key = "reset") {
            SettingsSection {
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
