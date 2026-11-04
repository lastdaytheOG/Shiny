package com.shiny.music.ui.liquid.player.styles.ring

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.lyrics.LyricsEntry
import com.shiny.music.models.MediaMetadata
import com.shiny.music.ui.utils.resize
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

private val RingPush = 34.dp
private val PosterChevron = 43.dp
private val PillWidth = 59.dp
private val PillHeight = 42.dp
private val PillGap = 11.dp
private val PillFill = Color.White.copy(alpha = 0.08f)
private const val BackdropZoom = 1.6f

@Composable
internal fun RingPosterPlayer(
    metadata: MediaMetadata?,
    lines: List<LyricsEntry>,
    showing: () -> Boolean,
) {
    val tones = remember { StyleTones() }
    val r = rememberResponsive()
    val density = LocalDensity.current
    val connection = LocalPlayerConnection.current
    val title = metadata?.title.orEmpty()
    val artist = metadata?.artists?.joinToString { it.name }.orEmpty()
    val artwork = metadata?.thumbnailUrl

    CompositionLocalProvider(LocalRingShowing provides showing) {
        val position = rememberLivePosition()
        val ring = rememberCoverRingState()
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1C))
                .pointerInput(connection) {
                    var travel = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { travel = 0f },
                        onHorizontalDrag = { change, amount ->
                            travel += amount
                            change.consume()
                        },
                        onDragEnd = {
                            val threshold = 80.dp.toPx()
                            when {
                                travel > threshold -> connection?.seekToNext()
                                travel < -threshold -> connection?.seekToPrevious()
                            }
                        },
                    )
                },
        ) {
            val w = maxWidth
            val h = maxHeight
            CoverGradientBackdrop(artwork, w, h, showing)
            val wPx = with(density) { w.toPx() }
            val circleSide = (w * 1.14f * 2f)
            val px = with(density) { circleSide.roundToPx() }.coerceAtMost(1600)
            CircleCover(
                url = artwork?.resize(px, px),
                center = Offset(0.13f, 0.06f),
                radius = 1.14f,
                unit = w,
                modifier = Modifier.fillMaxWidth().height(w * 1.22f),
            )
            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 36.dp + com.shiny.music.ui.liquid.player.styles.LocalStyleBottomReserve.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = (w * 1.24f).coerceAtMost(h * 0.6f),
                        start = 28.dp,
                        end = 22.dp + PillWidth + 16.dp,
                        bottom = bottom,
                    ),
            ) {
                Column(Modifier.fillMaxSize()) {
                    PosterText(
                        lines, position, title, artist, tones, r, baseSize = r.sp(0.12f, 34f, 56f), gap = 12.dp,
                        push = { Offset(0f, ring.expand.value * with(density) { RingPush.toPx() }) },
                    )
                }
            }
            CoverRing(
                state = ring,
                circle = RingCircle(Offset(0.13f * wPx, 0.06f * wPx), 1.14f * wPx),
                visualizer = false,
                live = true,
                position = position,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = bottom),
            ) {
                RingPills()
            }
        }
    }
}

@Composable
private fun ColumnScope.PosterText(
    lines: List<LyricsEntry>,
    position: LivePosition,
    title: String,
    artist: String,
    tones: StyleTones,
    r: Responsive,
    baseSize: TextUnit,
    gap: Dp,
    push: () -> Offset = { Offset.Zero },
) {
    val hasLyrics = lines.isNotEmpty()
    val found = remember { Animatable(if (hasLyrics) 1f else 0f) }
    LaunchedEffect(hasLyrics) {
        found.animateTo(if (hasLyrics) 1f else 0f, tween(820, easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)))
    }
    val titleShown by remember { derivedStateOf { found.value < 0.999f } }
    val places = remember { arrayOfNulls<LayoutCoordinates>(3) }
    val smallTitle = r.sp(0.05f, 17f, 22f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .graphicsLayer {
                val o = push()
                translationX = o.x
                translationY = o.y
            }
            .onPlaced { places[0] = it },
    ) {
        if (hasLyrics) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val f = found.value
                        if (f < 0.999f) alpha = ((f - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    },
            ) {
                ExpressiveLyrics(
                    lines = lines,
                    position = position.withLyricsOffset(),
                    color = tones.ink,
                    baseSize = baseSize,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (titleShown) {
            Box(
                modifier = Modifier
                    .onPlaced { places[1] = it }
                    .graphicsLayer {
                        val f = found.value
                        val column = places[0]
                        val from = places[1]
                        val to = places[2]
                        if (f > 0.0005f && column != null && from != null && to != null &&
                            column.isAttached && from.isAttached && to.isAttached
                        ) {
                            val a = column.localPositionOf(from, Offset.Zero)
                            val b = column.localPositionOf(to, Offset.Zero)
                            translationX = (b.x - a.x) * f
                            translationY = (b.y - a.y) * f
                        }
                        val s = 1f + (smallTitle.value / baseSize.value - 1f) * f
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0f)
                        alpha = 1f - ((f - 0.6f) / 0.35f).coerceIn(0f, 1f)
                    },
            ) {
                ExpressiveTitle(text = title, baseSize = baseSize, color = tones.ink)
            }
        }
    }
    Spacer(Modifier.height(gap))
    if (artist.isNotBlank()) {
        CreditLine(artist, color = tones.inkSoft, style = posterType(r.sp(0.034f, 12f, 15f), FontWeight.SemiBold))
    }
    Spacer(Modifier.height(3.dp))
    CreditLine(
        title,
        color = tones.inkSoft,
        style = posterType(smallTitle, FontWeight.Bold),
        modifier = Modifier
            .onPlaced { places[2] = it }
            .graphicsLayer { alpha = ((found.value - 0.6f) / 0.4f).coerceIn(0f, 1f) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CreditLine(text: String, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    val rtl = remember(text) { isRtlText(text) }
    var fullWidth by remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Text(
            text = text,
            color = color,
            style = style,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { fullWidth = it.size.width },
            modifier = modifier
                .graphicsLayer {
                    compositingStrategy = if (fullWidth > size.width + 0.5f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                }
                .drawWithCache {
                    val fade = if (rtl) {
                        Brush.horizontalGradient(0f to Color.Transparent, 0.18f to Color.Black)
                    } else {
                        Brush.horizontalGradient(0.82f to Color.Black, 1f to Color.Transparent)
                    }
                    onDrawWithContent {
                        drawContent()
                        if (fullWidth > size.width + 0.5f) drawRect(brush = fade, blendMode = BlendMode.DstIn)
                    }
                }
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 2500,
                    repeatDelayMillis = 2500,
                    velocity = 26.dp,
                ),
        )
    }
}

@Composable
private fun CircleCover(url: String?, center: Offset, radius: Float, unit: Dp, modifier: Modifier) {
    val density = LocalDensity.current
    val unitPx = with(density) { unit.toPx() }
    val c = Offset(center.x * unitPx, center.y * unitPx)
    val r = radius * unitPx
    val shape = remember(c, r) { GenericShape { _, _ -> addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) } }
    Box(
        modifier = modifier.drawBehind {
            drawCircle(
                brush = Brush.radialGradient(
                    0.9f to Color.Black.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                    center = c + Offset(0f, 10.dp.toPx()),
                    radius = r * 1.06f,
                ),
                radius = r * 1.06f,
                center = c + Offset(0f, 10.dp.toPx()),
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color.White.copy(alpha = 0.06f))
                .drawWithContent {
                    drawContent()
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.82f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.28f),
                            center = c,
                            radius = r,
                        ),
                        radius = r,
                        center = c,
                    )
                },
        ) {
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun RingPills() {
    val connection = LocalPlayerConnection.current ?: return
    val song by connection.currentSong.collectAsState(initial = null)
    val playing by connection.isPlaying.collectAsState()
    val canPrevious by connection.canSkipPrevious.collectAsState()
    val canNext by connection.canSkipNext.collectAsState()
    val hold = remember(connection) { HoldSpeed { connection.player } }
    DisposableEffect(hold) { onDispose { hold.end() } }
    val liked = song?.song?.liked == true
    Column(verticalArrangement = Arrangement.spacedBy(PillGap)) {
        Pill { PillIcon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) Color.White else Color.White.copy(alpha = 0.85f), onClick = { connection.toggleLike() }) }
        Pill { PillIcon(Icons.Rounded.SkipPrevious, Color.White, enabled = canPrevious, onClick = { connection.seekToPrevious() }, onHold = { if (it) hold.start(0.5f) else hold.end() }) }
        Pill { PillIcon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, Color.White, size = 26.dp, onClick = { connection.togglePlayPause() }) }
        Pill { PillIcon(Icons.Rounded.SkipNext, Color.White, enabled = canNext, onClick = { connection.seekToNext() }, onHold = { if (it) hold.start(2f) else hold.end() }) }
    }
}

@Composable
private fun Pill(content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(PillWidth, PillHeight)
            .clip(RoundedCornerShape(50))
            .background(PillFill),
    ) { content() }
}

@Composable
private fun PillIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 24.dp,
    onHold: ((Boolean) -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.84f else 1f, spring(dampingRatio = 0.54f, stiffness = 720f), label = "pillPress")
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val holdModifier = if (onHold == null || !enabled) {
        Modifier.clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
    } else {
        Modifier.pointerInput(onHold) {
            detectTapGestures(
                onPress = { at ->
                    val press = PressInteraction.Press(at)
                    interaction.emit(press)
                    var held = false
                    val timer = scope.launch {
                        delay(450)
                        held = true
                        onHold(true)
                    }
                    val released = tryAwaitRelease()
                    timer.cancel()
                    interaction.emit(PressInteraction.Release(press))
                    if (held) onHold(false) else if (released) onClick()
                },
            )
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .then(holdModifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

private class HoldSpeed(private val player: () -> androidx.media3.common.Player) {
    private var saved: androidx.media3.common.PlaybackParameters? = null

    fun start(factor: Float) {
        val p = player()
        val base = saved ?: p.playbackParameters.also { saved = it }
        p.playbackParameters = androidx.media3.common.PlaybackParameters((base.speed * factor).coerceIn(0.25f, 4f), base.pitch)
    }

    fun end() {
        val base = saved ?: return
        saved = null
        player().playbackParameters = base
    }
}

private val warpShader: android.graphics.RuntimeShader? by lazy {
    if (android.os.Build.VERSION.SDK_INT >= 33) android.graphics.RuntimeShader(WarpSource) else null
}

@Composable
private fun CoverGradientBackdrop(url: String?, width: Dp, height: Dp, showing: () -> Boolean) {
    val context = LocalContext.current
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { showing() }.collectLatest { shown ->
            while (shown && isActive) {
                withFrameNanos { now -> time.floatValue = ((now / 1_000_000f) * (100f / 100_000f)).mod(100f) }
            }
        }
    }
    val request = remember(context, url) {
        ImageRequest.Builder(context)
            .data(url?.resize(48, 48))
            .allowHardware(false)
            .size(CoilSize(48, 48))
            .build()
    }
    Box {
        val shader = warpShader
        if (shader != null && android.os.Build.VERSION.SDK_INT >= 33) {
            val bitmap by produceState<Bitmap?>(null, request) {
                value = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()?.let(::softened)
            }
            Spacer(
                modifier = Modifier
                    .requiredSize(width, height)
                    .drawWithCache {
                        val picture = bitmap
                        val input = picture?.let {
                            android.graphics.BitmapShader(it, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP).apply {
                                filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR
                                setLocalMatrix(android.graphics.Matrix().apply { setScale(size.width / it.width, size.height / it.height) })
                            }
                        }
                        onDrawBehind {
                            if (input == null) return@onDrawBehind
                            shader.setInputShader("image", input)
                            shader.setFloatUniform("resolution", size.width, size.height)
                            shader.setFloatUniform("time", time.floatValue)
                            shader.setFloatUniform("zoom", BackdropZoom)
                            drawRect(brush = ShaderBrush(shader))
                        }
                    },
            )
        } else {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .requiredSize(width, height)
                    .graphicsLayer {
                        scaleX = BackdropZoom
                        scaleY = BackdropZoom
                        renderEffect = BlurEffectCache.get(40f)
                    },
            )
        }
        Box(Modifier.requiredSize(width, height).background(Color.Black.copy(alpha = 0.22f)))
    }
}

private fun softened(input: Bitmap): Bitmap {
    val width = input.width
    val height = input.height
    if (width == 0 || height == 0) return input
    val pixels = IntArray(width * height)
    input.getPixels(pixels, 0, width, 0, 0, width, height)
    repeat(3) {
        boxBlur(pixels, width, height, 4, horizontal = true)
        boxBlur(pixels, width, height, 4, horizontal = false)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    val lineCount = if (horizontal) height else width
    val length = if (horizontal) width else height
    val line = IntArray(length)
    for (l in 0 until lineCount) {
        for (i in 0 until length) line[i] = pixels[if (horizontal) l * width + i else i * width + l]
        for (i in 0 until length) {
            var a = 0; var r = 0; var g = 0; var b = 0; var count = 0
            for (k in (i - radius).coerceAtLeast(0)..(i + radius).coerceAtMost(length - 1)) {
                val p = line[k]
                a += (p ushr 24) and 0xFF
                r += (p ushr 16) and 0xFF
                g += (p ushr 8) and 0xFF
                b += p and 0xFF
                count++
            }
            pixels[if (horizontal) l * width + i else i * width + l] =
                ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
        }
    }
}

private const val WarpSource = """
uniform float2 resolution;
uniform float time;
uniform float zoom;
uniform shader image;

half4 main(float2 fragCoord) {
    float2 centre = resolution * 0.5;
    float2 p = centre + (fragCoord - centre) / zoom;
    float2 uv = p / resolution;
    float t = time * 0.5;

    float2 warpOffset = float2(
        sin(uv.y * 3.0 + t) * 0.05 + cos(uv.x * 2.0 - t * 0.6) * 0.04,
        cos(uv.x * 3.0 + t * 0.7) * 0.05 + sin(uv.y * 2.5 + t * 0.9) * 0.04
    );

    return image.eval(p + warpOffset * resolution);
}
"""
