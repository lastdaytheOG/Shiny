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
    var query by rememberSaveable { mutableStateOf("") }
    val accountName by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val updateAvailable = remember { getUpdateAvailableState(context) && getAutoUpdateCheckSetting(context) }
    val catalog = remember { visibleCatalog(settingsCatalog()) }
    val matches = remember(query) { catalog.search(query) }
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
        Category(stringResource(R.string.social_title), "settings/social", rememberVectorPainter(Icons.Rounded.People)),
        Category("App Behaviour", "settings/behaviour", painterResource(R.drawable.tune)),
        Category("Downloads & Storage", "settings/storage", painterResource(R.drawable.download)),
        Category("Backup", "settings/backup_restore", painterResource(R.drawable.restore)),
        Category("Integrations", "settings/integrations", painterResource(R.drawable.link)),
        Category(stringResource(R.string.listen_together), "settings/together", painterResource(R.drawable.group)),
        Category("Supported Links", "settings/supported_links", painterResource(R.drawable.language)),
        Category(stringResource(R.string.about), "settings/about", painterResource(R.drawable.info), badge = updateAvailable),
    )

    SettingsPage(title = stringResource(R.string.settings), navController = navController, search = false) {
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

// the settings that moved onto app behaviour, by their catalog names
private val BehaviourTitles = setOf(
    "Data Saver", "Skip silence", "Count a listen after", "Skip tracks that fail", "Stop when Shiny is swiped away",
    "Volume slider", "Keep screen on", "Keep screen on while playing", "Content country", "App language",
)

// the names these rows carry on app behaviour
private val BehaviourNames = mapOf("Count a listen after" to "Count songs after", "Content country" to "Default country", "Stop when Shiny is swiped away" to "Stop when SHINY is swiped away")

private val VisibleRoutes = setOf(
    "settings/social", "settings/youtube", "settings/integrations", "settings/spotify_import", "settings/storage", "settings/backup_restore", "settings/discord", "settings/together",
    "settings/integrations/listen_together", "settings/supported_links", "settings/about", "settings/changelog", "settings/update",
)

// search and the suggestion only point at settings that are on screen: the moved ones go to
// app behaviour, the hidden pages drop out
private fun visibleCatalog(all: List<SettingsEntry>): List<SettingsEntry> = all.mapNotNull { e ->
    when {
        e.title in BehaviourTitles -> e.copy(title = BehaviourNames[e.title] ?: e.title, page = "App Behaviour", route = "settings/behaviour")
        e.route == "settings/account" && e.title.contains("Spotify", ignoreCase = true) -> e.copy(page = "Integrations", route = "settings/spotify_import")
        e.route == "settings/account" && !e.title.contains("ListenBrainz", ignoreCase = true) && !e.title.contains("Amazon", ignoreCase = true) ->
            e.copy(page = "YouTube Music", route = "settings/youtube")
        e.route in VisibleRoutes -> e
        else -> null
    }
}
