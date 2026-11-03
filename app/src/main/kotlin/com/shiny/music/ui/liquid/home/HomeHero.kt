package com.shiny.music.ui.liquid.home

import coil3.imageLoader
import coil3.request.ImageRequest
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
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
    val already = remember(key) { key in seen }
    if (already) return Modifier
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        // marked first, so a fling that drops the row mid-reveal doesn't replay it
        seen.add(key)
        progress.animateTo(1f, tween(RevealMs, delayMillis = HomeReveal.burstDelay(), easing = RevealEasing))
    }
    val rise = with(LocalDensity.current) { 20.dp.toPx() }
    return Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

internal val RevealEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
internal const val RevealMs = 520

// reveals that start together (a page of rows landing at once) go one after another
internal object HomeReveal {
    private var burstAt = 0L
    private var burst = 0
    val seenCards = HashSet<String>()

    fun burstDelay(): Int {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - burstAt > 120) {
            burstAt = now
            burst = 0
        }
        return minOf(burst++, 6) * 60
    }
}

// a card fades and rises the first time it shows, each a beat after the one before it
@Composable
internal fun Modifier.cardReveal(key: String, index: Int): Modifier {
    val already = remember(key) { key in HomeReveal.seenCards }
    if (already) return this
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        HomeReveal.seenCards.add(key)
        progress.animateTo(1f, tween(RevealMs, delayMillis = minOf(index, 4) * 60, easing = RevealEasing))
    }
    val rise = with(LocalDensity.current) { 20.dp.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

// scrolling away the hero rides up a little, zooms, dims and fades, like a playlist cover
internal fun Modifier.heroScrollAway(progress: () -> Float): Modifier = graphicsLayer {
    val p = progress()
    translationY = p * size.height * 0.2f
    val zoom = 1f + p * 0.22f
    scaleX = zoom
    scaleY = zoom
    transformOrigin = TransformOrigin(0.5f, 0.35f)
    alpha = 1f - smoothstep(0.2f, 0.85f, p)
    // softens as it goes, from just past rest to a little over half way
    val soft = 30f * smoothstep(HeroStillFrom, 0.55f, p)
    renderEffect = if (soft > 0.5f) BlurEffect(soft, soft, androidx.compose.ui.graphics.TileMode.Clamp) else null
}.drawWithContent {
    drawContent()
    val p = progress()
    if (p > 0f) drawRect(Color.Black.copy(alpha = 0.22f * p))
}

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
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

// one slide of the top of home: what it is, why it's there, and what play does
internal class HeroCard(
    val opensPage: Boolean = false,
    val key: String,
    val label: String,
    val labelCover: String?,
    val labelName: String?,
    val roundCover: Boolean,
    val title: String,
    val art: String?,
    val mediaId: String?,
    val play: () -> Unit,
)

private data object GachaKey

// the top of home: one full-bleed slide at a time, each blurring into the next, with the
// random plays stack second so the first turn shows it
@Composable
internal fun HeroCarousel(
    modifier: Modifier = Modifier,
    hero: HomeHeroState,
    cards: List<HeroCard>,
    gacha: List<HeroCard>,
    activeId: String?,
    isPlaying: Boolean,
    reduceMotion: Boolean,
    actions: LiquidActions,
    scrollProgress: () -> Float = { 0f },
) {
    val pages: List<Any> = remember(cards, gacha) {
        buildList {
            addAll(cards)
            if (gacha.size >= 4) add(minOf(1, size), GachaKey)
        }
    }
    if (pages.isEmpty()) return
    val n = pages.size
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val height = LocalConfiguration.current.screenHeightDp.dp * HeroHeightFraction

    fun distance(i: Int): Float {
        val raw = (i - hero.pos.value) % n
        return ((raw + n + n / 2f) % n) - n / 2f
    }
    val visible by remember(n) { derivedStateOf { (0 until n).filter { abs(distance(it)) < 0.999f } } }

    val context = LocalContext.current
    val current by remember(n) { derivedStateOf { Math.floorMod(hero.pos.value.roundToInt(), n) } }
    LaunchedEffect(n, reduceMotion, dragging, busy) {
        if (reduceMotion || n < 2 || dragging) return@LaunchedEffect
        while (true) {
            val onGacha = pages[current] !is HeroCard
            // the random plays slide waits twice as long, and once it has rolled it stays until a swipe
            if (onGacha && busy) return@LaunchedEffect
            delay(if (onGacha) 11_000L else 5_500L)
            // scrolled away there's nobody to show the next slide to
            if (scrollProgress() > HeroStillFrom) continue
            hero.pos.animateTo(hero.pos.value.roundToInt() + 1f, HeroFade)
        }
    }
    // the next cover is fetched ahead so a turn never fades in from nothing
    LaunchedEffect(current, pages) {
        (pages[Math.floorMod(current + 1, n)] as? HeroCard)?.art?.let { url ->
            context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
            .pointerInput(n) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        busy = false
                    },
                    onDragEnd = {
                        dragging = false
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        scope.launch { hero.pos.animateTo(hero.pos.value.roundToInt().toFloat(), HeroSettle) }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { hero.pos.animateTo(hero.pos.value.roundToInt().toFloat(), HeroSettle) }
                    },
                ) { change, dx ->
                    change.consume()
                    scope.launch { hero.pos.snapTo(hero.pos.value - dx / size.width.toFloat()) }
                }
            },
    ) {
        visible.forEach { index ->
            val page = pages[index]
            key(if (page is HeroCard) page.key else "gacha") {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val away = abs(distance(index)).coerceAtMost(1f)
                            alpha = 1f - away
                            val scale = 1f + away * 0.04f
                            scaleX = scale
                            scaleY = scale
                            renderEffect = heroBlur(away * 28f)
                        },
                ) {
                    if (page is HeroCard) {
                        val playing = page.mediaId != null && page.mediaId == activeId
                        HeroArtSlide(
                            card = page,
                            playing = playing && isPlaying,
                            onPlay = { if (playing) actions.togglePlayPause() else page.play() },
                        )
                    } else {
                        HeroGacha(pool = gacha, activeId = activeId, isPlaying = isPlaying, actions = actions, onBusy = { busy = it })
                    }
                }
            }
        }
    }
}

private fun heroBlur(radius: Float) = if (radius > 0.5f) BlurEffect(radius, radius) else null

internal const val HeroHeightFraction = 0.6f
private val HeroFade = tween<Float>(1150, easing = CubicBezierEasing(0.32f, 0f, 0.15f, 1f))
internal const val HeroStillFrom = 0.08f
private val HeroSettle = tween<Float>(600, easing = FastOutSlowInEasing)
private val HeroPillFill = Color.White.copy(alpha = 0.64f)
private val HeroPillInk = Color(0xFF2F2F2F)
private val GachaPointerColor = Color(0xFFF0908D)
private val GachaRoll = CubicBezierEasing(0.15f, 0.45f, 0.2f, 1f)
private const val HeroDim = 0.36f

@Composable
private fun HeroArtSlide(card: HeroCard, playing: Boolean, onPlay: () -> Unit) {
    val background = Liquid.colors.background
    val scrim = remember(background) {
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.45f),
            0.22f to Color.Black.copy(alpha = 0.12f),
            0.5f to Color.Black.copy(alpha = 0.28f),
            0.82f to background.copy(alpha = 0.85f),
            1f to background,
        )
    }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(model = card.art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(scrim),
        )
        HeroInfo(
            kicker = if (playing) stringResource(R.string.home_now_playing) else card.label,
            labelName = if (playing) null else card.labelName,
            labelCover = card.labelCover,
            roundCover = card.roundCover,
            title = card.title,
            buttonText = stringResource(if (playing) R.string.pause else if (card.opensPage) R.string.home_hero_view else R.string.liquid_play),
            buttonIcon = if (playing) Icons.Rounded.Pause else if (card.opensPage) null else Icons.Rounded.PlayArrow,
            trailingArrow = card.opensPage && !playing,
            onButton = onPlay,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun HeroInfo(
    kicker: String,
    labelName: String?,
    labelCover: String?,
    roundCover: Boolean,
    title: String,
    buttonText: String,
    buttonIcon: ImageVector?,
    onButton: () -> Unit,
    modifier: Modifier = Modifier,
    trailingArrow: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.padding(start = HomeInset, end = HomeInset, bottom = 62.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kicker, style = HeroKickerStyle, color = Color.White.copy(alpha = 0.69f), maxLines = 1)
            if (labelName != null) {
                Spacer(Modifier.width(5.dp))
                if (labelCover != null) {
                    Artwork(
                        model = labelCover.resize(60, 60),
                        shape = if (roundCover) CircleShape else RoundedCornerShape(3.dp),
                        hairline = false,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(labelName, style = HeroKickerStyle.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(title, style = HomeTitleStyle, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .height(30.dp)
                .pressScale(interaction, 0.94f)
                .clip(CircleShape)
                .background(HeroPillFill)
                .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onButton)
                .padding(start = if (buttonIcon != null) 10.dp else 14.dp, end = if (trailingArrow) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (buttonIcon != null) {
                Icon(buttonIcon, null, tint = HeroPillInk, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(buttonText, style = HomeLabelStyle.copy(fontSize = 15.sp), color = HeroPillInk, maxLines = 1)
            if (trailingArrow) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = HeroPillInk, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private val HeroKickerStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = (-0.03).em)

// the random plays slide: a stack of covers down the right edge and a pointer at the one in
// focus. roll spins the stack, slows down, lands on a song and plays it
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroGacha(
    pool: List<HeroCard>,
    activeId: String?,
    isPlaying: Boolean,
    actions: LiquidActions,
    onBusy: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val spin = remember { Animatable(0f) }
    var rolling by remember { mutableStateOf(false) }
    var landed by remember { mutableStateOf<HeroCard?>(null) }
    val n = pool.size
    val playingIt = landed?.mediaId != null && landed?.mediaId == activeId

    fun roll() {
        if (rolling) return
        rolling = true
        landed = null
        onBusy(true)
        scope.launch {
            val target = spin.value.roundToInt() + 18 + kotlin.random.Random.nextInt(n)
            spin.animateTo(target.toFloat(), tween(4200, easing = GachaRoll))
            val card = pool[Math.floorMod(target, n)]
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            landed = card
            rolling = false
            card.play()
            delay(4000)
            onBusy(false)
        }
    }
    val tap = { if (playingIt) actions.togglePlayPause() else roll() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(GachaGlow),
    ) {
        val density = LocalDensity.current
        val unit = maxWidth / 402.dp
        val side = 221.dp * unit
        val unitPx = with(density) { (1.dp * unit).toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val shape = remember(unit) { RoundedCornerShape(25.dp * unit) }
        val base by remember { derivedStateOf { spin.value.roundToInt() } }
        for (k in -2..2) {
            val slot = base + k
            key(slot) {
                val card = pool[Math.floorMod(slot, n)]
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .size(side)
                        .graphicsLayer {
                            val d = slot - spin.value
                            val away = abs(d)
                            val near = away.coerceAtMost(1f)
                            val scale = 1f - (1f - 187f / 221f) * near
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(1f, 0.5f)
                            translationX = widthPx + 28f * unitPx - 221f * unitPx
                            val offset = (near * 221f + (away - 1f).coerceAtLeast(0f) * 207f) * unitPx
                            translationY = 230.5f * unitPx - 110.5f * unitPx + if (d < 0) -offset else offset
                            this.shape = shape
                            clip = true
                        }
                        .drawWithContent {
                            drawContent()
                            val near = abs(slot - spin.value).coerceAtMost(1f)
                            if (near > 0f) drawRect(Color.Black, alpha = HeroDim * near)
                        }
                        .combinedClickable(interactionSource = interaction, indication = null, onClick = tap),
                ) {
                    Artwork(model = card.art?.resize(600, 600), shape = shape, hairline = false, modifier = Modifier.fillMaxSize())
                }
            }
        }
        val pointer = remember { MutableInteractionSource() }
        Canvas(
            Modifier
                .offset(x = 86.dp * unit, y = 198.5.dp * unit)
                .size(68.dp * unit, 75.dp * unit)
                .pressScale(pointer, 0.9f)
                .combinedClickable(interactionSource = pointer, indication = null, role = Role.Button, onClick = tap),
        ) {
            val r = size.minDimension * 0.16f
            val path = Path().apply {
                moveTo(r, r)
                lineTo(size.width - r, size.height / 2f)
                lineTo(r, size.height - r)
                close()
            }
            drawPath(path, GachaPointerColor)
            drawPath(path, GachaPointerColor, style = Stroke(width = r * 2f, join = StrokeJoin.Round))
        }
        val card = landed
        HeroInfo(
            kicker = stringResource(
                when {
                    card == null -> R.string.home_gacha_kicker
                    playingIt && isPlaying -> R.string.home_now_playing
                    else -> R.string.home_gacha_got
                },
            ),
            labelName = null,
            labelCover = null,
            roundCover = false,
            title = card?.title ?: stringResource(R.string.home_gacha_title),
            buttonText = stringResource(if (rolling) R.string.home_gacha_rolling else R.string.home_gacha_roll),
            buttonIcon = Icons.Rounded.Casino,
            onButton = { roll() },
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

private val GachaGlow = Brush.verticalGradient(
    0f to Color(0xFF8E7F87),
    0.45f to Color(0xFF5B5059),
    0.8f to Color(0xFF231F23),
    1f to Color.Transparent,
)

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
