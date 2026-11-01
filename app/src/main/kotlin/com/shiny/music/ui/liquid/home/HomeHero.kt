package com.shiny.music.ui.liquid.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.R
import com.shiny.music.db.entities.RecSurface
import com.shiny.music.db.entities.Song
import com.shiny.music.home.AroundNowSection
import com.shiny.music.home.Daypart
import com.shiny.music.home.DiscoveryMixSection
import com.shiny.music.home.HeroMixSection
import com.shiny.music.home.HomeSection
import com.shiny.music.home.RediscoverSection
import com.shiny.music.home.StartHereSection
import com.shiny.music.home.barCovers
import com.shiny.music.home.pickSongs
import com.shiny.music.home.topPickCovers
import com.shiny.music.playback.queues.PlaybackOrigin
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.Shelf
import com.shiny.music.ui.liquid.SkeletonBlock
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize

private val MixOrigin = PlaybackOrigin(RecSurface.HomeMix)
private val AroundNowOrigin = PlaybackOrigin(RecSurface.HomeAroundNow)
private val RediscoverOrigin = PlaybackOrigin(RecSurface.HomeRediscover)
private val CardShape = RoundedCornerShape(16.dp)
private val CardMaxWidth = 290.dp
private val CardHeight = 288.dp
private val CardCover = 72.dp

private fun cardWidth(available: Dp, cards: Int): Dp =
    if (cards <= 1) (available - PageMargin * 2).coerceAtMost(CardMaxWidth * 1.3f)
    else (available * 0.7f).coerceAtMost(CardMaxWidth)

private data class MixColors(val first: Color, val second: Color)

private fun mixColors(section: HomeSection): MixColors = when (section) {
    is HeroMixSection -> when {
        section.offlineOnly -> MixColors(Color(0xFF334155), Color(0xFF0F172A))
        section.daypart == Daypart.Morning -> MixColors(Color(0xFFC2410C), Color(0xFF7C1D3F))
        section.daypart == Daypart.Afternoon -> MixColors(Color(0xFF0E7490), Color(0xFF134E7A))
        section.daypart == Daypart.Evening -> MixColors(Color(0xFFB45309), Color(0xFF9D174D))
        else -> MixColors(Color(0xFF4338CA), Color(0xFF2E1065))
    }
    is DiscoveryMixSection -> MixColors(Color(0xFF7C3AED), Color(0xFF1D4ED8))
    is AroundNowSection -> MixColors(Color(0xFF047857), Color(0xFF134E4A))
    is RediscoverSection -> MixColors(Color(0xFF9F1239), Color(0xFF78350F))
    else -> MixColors(Color(0xFFBE123C), Color(0xFF581C87))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OnColorButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .pressScale(interaction, 0.95f)
            .clip(CircleShape)
            .background(if (primary) Color.White else Color.White.copy(alpha = 0.18f))
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val content = if (primary) Color.Black else Color.White
        Icon(icon, null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = LiquidTypography.headline, color = content, maxLines = 1)
    }
}

@Composable
private fun pickTitle(section: HomeSection): String = when (section) {
    is AroundNowSection -> stringResource(
        when (section.daypart) {
            Daypart.Morning -> R.string.home_around_morning
            Daypart.Afternoon -> R.string.home_around_afternoon
            Daypart.Evening -> R.string.home_around_evening
            Daypart.Night -> R.string.home_around_night
        },
    )
    is DiscoveryMixSection -> stringResource(R.string.home_discovery_mix)
    is RediscoverSection -> stringResource(R.string.home_rediscover)
    else -> stringResource(R.string.home_start_device_title)
}

private fun artistsLine(songs: List<Song>): String =
    songs.asSequence()
        .flatMap { it.artists.asSequence().take(1) }
        .map { it.name }
        .filter { it.isNotBlank() }
        .distinct()
        .take(3)
        .joinToString()

@Composable
internal fun TopPicks(
    sections: List<HomeSection>,
    queueTitle: String?,
    isPlaying: Boolean,
    actions: LiquidActions,
    onPlayedDiscovery: () -> Unit,
    takenCovers: List<String>,
    onShown: @Composable (HomeSection) -> Unit,
) {
    val covers = remember(sections, takenCovers) { topPickCovers(sections, takenCovers) }
    HomeHeader(stringResource(R.string.home_top_picks))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = cardWidth(maxWidth, sections.size)
        Shelf(items = sections, key = { it.key }) { section ->
            onShown(section)
            val title = pickTitle(section)
            val active = queueTitle == title
            val songs = remember(section) { section.pickSongs }
            val artists = remember(section) { artistsLine(songs) }
            TopPickCard(
                title = title,
                artists = artists,
                covers = covers[section.key].orEmpty(),
                colors = mixColors(section),
                width = width,
                playing = active && isPlaying,
                onClick = {
                    if (active) {
                        actions.togglePlayPause()
                    } else {
                        when (section) {
                            is DiscoveryMixSection -> {
                                onPlayedDiscovery()
                                actions.playSongs(title, songs, origin = discoverOrigin(section.picks))
                            }
                            is AroundNowSection -> actions.playSongs(title, songs, shuffle = true, origin = AroundNowOrigin)
                            is RediscoverSection -> actions.playSongs(title, songs, origin = RediscoverOrigin)
                            is StartHereSection -> actions.playSongs(title, songs)
                            else -> Unit
                        }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopPickCard(
    title: String,
    artists: String,
    covers: List<String>,
    colors: MixColors,
    width: Dp,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val px = with(LocalDensity.current) { CardCover.roundToPx() }
    val models = remember(covers, px) { covers.map { it.resize(px, px) } }
    val brush = remember(colors) { Brush.linearGradient(listOf(colors.first, colors.second)) }
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(if (playing) R.string.pause else R.string.liquid_play)
    Column(
        Modifier
            .size(width = width, height = CardHeight)
            .pressScale(interaction, 0.98f)
            .clip(CardShape)
            .background(brush)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.1f),
                        radius = size.width * 0.8f,
                    ),
                )
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$title. $artists" }
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            models.forEach { cover ->
                Artwork(
                    model = cover,
                    shape = RoundedCornerShape(8.dp),
                    hairline = false,
                    modifier = Modifier.size(CardCover),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = title,
            style = LiquidTypography.largeTitle.copy(fontSize = 30.sp, lineHeight = 33.sp),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = artists,
                style = LiquidTypography.footnote,
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MixBar(
    section: HeroMixSection,
    queueTitle: String?,
    isPlaying: Boolean,
    reduceMotion: Boolean,
    actions: LiquidActions,
    onChooseStyle: () -> Unit,
) {
    val title = stringResource(
        if (section.offlineOnly) R.string.home_mix_offline else when (section.daypart) {
            Daypart.Morning -> R.string.home_mix_morning
            Daypart.Afternoon -> R.string.home_mix_afternoon
            Daypart.Evening -> R.string.home_mix_evening
            Daypart.Night -> R.string.home_mix_night
        },
    )
    val covers = remember(section) { section.barCovers().map { it.resize(360, 360) } }
    val artists = remember(section) { artistsLine(section.songs) }
    val colors = mixColors(section)
    val brush = remember(section.daypart, section.offlineOnly) { Brush.linearGradient(listOf(colors.first, colors.second)) }
    val active = queueTitle == title
    val playing = active && isPlaying
    val onPlay = {
        if (active) actions.togglePlayPause() else actions.playSongs(title, section.songs, origin = MixOrigin)
    }
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .padding(horizontal = PageMargin)
            .padding(top = 12.dp)
            .fillMaxWidth()
            .pressScale(interaction, 0.985f)
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = stringResource(if (playing) R.string.pause else R.string.liquid_play),
                onClick = onPlay,
            )
            .semantics { contentDescription = "$title. $artists" }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverFan(covers, size = 44.dp, reduceMotion = reduceMotion)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Text(title, style = LiquidTypography.title3.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artists, style = LiquidTypography.footnote, color = Color.White.copy(alpha = 0.86f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (section.styleable) {
            BarDisc(
                icon = Icons.Rounded.Tune,
                label = stringResource(R.string.style_choose),
                background = Color.White.copy(alpha = 0.18f),
                tint = Color.White,
                iconSize = 20.dp,
                onClick = onChooseStyle,
            )
            Spacer(Modifier.width(8.dp))
        }
        BarDisc(
            icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            label = stringResource(if (playing) R.string.pause else R.string.liquid_play),
            background = Color.White,
            tint = Color.Black,
            iconSize = 26.dp,
            onClick = onPlay,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarDisc(icon: ImageVector, label: String, background: Color, tint: Color, iconSize: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(44.dp)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .background(background)
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun CoverFan(covers: List<String>, size: Dp, reduceMotion: Boolean) {
    val spread = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { spread.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 180f)) }
    val step = with(LocalDensity.current) { (size * 0.26f).toPx() }
    val offsets = when (covers.size) {
        1 -> listOf(0f)
        2 -> listOf(0.5f, -0.5f)
        else -> listOf(0f, -1f, 1f)
    }
    val drawOrder = covers.indices.sortedWith(compareByDescending<Int> { kotlin.math.abs(offsets[it]) }.thenByDescending { it })
    Box(Modifier.size(width = size * 1.52f, height = size * 1.1f), contentAlignment = Alignment.Center) {
        drawOrder.forEach { i ->
            val offset = offsets[i]
            val lead = i == 0
            Artwork(
                model = covers[i],
                shape = RoundedCornerShape(10.dp),
                hairline = false,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        val t = spread.value
                        translationX = offset * step * t
                        rotationZ = offset * 7f * t
                        val s = if (lead) 1f else 0.9f
                        scaleX = s
                        scaleY = s
                    }
                    .shadow(if (lead) 18.dp else 8.dp, RoundedCornerShape(10.dp), clip = false),
            )
        }
    }
}

@Composable
internal fun TopPicksSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        SkeletonBlock(
            Modifier
                .padding(start = PageMargin, end = PageMargin, top = 12.dp)
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(18.dp),
        )
        SkeletonBlock(
            Modifier
                .padding(start = PageMargin, top = 28.dp, bottom = 12.dp)
                .size(width = 130.dp, height = 22.dp),
            shape = RoundedCornerShape(6.dp),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = cardWidth(maxWidth, 2)
            LazyRow(
                contentPadding = PaddingValues(horizontal = PageMargin),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
            ) {
                items(2) {
                    SkeletonBlock(Modifier.size(width = width, height = CardHeight), shape = CardShape)
                }
            }
        }
    }
}

@Composable
internal fun QuietStart(online: Boolean) {
    val colors = Liquid.colors
    Column(
        Modifier
            .padding(horizontal = PageMargin)
            .padding(top = 24.dp, bottom = 6.dp)
            .fillMaxWidth(),
    ) {
        Text(
            text = stringResource(if (online) R.string.home_quiet_title else R.string.home_quiet_offline_title),
            style = LiquidTypography.title2,
            color = colors.label,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(if (online) R.string.home_quiet_body else R.string.home_quiet_offline_body),
            style = LiquidTypography.subheadline,
            color = colors.secondaryLabel,
        )
    }
}

@Composable
internal fun OfflineNotice() {
    val colors = Liquid.colors
    Row(
        Modifier
            .padding(horizontal = PageMargin)
            .padding(top = 6.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CloudOff, null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.home_offline_title),
            style = LiquidTypography.footnote,
            color = colors.secondaryLabel,
        )
    }
}
