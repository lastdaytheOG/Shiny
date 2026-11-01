package com.shiny.music.ui.liquid.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shiny.music.R
import com.shiny.music.intelligence.Reason
import com.shiny.music.intelligence.RecommendationStyle
import com.shiny.music.intelligence.TasteInsight
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.theme.pressScale

@Composable
fun reasonLine(reason: Reason): String = when (reason) {
    is Reason.BecauseSong -> stringResource(R.string.home_because, reason.title)
    is Reason.SimilarToArtist -> stringResource(R.string.reason_similar, reason.artist)
    is Reason.ThroughArtist -> stringResource(R.string.reason_through, reason.artist, reason.via)
    is Reason.Connects -> stringResource(R.string.reason_connects, reason.first, reason.second)
    is Reason.YourArtist -> stringResource(R.string.reason_your_artist, reason.artist)
    is Reason.YouChose -> stringResource(R.string.reason_you_chose, reason.artist)
}

@Composable
fun insightLine(insight: TasteInsight): String = when (insight) {
    is TasteInsight.Rising -> stringResource(R.string.insight_rising, insight.artist)
    is TasteInsight.Loyal -> stringResource(R.string.insight_loyal, insight.artist, insight.plays, insight.songs)
    is TasteInsight.ExploringMore -> pluralStringResource(R.plurals.insight_exploring, insight.accepted, insight.accepted)
    is TasteInsight.Bridge -> stringResource(R.string.insight_bridge, insight.bridge, insight.first, insight.second)
    is TasteInsight.Unexplored -> stringResource(R.string.insight_unexplored, insight.saved, insight.artist)
    is TasteInsight.Tempo -> stringResource(R.string.insight_tempo, insight.lowBpm, insight.highBpm, insight.measuredSongs)
}

fun insightRoute(insight: TasteInsight): String = when (insight) {
    is TasteInsight.Rising -> "artist/${insight.artistId}"
    is TasteInsight.Loyal -> "artist/${insight.artistId}"
    is TasteInsight.Bridge -> "artist/${insight.bridgeId}"
    is TasteInsight.Unexplored -> "artist/${insight.artistId}"
    is TasteInsight.ExploringMore, is TasteInsight.Tempo -> "settings/recommendations"
}

@Composable
fun styleName(style: RecommendationStyle): String = stringResource(
    when (style) {
        RecommendationStyle.Balanced -> R.string.style_balanced
        RecommendationStyle.Comfort -> R.string.style_comfort
        RecommendationStyle.Discovery -> R.string.style_discovery
        RecommendationStyle.Adventure -> R.string.style_adventure
        RecommendationStyle.DeepListening -> R.string.style_deep
    },
)

@Composable
fun styleDescription(style: RecommendationStyle): String = stringResource(
    when (style) {
        RecommendationStyle.Balanced -> R.string.style_balanced_body
        RecommendationStyle.Comfort -> R.string.style_comfort_body
        RecommendationStyle.Discovery -> R.string.style_discovery_body
        RecommendationStyle.Adventure -> R.string.style_adventure_body
        RecommendationStyle.DeepListening -> R.string.style_deep_body
    },
)

@Composable
internal fun PickArtistsCard(onPick: () -> Unit, onDismiss: () -> Unit) {
    HomeHeader(stringResource(R.string.pick_artists_title))
    Row(
        Modifier.padding(horizontal = PageMargin),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CardButton(stringResource(R.string.pick_artists_action), primary = true, onClick = onPick)
        CardButton(stringResource(R.string.not_now), primary = false, onClick = onDismiss)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = text,
        style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
        color = if (primary) colors.background else colors.label,
        modifier = Modifier
            .pressScale(interaction, 0.95f)
            .clip(CircleShape)
            .background(if (primary) colors.label else colors.quaternaryFill)
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
