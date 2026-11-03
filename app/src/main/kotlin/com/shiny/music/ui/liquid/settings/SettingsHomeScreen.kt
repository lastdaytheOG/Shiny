package com.shiny.music.ui.liquid.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.shiny.music.BuildConfig
import com.shiny.music.LocalDatabase
import com.shiny.music.R
import com.shiny.music.constants.AccountEmailKey
import com.shiny.music.constants.AccountNameKey
import com.shiny.music.shinymusic.updater.getAutoUpdateCheckSetting
import com.shiny.music.shinymusic.updater.getUpdateAvailableState
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidSearchField
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.utils.rememberPreference

private data class Category(val title: String, val route: String, val glyph: Painter, val badge: Boolean = false)

// who you are and how much you've listened, then a search, one suggestion, and the places to go
@Composable
fun SettingsHomeScreen(navController: NavController) {
    val context = LocalContext.current
    val colors = Liquid.colors
    val database = LocalDatabase.current
    var query by rememberSaveable { mutableStateOf("") }
    val accountName by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val updateAvailable = remember { getUpdateAvailableState(context) && getAutoUpdateCheckSetting(context) }
    val catalog = remember { settingsCatalog() }
    val matches = remember(query) { catalog.search(query) }
    val totalMs by remember { database.getTotalPlayTimeInRange(0, Long.MAX_VALUE) }.collectAsState(initial = 0L)
    val recommendedIndex = rememberSaveable { catalog.indices.random() }
    val recommended = catalog.getOrNull(recommendedIndex)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (SettingsSearchRequest.pending) {
            SettingsSearchRequest.pending = false
            runCatching { focus.requestFocus() }
        }
    }

    val categories = listOf(
        Category("Playback", "settings/player", painterResource(R.drawable.play)),
        Category("Now Playing", "settings/now_playing", painterResource(R.drawable.album)),
        Category("Content", "settings/content", painterResource(R.drawable.language)),
        Category("Appearance", "settings/appearance", painterResource(R.drawable.palette)),
        Category("Library & Home", "settings/library", painterResource(R.drawable.grid_view)),
        Category("Downloads & Storage", "settings/storage", painterResource(R.drawable.download)),
        Category("Backup & Restore", "settings/backup_restore", painterResource(R.drawable.restore)),
        Category(stringResource(R.string.social_title), "settings/social", rememberVectorPainter(Icons.Rounded.People)),
        Category(stringResource(R.string.listen_together), "settings/together", painterResource(R.drawable.group)),
        Category(stringResource(R.string.discord_settings_title), "settings/discord", painterResource(R.drawable.discord)),
        Category("Privacy", "settings/privacy", painterResource(R.drawable.lock)),
    ) + listOfNotNull(
        if (SettingsVisibility.ADVANCED) Category("Advanced", "settings/advanced", painterResource(R.drawable.tune)) else null,
        Category("Supported links", "settings/supported_links", painterResource(R.drawable.link)),
        Category(stringResource(R.string.about), "settings/about", painterResource(R.drawable.info), badge = updateAvailable),
    )

    SettingsPage(title = stringResource(R.string.settings), navController = navController, search = false) {
        if (query.isBlank()) {
            item(key = "listening") {
                ListeningCard(totalMs = totalMs ?: 0L, onStats = { navController.navigate("stats") })
            }
            item(key = "version") {
                VersionCard(version = BuildConfig.VERSION_NAME, update = updateAvailable, onAbout = { navController.navigate("settings/about") })
            }
            item(key = "account") {
                PillRow(
                    title = accountName.ifBlank { stringResource(R.string.account) },
                    subtitle = accountEmail.ifBlank { "Sign in to sync your library" },
                    glyph = rememberVectorPainter(Icons.Rounded.Person),
                    onClick = { navController.navigate("settings/account") },
                )
            }
            item(key = "settings_label") {
                SettingsSectionLabel("Settings", Modifier.padding(start = SettingsCardMargin + 4.dp, top = 22.dp, bottom = 10.dp))
            }
        }
        item(key = "search") {
            LiquidSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search settings",
                focusRequester = focus,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsCardMargin)
                    .padding(top = 4.dp, bottom = 6.dp),
            )
        }
        if (query.isNotBlank()) {
            if (matches.isEmpty()) {
                item(key = "no_results") {
                    Text(
                        text = "Nothing matches “$query”",
                        fontSize = 16.sp,
                        color = colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, start = SettingsGutter, end = SettingsGutter),
                    )
                }
            } else {
                matches.forEachIndexed { i, entry ->
                    item(key = "result_$i") {
                        SuggestionCard(label = null, title = entry.title, crumb = "Settings › ${entry.page}", onClick = { navController.navigate(entry.route) })
                    }
                }
            }
            item(key = "tail") { SettingsTailSpace() }
            return@SettingsPage
        }
        recommended?.let { entry ->
            item(key = "recommended") {
                SuggestionCard(label = "Recommended item", title = entry.title, crumb = "Settings › ${entry.page}", onClick = { navController.navigate(entry.route) })
            }
        }
        item(key = "rule") {
            Box(
                Modifier
                    .padding(horizontal = SettingsCardMargin, vertical = 10.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.label.copy(alpha = 0.08f)),
            )
        }
        categories.forEach { c ->
            item(key = "cat_${c.route}") { CategoryRow(c) { navController.navigate(c.route) } }
        }
        item(key = "tail") { SettingsTailSpace(40.dp) }
    }
}

@Composable
private fun cardModifier(): Modifier = Modifier
    .padding(horizontal = SettingsCardMargin, vertical = 7.dp)
    .fillMaxWidth()
    .clip(RoundedCornerShape(26.dp))
    .background(settingsCard())

// total listening time as one big number, and the way into the stats
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListeningCard(totalMs: Long, onStats: () -> Unit) {
    val colors = Liquid.colors
    val minutes = totalMs / 60_000L
    val interaction = remember { MutableInteractionSource() }
    Column(
        cardModifier()
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onStats)
            .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${minutes / 60}", fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.6).sp, color = colors.label)
                Text("h", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.label, modifier = Modifier.padding(bottom = 7.dp))
            }
            Box(
                Modifier
                    .padding(horizontal = 14.dp)
                    .width(1.dp)
                    .height(40.dp)
                    .background(colors.label.copy(alpha = 0.25f)),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${minutes % 60}", fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.6).sp, color = colors.label)
                Text("m", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.label, modifier = Modifier.padding(bottom = 7.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("Total Listening\ntime", fontSize = 15.sp, lineHeight = 19.sp, color = colors.secondaryLabel, textAlign = TextAlign.End)
        }
        Box(
            Modifier
                .padding(top = 14.dp, bottom = 12.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.label.copy(alpha = 0.12f)),
        )
        Text("View Stats  >", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.secondaryLabel)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VersionCard(version: String, update: Boolean, onAbout: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        cardModifier().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (update) "Update available" else "Version & about app", fontSize = 15.sp, color = if (update) colors.accent else colors.secondaryLabel)
            Text(version, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.6).sp, color = colors.label, maxLines = 1)
        }
        Box(
            Modifier
                .pressScale(interaction, 0.94f)
                .clip(CircleShape)
                .background(if (colors.isDark) Color(0xFF3A3A3C) else colors.quaternaryFill)
                .combinedClickable(interactionSource = interaction, indication = null, onClick = onAbout)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.about), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.label)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PillRow(title: String, subtitle: String?, glyph: Painter, onClick: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .padding(horizontal = SettingsCardMargin, vertical = 7.dp)
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(RoundedCornerShape(30.dp))
            .background(settingsCard())
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (colors.isDark) Color(0xFF2C2C2E) else colors.quaternaryFill),
            contentAlignment = Alignment.Center,
        ) { Icon(glyph, null, tint = colors.label, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 13.sp, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// a setting worth knowing about, and where it lives
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestionCard(label: String?, title: String, crumb: String, onClick: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Column(
        cardModifier()
            .pressScale(interaction, 0.98f)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 20.dp, end = 20.dp, top = if (label != null) 18.dp else 14.dp, bottom = 16.dp),
    ) {
        if (label != null) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.secondaryLabel)
            Spacer(Modifier.height(14.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (colors.isDark) Color(0xFF2C2C2E) else colors.quaternaryFill),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MoreHoriz, null, tint = colors.label, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, color = colors.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(crumb, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// one place to go: a round plate with its glyph and its name, nothing else
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(category: Category, onClick: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .padding(horizontal = SettingsCardMargin, vertical = 7.dp)
            .fillMaxWidth()
            .height(72.dp)
            .pressScale(interaction, 0.98f)
            .clip(RoundedCornerShape(24.dp))
            .background(settingsCard())
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (colors.isDark) Color(0xFF2C2C2E) else colors.quaternaryFill),
            contentAlignment = Alignment.Center,
        ) { Icon(category.glyph, null, tint = colors.label, modifier = Modifier.size(21.dp)) }
        Spacer(Modifier.width(16.dp))
        Text(category.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (category.badge) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(colors.destructive),
            )
        }
    }
}
