package com.shiny.music.ui.liquid.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.music.innertube.models.ArtistItem
import com.shiny.music.R
import com.shiny.music.discover.AppleArtwork
import com.shiny.music.discover.ChartArtist
import com.shiny.music.discover.ChartChoice
import com.shiny.music.discover.ChartEntry
import com.shiny.music.discover.ChartRegion
import com.shiny.music.discover.ChartRegions
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LocalLiquidBottomInset
import com.shiny.music.ui.liquid.NeutralTones
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.PanelShape
import com.shiny.music.ui.liquid.SkeletonBlock
import com.shiny.music.ui.liquid.SongRow
import com.shiny.music.ui.liquid.artworkRadius
import com.shiny.music.ui.liquid.home.OnColorButton
import com.shiny.music.ui.liquid.rememberArtworkTones
import com.shiny.music.ui.liquid.rememberRowHighlight
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize
import com.shiny.music.viewmodels.DiscoverEvent
import com.shiny.music.viewmodels.DiscoverNotice
import com.shiny.music.viewmodels.DiscoverViewModel
import java.net.URLEncoder

internal val ChartRankStyle = LiquidTypography.headline.copy(fontSize = 17.sp, fontFeatureSettings = "tnum", textAlign = TextAlign.End)
internal val DiscoverSectionStyle = LiquidTypography.title1.copy(fontSize = 26.sp, lineHeight = 31.sp)
internal val DiscoverHeroStyle = LiquidTypography.title1.copy(fontSize = 30.sp, lineHeight = 35.sp)
internal val DiscoverEyebrowStyle = LiquidTypography.caption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)

internal val ChartArtwork = 52.dp
internal val ChartRowHeight = ChartArtwork + 14.dp
private val HeroArtwork = 92.dp
private val AlbumTitleStyle = LiquidTypography.subheadline.copy(lineHeight = 19.sp, fontWeight = FontWeight.Medium)

internal fun rankWidth(highest: Int): Dp = when {
    highest >= 100 -> 42.dp
    highest >= 10 -> 34.dp
    else -> 26.dp
}

internal fun peekWidth(available: Dp, visible: Float, gap: Dp, min: Dp, max: Dp): Dp =
    ((available - PageMargin - gap * kotlin.math.floor(visible)) / visible).coerceIn(min, max)

@Composable
internal fun DiscoverEyebrow(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = DiscoverEyebrowStyle,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
internal fun DiscoverLifecycle(viewModel: DiscoverViewModel) {
    val owner = LocalLifecycleOwner.current
    val viewer = remember { Any() }
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setVisible(viewer, true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setVisible(viewer, false)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            viewModel.setVisible(viewer, false)
        }
    }
}

@Composable
internal fun DiscoverEvents(viewModel: DiscoverViewModel, actions: LiquidActions) {
    val current by rememberUpdatedState(actions)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DiscoverEvent.Play -> current.playerConnection.playQueue(event.queue)
                is DiscoverEvent.Open -> current.open(event.item)
                is DiscoverEvent.SongMenu -> current.menu(event.song)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DiscoverHeader(
    title: String,
    onSeeAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val seeAll = stringResource(R.string.liquid_see_all)
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 4.dp)
            .then(
                if (onSeeAll != null) {
                    Modifier
                        .pressScale(interaction, 0.985f)
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = seeAll,
                            onClick = onSeeAll,
                        )
                } else {
                    Modifier
                }
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = PageMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = DiscoverSectionStyle,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .semantics { heading() },
        )
        if (onSeeAll != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.secondaryLabel,
                modifier = Modifier
                    .padding(start = 1.dp)
                    .size(26.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChartHero(
    songs: List<ChartEntry>,
    region: ChartRegion,
    drift: (() -> Float)?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onOpen: () -> Unit,
) {
    val density = LocalDensity.current
    val lead = songs.firstOrNull() ?: return
    val coverPx = with(density) { HeroArtwork.roundToPx() }
    val cover = remember(lead.artwork, coverPx) { AppleArtwork.square(lead.artwork, coverPx * 2) }
    val tones = rememberArtworkTones(cover, NeutralTones)
    val ground = lerp(tones.deep, Color.Black, 0.22f)
    val driftPx = with(density) { 14.dp.toPx() }
    val country = ChartRegions.name(region.storefront)
    val interaction = remember { MutableInteractionSource() }
    val eyebrow = stringResource(R.string.discover_hero_eyebrow, country)
    val title = stringResource(R.string.discover_hero_title, country)
    val numberOne = stringResource(R.string.discover_number_one)
    Column(
        Modifier
            .padding(horizontal = PageMargin)
            .padding(top = 8.dp)
            .fillMaxWidth()
            .pressScale(interaction, 0.985f)
            .clip(PanelShape)
            .background(ground)
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "$eyebrow. $title. $numberOne: ${lead.title}, ${lead.artist}" }
            .padding(20.dp),
    ) {
        DiscoverEyebrow(eyebrow, Color.White.copy(alpha = 0.66f))
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = DiscoverHeroStyle,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                model = cover,
                shape = RoundedCornerShape(10.dp),
                hairline = false,
                modifier = Modifier
                    .size(HeroArtwork)
                    .graphicsLayer {
                        val t = drift?.invoke() ?: 0f
                        translationY = t * driftPx
                        val s = 1f - t * 0.04f
                        scaleX = s
                        scaleY = s
                    },
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                DiscoverEyebrow(numberOne, Color.White.copy(alpha = 0.66f))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = lead.title,
                    style = LiquidTypography.headline.copy(fontSize = 18.sp, lineHeight = 23.sp),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = lead.artist,
                    style = LiquidTypography.subheadline,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OnColorButton(
                stringResource(R.string.liquid_play),
                Icons.Rounded.PlayArrow,
                primary = true,
                onClick = onPlay,
                modifier = Modifier.weight(1f),
            )
            OnColorButton(
                stringResource(R.string.liquid_shuffle),
                Icons.Rounded.Shuffle,
                primary = false,
                onClick = onShuffle,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun ChartHeroSkeleton() {
    SkeletonBlock(
        Modifier
            .padding(horizontal = PageMargin)
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(296.dp),
        shape = PanelShape,
    )
}

@Composable
internal fun ChartGridSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        SkeletonBlock(
            Modifier
                .padding(start = PageMargin, top = 34.dp, bottom = 12.dp)
                .size(width = 150.dp, height = 24.dp),
            shape = RoundedCornerShape(6.dp),
        )
        repeat(4) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ChartRowHeight)
                    .padding(horizontal = PageMargin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(rankWidth(20)))
                SkeletonBlock(Modifier.size(ChartArtwork), RoundedCornerShape(artworkRadius(ChartArtwork)))
                Spacer(Modifier.width(13.dp))
                Column {
                    SkeletonBlock(Modifier.size(width = 170.dp, height = 14.dp), RoundedCornerShape(4.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(Modifier.size(width = 110.dp, height = 12.dp), RoundedCornerShape(4.dp))
                }
            }
        }
    }
}

@Composable
internal fun ChartSongRow(
    entry: ChartEntry,
    isActive: Boolean,
    isPlaying: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    showSeparator: Boolean = true,
    startPadding: Dp = PageMargin,
    artworkSize: Dp = ChartArtwork,
    highestRank: Int = entry.position,
) {
    val colors = Liquid.colors
    val px = with(LocalDensity.current) { artworkSize.roundToPx() }
    val artwork = remember(entry.artwork, px) { AppleArtwork.square(entry.artwork, (px * 1.5f).toInt()) }
    SongRow(
        title = entry.title,
        subtitle = entry.artist,
        artwork = artwork,
        onClick = onClick,
        onLongClick = onMore,
        onMore = onMore,
        isActive = isActive,
        isPlaying = isPlaying,
        explicit = entry.explicit,
        leadingRank = entry.position,
        leadingRankStyle = ChartRankStyle,
        leadingRankColor = colors.secondaryLabel,
        leadingRankWidth = rankWidth(highestRank),
        showSeparator = showSeparator,
        startPadding = startPadding,
        artworkSize = artworkSize,
        trailing = if (busy) {
            { ActivityIndicator(size = 18.dp, modifier = Modifier.padding(horizontal = 4.dp)) }
        } else {
            null
        },
        modifier = modifier,
    )
}

@Composable
internal fun ArtistPhoto(name: String, photo: String?, sizePx: Int, modifier: Modifier = Modifier) {
    val colors = Liquid.colors
    if (photo != null) {
        Artwork(model = photo.resize(sizePx, sizePx), shape = CircleShape, modifier = modifier)
        return
    }
    val initial = remember(name) { name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "" }
    Box(
        modifier
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(colors.gray4, colors.gray5))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = LiquidTypography.title1.copy(fontWeight = FontWeight.SemiBold),
            color = colors.secondaryLabel,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArtistTile(
    artist: ChartArtist,
    rank: Int,
    photo: ArtistItem?,
    busy: Boolean,
    onClick: () -> Unit,
    width: Dp = 112.dp,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val px = with(LocalDensity.current) { width.roundToPx() }
    Column(
        Modifier
            .width(width)
            .pressScale(interaction, 0.965f)
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            ArtistPhoto(artist.name, photo?.thumbnail, px, Modifier.fillMaxSize())
            if (busy) BusyVeil(CircleShape)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = artist.name,
            style = LiquidTypography.subheadline.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "$rank · " + pluralStringResource(R.plurals.discover_artist_songs_short, artist.songCount, artist.songCount),
            style = LiquidTypography.footnote,
            color = colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun BoxScope.BusyVeil(shape: Shape) {
    Box(
        Modifier
            .matchParentSize()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.38f)),
        contentAlignment = Alignment.Center,
    ) {
        ActivityIndicator(color = Color.White, size = 22.dp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumTile(
    entry: ChartEntry,
    busy: Boolean,
    onClick: () -> Unit,
    width: Dp = 152.dp,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val px = with(LocalDensity.current) { (if (width == Dp.Unspecified) 200.dp else width).roundToPx() }
    val artwork = remember(entry.artwork, px) { AppleArtwork.square(entry.artwork, (px * 1.5f).toInt()) }
    val shape = RoundedCornerShape(if (width == Dp.Unspecified) 8.dp else artworkRadius(width))
    Column(
        Modifier
            .then(if (width == Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.width(width))
            .pressScale(interaction, 0.965f)
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Artwork(model = artwork, shape = shape, modifier = Modifier.fillMaxSize())
            if (busy) BusyVeil(shape)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = entry.position.toString(),
                style = AlbumTitleStyle.copy(fontFeatureSettings = "tnum"),
                color = colors.tertiaryLabel,
                maxLines = 1,
                modifier = Modifier.padding(end = 6.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = AlbumTitleStyle,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.artist,
                    style = LiquidTypography.subheadline.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VideoCard(
    entry: ChartEntry,
    busy: Boolean,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 292.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val px = with(LocalDensity.current) { (if (width == Dp.Unspecified) 360.dp else width).roundToPx() }
    val still = remember(entry.artwork, px) { AppleArtwork.landscape(entry.artwork, px) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .then(if (width == Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.width(width))
            .aspectRatio(16f / 9f)
            .pressScale(interaction, 0.975f)
            .clip(shape)
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onPlay, onLongClick = onMore),
    ) {
        Artwork(
            model = still,
            shape = shape,
            placeholder = Icons.Rounded.Videocam,
            hairline = false,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.30f to Color.Transparent,
                        0.66f to Color.Black.copy(alpha = 0.46f),
                        1f to Color.Black.copy(alpha = 0.86f),
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            DiscoverEyebrow("No. ${entry.position}", Color.White.copy(alpha = 0.72f))
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.title,
                style = LiquidTypography.headline,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.artist,
                style = LiquidTypography.footnote,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) BusyVeil(shape)
    }
}

@Composable
internal fun BoxScope.DiscoverNoticeHost(
    notice: DiscoverNotice?,
    reduceMotion: Boolean,
    navController: NavController,
    onDismiss: (Long) -> Unit,
    onChoose: (Long, ChartChoice) -> Unit,
) {
    val colors = Liquid.colors
    val bottom = LocalLiquidBottomInset.current
    LaunchedEffect(notice?.id) {
        val current = notice ?: return@LaunchedEffect
        kotlinx.coroutines.delay(
            when (current.kind) {
                DiscoverNotice.Kind.Choices -> 12_000
                DiscoverNotice.Kind.Alternative -> 3_500
                else -> 5_000
            }
        )
        onDismiss(current.id)
    }
    var shown by remember { mutableStateOf(notice) }
    if (notice != null) shown = notice
    AnimatedVisibility(
        visible = notice != null,
        enter = if (reduceMotion) fadeIn() else fadeIn() + slideInVertically(spring(dampingRatio = 0.86f, stiffness = 520f)) { it / 2 },
        exit = if (reduceMotion) fadeOut() else fadeOut() + slideOutVertically { it / 3 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottom + 12.dp)
            .padding(horizontal = 12.dp),
    ) {
        val current = shown ?: return@AnimatedVisibility
        val title = when (current.kind) {
            DiscoverNotice.Kind.NoMatch, DiscoverNotice.Kind.Choices -> stringResource(R.string.discover_no_match, current.title)
            DiscoverNotice.Kind.Unreachable -> stringResource(R.string.discover_unreachable, current.title)
            DiscoverNotice.Kind.Alternative -> stringResource(R.string.discover_alternative, current.title)
        }
        val body = when (current.kind) {
            DiscoverNotice.Kind.NoMatch -> stringResource(R.string.discover_no_match_body)
            DiscoverNotice.Kind.Choices -> stringResource(R.string.discover_choices_body)
            DiscoverNotice.Kind.Unreachable -> stringResource(R.string.discover_unreachable_body)
            DiscoverNotice.Kind.Alternative -> null
        }
        val searchable = current.kind == DiscoverNotice.Kind.NoMatch || current.kind == DiscoverNotice.Kind.Choices
        val surface = if (colors.isDark) Color(0xFF252527) else Color(0xFFF7F7F8)
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(20.dp), clip = false)
                .clip(RoundedCornerShape(20.dp))
                .background(surface)
                .padding(vertical = 6.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (body != null) {
                        Text(body, style = LiquidTypography.footnote, color = colors.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (searchable) {
                    NoticeAction(stringResource(R.string.discover_search)) {
                        onDismiss(current.id)
                        navController.navigate("search/${URLEncoder.encode(current.query, "UTF-8")}")
                    }
                }
            }
            current.choices.forEach { choice ->
                ChoiceRow(choice) { onChoose(current.id, choice) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoticeAction(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = label,
        style = LiquidTypography.headline,
        color = Liquid.colors.accent,
        modifier = Modifier
            .clip(CircleShape)
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChoiceRow(choice: ChartChoice, onClick: () -> Unit) {
    val colors = Liquid.colors
    val px = with(LocalDensity.current) { 44.dp.roundToPx() * 2 }
    val interaction = remember { MutableInteractionSource() }
    val kind = stringResource(if (choice.isAlbum) R.string.discover_choice_album else R.string.discover_choice_song)
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(interactionSource = interaction, indication = rememberRowHighlight(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(model = choice.thumbnail?.resize(px, px), shape = RoundedCornerShape(6.dp), modifier = Modifier.size(44.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(choice.title, style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.Medium), color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$kind · ${choice.artists}", style = LiquidTypography.footnote, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            imageVector = if (choice.isAlbum) Icons.Rounded.ChevronRight else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = colors.tertiaryLabel,
            modifier = Modifier.size(20.dp),
        )
    }
}
