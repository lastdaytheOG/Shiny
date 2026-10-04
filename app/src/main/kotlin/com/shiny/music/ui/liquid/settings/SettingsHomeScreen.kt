package com.shiny.music.ui.liquid.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shiny.music.R
import com.shiny.music.constants.AccountEmailKey
import com.shiny.music.constants.AccountNameKey
import com.shiny.music.shinymusic.updater.getAutoUpdateCheckSetting
import com.shiny.music.shinymusic.updater.getUpdateAvailableState
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidSearchField
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.utils.rememberPreference

/**
 * The way into Shiny's settings: who you are, then the areas, in the order people reach
 * for them. Ten destinations, each of which is a page worth opening — not fifty rows and
 * a scrollbar.
 *
 * Glyphs appear here and nowhere else. On an index they make a destination findable at a
 * glance; on a page of switches they would only be decoration.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsHomeScreen(navController: NavController) {
    val context = LocalContext.current
    val colors = Liquid.colors
    var query by rememberSaveable { mutableStateOf("") }
    val accountName by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val updateAvailable = remember { getUpdateAvailableState(context) && getAutoUpdateCheckSetting(context) }
    val catalog = remember { settingsCatalog() }
    val matches = remember(query) { catalog.search(query) }

    SettingsPage(title = stringResource(R.string.settings), navController = navController) {
        item(key = "search") {
            LiquidSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsGutter)
                    .padding(top = 4.dp, bottom = 2.dp),
            )
        }

        if (query.isNotBlank()) {
            if (matches.isEmpty()) {
                item(key = "no_results") {
                    Text(
                        text = "Nothing matches “$query”",
                        style = LiquidTypography.body,
                        color = colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, start = SettingsGutter, end = SettingsGutter),
                    )
                }
            } else {
                item(key = "results") {
                    SettingsSection(title = "${matches.size} result${if (matches.size == 1) "" else "s"}") {
                        matches.forEachIndexed { index, entry ->
                            SettingsRow(
                                title = entry.title,
                                subtitle = entry.page,
                                chevron = true,
                                divider = index != matches.lastIndex,
                                onClick = { navController.navigate(entry.route) },
                            )
                        }
                    }
                }
            }
            item(key = "tail") { SettingsTailSpace() }
            return@SettingsPage
        }

        item(key = "account") {
            AccountRow(
                name = accountName.ifBlank { stringResource(R.string.account) },
                detail = accountEmail.ifBlank { "Sign in to sync your library" },
                onClick = { navController.navigate("settings/account") },
            )
        }

        item(key = "playback_group") {
            SettingsSection(title = "Playback") {
                SettingsNavRow(
                    title = "Playback",
                    subtitle = "Sound, transitions, queue",
                    glyph = settingsGlyph(painterResource(R.drawable.play)),
                    onClick = { navController.navigate("settings/player") },
                )
                SettingsNavRow(
                    title = "Now Playing",
                    subtitle = if (SettingsVisibility.LYRICS) "Animated covers, controls, lyrics" else "Animated covers, controls",
                    glyph = settingsGlyph(painterResource(R.drawable.album)),
                    onClick = { navController.navigate("settings/now_playing") },
                )
                SettingsNavRow(
                    title = "Content",
                    subtitle = if (SettingsVisibility.LYRICS) "Language, filters, lyrics sources" else "Language, region, proxy",
                    glyph = settingsGlyph(painterResource(R.drawable.language)),
                    onClick = { navController.navigate("settings/content") },
                    divider = false,
                )
            }
        }

        item(key = "interface_group") {
            SettingsSection(title = "Interface") {
                SettingsNavRow(
                    title = "Appearance",
                    subtitle = "Theme, accent, atmosphere, size",
                    glyph = settingsGlyph(painterResource(R.drawable.palette)),
                    onClick = { navController.navigate("settings/appearance") },
                )
                SettingsNavRow(
                    title = "Library & Home",
                    subtitle = "Tabs, shelves, gestures",
                    glyph = settingsGlyph(painterResource(R.drawable.grid_view)),
                    onClick = { navController.navigate("settings/library") },
                    divider = false,
                )
            }
        }

        item(key = "music_group") {
            SettingsSection(title = "Your music") {
                SettingsNavRow(
                    title = "Downloads & Storage",
                    subtitle = "Download folder, cache",
                    glyph = settingsGlyph(painterResource(R.drawable.storage)),
                    onClick = { navController.navigate("settings/storage") },
                )
                SettingsNavRow(
                    title = "Backup & Restore",
                    subtitle = "Backups, Spotify and playlist imports",
                    glyph = settingsGlyph(painterResource(R.drawable.restore)),
                    onClick = { navController.navigate("settings/backup_restore") },
                    divider = false,
                )
            }
        }

        item(key = "social_group") {
            SettingsSection(title = "Sharing") {
                SettingsNavRow(
                    title = stringResource(R.string.social_title),
                    subtitle = stringResource(R.string.social_friends_desc),
                    glyph = { Icon(Icons.Rounded.People, null, tint = colors.secondaryLabel, modifier = Modifier.size(21.dp)) },
                    onClick = { navController.navigate("settings/social") },
                )
                SettingsNavRow(
                    title = stringResource(R.string.listen_together),
                    subtitle = stringResource(R.string.together_settings_row),
                    glyph = settingsGlyph(painterResource(R.drawable.group)),
                    onClick = { navController.navigate("settings/together") },
                )
                SettingsNavRow(
                    title = stringResource(R.string.discord_settings_title),
                    subtitle = stringResource(R.string.discord_settings_desc),
                    glyph = settingsGlyph(painterResource(R.drawable.discord)),
                    onClick = { navController.navigate("settings/discord") },
                    divider = false,
                )
            }
        }

        item(key = "system_group") {
            SettingsSection(title = "System") {
                SettingsNavRow(
                    title = "Privacy",
                    subtitle = "Listening and search history",
                    glyph = settingsGlyph(painterResource(R.drawable.lock)),
                    onClick = { navController.navigate("settings/privacy") },
                )
                SettingsNavRow(
                    title = "Advanced",
                    subtitle = "Stream resolver, screenshots",
                    glyph = settingsGlyph(painterResource(R.drawable.tune)),
                    onClick = { navController.navigate("settings/advanced") },
                )
                SettingsNavRow(
                    title = "Supported links",
                    subtitle = "YouTube and shared links that open in Shiny",
                    glyph = settingsGlyph(painterResource(R.drawable.link)),
                    onClick = { navController.navigate("settings/supported_links") },
                )
                SettingsNavRow(
                    title = stringResource(R.string.about),
                    subtitle = if (updateAvailable) "Update available" else null,
                    glyph = settingsGlyph(painterResource(R.drawable.info)),
                    badge = updateAvailable,
                    onClick = { navController.navigate("settings/about") },
                    divider = false,
                )
            }
        }

        item(key = "tail") { SettingsTailSpace(40.dp) }
    }
}

/** The account cell: the one place on the index that is a person rather than a category. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountRow(name: String, detail: String, onClick: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = com.shiny.music.ui.liquid.rememberRowHighlight(),
                onClick = onClick,
            )
            .padding(horizontal = SettingsGutter, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.secondaryFill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Person, null, tint = colors.secondaryLabel, modifier = Modifier.size(30.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = name,
                style = LiquidTypography.title3,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = LiquidTypography.footnote,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(20.dp))
    }
}
