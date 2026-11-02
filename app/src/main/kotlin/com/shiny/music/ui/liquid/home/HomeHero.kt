package com.shiny.music.ui.liquid.home

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.spring
import androidx.compose.runtime.derivedStateOf
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.shiny.music.ui.liquid.SkeletonBlock
import com.shiny.music.ui.liquid.cachedArtworkTones
import com.shiny.music.ui.liquid.loadArtworkTones
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private val MixOrigin = PlaybackOrigin(RecSurface.HomeMix)
private val AroundNowOrigin = PlaybackOrigin(RecSurface.HomeAroundNow)
private val RediscoverOrigin = PlaybackOrigin(RecSurface.HomeRediscover)
private val HeroMaxWidth = 340.dp
private val HeroGap = 12.dp
private val HeroOverlap = 64.dp
private val HeroShape = RoundedCornerShape(22.dp)
private val HeroDisc = 50.dp
private val HeroTitleStyle = LiquidTypography.title1.copy(fontSize = 26.sp, lineHeight = 31.sp)
private val HeroArtistsStyle = LiquidTypography.subheadline.copy(fontSize = 14.sp, lineHeight = 18.sp)
private val AmbienceHeight = 640.dp
private val ExpoOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private const val TonePx = 240

@Stable
internal class HomeHeroState(val list: LazyListState) {
    val tones = mutableStateMapOf<Int, HeroTone>()
    val pos = Animatable(0f)

    fun position(): Float = pos.value

    fun field(pale: Boolean): Color {
        val at = position().coerceAtLeast(0f)
        val before = tones[floor(at).toInt()] ?: NeutralHeroTone
        val after = tones[ceil(at).toInt()] ?: before
        return if (pale) {
            lerp(before.paleField, after.paleField, at - floor(at))
        } else {
            lerp(before.field, after.field, at - floor(at))
        }
    }
}

@Composable
internal fun BoxScope.HomeAmbience(hero: HomeHeroState, page: LazyListState, reduceMotion: Boolean) {
    val pale = !Liquid.colors.isDark
    val fadeOverPx = with(LocalDensity.current) { 460.dp.toPx() }
    val appear = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val toned = hero.tones.isNotEmpty()
    LaunchedEffect(toned) {
        if (toned) appear.animateTo(1f, tween(900, easing = ExpoOut))
    }
    Spacer(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(AmbienceHeight)
            .graphicsLayer {
                val scrolled = when (page.firstVisibleItemIndex) {
                    0 -> page.firstVisibleItemScrollOffset.toFloat()
                    1 -> page.firstVisibleItemScrollOffset + 60.dp.toPx()
                    else -> fadeOverPx
                }
                translationY = -scrolled * 0.55f
                alpha = appear.value * (1f - scrolled / fadeOverPx).coerceIn(0f, 1f)
            }
            .drawBehind {
                val colour = hero.field(pale)
                drawRect(
                    Brush.verticalGradient(
                        0f to colour,
                        0.42f to colour.copy(alpha = 0.62f),
                        1f to colour.copy(alpha = 0f),
                    ),
                )
            },
    )
}

@Composable
internal fun rememberReveal(key: String, seen: MutableSet<String>, reduceMotion: Boolean): Modifier {
    if (reduceMotion) return Modifier
    val progress = remember(key) { Animatable(if (key in seen) 1f else 0f) }
    val scope = rememberCoroutineScope()
    val rise = with(LocalDensity.current) { 22.dp.toPx() }
    return Modifier
        .onPlaced {
            if (seen.add(key)) scope.launch { progress.animateTo(1f, tween(620, easing = ExpoOut)) }
        }
        .graphicsLayer {
            alpha = 0.2f + 0.8f * progress.value
            translationY = (1f - progress.value) * rise
        }
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
private fun cardTitle(section: HomeSection): String = when (section) {
    is HeroMixSection -> stringResource(
        if (section.offlineOnly) R.string.home_mix_offline else when (section.daypart) {
            Daypart.Morning -> R.string.home_mix_morning
            Daypart.Afternoon -> R.string.home_mix_afternoon
            Daypart.Evening -> R.string.home_mix_evening
            Daypart.Night -> R.string.home_mix_night
        },
    )
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
internal fun HeroCarousel(
    hero: HomeHeroState,
    mix: HeroMixSection?,
    picks: List<HomeSection>,
    takenCovers: List<String>,
    queueTitle: String?,
    isPlaying: Boolean,
    reduceMotion: Boolean,
    actions: LiquidActions,
    onPlayedDiscovery: () -> Unit,
    onChooseStyle: () -> Unit,
    onShown: @Composable (HomeSection) -> Unit,
) {
    val pickCovers = remember(picks, takenCovers) { topPickCovers(picks, takenCovers) }
    val cards = remember(mix, picks) { listOfNotNull<HomeSection>(mix) + picks }
    if (cards.isEmpty()) return
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    val last = cards.lastIndex
    val current by remember(cards) { derivedStateOf { hero.pos.value.roundToInt().coerceIn(0, last) } }

    // turns on its own while nobody is touching it, and holds still for reduced motion
    LaunchedEffect(cards.size, reduceMotion) {
        if (reduceMotion || cards.size < 2) return@LaunchedEffect
        while (true) {
            delay(5500)
            if (!dragging) {
                val next = (hero.pos.value.roundToInt() + 1).let { if (it > last) 0 else it }
                hero.pos.animateTo(next.toFloat(), HeroSlide)
            }
        }
    }

    val play: (HomeSection, String, List<Song>) -> Unit = { section, title, songs ->
        if (queueTitle == title) {
            actions.togglePlayPause()
        } else {
            when (section) {
                is HeroMixSection -> actions.playSongs(title, songs, origin = MixOrigin)
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
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        // laid out on a 402 wide frame and scaled to the screen
        val unit = maxWidth / 402.dp
        val side = 221.dp * unit
        val height = LocalConfiguration.current.screenHeightDp.dp * HeroHeightFraction
        val sidePx = with(density) { side.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val unitPx = with(density) { (1.dp * unit).toPx() }
        val textWidth = maxWidth + 28.dp * unit - 187.dp * unit - HomeInset - 12.dp

        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clipToBounds()
                .pointerInput(cards.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            val target = hero.pos.value.roundToInt().coerceIn(0, last)
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            scope.launch { hero.pos.animateTo(target.toFloat(), HeroGlide) }
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { hero.pos.animateTo(hero.pos.value.roundToInt().coerceIn(0, last).toFloat(), HeroGlide) }
                        },
                    ) { change, dx ->
                        change.consume()
                        scope.launch { hero.pos.snapTo((hero.pos.value - dx / (sidePx * 1.1f)).coerceIn(-0.35f, last + 0.35f)) }
                    }
                },
        ) {
            cards.forEachIndexed { index, section ->
                key(section.key) {
                    val cardName = cardTitle(section)
                    val cover = remember(section, pickCovers) {
                        if (section is HeroMixSection) section.barCovers().firstOrNull() else pickCovers[section.key]?.firstOrNull()
                    }
                    HeroStackCard(
                        index = index,
                        hero = hero,
                        side = side,
                        sidePx = sidePx,
                        widthPx = widthPx,
                        unitPx = unitPx,
                        corner = 25.dp * unit,
                        cover = cover,
                        onClick = {
                            if (index == current) {
                                play(section, cardName, if (section is HeroMixSection) section.songs else section.pickSongs)
                            } else {
                                scope.launch { hero.pos.animateTo(index.toFloat(), HeroGlide) }
                            }
                        },
                    )
                }
            }

            val section = cards[current]
            onShown(section)
            val title = cardTitle(section)
            val songs = remember(section) { if (section is HeroMixSection) section.songs else section.pickSongs }
            val artists = remember(section) { artistsLine(songs) }
            val active = queueTitle == title

            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    val up = targetState > initialState
                    (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.86f, stiffness = 380f)) { if (up) it / 5 else -it / 5 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically(tween(200)) { if (up) -it / 6 else it / 6 })
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = HomeInset, bottom = 56.dp)
                    .width(textWidth),
                label = "heroText",
            ) { _ ->
                HeroText(
                    kicker = heroKicker(section),
                    title = title,
                    artists = artists,
                    playing = active && isPlaying,
                    onPlay = { play(section, title, songs) },
                    onChooseStyle = if (section is HeroMixSection && section.styleable) onChooseStyle else null,
                )
            }
        }
    }
}

private val HeroGlide = spring<Float>(dampingRatio = 0.84f, stiffness = 160f)

internal const val HeroHeightFraction = 0.6f
private val HeroSlide = tween<Float>(1150, easing = CubicBezierEasing(0.32f, 0f, 0.15f, 1f))
private const val HeroDim = 0.36f

// the current card big against the right edge and a little off it, the others smaller above
// and below. all of it is read in the draw phase so a swipe recomposes nothing
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroStackCard(
    index: Int,
    hero: HomeHeroState,
    side: Dp,
    sidePx: Float,
    widthPx: Float,
    unitPx: Float,
    corner: Dp,
    cover: String?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { side.roundToPx() }
    val model = remember(cover, px) { cover?.resize(px * 4 / 3, px * 4 / 3) }
    val toneUrl = remember(cover) { cover?.resize(TonePx, TonePx) }
    LaunchedEffect(toneUrl, index) {
        if (toneUrl == null) return@LaunchedEffect
        val tones = cachedArtworkTones(toneUrl) ?: loadArtworkTones(context, toneUrl) ?: return@LaunchedEffect
        hero.tones[index] = heroTone(tones.deep, tones.vivid)
    }
    val shape = remember(corner) { RoundedCornerShape(corner) }
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(side)
            .graphicsLayer {
                val d = index - hero.pos.value
                val away = abs(d)
                val near = away.coerceAtMost(1f)
                val scale = 1f - (1f - 187f / 221f) * near
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(1f, 0.5f)
                translationX = widthPx + 28f * unitPx - sidePx
                val offset = (near * 221f + (away - 1f).coerceAtLeast(0f) * 207f) * unitPx
                translationY = 230.5f * unitPx - sidePx / 2f + if (d < 0) -offset else offset
                alpha = if (away > 2.6f) 0f else 1f
                this.shape = shape
                clip = true
            }
            .drawWithContent {
                drawContent()
                val near = abs(index - hero.pos.value).coerceAtMost(1f)
                if (near > 0f) drawRect(Color.Black, alpha = HeroDim * near)
            }
            .pressScale(interaction, 0.97f)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Artwork(model = model, shape = shape, hairline = false, modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroText(
    kicker: String,
    title: String,
    artists: String,
    playing: Boolean,
    onPlay: () -> Unit,
    onChooseStyle: (() -> Unit)?,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val fill = if (colors.isDark) Color.White.copy(alpha = 0.64f) else Color.Black.copy(alpha = 0.08f)
    val ink = if (colors.isDark) Color(0xFF2F2F2F) else colors.label
    Column {
        Text(kicker, style = HomeLabelStyle, color = colors.label.copy(alpha = 0.69f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(title, style = HomeTitleStyle, color = colors.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (artists.isNotBlank()) {
            Text(artists, style = HomeCardSubtitleStyle, color = colors.label.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .height(34.dp)
                    .pressScale(interaction, 0.94f)
                    .clip(CircleShape)
                    .background(fill)
                    .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onPlay)
                    .padding(start = 10.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Crossfade(targetState = playing, animationSpec = tween(180), label = "heroPlay") { on ->
                    Icon(if (on) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = ink, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(if (playing) R.string.pause else R.string.liquid_play),
                    style = HomeLabelStyle,
                    color = ink,
                )
            }
            if (onChooseStyle != null) {
                Spacer(Modifier.width(6.dp))
                StyleButton(onChooseStyle)
            }
        }
    }
}

@Composable
private fun heroKicker(section: HomeSection): String = stringResource(
    when (section) {
        is HeroMixSection -> R.string.home_kicker_mix
        is DiscoveryMixSection -> R.string.home_kicker_discovery
        is AroundNowSection -> R.string.home_kicker_around
        is RediscoverSection -> R.string.home_kicker_rediscover
        else -> R.string.home_kicker_start
    },
)

private val HeroCardShape = RoundedCornerShape(30.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StyleButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(R.string.style_choose)
    Box(
        Modifier
            .padding(end = 4.dp)
            .size(HeroDisc)
            .pressScale(interaction, 0.88f)
            .clip(CircleShape)
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Tune, null, tint = Liquid.colors.label, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun HeroSkeleton() {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        val width = (maxWidth * 0.78f).coerceAtMost(HeroMaxWidth)
        LazyRow(
            contentPadding = PaddingValues(horizontal = (maxWidth - width) / 2),
            horizontalArrangement = Arrangement.spacedBy(HeroGap),
            userScrollEnabled = false,
        ) {
            items(2) {
                SkeletonBlock(Modifier.size(width = width, height = width + HeroOverlap), shape = HeroShape)
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
