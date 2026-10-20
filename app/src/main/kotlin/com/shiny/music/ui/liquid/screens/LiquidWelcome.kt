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
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
