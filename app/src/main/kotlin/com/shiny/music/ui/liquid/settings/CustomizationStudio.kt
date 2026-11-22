package com.shiny.music.ui.liquid.settings

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.R
import com.shiny.music.constants.LiquidGlassGlobalEnabledKey
import com.shiny.music.constants.SwipeToSongKey
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.InterDisplay
import com.shiny.music.ui.liquid.InterText
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.customize.AnimationSpeeds
import com.shiny.music.ui.liquid.customize.BlurQuality
import com.shiny.music.ui.liquid.customize.CustomizePrefs
import com.shiny.music.ui.liquid.customize.DefaultSwipeLeft
import com.shiny.music.ui.liquid.customize.DefaultSwipeRight
import com.shiny.music.ui.liquid.customize.HomeLayoutPrefs
import com.shiny.music.ui.liquid.customize.HomePart
import com.shiny.music.ui.liquid.customize.HomeStyle
import com.shiny.music.ui.liquid.customize.LocalPreviewArtist
import com.shiny.music.ui.liquid.customize.LocalPreviewArtwork
import com.shiny.music.ui.liquid.customize.LocalPreviewTitle
import com.shiny.music.ui.liquid.customize.LyricsStyle
import com.shiny.music.ui.liquid.customize.PlayerStyle
import com.shiny.music.ui.liquid.customize.PlaylistStyle
import com.shiny.music.ui.liquid.customize.SongListStyle
import com.shiny.music.ui.liquid.customize.PreviewArtwork
import com.shiny.music.ui.liquid.customize.SongSwipeAction
import com.shiny.music.ui.liquid.customize.animationSpeedLabel
import com.shiny.music.ui.liquid.customize.rememberPlayerStyle
import com.shiny.music.ui.liquid.glassPressScale
import com.shiny.music.ui.liquid.liquidBottomPadding
import com.shiny.music.ui.liquid.rememberGlassPress
import com.shiny.music.ui.liquid.statusBarHeight
import com.shiny.music.utils.rememberEnumPreference
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.rememberPreference
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val PageMargin = 20.dp
private val PageMaxWidth = 680.dp
private val CardPadding = 20.dp
private const val MaxCoverRadius = 45f
private val CardShape = RoundedCornerShape(22.dp)
private val SliderCardShape = RoundedCornerShape(20.dp)
private val RoundingCardHeight = 186.dp
private val StyleCardHeight = 187.dp
private val CardTextInset = 22.dp
private val SwatchWidth = 146.dp
private val SwatchHeight = 135.dp
private val SwatchColor = Color(0xFFD9D9D9)
private val ArcGap = 19.dp
private val ArcTail = 12.dp
private val HighlightColor = Color.White.copy(alpha = 0.9f)
private val PreviewWidth = 133.dp
private val BlurPreviewWidth = 104.dp
private val FrameShape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)
private val FloatingFrameShape = RoundedCornerShape(15.dp)
private val FrameFill = Color(0xFF2B2B2D)
private val ArtFill = Brush.verticalGradient(listOf(Color(0xFF636365), Color(0xFF3F3F41)))
private val SoftArtFill = Brush.verticalGradient(listOf(Color(0xFF545456), FrameFill))
private val ArtLight = Color(0xFF7C7C7E)
private val Ink = Color.White.copy(alpha = 0.92f)
private val Bone = Color.White.copy(alpha = 0.2f)
private val BoneDim = Color.White.copy(alpha = 0.1f)
private val Pill = RoundedCornerShape(50)
private val PillShape = RoundedCornerShape(14.dp)
private val CardFill = Color(0xFF1C1C1E)
private val RowFill = Color(0xFF26262A)
private val Secondary = Color(0xFF9A9AA0)
private val PreviewArt = Brush.linearGradient(listOf(Color(0xFFF28BA8), Color(0xFF8C9EF2), Color(0xFFF2A46B)))
private val PreviewTile = Brush.linearGradient(listOf(Color(0xFF6BD9C4), Color(0xFFB98CF2)))
private val NavPreviewScreen = Brush.verticalGradient(0f to FrameFill, 0.13f to Color(0xFF545456))
private val NavPreviewLip = Color(0xFF414143)
private val NavPreviewIcon = Color(0xFFD9D9D9)

private val PageTitleStyle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.04).em)
private val CardLabelStyle = TextStyle(fontFamily = InterText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 14.sp, letterSpacing = (-0.03).em)
private val CardValueStyle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-0.045).em, color = Color.White)
private val SmallLabelStyle = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = (-0.01).em)
private val CaptionStyle = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = (-0.01).em)
private val SliderTitleStyle = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = (-0.03).em)
private val PillTextStyle = TextStyle(fontFamily = InterText, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = (-0.02).em)
private val SectionTitleStyle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.05).em)
private val SwipeTitleStyle = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.02).em)
private val SwipeSubtitleStyle = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Medium, fontSize = 13.sp)

private enum class CustomiseSection(val title: String, val words: String) {
    Rounding("Cover rounding", "cover rounding corner radius square rounded art"),
    Playlist("Playlist styles", "playlist styles flow classic minimal header"),
    Player("Player styles", "player styles shiny soft turntable poster timeline lyrics video bedside clock editor customize"),
    Home("Home styles", "home styles showcase quick play shortcuts custom home screen sections shelves show hide liked songs downloads on this device recently added recently played most played frequently listened recommendations community jump back in history live shows artists playlists forget music videos youtube hits"),
    SongList("Song lists", "song lists songs recently added liked history downloaded stack carousel posters cards records list rows"),
    Blur("Blur", "blur glass frosted progressive flat quality low high strength px"),
    ElementBlur("Element blur", "element blur lyrics lines cover zoom titles menus page swipes"),
    Animation("Animation", "animation speed motion transitions off faster slower"),
    Lyrics("Lyrics animation", "lyrics animation style shiny rise calm plain glow wave"),
    TopBar("Top Bar on player", "top bar status bar player clock battery icons hide"),
    BottomBar("Bottom Bar on player", "bottom bar navigation bar nav buttons gesture handle back home recents player hide"),
    Gesture("Gesture", "gesture swipe left right slide songs player close down");

    fun matches(query: String): Boolean {
        val words = query.trim().lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val haystack = (this.words + " " + title).lowercase()
        return words.all { it in haystack }
    }
}

@Composable
fun CustomizeStudioScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }
    val connection = LocalPlayerConnection.current
    val metadata = connection?.mediaMetadata?.collectAsState()?.value
    val shown = CustomiseSection.entries.filter { it.matches(query) }

    CompositionLocalProvider(
        LocalPreviewArtwork provides metadata?.thumbnailUrl,
        LocalPreviewTitle provides (metadata?.title?.takeIf { it.isNotBlank() } ?: "Shiny Music"),
        LocalPreviewArtist provides (metadata?.artists?.joinToString { it.name }.orEmpty()),
    ) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = PageMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin)
                    .padding(top = statusBarHeight() + 18.dp, bottom = liquidBottomPadding() + 32.dp),
            ) {
                LiquidBackButton(onClick = { navController.navigateUp() })
                Spacer(Modifier.height(14.dp))
                Text(text = "Customise", style = PageTitleStyle, color = Color.White)
                Spacer(Modifier.height(16.dp))
                CustomiseSearchBar(query = query, onQueryChange = { query = it })
                Spacer(Modifier.height(18.dp))
                if (shown.isEmpty()) {
                    Text(
                        text = "Nothing here matches “$query”. Try another word.",
                        fontFamily = InterText,
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    )
                }
                shown.forEachIndexed { index, section ->
                    if (index > 0) SectionRule()
                    CustomiseSectionContent(section, navController)
                }
            }
        }
    }
}

@Composable
private fun CustomiseSectionContent(section: CustomiseSection, navController: NavController) {
    val pagerBleed = Modifier.bleedHorizontally(PageMargin)
    val (rounding, setRounding) = rememberPreference(CustomizePrefs.CoverRounding, CustomizePrefs.CoverRoundingDefault)
    val coverRadius = rounding.dp
    when (section) {
        CustomiseSection.Rounding -> CoverRoundingSection(rounding, setRounding)
        CustomiseSection.Playlist -> {
            val (style, setStyle) = rememberEnumPreference(CustomizePrefs.PlaylistStyle, PlaylistStyle.Classic)
            StylePagerSection("Playlist styles", PlaylistStyle.entries, style, { it.title }, setStyle, pagerBleed) {
                PlaylistStylePreview(it, coverRadius)
            }
        }
        CustomiseSection.Player -> {
            val style by rememberPlayerStyle()
            val context = androidx.compose.ui.platform.LocalContext.current
            val connection = LocalPlayerConnection.current
            PlayerStyleCard(style) {
                if (connection?.player?.currentMediaItem != null) {
                    com.shiny.music.ui.liquid.player.studio.PlayerStudioRequests.request()
                } else {
                    android.widget.Toast.makeText(context, "Play a song first to open player customisation", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        CustomiseSection.Home -> {
            val (style, setStyle) = rememberEnumPreference(CustomizePrefs.HomeStyle, HomeStyle.Hero)
            var viewing by remember { mutableStateOf(style) }
            Column {
                StylePagerSection("Home styles", HomeStyle.entries, style, { it.title }, setStyle, pagerBleed, onPage = { viewing = it }) {
                    HomePreview(it, coverRadius)
                }
                Spacer(Modifier.height(26.dp))
                HomeScreenSection(viewing, inUse = viewing == style)
            }
        }
        CustomiseSection.SongList -> {
            val (style, setStyle) = rememberEnumPreference(CustomizePrefs.SongListStyle, SongListStyle.List)
            StylePagerSection("Song lists", SongListStyle.entries, style, { it.title }, setStyle, pagerBleed) {
                SongListStylePreview(it, coverRadius)
            }
        }
        CustomiseSection.Blur -> BlurSection()
        CustomiseSection.ElementBlur -> ElementBlurSection()
        CustomiseSection.Animation -> AnimationSection()
        CustomiseSection.Lyrics -> {
            val (style, setStyle) = rememberEnumPreference(CustomizePrefs.LyricsStyle, LyricsStyle.Shiny)
            StylePagerSection("Lyrics\nAnimation", LyricsStyle.entries, style, { it.title }, setStyle, pagerBleed) {
                LyricsLivePreview(it)
            }
        }
        CustomiseSection.TopBar -> {
            val (on, setOn) = rememberPreference(CustomizePrefs.PlayerStatusBar, true)
            EffectCard(title = "Top Bar on player", checked = on, onCheckedChange = setOn, preview = { StatusBarPreview(on) }) {
                Text("The phone's clock and icons over the open player", style = CaptionStyle, color = Color.White.copy(alpha = 0.5f))
            }
        }
        CustomiseSection.BottomBar -> {
            val (on, setOn) = rememberPreference(CustomizePrefs.PlayerNavBar, true)
            EffectCard(title = "Bottom Bar on player", checked = on, onCheckedChange = setOn, floatingPreview = true, preview = { NavBarPreview(on) }) {
                Text("The phone's navigation buttons under the open player. Off, the player runs to the bottom of the screen", style = CaptionStyle, color = Color.White.copy(alpha = 0.5f))
            }
        }
        CustomiseSection.Gesture -> GestureSection()
    }
}

private fun Modifier.bleedHorizontally(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = (amount * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-amount.roundToPx(), 0) }
}

@Composable
private fun CustomiseSearchBar(query: String, onQueryChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(CardFill)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = InterText, fontSize = 16.sp, color = Color.White),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search settings", fontFamily = InterText, fontSize = 16.sp, color = Color.White.copy(alpha = 0.5f))
                    inner()
                },
            )
            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = "Clear search",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp)
                        .clickable { onQueryChange("") },
                )
            }
        }
    }
}

@Composable
private fun SectionRule() {
    Box(
        Modifier
            .padding(top = 18.dp, bottom = 20.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.1f)),
    )
}

@Composable
private fun CoverRoundingSection(radiusPx: Float, onRadiusChange: (Float) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var lastTick by remember { mutableIntStateOf((radiusPx / 5f).roundToInt()) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shownRadius = dragging ?: radiusPx
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RoundingCardHeight)
                .clip(CardShape)
                .background(CardFill),
        ) {
            RoundingSwatch(radiusPx = shownRadius, modifier = Modifier.matchParentSize())
            Text(
                text = "Cover\nrounding",
                style = CardLabelStyle,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(start = CardTextInset, top = CardTextInset),
            )
            BasicText(
                text = "${shownRadius.roundToInt()}px",
                style = CardValueStyle,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = CardTextInset, bottom = 24.dp),
            )
        }
        Spacer(Modifier.height(11.dp))
        CustomiseSliderCard(
            title = "customize it",
            startLabel = "square",
            endLabel = "more rounded",
            fraction = shownRadius / MaxCoverRadius,
            onFractionChange = { f ->
                val next = f * MaxCoverRadius
                val tick = (next / 5f).roundToInt()
                if (tick != lastTick) {
                    lastTick = tick
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                dragging = next
            },
            onFinished = {
                dragging?.let { onRadiusChange(it.roundToInt().toFloat()) }
                dragging = null
            },
        )
    }
}

@Composable
private fun CustomiseSliderCard(
    title: String,
    startLabel: String,
    endLabel: String,
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    onFinished: () -> Unit,
    valueText: String? = null,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SliderCardShape)
            .background(CardFill)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(start = CardTextInset, end = CardTextInset, top = 16.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = title, style = SliderTitleStyle, color = Color.White, modifier = Modifier.weight(1f))
            if (valueText != null) Text(text = valueText, style = SliderTitleStyle, color = Color.White.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(4.dp))
        BareSlider(fraction = fraction, onFractionChange = onFractionChange, onFinished = onFinished, enabled = enabled)
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(startLabel, style = SmallLabelStyle, color = Color.White.copy(alpha = 0.5f))
            Text(endLabel, style = SmallLabelStyle, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun RoundingSwatch(radiusPx: Float, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val r = radiusPx.coerceIn(0f, MaxCoverRadius).dp.toPx()
        val left = size.width - SwatchWidth.toPx()
        val bottom = SwatchHeight.toPx()
        drawRoundRect(
            color = SwatchColor,
            topLeft = Offset(left, -r),
            size = Size(size.width - left + r, bottom + r),
            cornerRadius = CornerRadius(r, r),
        )
        val gap = ArcGap.toPx()
        val tail = ArcTail.toPx()
        val arcR = r + gap
        val x = left - gap
        val y = bottom + gap
        val path = Path().apply {
            moveTo(x, y - arcR - tail)
            lineTo(x, y - arcR)
            arcTo(
                rect = Rect(left = x, top = y - arcR * 2f, right = x + arcR * 2f, bottom = y),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false,
            )
            lineTo(x + arcR + tail, y)
        }
        drawPath(path = path, color = Color.White, style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun BareSlider(fraction: Float, onFractionChange: (Float) -> Unit, onFinished: () -> Unit, enabled: Boolean) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val shown = fraction.coerceIn(0f, 1f)
    val currentShown by rememberUpdatedState(shown)
    val currentOnChange by rememberUpdatedState(onFractionChange)
    val currentFinished by rememberUpdatedState(onFinished)
    fun updateFromX(x: Float) = currentOnChange((x / widthPx).coerceIn(0f, 1f))
    var dragX by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        dragX += delta
        updateFromX(dragX)
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { offset ->
                    updateFromX(offset.x)
                    currentFinished()
                }
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStarted = { dragX = currentShown * widthPx },
                onDragStopped = { currentFinished() },
            ),
    ) {
        val y = size.height / 2f
        val stroke = 3.dp.toPx()
        val start = stroke / 2f
        val end = size.width - stroke / 2f
        drawLine(Color.White.copy(alpha = 0.2f), Offset(start, y), Offset(end, y), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(start, y), Offset(start + (end - start) * shown, y), stroke, StrokeCap.Round)
    }
}

@Composable
private fun <T> StylePagerSection(
    category: String,
    styles: List<T>,
    selected: T,
    nameOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    onPage: ((T) -> Unit)? = null,
    preview: @Composable BoxScope.(T) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = styles.indexOf(selected).coerceAtLeast(0)) { styles.size }
    if (onPage != null) {
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { onPage(styles[it]) }
        }
    }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(start = PageMargin, end = PageMargin + 4.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val style = styles[page]
            StyleCard(
                category = category,
                title = nameOf(style),
                highlighted = style == selected,
                onClick = {
                    if (style != selected) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(style)
                    }
                    if (pagerState.currentPage != page) scope.launch { pagerState.animateScrollToPage(page) }
                },
            ) { preview(style) }
        }
        StyleCaption("swipe, then tap one to use it", Modifier.padding(start = PageMargin))
    }
}

@Composable
private fun PlayerStyleCard(selected: PlayerStyle, onOpenEditor: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        StyleCard(
            category = "Player styles",
            title = selected.title,
            highlighted = false,
            onClick = onOpenEditor,
        ) { PlayerStylePreview(selected) }
        StyleCaption("tap to go into customisation")
    }
}

@Composable
private fun StyleCaption(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = CaptionStyle, color = Color.White.copy(alpha = 0.45f), modifier = modifier.padding(top = 10.dp))
}

@Composable
private fun StyleCard(
    category: String,
    title: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    preview: @Composable BoxScope.() -> Unit,
) {
    val ring by animateColorAsState(if (highlighted) HighlightColor else Color.Transparent, tween(220), label = "styleHighlight")
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    Box(
        modifier = Modifier
            .glassPressScale(press, 0.02f)
            .fillMaxWidth()
            .height(StyleCardHeight)
            .clip(CardShape)
            .background(CardFill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        PreviewFrame(modifier = Modifier.align(Alignment.BottomEnd), content = preview)
        Text(
            text = category,
            style = CardLabelStyle,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(start = CardTextInset, top = CardTextInset),
        )
        BasicText(
            text = title,
            style = CardValueStyle,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 32.sp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = CardTextInset, bottom = 24.dp, end = PreviewWidth + 28.dp),
        )
        Box(modifier = Modifier.matchParentSize().border(2.dp, ring, CardShape))
    }
}

@Composable
private fun PreviewFrame(
    modifier: Modifier = Modifier,
    width: Dp = PreviewWidth,
    top: Dp = 23.dp,
    floating: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .padding(top = top, end = 18.dp, bottom = if (floating) 18.dp else 0.dp)
            .width(width)
            .fillMaxHeight()
            .clip(if (floating) FloatingFrameShape else FrameShape)
            .background(FrameFill)
            .then(if (floating) Modifier else SinkingFoot),
        content = content,
    )
}

private val SinkingFoot = Modifier.drawWithCache {
    val top = size.height * 0.72f
    val foot = Brush.verticalGradient(0f to Color.Transparent, 1f to CardFill, startY = top, endY = size.height)
    onDrawWithContent {
        drawContent()
        drawRect(foot, topLeft = Offset(0f, top))
    }
}

@Composable
private fun BlurSection() {
    val (glass, setGlass) = rememberPreference(LiquidGlassGlobalEnabledKey, true)
    val (progressive, setProgressive) = rememberPreference(CustomizePrefs.ProgressiveBlur, true)
    val (quality, setQuality) = rememberEnumPreference(CustomizePrefs.BlurQuality, BlurQuality.High)
    val (strength, setStrength) = rememberPreference(CustomizePrefs.BlurStrength, CustomizePrefs.BlurStrengthDefault)
    val haptic = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shownStrength = dragging ?: strength
    var lastTick by remember { mutableIntStateOf((strength / 5f).roundToInt()) }
    val min = CustomizePrefs.BlurStrengthMin
    val max = CustomizePrefs.BlurStrengthMax
    Column(Modifier.fillMaxWidth()) {
        EffectCard(
            title = "Blur",
            checked = glass,
            onCheckedChange = setGlass,
            preview = { BlurLivePreview(glass, progressive, quality, shownStrength) },
            previewWidth = BlurPreviewWidth,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.graphicsLayer { alpha = if (glass) 1f else 0.45f },
            ) {
                PillSwitchRow(title = "Progressive blur", checked = progressive, onCheckedChange = setProgressive, enabled = glass)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PillShape)
                        .background(RowFill)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text("Blur quality", style = PillTextStyle, color = Color.White)
                    Spacer(Modifier.height(7.dp))
                    MiniSegmented(
                        options = listOf("Low", "High"),
                        selectedIndex = if (quality == BlurQuality.Low) 0 else 1,
                        onSelect = { setQuality(if (it == 0) BlurQuality.Low else BlurQuality.High) },
                        enabled = glass,
                    )
                }
            }
        }
        Spacer(Modifier.height(11.dp))
        CustomiseSliderCard(
            title = "Blur strength",
            valueText = "${shownStrength.roundToInt()}px",
            startLabel = "${min.roundToInt()}px",
            endLabel = "${max.roundToInt()}px",
            fraction = (shownStrength - min) / (max - min),
            onFractionChange = { f ->
                val next = min + f * (max - min)
                val tick = (next / 5f).roundToInt()
                if (tick != lastTick) {
                    lastTick = tick
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                dragging = next
            },
            onFinished = {
                dragging?.let { setStrength(it.roundToInt().toFloat()) }
                dragging = null
            },
            enabled = glass,
        )
    }
}

@Composable
private fun ElementBlurSection() {
    val (on, setOn) = rememberPreference(CustomizePrefs.ElementBlur, true)
    EffectCard(title = "Element blur", checked = on, onCheckedChange = setOn, preview = { ElementBlurPreview(on) }) {
        Text("Lyrics, cover zoom, titles, menus and page swipes", style = CaptionStyle, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun AnimationSection() {
    val (enabled, setEnabled) = rememberPreference(CustomizePrefs.Animations, true)
    val (speed, setSpeed) = rememberPreference(CustomizePrefs.AnimationSpeed, 1f)
    val haptic = LocalHapticFeedback.current
    val stops = AnimationSpeeds
    val index = stops.indexOfFirst { it >= speed - 0.01f }.takeIf { it >= 0 } ?: stops.lastIndex
    Column(Modifier.fillMaxWidth()) {
        EffectCard(
            title = "Animation",
            checked = enabled,
            onCheckedChange = setEnabled,
            value = if (enabled) animationSpeedLabel(speed) else "Off",
            preview = { AnimationPreview(enabled) },
        ) {
            Text(
                text = if (speed == 1f) "Follows your phone's animation speed" else "On top of your phone's animation speed",
                style = CaptionStyle,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.height(11.dp))
        CustomiseSliderCard(
            title = "Animation speed",
            valueText = animationSpeedLabel(speed),
            startLabel = "slower",
            endLabel = "faster",
            fraction = index / stops.lastIndex.toFloat(),
            onFractionChange = { f ->
                val next = stops[(f * stops.lastIndex).roundToInt()]
                if (next != speed) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    setSpeed(next)
                }
            },
            onFinished = {},
            enabled = enabled,
        )
    }
}

@Composable
private fun EffectCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    value: String? = null,
    previewWidth: Dp = PreviewWidth,
    floatingPreview: Boolean = false,
    preview: @Composable BoxScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(StyleCardHeight)
            .clip(CardShape)
            .background(CardFill),
    ) {
        PreviewFrame(
            modifier = Modifier.align(Alignment.BottomEnd),
            width = previewWidth,
            top = 58.dp,
            floating = floatingPreview,
            content = preview,
        )
        Text(
            text = title,
            style = CardLabelStyle,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(start = CardTextInset, top = CardTextInset),
        )
        SettingsSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 18.dp),
        )
        Column(
            modifier = Modifier.padding(start = CardTextInset, top = 52.dp, end = previewWidth + 30.dp),
            content = content,
        )
        if (value != null) {
            BasicText(
                text = value,
                style = CardValueStyle,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = CardTextInset, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun PillSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(RowFill)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(title, style = PillTextStyle, color = Color.White, modifier = Modifier.weight(1f))
        Box(Modifier.size(width = 38.dp, height = 24.dp), contentAlignment = Alignment.Center) {
            SettingsSwitch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                modifier = Modifier.scale(0.72f),
            )
        }
    }
}

@Composable
private fun MiniSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, enabled: Boolean) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardFill)
            .padding(2.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, spring(dampingRatio = 0.82f, stiffness = 520f), label = "miniSegment")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val color by animateColorAsState(if (index == selectedIndex) Color.Black else Color.White, tween(160), label = "miniSegmentText")
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, null, enabled = enabled) { onSelect(index) },
                ) {
                    Text(label, style = PillTextStyle, color = color)
                }
            }
        }
    }
}

@Composable
private fun HomeScreenSection(style: HomeStyle, inUse: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val key = HomeLayoutPrefs.changed(style)
    val changed by rememberPreference(key, emptySet())
    fun write(change: (Set<String>) -> Set<String>) {
        scope.launch { context.dataStore.edit { it[key] = change(it[key].orEmpty()) } }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(34.dp)) {
            Text(text = "Home screen", style = SectionTitleStyle, color = Color.White, modifier = Modifier.weight(1f))
            AnimatedVisibility(visible = changed.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = "Reset",
                    style = PillTextStyle.copy(fontSize = 13.sp),
                    color = Color.Black,
                    modifier = Modifier
                        .clip(PillShape)
                        .background(Color.White)
                        .clickable { write { emptySet() } }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        AnimatedContent(
            targetState = style to inUse,
            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            label = "homeScreenStyle",
        ) { (shownStyle, used) ->
            Text(
                text = if (used) "For ${shownStyle.title} · in use" else "For ${shownStyle.title}",
                style = SwipeSubtitleStyle,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
            )
        }
        LabCard {
            HomePart.entries.forEach { part ->
                HomePartRow(
                    title = part.label(style),
                    shown = part.shownWith(changed, style),
                    onShownChange = { _ -> write { if (part.name in it) it - part.name else it + part.name } },
                )
            }
        }
    }
}

@Composable
private fun HomePartRow(title: String, shown: Boolean, onShownChange: (Boolean) -> Unit) {
    val fade by animateFloatAsState(if (shown) 1f else 0.42f, tween(220), label = "homePart")
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interaction, indication = null) { onShownChange(!shown) }
            .padding(vertical = 9.dp),
    ) {
        Text(
            text = title,
            style = SwipeSubtitleStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.weight(1f).graphicsLayer { alpha = fade },
        )
        SettingsSwitch(checked = shown, onCheckedChange = onShownChange)
    }
}

@Composable
private fun GestureSection() {
    val (swipeOn, setSwipeOn) = rememberPreference(SwipeToSongKey, true)
    val (right, setRight) = rememberEnumPreference(CustomizePrefs.SwipeRight, DefaultSwipeRight)
    val (left, setLeft) = rememberEnumPreference(CustomizePrefs.SwipeLeft, DefaultSwipeLeft)
    Column {
        Text(text = "Gesture", style = SectionTitleStyle, color = Color.White)
        Spacer(Modifier.height(14.dp))
        SwipeCard("Swipe right", "Slide a song in any list to the right", toRight = true, action = right, enabled = swipeOn, onPick = setRight)
        Spacer(Modifier.height(14.dp))
        SwipeCard("Swipe left", "Slide a song in any list to the left", toRight = false, action = left, enabled = swipeOn, onPick = setLeft)
        Spacer(Modifier.height(14.dp))
        PlayerGestureCard(swipeOn, setSwipeOn)
    }
}

@Composable
private fun LabCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(CardFill)
            .padding(CardPadding),
        content = content,
    )
}

@Composable
private fun SwipeCard(
    title: String,
    subtitle: String,
    toRight: Boolean,
    action: SongSwipeAction,
    enabled: Boolean,
    onPick: (SongSwipeAction) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val dim by animateFloatAsState(if (enabled) 1f else 0.45f, tween(200), label = "swipeCardDim")
    LabCard {
        Column(Modifier.graphicsLayer { alpha = dim }) {
            Text(title, style = SwipeTitleStyle, color = Color.White)
            Text(subtitle, style = SwipeSubtitleStyle, color = Secondary, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(20.dp))
            SwipePreview(action, toRight, Modifier.bleedHorizontally(CardPadding))
            Spacer(Modifier.height(20.dp))
            ActionDropdown(action, open, enabled) { open = !open }
            AnimatedVisibility(
                visible = open && enabled,
                enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) + fadeIn(),
                exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 500f)) + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    SongSwipeAction.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(RowFill)
                                .clickable {
                                    onPick(option)
                                    open = false
                                }
                                .padding(horizontal = 14.dp),
                        ) {
                            ActionOptionBadge(option)
                            Text(
                                option.title,
                                fontFamily = InterText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                            )
                            if (option == action) {
                                Icon(painterResource(R.drawable.check), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionDropdown(action: SongSwipeAction, open: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.8f, stiffness = 420f), label = "dropdownTurn")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(RowFill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        ActionOptionBadge(action)
        Crossfade(targetState = action, animationSpec = tween(180), modifier = Modifier.weight(1f), label = "swipeAction") { shown ->
            Text(
                shown.title,
                fontFamily = InterText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = (-0.01).em,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = Secondary,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn },
        )
    }
}

@Composable
private fun ActionOptionBadge(action: SongSwipeAction) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(action.color.copy(alpha = 0.22f)),
    ) {
        Icon(painter = painterResource(action.icon), contentDescription = null, tint = action.color, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun PlayerGestureCard(swipeOn: Boolean, setSwipeOn: (Boolean) -> Unit) {
    val (anywhere, setAnywhere) = rememberPreference(CustomizePrefs.SwipeDownAnywhere, true)
    LabCard {
        Text("Player", style = SwipeTitleStyle, color = Color.White)
        Text("How the open player answers your swipes", style = SwipeSubtitleStyle, color = Secondary, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(14.dp))
        GestureToggleRow("Swipe down anywhere to close", "Otherwise only from the top of the player and its cover", anywhere, setAnywhere)
        GestureToggleRow("Swipe songs in lists", "Off, a song row only taps and holds", swipeOn, setSwipeOn)
    }
}

@Composable
private fun GestureToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = InterText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
            Text(subtitle, fontFamily = InterText, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Secondary, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        SettingsSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private const val LoopSeconds = 4f
private const val RestSeconds = 0.5f
private const val PullSeconds = 0.9f
private const val HoldUntil = 3f
private const val ReturnSeconds = 0.7f
private val SwipeRowHeight = 60.dp
private val SlabInset = 6.dp

private fun pullAt(seconds: Float): Float {
    val t = seconds.mod(LoopSeconds)
    return when {
        t < RestSeconds -> 0f
        t < RestSeconds + PullSeconds -> FastOutSlowInEasing.transform((t - RestSeconds) / PullSeconds)
        t < HoldUntil -> 1f
        t < HoldUntil + ReturnSeconds -> 1f - FastOutSlowInEasing.transform((t - HoldUntil) / ReturnSeconds)
        else -> 0f
    }
}

@Composable
private fun loopSeconds(periodSeconds: Float): () -> Float {
    val infinite = rememberInfiniteTransition(label = "customiseLoop")
    val t = infinite.animateFloat(
        initialValue = 0f,
        targetValue = periodSeconds,
        animationSpec = infiniteRepeatable(tween((periodSeconds * 1000).roundToInt(), easing = LinearEasing)),
        label = "customiseLoopTime",
    )
    return { t.value }
}

@Composable
private fun SwipePreview(action: SongSwipeAction, toRight: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val time = loopSeconds(LoopSeconds)
    var width by remember { mutableIntStateOf(0) }
    val line = (width * 0.42f).coerceIn(with(density) { 150.dp.toPx() }, with(density) { 200.dp.toPx() })
    val pull = { pullAt(time()) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SwipeRowHeight)
            .onSizeChanged { width = it.width },
    ) {
        SwipeDemoPill(action, toRight, pull, line, Modifier.fillMaxSize())
        SampleRow(Modifier.graphicsLayer { translationX = (if (toRight) 1f else -1f) * pull() * line })
    }
}

@Composable
private fun SwipeDemoPill(action: SongSwipeAction, atStart: Boolean, pull: () -> Float, line: Float, modifier: Modifier) {
    val icon = painterResource(action.icon)
    val label = action.pill
    val density = LocalDensity.current
    val inset = with(density) { (SlabInset + 4.dp).toPx() }
    val labelRoom = with(density) { 104.dp.toPx() }
    Box(
        modifier.drawBehind {
            val p = pull()
            val w = (p * line - inset * 1.6f).coerceAtLeast(0f)
            if (w <= 0f) return@drawBehind
            val h = size.height * 0.68f
            val top = (size.height - h) / 2f
            val left = if (atStart) inset else size.width - inset - w
            val armed = p >= 0.999f
            drawRoundRect(
                color = if (armed) action.color else Color.White.copy(alpha = 0.16f),
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f),
            )
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(if (atStart) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = SlabInset + 16.dp)
                .graphicsLayer {
                    val p = pull()
                    alpha = ((p * line - labelRoom * 0.5f) / (labelRoom * 0.5f)).coerceIn(0f, 1f)
                },
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(label, fontFamily = InterText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun SampleRow(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val inset = SlabInset.toPx()
                drawRoundRect(
                    color = Color(0xFF2E2E31),
                    topLeft = Offset(inset, 0f),
                    size = Size(size.width - inset * 2f, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
            .padding(horizontal = SlabInset + 10.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.14f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
            Box(Modifier.fillMaxWidth(0.32f).height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.13f)))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
    }
}

private fun scaledRadius(coverRadius: Dp, scale: Float): Dp = (coverRadius * scale).coerceIn(0.dp, 14.dp)

@Composable
private fun Bar(width: Dp, height: Dp, color: Color = Ink) {
    Box(Modifier.size(width, height).background(color, Pill))
}

@Composable
private fun TitleBars(modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Bar(42.dp, 4.5.dp)
        Bar(30.dp, 4.5.dp)
    }
}

@Composable
private fun PlaylistStylePreview(style: PlaylistStyle, coverRadius: Dp) {
    when (style) {
        PlaylistStyle.Flow -> Box(Modifier.fillMaxSize().background(ArtFill)) {
            TitleBars(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 38.dp))
        }
        PlaylistStyle.Classic -> Column(Modifier.fillMaxSize().background(SoftArtFill).padding(top = 14.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(78.dp)
                    .background(ArtLight, RoundedCornerShape(scaledRadius(coverRadius, 0.28f))),
            )
            Spacer(Modifier.height(10.dp))
            TitleBars(Modifier.padding(start = 12.dp))
        }
        PlaylistStyle.Minimal -> Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 16.dp)) {
            Bar(56.dp, 7.dp)
            Spacer(Modifier.height(5.dp))
            Bar(26.dp, 8.dp)
            Spacer(Modifier.height(12.dp))
            repeat(4) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).background(Bone, RoundedCornerShape(scaledRadius(coverRadius, 0.12f))))
                    Spacer(Modifier.width(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Bar(48.dp, 4.dp)
                        Bar(30.dp, 3.dp, BoneDim)
                    }
                }
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}

@Composable
private fun PlayerStylePreview(style: PlayerStyle) {
    when (style) {
        PlayerStyle.Bedside -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            modifier = Modifier.fillMaxSize().background(Color.Black),
        ) {
            Bar(46.dp, 5.dp, BoneDim)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Bar(40.dp, 26.dp, Bone)
                Bar(5.dp, 5.dp, Bone)
                Bar(40.dp, 26.dp, Bone)
            }
        }
        PlayerStyle.BedsideTiles -> Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f).background(ArtFill)) {
                Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 10.dp)) {
                    Bar(64.dp, 7.dp)
                    Spacer(Modifier.height(6.dp))
                    Bar(PreviewWidth - 36.dp, 2.5.dp, Bone)
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Bar(24.dp, 4.dp, BoneDim)
                Bar(40.dp, 22.dp, Bone)
                Bar(40.dp, 22.dp, Bone)
            }
        }
        PlayerStyle.Dial -> Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(34.dp).background(BoneDim, RoundedCornerShape(10.dp)))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(20.dp).background(Bone, CircleShape))
            Spacer(Modifier.height(8.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(78.dp).background(ArtFill, CircleShape))
                Box(Modifier.size(66.dp).border(1.5.dp, Bone, CircleShape))
            }
        }
        PlayerStyle.Poster, PlayerStyle.PosterRing -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(14.dp))
            Box(contentAlignment = Alignment.Center) {
                if (style == PlayerStyle.PosterRing) Box(Modifier.size(90.dp).border(2.dp, Bone, CircleShape))
                Box(Modifier.size(80.dp).background(ArtFill, CircleShape))
            }
            Spacer(Modifier.height(12.dp))
            Bar(96.dp, 9.dp)
            Spacer(Modifier.height(5.dp))
            Bar(70.dp, 9.dp, Bone)
        }
        PlayerStyle.Soft -> Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF6A6A6E), Color(0xFF3A3A3D))))) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth().height(84.dp).background(ArtLight, RoundedCornerShape(8.dp)))
            NowPlayingBars()
        }
        else -> Column(Modifier.fillMaxSize()) {
            if (style == PlayerStyle.Turntable) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(96.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp).background(Bone, CircleShape)) {
                        Box(Modifier.size(26.dp).background(ArtLight, CircleShape))
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().height(96.dp).background(ArtFill))
            }
            Spacer(Modifier.height(10.dp))
            NowPlayingBars()
        }
    }
}

@Composable
private fun NowPlayingBars() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        TitleBars()
        Spacer(Modifier.height(8.dp))
        Bar(PreviewWidth, 2.5.dp, Bone)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(9.dp).background(Bone, CircleShape))
            Box(Modifier.size(14.dp).background(Ink, CircleShape))
            Box(Modifier.size(9.dp).background(Bone, CircleShape))
        }
    }
}

@Composable
private fun HomePreview(style: HomeStyle, coverRadius: Dp) {
    val radius = RoundedCornerShape(scaledRadius(coverRadius, 0.2f))
    when (style) {
        HomeStyle.Hero -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(108.dp).background(ArtFill)) {
                TitleBars(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp))
            }
            Spacer(Modifier.height(10.dp))
            Shelf(radius, Modifier.padding(start = 12.dp))
        }
        HomeStyle.ListenNow -> Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 10.dp, top = 16.dp)) {
            Bar(62.dp, 6.dp)
            Spacer(Modifier.height(9.dp))
            repeat(3) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(22.dp).background(Bone, radius))
                    Spacer(Modifier.width(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                        Bar(52.dp, 4.dp)
                        Bar(24.dp, 3.dp, BoneDim)
                    }
                    Box(Modifier.size(7.dp).background(Bone, CircleShape))
                }
                Spacer(Modifier.height(7.dp))
            }
            Spacer(Modifier.height(4.dp))
            Bar(44.dp, 5.dp)
            Spacer(Modifier.height(6.dp))
            Shelf(radius)
        }
        HomeStyle.HopIn -> Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 16.dp)) {
            Bar(34.dp, 6.dp)
            Spacer(Modifier.height(9.dp))
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(2) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).height(24.dp).clip(radius).background(BoneDim),
                        ) {
                            Box(Modifier.size(24.dp).background(Bone))
                            Spacer(Modifier.width(4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Bar(18.dp, 3.5.dp)
                                Bar(12.dp, 3.dp, BoneDim)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            Spacer(Modifier.height(6.dp))
            Bar(44.dp, 5.dp)
            Spacer(Modifier.height(6.dp))
            Shelf(radius)
        }
    }
}

@Composable
private fun SongListStylePreview(style: SongListStyle, coverRadius: Dp) {
    val radius = RoundedCornerShape(scaledRadius(coverRadius, 0.2f))
    when (style) {
        SongListStyle.List -> Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 10.dp, top = 16.dp)) {
            Bar(48.dp, 6.dp)
            Spacer(Modifier.height(10.dp))
            repeat(5) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(20.dp).background(Bone, radius))
                    Spacer(Modifier.width(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Bar(56.dp, 4.dp)
                        Bar(28.dp, 3.dp, BoneDim)
                    }
                }
                Spacer(Modifier.height(7.dp))
            }
        }
        SongListStyle.Stack -> Box(Modifier.fillMaxSize()) {
            val cards = listOf(
                Triple(Color(0xFF5A4A3A), 0.80f, 6.dp),
                Triple(Color(0xFF6B4A2E), 0.87f, 22.dp),
                Triple(Color(0xFF2E3F78), 0.94f, 38.dp),
            )
            cards.forEach { (fill, scale, top) ->
                StackPreviewCard(fill, scale, top, 40.dp)
            }
            Box(Modifier.align(Alignment.TopCenter).padding(top = 132.dp)) {
                StackPreviewCard(Color(0xFF6E2F2C), 0.94f, 0.dp, 36.dp)
            }
            StackPreviewCard(Color(0xFF6A5E1C), 1f, 56.dp, 74.dp, focused = true)
        }
    }
}

@Composable
private fun StackPreviewCard(fill: Color, scale: Float, top: Dp, height: Dp, focused: Boolean = false) {
    Box(
        Modifier
            .padding(top = top)
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            }
            .height(height)
            .clip(RoundedCornerShape(9.dp))
            .background(fill),
    ) {
        Column(Modifier.padding(start = 8.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Bar(if (focused) 40.dp else 32.dp, if (focused) 6.dp else 5.dp)
            if (focused) Bar(26.dp, 3.5.dp, Color.White.copy(alpha = 0.5f))
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = if (focused) 12.dp else 8.dp, end = 7.dp)
                .size(if (focused) 46.dp else 40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ArtLight),
        )
    }
}

@Composable
private fun Shelf(shape: RoundedCornerShape, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        repeat(3) { Box(Modifier.size(34.dp).background(Bone, shape)) }
    }
}

@Composable
private fun BlurLivePreview(on: Boolean, progressive: Boolean, quality: BlurQuality, strength: Float) {
    val radius = if (on) (strength * (if (quality == BlurQuality.Low) 0.3f else 0.5f)).dp else 0.dp
    Box(Modifier.fillMaxSize()) {
        BlurPreviewPage(Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(0.dp)),
        ) {
            BlurPreviewPage(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .graphicsLayer {
                        val r = radius.toPx()
                        renderEffect = if (r > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BlurEffect(r, r, TileMode.Clamp) else null
                    },
            )
            Box(
                Modifier.fillMaxSize().background(
                    if (!on) {
                        SolidColor(FrameFill)
                    } else if (progressive) {
                        Brush.verticalGradient(listOf(FrameFill.copy(alpha = 0.55f), Color.Transparent))
                    } else {
                        SolidColor(Color.Black.copy(alpha = 0.18f))
                    },
                ),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 9.dp, end = 9.dp, top = 13.dp),
        ) {
            Box(Modifier.size(13.dp).background(Ink.copy(alpha = 0.7f), CircleShape))
            Spacer(Modifier.weight(1f))
            Bar(16.dp, 4.dp, Ink.copy(alpha = 0.7f))
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
                .size(width = 80.dp, height = 20.dp)
                .clip(Pill)
                .background(if (on) Color.White.copy(alpha = 0.22f) else Color(0xFF3A3A3C)),
        )
    }
}

@Composable
private fun BlurPreviewPage(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(FrameFill)
        val side = 9.dp.toPx()
        drawRoundRect(
            brush = PreviewArt,
            topLeft = Offset(side, 6.dp.toPx()),
            size = Size(size.width - side * 2, 64.dp.toPx()),
            cornerRadius = CornerRadius(8.dp.toPx()),
        )
        var y = 78.dp.toPx()
        repeat(4) { row ->
            drawRoundRect(
                brush = if (row == 3) PreviewTile else SolidColor(Bone),
                topLeft = Offset(side, y),
                size = Size(14.dp.toPx(), 14.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            drawRoundRect(
                color = Ink.copy(alpha = 0.6f),
                topLeft = Offset(side + 19.dp.toPx(), y + 2.dp.toPx()),
                size = Size(38.dp.toPx(), 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            drawRoundRect(
                color = BoneDim,
                topLeft = Offset(side + 19.dp.toPx(), y + 8.dp.toPx()),
                size = Size(24.dp.toPx(), 3.dp.toPx()),
                cornerRadius = CornerRadius(1.5f.dp.toPx()),
            )
            y += 20.dp.toPx()
        }
    }
}

@Composable
private fun ElementBlurPreview(on: Boolean) {
    val focus = remember { Animatable(0f) }
    val blurOn by rememberUpdatedState(on)
    LaunchedEffect(Unit) {
        while (true) {
            focus.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            delay(900)
            focus.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
            delay(500)
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .offset(y = (-14).dp)
                .size(64.dp)
                .graphicsLayer {
                    val f = focus.value
                    val zoom = 0.62f + 0.38f * f
                    scaleX = zoom
                    scaleY = zoom
                    alpha = 0.35f + 0.65f * f
                    val r = (1f - f) * 10.dp.toPx()
                    renderEffect = if (blurOn && r > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BlurEffect(r, r, TileMode.Clamp) else null
                }
                .drawBehind { drawCircle(Color.White, radius = 13.dp.toPx()) },
        )
    }
}

@Composable
private fun AnimationPreview(enabled: Boolean) {
    val travel = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        var target = if (travel.value > 0.5f) 0f else 1f
        while (true) {
            if (enabled) travel.animateTo(target, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)) else travel.snapTo(target)
            delay(650)
            target = 1f - target
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(y = (-14).dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(BoneDim, CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val span = (PreviewWidth - 20.dp - 26.dp).toPx()
                    IntOffset((10.dp.toPx() + travel.value * span).roundToInt(), (-14).dp.roundToPx())
                }
                .size(26.dp)
                .background(Color.White, CircleShape),
        )
    }
}

private val PreviewLines = listOf(
    Triple(0f, 1.6f, "if i am a stone"),
    Triple(1.7f, 3.7f, "would you still love me?"),
    Triple(3.9f, 6.4f, "of course, what do you mean my love"),
)
private const val LyricsLoop = 7.4f

@Composable
private fun LyricsLivePreview(style: LyricsStyle) {
    val time = loopSeconds(LyricsLoop)
    Column(
        Modifier.fillMaxSize().padding(start = 10.dp, end = 8.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PreviewLines.forEachIndexed { index, (start, end, text) ->
            LyricPreviewLine(style, index, start, end, text, time, alignEnd = index == 2)
        }
    }
}

@Composable
private fun LyricPreviewLine(style: LyricsStyle, index: Int, start: Float, end: Float, text: String, time: () -> Float, alignEnd: Boolean) {
    val words = remember(text) { text.split(' ') }
    val now = time()
    val active = now >= start && now < end + 0.15f
    val past = now >= end + 0.15f
    val progress = ((now - start) / (end - start)).coerceIn(0f, 1f)
    val lit = if (style.words) (progress * words.size).toInt().coerceIn(0, words.size) else if (now >= start) words.size else 0
    val lift by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "lyricLift")
    val delayPx = if (style.cascade) index * 2f else 0f
    val annotated = buildAnnotatedString {
        words.forEachIndexed { i, word ->
            val on = active && i < lit
            withStyle(
                SpanStyle(
                    color = when {
                        on -> Color.White
                        past -> Color.White.copy(alpha = 0.3f)
                        else -> Color.White.copy(alpha = 0.32f)
                    },
                    shadow = if (on && style.glow) Shadow(Color.White.copy(alpha = 0.8f), blurRadius = 10f) else null,
                    baselineShift = if (on && style.rise) androidx.compose.ui.text.style.BaselineShift(0.08f) else null,
                ),
            ) { append(word) }
            if (i < words.lastIndex) append(' ')
        }
    }
    Text(
        text = annotated,
        fontFamily = InterDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 13.sp,
        letterSpacing = (-0.03).em,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = 0.94f + 0.06f * lift
                scaleX = s
                scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (alignEnd) 1f else 0f, 0.5f)
                translationY = (1f - lift) * delayPx
            },
    )
}

@Composable
private fun StatusBarPreview(shown: Boolean) {
    val visible by animateFloatAsState(if (shown) 1f else 0f, tween(260), label = "statusBarPreview")
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .padding(horizontal = 10.dp)
                .graphicsLayer {
                    alpha = visible
                    translationY = -(1f - visible) * 8.dp.toPx()
                },
        ) {
            Bar(18.dp, 5.dp)
            Spacer(Modifier.weight(1f))
            repeat(3) { Box(Modifier.padding(start = 2.5.dp).size(4.dp).background(Ink, CircleShape)) }
        }
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .height(72.dp)
                .background(ArtFill, RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(10.dp))
        TitleBars(Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun NavBarPreview(shown: Boolean) {
    val visible by animateFloatAsState(if (shown) 1f else 0f, tween(260), label = "navBarPreview")
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val stripHeight = 21.dp.toPx()
                val lip = 2.dp.toPx()
                val strip = Brush.verticalGradient(listOf(Color(0xFF707072), Color(0xFF505052)), startY = 0f, endY = stripHeight)
                val centerY = stripHeight / 2f
                val spacing = 38.dp.toPx()
                val recentsX = size.width / 2f - spacing
                val backX = size.width / 2f + spacing
                val lineHalf = 5.5.dp.toPx()
                val lineGap = 1.9.dp.toPx()
                val lineWidth = 1.dp.toPx()
                val back = Path().apply {
                    moveTo(backX - 4.5.dp.toPx(), centerY)
                    lineTo(backX + 4.5.dp.toPx(), centerY - 5.dp.toPx())
                    lineTo(backX + 4.5.dp.toPx(), centerY + 5.dp.toPx())
                    close()
                }
                val roundedTip = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round)
                onDrawBehind {
                    drawRect(NavPreviewScreen)
                    val shownBy = visible
                    if (shownBy == 0f) return@onDrawBehind
                    translate(top = size.height - stripHeight - lip + (1f - shownBy) * 8.dp.toPx()) {
                        drawRect(strip, size = Size(size.width, stripHeight), alpha = shownBy)
                        drawRect(NavPreviewLip, topLeft = Offset(0f, stripHeight), size = Size(size.width, lip), alpha = shownBy)
                        drawLine(Color.White, Offset(recentsX - lineHalf, centerY - lineGap), Offset(recentsX + lineHalf, centerY - lineGap), strokeWidth = lineWidth, alpha = shownBy)
                        drawLine(Color.White, Offset(recentsX - lineHalf, centerY + lineGap), Offset(recentsX + lineHalf, centerY + lineGap), strokeWidth = lineWidth, alpha = shownBy)
                        drawCircle(NavPreviewIcon, radius = 5.dp.toPx(), center = Offset(size.width / 2f, centerY), alpha = shownBy)
                        drawPath(back, NavPreviewIcon, alpha = shownBy)
                        drawPath(back, NavPreviewIcon, alpha = shownBy, style = roundedTip)
                    }
                }
            },
    )
}

private val StageGround = Color(0xFF0D0D0F)

@Composable
internal fun PlayerStyleShowcase(style: PlayerStyle, rounding: Float, metadata: com.shiny.music.models.MediaMetadata?) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsCardMargin, vertical = 8.dp)
            .height(216.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(StageGround),
    ) {
        PlayerStage(style, rounding, metadata)
    }
}

@Composable
private fun PlayerStage(style: PlayerStyle, rounding: Float, metadata: com.shiny.music.models.MediaMetadata?) {
    val title = metadata?.title?.takeIf { it.isNotBlank() } ?: "SHINY"
    val artist = metadata?.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() } ?: style.title
    val radius = (14f * rounding / CustomizePrefs.CoverRoundingDefault).coerceIn(0f, 24f).dp
    val immersive = style in setOf(PlayerStyle.Soft, PlayerStyle.Poster, PlayerStyle.PosterRing)
    Box(Modifier.fillMaxSize()) {
        if (immersive) {
            Artwork(
                model = metadata?.thumbnailUrl,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
                placeholderContent = { PreviewArtwork(Modifier.fillMaxSize(), RoundedCornerShape(0.dp)) },
                hairline = false,
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.86f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(style.title, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(title, color = Color.White, fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Row(Modifier.fillMaxSize().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(154.dp).clip(RoundedCornerShape(radius)).background(Color(0xFF22242A))) {
                    if (style == PlayerStyle.Turntable) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF09090A), CircleShape).padding(13.dp).clip(CircleShape)) {
                            Artwork(
                                model = metadata?.thumbnailUrl,
                                modifier = Modifier.fillMaxSize(),
                                shape = CircleShape,
                                placeholderContent = { PreviewArtwork(Modifier.fillMaxSize(), CircleShape) },
                                hairline = false,
                            )
                        }
                    } else {
                        Artwork(
                            model = metadata?.thumbnailUrl,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(radius),
                            placeholderContent = { PreviewArtwork(Modifier.fillMaxSize(), RoundedCornerShape(radius)) },
                            hairline = false,
                            dissolve = true,
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(style.title, color = Color.White.copy(alpha = 0.66f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(7.dp))
                    Text(title, color = Color.White, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(artist, color = Color.White.copy(alpha = 0.68f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                    Box(Modifier.fillMaxWidth().padding(top = 17.dp).height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))) {
                        Box(Modifier.fillMaxWidth(0.38f).fillMaxSize().background(Liquid.colors.accent, CircleShape))
                    }
                }
            }
        }
    }
}

