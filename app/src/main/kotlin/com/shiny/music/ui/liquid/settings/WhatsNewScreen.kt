package com.shiny.music.ui.liquid.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.shiny.music.BuildConfig
import com.shiny.music.ui.liquid.Hairline
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography

/**
 * What's New: the current version first, told as a release rather than a commit log — a
 * sentence about it, the three things worth knowing, then everything else sorted into New,
 * Improved and Fixed. Older versions fold away underneath.
 */
@Composable
fun WhatsNewScreen(navController: NavController) {
    val current = ShinyReleases.firstOrNull { it.version == BuildConfig.VERSION_NAME } ?: ShinyReleases.first()
    val earlier = ShinyReleases.filter { it !== current }

    SettingsPage(title = "What's New", navController = navController) {
        item(key = "hero") { ReleaseHero(current) }

        if (current.featured.isNotEmpty()) {
            item(key = "featured") {
                SettingsSection(title = "Featured") {
                    current.featured.forEachIndexed { index, highlight ->
                        FeaturedRow(
                            number = index + 1,
                            highlight = highlight,
                            divider = index != current.featured.lastIndex,
                        )
                    }
                }
            }
        }

        releaseNotes(current, keyPrefix = "current")

        if (earlier.isNotEmpty()) {
            item(key = "earlier") {
                SettingsSection(title = "Earlier versions") {
                    earlier.forEachIndexed { index, release ->
                        EarlierRelease(release, divider = index != earlier.lastIndex)
                    }
                }
            }
        }

        item(key = "tail") { SettingsTailSpace(40.dp) }
    }
}

@Composable
private fun ReleaseHero(release: Release) {
    val colors = Liquid.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsGutter)
            .padding(top = 10.dp, bottom = 4.dp),
    ) {
        Text(
            text = if (release.version == BuildConfig.VERSION_NAME) "CURRENT VERSION" else "LATEST VERSION",
            style = LiquidTypography.caption1.copy(fontWeight = FontWeight.SemiBold),
            letterSpacing = 0.9.sp,
            color = colors.accent,
        )
        Text(
            text = "Version ${release.version}",
            style = LiquidTypography.title1,
            color = colors.label,
            modifier = Modifier.padding(top = 4.dp),
        )
        release.summary?.let {
            Text(
                text = it,
                style = LiquidTypography.callout,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun FeaturedRow(number: Int, highlight: Highlight, divider: Boolean) {
    val colors = Liquid.colors
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsGutter, vertical = 14.dp),
        ) {
            Text(
                text = number.toString().padStart(2, '0'),
                style = LiquidTypography.title3.copy(fontWeight = FontWeight.Bold),
                color = colors.accent,
                modifier = Modifier.widthIn(min = 38.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(text = highlight.title, style = LiquidTypography.headline, color = colors.label)
                Text(
                    text = highlight.text,
                    style = LiquidTypography.subheadline,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (divider) Hairline(Modifier.align(Alignment.BottomStart), startIndent = SettingsGutter + 38.dp)
    }
}

/** New, Improved and Fixed, each only when it has something in it. */
private fun androidx.compose.foundation.lazy.LazyListScope.releaseNotes(release: Release, keyPrefix: String) {
    listOf("New" to release.new, "Improved" to release.improved, "Fixed" to release.fixed)
        .filter { it.second.isNotEmpty() }
        .forEach { (title, notes) ->
            item(key = "$keyPrefix-$title") {
                SettingsSection(title = title) { NoteList(notes) }
            }
        }
}

@Composable
private fun NoteList(notes: List<String>) {
    Column(Modifier.padding(horizontal = SettingsGutter)) {
        notes.forEach { note -> Note(note) }
    }
}

@Composable
private fun Note(text: String) {
    val colors = Liquid.colors
    Row(Modifier.padding(vertical = 6.dp)) {
        Box(
            Modifier
                .padding(top = 9.dp)
                .size(5.dp)
                .background(colors.tertiaryLabel, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = text, style = LiquidTypography.callout, color = colors.label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EarlierRelease(release: Release, divider: Boolean) {
    val colors = Liquid.colors
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "releaseChevron",
    )
    Column(Modifier.fillMaxWidth()) {
        SettingsRow(
            title = "Version ${release.version}",
            subtitle = release.date,
            divider = divider && !expanded,
            trailing = {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.tertiaryLabel,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = rotation },
                )
            },
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 500f)) + fadeOut(),
        ) {
            Column(Modifier.padding(bottom = 12.dp)) {
                listOf("New" to release.new, "Improved" to release.improved, "Fixed" to release.fixed)
                    .filter { it.second.isNotEmpty() }
                    .forEach { (title, notes) ->
                        Text(
                            text = title.uppercase(),
                            style = LiquidTypography.caption2.copy(fontWeight = FontWeight.SemiBold),
                            letterSpacing = 0.8.sp,
                            color = colors.secondaryLabel,
                            modifier = Modifier.padding(start = SettingsGutter, top = 12.dp, bottom = 2.dp),
                        )
                        NoteList(notes)
                    }
                Spacer(Modifier.height(4.dp))
            }
        }
        if (divider && expanded) Hairline(startIndent = SettingsGutter)
    }
}
