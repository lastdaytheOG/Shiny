package com.shiny.music.ui.liquid.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shiny.music.LocalIntelligence
import com.shiny.music.constants.AudioAnalysisEnabledKey
import com.shiny.music.constants.PersonalisedRecommendationsKey
import com.shiny.music.constants.RecommendationStyleKey
import com.shiny.music.db.entities.MusicFeedback
import com.shiny.music.intelligence.FeedbackKind
import com.shiny.music.intelligence.RecommendationStyle
import com.shiny.music.intelligence.SurfaceReport
import com.shiny.music.intelligence.TasteInsight
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidAlert
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.home.insightLine
import com.shiny.music.ui.liquid.home.insightRoute
import com.shiny.music.ui.liquid.home.styleDescription
import com.shiny.music.ui.liquid.home.styleName
import com.shiny.music.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
fun RecommendationSettingsScreen(navController: NavController) {
    val intelligence = LocalIntelligence.current
    var personalised by rememberPreference(PersonalisedRecommendationsKey, true)
    var styleKey by rememberPreference(RecommendationStyleKey, RecommendationStyle.Balanced.name)
    var analysis by rememberPreference(AudioAnalysisEnabledKey, true)
    val insights by produceState<List<TasteInsight>>(emptyList(), personalised) {
        value = runCatching { intelligence.insights() }.getOrDefault(emptyList())
    }
    val style = remember(styleKey) {
        runCatching { RecommendationStyle.valueOf(styleKey) }.getOrDefault(RecommendationStyle.Balanced)
    }

    SettingsPage(title = "Recommendations", navController = navController) {
        item(key = "intro") {
            SettingsIntro("SHINY learns from what you play, on this device. Nothing about your listening is uploaded for this.")
        }

        item(key = "style") {
            SettingsSection(title = "Style") {
                SettingsToggleRow(
                    title = "Personalise",
                    subtitle = "Off keeps YouTube's order and Home's plain rules",
                    checked = personalised,
                    onCheckedChange = { personalised = it },
                )
                if (personalised) {
                    val labels = RecommendationStyle.entries.associateWith { styleName(it) }
                    SettingsChoiceRow(
                        title = "Style",
                        options = RecommendationStyle.entries.toList(),
                        selected = style,
                        label = { labels.getValue(it) },
                        onSelect = { styleKey = it.name },
                        divider = false,
                    )
                    Text(
                        text = styleDescription(style),
                        style = LiquidTypography.footnote,
                        color = Liquid.colors.secondaryLabel,
                        modifier = Modifier.padding(start = SettingsGutter, end = SettingsGutter, top = 4.dp),
                    )
                }
            }
        }

        if (insights.isNotEmpty()) {
            item(key = "insights") {
                SettingsSection(title = "What SHINY has noticed") {
                    insights.forEachIndexed { index, insight ->
                        val route = insightRoute(insight)
                        SettingsNavRow(
                            title = insightLine(insight),
                            onClick = { if (route != "settings/recommendations") navController.navigate(route) },
                            divider = index < insights.lastIndex,
                        )
                    }
                }
            }
        }

        item(key = "control") {
            SettingsSection(title = "Your choices") {
                SettingsNavRow(
                    title = "Artists you love",
                    subtitle = "Give Home somewhere to start",
                    onClick = { navController.navigate("settings/recommendations/artists") },
                )
                SettingsNavRow(
                    title = "Hidden and not interested",
                    subtitle = "See and undo what you've ruled out",
                    onClick = { navController.navigate("settings/recommendations/hidden") },
                    divider = false,
                )
            }
        }

        item(key = "sound") {
            SettingsSection(title = "Sound") {
                SettingsToggleRow(
                    title = "Measure songs on this device",
                    subtitle = "Tempo, energy and key of files on your phone, while charging",
                    checked = analysis,
                    onCheckedChange = { analysis = it },
                    divider = false,
                )
            }
        }

        item(key = "quality") {
            SettingsSection(title = "Check") {
                SettingsNavRow(
                    title = "How it's doing",
                    subtitle = "Skips and full listens by place, last 30 days",
                    onClick = { navController.navigate("settings/recommendations/quality") },
                    divider = false,
                )
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }
}

@Composable
fun HiddenRecommendationsScreen(navController: NavController) {
    val intelligence = LocalIntelligence.current
    val rows by remember { intelligence.feedback() }.collectAsState(emptyList())
    var confirmClear by remember { mutableStateOf(false) }
    val kinds = remember { FeedbackKind.entries.associateBy { it.name } }
    val shown = rows.filter { kinds[it.kind] != FeedbackKind.SeedArtist && kinds[it.kind] != FeedbackKind.ExploreMore }

    SettingsPage(title = "Hidden and not interested", navController = navController) {
        if (shown.isEmpty()) {
            item(key = "empty") { SettingsIntro("Nothing here. Use a song's Recommendations row to rule things in or out.") }
        } else {
            item(key = "list") {
                SettingsSection(title = "Tap to undo") {
                    shown.forEachIndexed { index, row ->
                        SettingsActionRow(
                            title = row.label ?: row.targetId,
                            subtitle = feedbackLabel(row),
                            onClick = { kinds[row.kind]?.let { intelligence.removeFeedback(it, row.targetId) } },
                            divider = index < shown.lastIndex,
                        )
                    }
                }
            }
            item(key = "clear") {
                SettingsSection(title = "All") {
                    SettingsActionRow(
                        title = "Clear all",
                        destructive = true,
                        onClick = { confirmClear = true },
                        divider = false,
                    )
                }
            }
        }
        item(key = "tail") { SettingsTailSpace() }
    }

    if (confirmClear) {
        LiquidAlert(
            title = "Clear all?",
            message = "Everything you've hidden or ruled out can come back in recommendations.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                confirmClear = false
                intelligence.clearFeedback()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

private fun feedbackLabel(row: MusicFeedback): String = when (row.kind) {
    FeedbackKind.HideArtist.name -> "Artist hidden"
    FeedbackKind.BlockSong.name -> "Won't be recommended"
    FeedbackKind.NotInterested.name -> "Not interested"
    FeedbackKind.LessLikeThis.name -> "Less like this"
    FeedbackKind.MoreFromArtist.name -> "More from this artist"
    FeedbackKind.SaveForLater.name -> "Saved for later"
    else -> row.kind
}

@Composable
fun RecommendationQualityScreen(navController: NavController) {
    val intelligence = LocalIntelligence.current
    val reports by produceState<List<SurfaceReport>?>(null) {
        value = runCatching { intelligence.surfaceReports() }.getOrDefault(emptyList())
    }

    SettingsPage(title = "How it's doing", navController = navController) {
        item(key = "intro") { SettingsIntro("Worked out on this device from the last 30 days. It isn't sent anywhere.") }
        val list = reports
        if (list != null && list.isEmpty()) {
            item(key = "empty") { SettingsNote("Nothing to measure yet. Play some music first.") }
        }
        if (list != null) {
            items(list, key = { it.surface }) { report ->
                SettingsSection(title = surfaceLabel(report.surface)) {
                    SurfaceRows(report)
                }
            }
        }
        item(key = "tail") { SettingsTailSpace() }
    }
}

@Composable
private fun SurfaceRows(report: SurfaceReport) {
    fun percent(value: Double?) = value?.let { "${(it * 100).roundToInt()}%" } ?: "–"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        SettingsValueRow(title = "Shown", value = report.impressions.toString())
        SettingsValueRow(title = "Picked when shown", value = percent(report.selectionRate))
        SettingsValueRow(title = "Listens", value = report.listens.toString())
        SettingsValueRow(title = "Listened properly", value = percent(report.meaningfulRate))
        SettingsValueRow(title = "Played to the end", value = percent(report.completionRate))
        SettingsValueRow(title = "Skipped", value = percent(report.skipRate))
        SettingsValueRow(title = "New songs that stuck", value = percent(report.discoverySuccess))
        SettingsValueRow(title = "Different artists", value = report.distinctArtists.toString())
        SettingsValueRow(title = "Shown again", value = percent(report.repeatedRate))
    }
}

private fun surfaceLabel(surface: String): String = when (surface) {
    "Direct" -> "Songs you chose"
    "Collection" -> "Your playlists and albums"
    "HomeMix" -> "Home mix"
    "HomeDiscover" -> "Home discoveries"
    "HomeRotation" -> "Home rotation"
    "HomeAroundNow" -> "Home, around now"
    "HomeRediscover" -> "Home, rediscover"
    "HomeContinue" -> "Home, keep listening"
    "Discover" -> "New to you"
    "NewReleases" -> "New releases"
    "Radio" -> "Radio"
    "Autoplay" -> "Autoplay"
    "SmartShuffle" -> "Smart Shuffle"
    "OfflineAutoplay" -> "Offline autoplay"
    "ExploreMore" -> "Explore more"
    else -> surface
}
