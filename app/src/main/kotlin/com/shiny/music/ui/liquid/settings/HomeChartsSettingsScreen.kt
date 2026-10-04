package com.shiny.music.ui.liquid.settings

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.navigation.NavController
import com.shiny.music.constants.HomeChartsCountryKey
import com.shiny.music.constants.HomeChartsGlobalKey
import com.shiny.music.home.HomeCharts
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.utils.rememberPreference

/** What the chart setting reads as on the Library & Home page. */
fun homeChartsLabel(setting: String, detected: String?): String = when (setting) {
    HomeCharts.OFF -> "Off"
    HomeCharts.GLOBAL -> "Global"
    HomeCharts.AUTO -> detected?.let(HomeCharts::countryName) ?: "Global"
    else -> HomeCharts.countryName(setting)
}

/**
 * Which YouTube chart Home shows. Your location is the default; any country YouTube Charts
 * publishes can be chosen instead, or Global alone, or no charts at all.
 */
@Composable
fun HomeChartsSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    var setting by rememberPreference(HomeChartsCountryKey, HomeCharts.AUTO)
    var showGlobal by rememberPreference(HomeChartsGlobalKey, true)
    val detected = remember { HomeCharts.detectCountry(context) }
    val countries = remember { HomeCharts.COUNTRIES.sortedBy { HomeCharts.countryName(it) } }

    SettingsPage(title = "Charts", navController = navController) {
        item(key = "intro") {
            SettingsIntro(
                "Home shows YouTube Charts' own ranking, refreshed about once an hour. " +
                    "Your location is read from your mobile network or SIM, never from GPS.",
            )
        }

        item(key = "source") {
            SettingsSection(title = "Show charts from") {
                ChartChoice(
                    title = "Your location",
                    subtitle = detected?.let { HomeCharts.countryName(it) }
                        ?: "Not detected on this device, so Global is shown",
                    selected = setting == HomeCharts.AUTO,
                    onClick = { setting = HomeCharts.AUTO },
                )
                ChartChoice(
                    title = "Global",
                    subtitle = "The worldwide chart only",
                    selected = setting == HomeCharts.GLOBAL,
                    onClick = { setting = HomeCharts.GLOBAL },
                )
                ChartChoice(
                    title = "Off",
                    subtitle = "No charts on Home",
                    selected = setting == HomeCharts.OFF,
                    onClick = { setting = HomeCharts.OFF },
                    divider = false,
                )
            }
        }

        item(key = "global") {
            SettingsSection {
                SettingsToggleRow(
                    title = "Global chart too",
                    subtitle = "Show the worldwide chart beside your country's",
                    checked = showGlobal,
                    onCheckedChange = { showGlobal = it },
                    enabled = setting != HomeCharts.OFF && setting != HomeCharts.GLOBAL,
                    divider = false,
                )
            }
        }

        item(key = "countries_header") {
            SettingsSection(title = "Or choose a country") {}
        }
        items(countries, key = { it }) { code ->
            ChartChoice(
                title = HomeCharts.countryName(code),
                selected = setting == code,
                onClick = { setting = code },
                divider = code != countries.last(),
            )
        }

        item(key = "tail") { SettingsTailSpace() }
    }
}

@Composable
private fun ChartChoice(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    divider: Boolean = true,
) {
    val colors = Liquid.colors
    SettingsRow(
        title = title,
        subtitle = subtitle,
        divider = divider,
        trailing = {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = if (selected) "Selected" else null,
                tint = if (selected) colors.accent else Color.Transparent,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
    )
}
