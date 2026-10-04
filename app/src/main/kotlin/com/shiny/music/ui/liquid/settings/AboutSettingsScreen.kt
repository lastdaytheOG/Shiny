package com.shiny.music.ui.liquid.settings

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.shiny.music.BuildConfig
import com.shiny.music.shinymusic.updater.getAutoUpdateCheckSetting
import com.shiny.music.shinymusic.updater.getLastCheckedTime
import com.shiny.music.shinymusic.updater.getUpdateAvailableState
import com.shiny.music.shinymusic.updater.getUpdateNotificationsSetting
import com.shiny.music.shinymusic.updater.saveAutoUpdateCheckSetting
import com.shiny.music.shinymusic.updater.saveUpdateNotificationsSetting
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography

/**
 * About Shiny: the name, the version, how it stays up to date, and what it has to say.
 *
 * The wordmark is the page's one large thing. It sits close under the page title with the
 * version tucked beneath it, and the room goes *after* it, so the rows below read as the
 * product's details rather than a settings list. Rows carry no supporting lines: each name
 * already says what it does.
 *
 * "Include beta builds" is gone: the preference was saved but the updater only ever asks for
 * the latest stable release, so the switch changed nothing. The build type and ABI only show
 * in debug builds, where they are the first thing a developer needs.
 *
 * The licence, the notices and the third-party licences are read in the repository, not here:
 * "Source code" opens it, and the same files travel inside the APK under assets/legal
 * (LICENSE_COMPLIANCE.md). The address comes from gradle.properties `shiny.sourceCodeUrl`.
 */
@Composable
fun AboutSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val colors = Liquid.colors

    var autoCheck by remember { mutableStateOf(getAutoUpdateCheckSetting(context)) }
    var notifyUpdates by remember { mutableStateOf(getUpdateNotificationsSetting(context)) }
    val updateAvailable = remember { getUpdateAvailableState(context) }
    val lastChecked = remember { getLastCheckedTime(context) }

    SettingsPage(title = "About", navController = navController) {
        item(key = "identity") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsGutter)
                    .padding(top = 6.dp, bottom = 22.dp),
            ) {
                Text(
                    text = "SHINY",
                    style = LiquidTypography.largeTitle.copy(
                        fontSize = 64.sp,
                        lineHeight = 64.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                    ),
                    color = colors.label,
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = LiquidTypography.subheadline,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (BuildConfig.DEBUG) {
                    Text(
                        text = "Debug build ${BuildConfig.VERSION_CODE} · ${BuildConfig.FLAVOR}",
                        style = LiquidTypography.footnote,
                        color = colors.tertiaryLabel,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        item(key = "updates") {
            SettingsSection(
                title = "Updates",
                footer = if (lastChecked.isNotBlank()) "Last checked $lastChecked." else null,
            ) {
                SettingsNavRow(
                    title = if (updateAvailable) "Update available" else "Check for updates",
                    badge = updateAvailable,
                    onClick = { navController.navigate("update") },
                )
                SettingsToggleRow(
                    title = "Automatic updates",
                    checked = autoCheck,
                    onCheckedChange = {
                        autoCheck = it
                        saveAutoUpdateCheckSetting(context, it)
                    },
                )
                SettingsToggleRow(
                    title = "Update notifications",
                    // Only said while the switch is greyed out, so it never reads as broken.
                    subtitle = if (autoCheck) null else "Needs automatic updates",
                    checked = notifyUpdates,
                    enabled = autoCheck,
                    onCheckedChange = {
                        notifyUpdates = it
                        saveUpdateNotificationsSetting(context, it)
                    },
                    divider = false,
                )
            }
        }

        item(key = "information") {
            SettingsSection(title = "Information") {
                val sourceUrl = BuildConfig.SOURCE_CODE_URL
                SettingsNavRow(
                    title = "What's New",
                    onClick = { navController.navigate("settings/changelog") },
                    divider = sourceUrl.isNotEmpty(),
                )
                if (sourceUrl.isNotEmpty()) {
                    SettingsNavRow(
                        title = "Source code",
                        value = if (Uri.parse(sourceUrl).host?.removePrefix("www.") == "github.com") "GitHub" else null,
                        onClick = { uriHandler.openUri(sourceUrl) },
                        divider = false,
                    )
                }
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }
}
