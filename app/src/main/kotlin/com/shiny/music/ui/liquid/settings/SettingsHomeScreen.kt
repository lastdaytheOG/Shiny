package com.shiny.music.ui.liquid.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import com.shiny.music.ui.liquid.Hairline
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidSearchField
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.PanelMargin
import com.shiny.music.ui.liquid.PanelPadding
import com.shiny.music.ui.liquid.liquidPanel
import com.shiny.music.ui.liquid.rememberRowHighlight
import com.shiny.music.utils.rememberPreference

/**
 * The way into Shiny's settings: who you are, then the areas, in the order people reach
 * for them. Ten destinations, each of which is a page worth opening — not fifty rows and
 * a scrollbar.
 *
 * The index is the one settings page set in panels, with a coloured plate behind each
 * glyph. On an index they make a destination findable at a glance; on a page of switches
 * they would only be decoration, so the pages it opens stay plain type on the ground.
 */
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

    SettingsPage(
        title = stringResource(R.string.settings),
        navController = navController,
        // Panels need a ground to be raised off: grey in Light, where the page ground is
        // the panel's own white. The dark grounds are the same black either way.
        background = colors.groupedBackground,
    ) {
        item(key = "search") {
            LiquidSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search settings",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PanelMargin)
                    .padding(top = 6.dp, bottom = 2.dp)
                    .border(0.5.dp, colors.panelRim, CircleShape),
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
                    IndexPanel(title = "${matches.size} result${if (matches.size == 1) "" else "s"}") {
                        matches.forEachIndexed { index, entry ->
                            IndexRow(
                                title = entry.title,
                                subtitle = entry.page,
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
            IndexPanel {
                // The one row on the index that is a person rather than a category: a round plate.
                IndexRow(
                    title = accountName.ifBlank { stringResource(R.string.account) },
                    subtitle = accountEmail.ifBlank { "Sign in to sync your library" },
                    glyph = rememberVectorPainter(Icons.Rounded.Person),
                    plate = Plate.Blue,
                    plateShape = CircleShape,
                    minHeight = 66.dp,
                    divider = false,
                    onClick = { navController.navigate("settings/account") },
                )
            }
        }

        item(key = "playback_group") {
            IndexPanel(title = "Playback") {
                IndexRow(
                    title = "Playback",
                    subtitle = "Sound, transitions, queue",
                    glyph = painterResource(R.drawable.play),
                    plate = Plate.Rose,
                    onClick = { navController.navigate("settings/player") },
                )
                IndexRow(
                    title = "Now Playing",
                    subtitle = if (SettingsVisibility.LYRICS) "Animated covers, controls, lyrics" else "Animated covers, controls",
                    glyph = painterResource(R.drawable.album),
                    plate = Plate.Violet,
                    onClick = { navController.navigate("settings/now_playing") },
                )
                IndexRow(
                    title = "Content",
                    subtitle = if (SettingsVisibility.LYRICS) "Language, filters, lyrics sources" else "Language, region, proxy",
                    glyph = painterResource(R.drawable.language),
                    plate = Plate.Amber,
                    onClick = { navController.navigate("settings/content") },
                    divider = false,
                )
            }
        }

        item(key = "interface_group") {
            IndexPanel(title = "Interface") {
                IndexRow(
                    title = "Appearance",
                    subtitle = "Theme, accent, atmosphere, size",
                    glyph = painterResource(R.drawable.palette),
                    plate = Plate.Indigo,
                    onClick = { navController.navigate("settings/appearance") },
                )
                IndexRow(
                    title = "Library & Home",
                    subtitle = "Tabs, shelves, gestures",
                    glyph = painterResource(R.drawable.grid_view),
                    plate = Plate.Pink,
                    onClick = { navController.navigate("settings/library") },
                    divider = false,
                )
            }
        }

        item(key = "music_group") {
            IndexPanel(title = "Your music") {
                IndexRow(
                    title = "Downloads & Storage",
                    subtitle = "Download folder, cache",
                    glyph = painterResource(R.drawable.download),
                    plate = Plate.Green,
                    onClick = { navController.navigate("settings/storage") },
                )
                IndexRow(
                    title = "Backup & Restore",
                    subtitle = "Backups, Spotify and playlist imports",
                    glyph = painterResource(R.drawable.restore),
                    plate = Plate.Violet,
                    onClick = { navController.navigate("settings/backup_restore") },
                    divider = false,
                )
            }
        }

        item(key = "social_group") {
            IndexPanel(title = "Sharing") {
                IndexRow(
                    title = stringResource(R.string.social_title),
                    subtitle = stringResource(R.string.social_friends_desc),
                    glyph = rememberVectorPainter(Icons.Rounded.People),
                    plate = Plate.Blue,
                    onClick = { navController.navigate("settings/social") },
                )
                IndexRow(
                    title = stringResource(R.string.listen_together),
                    subtitle = stringResource(R.string.together_settings_row),
                    glyph = painterResource(R.drawable.group),
                    plate = Plate.Rose,
                    onClick = { navController.navigate("settings/together") },
                )
                IndexRow(
                    title = stringResource(R.string.discord_settings_title),
                    subtitle = stringResource(R.string.discord_settings_desc),
                    glyph = painterResource(R.drawable.discord),
                    plate = Plate.Blurple,
                    onClick = { navController.navigate("settings/discord") },
                    divider = false,
                )
            }
        }

        item(key = "system_group") {
            IndexPanel(title = "System") {
                IndexRow(
                    title = "Privacy",
                    subtitle = "Listening and search history",
                    glyph = painterResource(R.drawable.lock),
                    plate = Plate.Green,
                    onClick = { navController.navigate("settings/privacy") },
                )
                IndexRow(
                    title = "Advanced",
                    subtitle = "Stream resolver, screenshots",
                    glyph = painterResource(R.drawable.tune),
                    plate = Plate.Blue,
                    onClick = { navController.navigate("settings/advanced") },
                )
                IndexRow(
                    title = "Supported links",
                    subtitle = "YouTube and shared links that open in Shiny",
                    glyph = painterResource(R.drawable.link),
                    plate = Plate.Amber,
                    onClick = { navController.navigate("settings/supported_links") },
                )
                IndexRow(
                    title = stringResource(R.string.about),
                    subtitle = if (updateAvailable) "Update available" else null,
                    glyph = painterResource(R.drawable.info),
                    plate = Plate.Graphite,
                    badge = updateAvailable,
                    onClick = { navController.navigate("settings/about") },
                    divider = false,
                )
            }
        }

        item(key = "tail") { SettingsTailSpace(40.dp) }
    }
}

/**
 * The colours behind the index glyphs. One muted step down from the system palette, so a
 * page of them reads as a set rather than as a row of stickers; the accent is not among
 * them, since nothing here is a control.
 */
private object Plate {
    val Rose = Color(0xFFE2456A)
    val Violet = Color(0xFF8E62DE)
    val Amber = Color(0xFFDE8A2F)
    val Indigo = Color(0xFF5568E8)
    val Pink = Color(0xFFD4509A)
    val Green = Color(0xFF2FAE6A)
    val Blue = Color(0xFF3683EA)

    /** Discord's own colour. */
    val Blurple = Color(0xFF5865F2)
    val Graphite = Color(0xFF7C7C84)
}

private val PlateSize = 30.dp
private val PlateShape = RoundedCornerShape(8.dp)
private val PlateGap = 14.dp
private val IndexRowMinHeight = 60.dp

/** A panel of index rows under an optional label. */
@Composable
private fun IndexPanel(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            SettingsSectionLabel(
                title = title,
                modifier = Modifier.padding(start = SettingsGutter, end = SettingsGutter, top = 26.dp, bottom = 8.dp),
            )
        } else {
            Spacer(Modifier.height(16.dp))
        }
        Column(
            modifier = Modifier
                .padding(horizontal = PanelMargin)
                .fillMaxWidth()
                .liquidPanel(),
            content = content,
        )
    }
}

/** One destination: its glyph on a plate, its name, a line about it, and a chevron. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IndexRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    glyph: Painter? = null,
    plate: Color = Color.Unspecified,
    plateShape: Shape = PlateShape,
    minHeight: androidx.compose.ui.unit.Dp = IndexRowMinHeight,
    badge: Boolean = false,
    divider: Boolean = true,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = rememberRowHighlight(),
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(horizontal = PanelPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) {
                GlyphPlate(glyph = glyph, color = plate, shape = plateShape)
                Spacer(Modifier.width(PlateGap))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = LiquidTypography.body,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = LiquidTypography.footnote,
                        color = colors.secondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            if (badge) {
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.destructive)
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.tertiaryLabel,
                modifier = Modifier.size(20.dp),
            )
        }
        if (divider) {
            Hairline(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(end = PanelPadding),
                startIndent = if (glyph != null) PanelPadding + PlateSize + PlateGap else PanelPadding,
                color = colors.panelSeparator,
            )
        }
    }
}

/**
 * A white glyph on a plate of [color], lit from above: a shade lighter at the head, a shade
 * deeper at the foot, and a hairline where the light catches its edge. Two fills, no layer.
 */
@Composable
private fun GlyphPlate(glyph: Painter, color: Color, shape: Shape) {
    val fill = remember(color) {
        Brush.verticalGradient(listOf(lerp(color, Color.White, 0.14f), lerp(color, Color.Black, 0.18f)))
    }
    Box(
        modifier = Modifier
            .size(PlateSize)
            .background(fill, shape)
            .border(0.5.dp, PlateRim, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = glyph,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

private val PlateRim = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.04f))
)
