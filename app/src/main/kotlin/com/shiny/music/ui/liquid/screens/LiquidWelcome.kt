package com.shiny.music.ui.liquid.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.shiny.music.BuildConfig
import com.shiny.music.R
import com.shiny.music.constants.DarkModeKey
import com.shiny.music.constants.LiquidGlassGlobalEnabledKey
import com.shiny.music.constants.SelectedThemeColorKey
import com.shiny.music.ui.liquid.ButtonTone
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidButton
import com.shiny.music.ui.liquid.LiquidSegmentedControl
import com.shiny.music.ui.liquid.LiquidSwitch
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.screens.settings.DarkMode
import com.shiny.music.ui.liquid.settings.TintSwatch
import com.shiny.music.ui.liquid.settings.TintSwatches
import com.shiny.music.ui.theme.DefaultThemeColor
import com.shiny.music.utils.rememberEnumPreference
import com.shiny.music.utils.rememberPreference

private class Feature(val icon: ImageVector, val title: Int, val body: Int)

private val WhatsNewFeatures = listOf(
    Feature(Icons.Rounded.WaterDrop, R.string.liquid_new_f1_title, R.string.liquid_new_f1_body),
    Feature(Icons.Rounded.Headphones, R.string.liquid_new_f2_title, R.string.liquid_new_f2_body),
    Feature(Icons.Rounded.Lyrics, R.string.liquid_new_f3_title, R.string.liquid_new_f3_body),
    Feature(Icons.Rounded.BarChart, R.string.liquid_new_f4_title, R.string.liquid_new_f4_body),
    Feature(Icons.Rounded.GraphicEq, R.string.liquid_new_f5_title, R.string.liquid_new_f5_body),
)

/**
 * First run: "Make It Yours" — appearance, tint and glass bound to the real preferences,
 * so the choices take effect behind the page as you make them. (The "Welcome to Shiny"
 * feature page that used to come first was removed at the user's request.)
 */
@Composable
fun LiquidOnboarding(onFinish: () -> Unit) {
    val colors = Liquid.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        MakeItYoursPage(onFinish = onFinish)
    }
}

@Composable
private fun MakeItYoursPage(onFinish: () -> Unit) {
    val colors = Liquid.colors
    var darkMode by rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    var tint by rememberPreference(SelectedThemeColorKey, DefaultThemeColor.toArgb())
    var glass by rememberPreference(LiquidGlassGlobalEnabledKey, true)
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Box(
                Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.WaterDrop, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
            Spacer(Modifier.height(22.dp))
            Text(stringResource(R.string.liquid_make_it_yours), style = LiquidTypography.largeTitle, color = colors.label, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.liquid_make_it_yours_body),
                style = LiquidTypography.body,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )

            Caption(stringResource(R.string.liquid_appearance_caption))
            LiquidSegmentedControl(
                items = listOf(
                    stringResource(R.string.liquid_automatic),
                    stringResource(R.string.liquid_light),
                    stringResource(R.string.liquid_dark),
                ),
                selectedIndex = when (darkMode) {
                    DarkMode.AUTO -> 0
                    DarkMode.OFF -> 1
                    DarkMode.ON -> 2
                },
                onSelect = { darkMode = listOf(DarkMode.AUTO, DarkMode.OFF, DarkMode.ON)[it] },
                modifier = Modifier.fillMaxWidth(),
            )

            Caption(stringResource(R.string.liquid_tint_caption))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.secondaryGroupedBackground.takeIf { !colors.isDark } ?: Color(0xFF1C1C1E))
                    .padding(vertical = 14.dp),
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                ) {
                    items(TintSwatches) { swatch ->
                        val shown = if (swatch == DefaultThemeColor) Color(0xFFFF2D55) else swatch
                        TintSwatch(color = shown, selected = tint == swatch.toArgb(), onClick = { tint = swatch.toArgb() })
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.secondaryGroupedBackground.takeIf { !colors.isDark } ?: Color(0xFF1C1C1E))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.liquid_glass_title), style = LiquidTypography.body, color = colors.label)
                    Text(stringResource(R.string.liquid_glass_body), style = LiquidTypography.footnote, color = colors.secondaryLabel)
                }
                LiquidSwitch(checked = glass, onCheckedChange = { glass = it })
            }
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.padding(horizontal = 28.dp, vertical = 16.dp)) {
            LiquidButton(
                text = stringResource(R.string.liquid_get_started),
                tone = ButtonTone.Filled,
                height = 54.dp,
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * "What's New in Shiny" — the per-version sheet: icon, title, version, the headline
 * changes as feature rows, Continue.
 */
@Composable
fun LiquidWhatsNew(onDismiss: () -> Unit) {
    val colors = Liquid.colors
    val surface = if (colors.isDark) Color(0xFF1C1C1E) else Color.White
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .shadow(30.dp, RoundedCornerShape(34.dp))
                .clip(RoundedCornerShape(34.dp))
                .background(surface),
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(30.dp))
                AppIcon(size = 72.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.liquid_whats_new),
                    style = LiquidTypography.title1,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.liquid_version, BuildConfig.VERSION_NAME),
                    style = LiquidTypography.footnote,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(24.dp))
                WhatsNewFeatures.forEach { FeatureRow(it, compact = true) }
            }
            LiquidButton(
                text = stringResource(R.string.liquid_continue),
                tone = ButtonTone.Filled,
                height = 52.dp,
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun AppIcon(size: androidx.compose.ui.unit.Dp) {
    val shape = RoundedCornerShape(size * 0.225f)
    AsyncImage(
        model = R.mipmap.ic_launcher,
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .shadow(14.dp, shape)
            .clip(shape)
            .background(Liquid.colors.secondaryGroupedBackground)
            .graphicsLayer {
                // Adaptive icons carry a safe-zone margin; fill the tile with the art.
                scaleX = 1.45f
                scaleY = 1.45f
            },
    )
}

@Composable
private fun FeatureRow(feature: Feature, compact: Boolean = false) {
    val colors = Liquid.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 10.dp else 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(52.dp).padding(top = 2.dp), contentAlignment = Alignment.TopCenter) {
            Icon(feature.icon, null, tint = colors.accent, modifier = Modifier.size(if (compact) 30.dp else 34.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(feature.title), style = LiquidTypography.headline, color = colors.label)
            Text(stringResource(feature.body), style = LiquidTypography.subheadline, color = colors.secondaryLabel)
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text.uppercase(),
        style = LiquidTypography.footnote,
        color = Liquid.colors.secondaryLabel,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 30.dp, bottom = 8.dp),
    )
}
