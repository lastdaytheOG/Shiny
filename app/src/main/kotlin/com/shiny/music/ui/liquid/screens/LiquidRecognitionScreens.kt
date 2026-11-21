package com.shiny.music.ui.liquid.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.shazamkit.models.RecognitionResult
import com.music.shazamkit.models.RecognitionStatus
import com.shiny.music.LocalDatabase
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.R
import com.shiny.music.db.entities.RecognitionHistory
import com.shiny.music.models.toMediaMetadata
import com.shiny.music.playback.queues.YouTubeQueue
import com.shiny.music.recognition.MusicRecognitionService
import com.shiny.music.ui.component.backdrop.backdrops.layerBackdrop
import com.shiny.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.ArtworkTones
import com.shiny.music.ui.liquid.ButtonTone
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.GlassIconButton
import com.shiny.music.ui.liquid.GlassKind
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidAlert
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.LiquidButton
import com.shiny.music.ui.liquid.LiquidPullDownMenu
import com.shiny.music.ui.liquid.LiquidSearchField
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.LocalLiquidBackdrop
import com.shiny.music.ui.liquid.MenuAction
import com.shiny.music.ui.liquid.MenuDivider
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.SectionHeader
import com.shiny.music.ui.liquid.SongRow
import com.shiny.music.ui.liquid.liquidGlass
import com.shiny.music.ui.liquid.rememberArtworkTones
import com.shiny.music.ui.liquid.rememberGlassPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private val OnColor = Color.White
private val OnColorSecondary = Color.White.copy(alpha = 0.74f)

private class RecognizedPlayback(val videoId: String, val queue: YouTubeQueue)

private suspend fun findRecognizedSong(title: String, artist: String, youtubeVideoId: String?): RecognizedPlayback? =
    withContext(Dispatchers.IO) {
        val songs = YouTube.search("$title $artist", YouTube.SearchFilter.FILTER_SONG)
            .getOrNull()?.items.orEmpty().filterIsInstance<SongItem>()
        val wanted = normalizeTitle(title)
        val match = songs.firstOrNull { normalizeTitle(it.title) == wanted }
            ?: songs.firstOrNull { normalizeTitle(it.title).let { t -> t.contains(wanted) || wanted.contains(t) } }
        fun of(song: SongItem) = RecognizedPlayback(
            song.id,
            YouTubeQueue(song.endpoint ?: WatchEndpoint(videoId = song.id), song.toMediaMetadata()),
        )
        when {
            match != null -> of(match)
            !youtubeVideoId.isNullOrBlank() -> RecognizedPlayback(youtubeVideoId, YouTubeQueue(WatchEndpoint(videoId = youtubeVideoId)))
            songs.isNotEmpty() -> of(songs.first())
            else -> null
        }
    }

private fun normalizeTitle(title: String): String =
    title.lowercase().replace(Regex("[(\\[].*?[)\\]]"), "").trim()

private enum class RecognitionPhase { Orb, Result }

private class OrbAnchor {
    var container: LayoutCoordinates? = null
    var orb: LayoutCoordinates? = null

    fun centerIn(): Offset? {
        val c = container ?: return null
        val o = orb ?: return null
        if (!c.isAttached || !o.isAttached) return null
        return c.localPositionOf(o, Offset(o.size.width / 2f, o.size.height / 2f))
    }
}

@Composable
fun LiquidRecognitionScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val colors = Liquid.colors
    val status by MusicRecognitionService.recognitionStatus.collectAsState()

    LaunchedEffect(Unit) {
        val current = MusicRecognitionService.recognitionStatus.value
        if (current is RecognitionStatus.NoMatch || current is RecognitionStatus.Error) MusicRecognitionService.reset()
    }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) MusicRecognitionService.start(context)
    }
    val start = {
        if (hasPermission) MusicRecognitionService.start(context) else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val stop = { MusicRecognitionService.stop(context) }

    val result = (status as? RecognitionStatus.Success)?.result
    LaunchedEffect(result) {
        val found = result ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val latest = database.recognitionHistory().first().firstOrNull()
            if (latest?.trackId == found.trackId && latest.recognizedAt.isAfter(LocalDateTime.now().minusMinutes(10))) return@withContext
            database.query { insert(found.toHistory()) }
        }
    }

    val art = result?.let { (it.coverArtHqUrl ?: it.coverArtUrl)?.replace("400x400", "1000x1000") }
    val idle = ArtworkTones(deep = lerp(colors.accent, Color.Black, 0.64f), vivid = colors.accent)
    val tones = rememberArtworkTones(art, idle)
    val deep by animateColorAsState(if (art != null) tones.deep else idle.deep, tween(700), label = "recDeep")
    val vivid by animateColorAsState(if (art != null) tones.vivid else idle.vivid, tween(700), label = "recVivid")
    val backdrop = rememberLayerBackdrop()
    val anchor = remember { OrbAnchor() }
    val active = status is RecognitionStatus.Listening || status is RecognitionStatus.Processing

    var playingId by remember(result?.trackId) { mutableStateOf<String?>(null) }
    var finding by remember(result?.trackId) { mutableStateOf(false) }
    var notFound by remember(result?.trackId) { mutableStateOf(false) }
    val nowPlaying = playerConnection?.mediaMetadata?.collectAsState()?.value
    val playerPlaying = playerConnection?.isPlaying?.collectAsState()?.value == true
    val isCurrent = playingId != null && nowPlaying?.id == playingId
    val playResult: (RecognitionResult) -> Unit = { found ->
        if (isCurrent) {
            playerConnection?.togglePlayPause()
        } else if (!finding) {
            finding = true
            notFound = false
            scope.launch {
                val playback = findRecognizedSong(found.title, found.artist, found.youtubeVideoId)
                finding = false
                if (playback == null || playerConnection == null) {
                    notFound = true
                } else {
                    playerConnection.playQueue(playback.queue)
                    playingId = playback.videoId
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPlaced { anchor.container = it }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(lerp(vivid, deep, 0.15f), deep, lerp(deep, Color.Black, 0.55f))))
                    drawRect(
                        Brush.radialGradient(
                            listOf(vivid.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.32f),
                            radius = size.width * 1.05f,
                        ),
                    )
                }
        ) {
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.5f }
                        .blur(70.dp),
                )
            }
            if (active && result == null) Ripples(anchor)
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides backdrop) {
            AnimatedContent(
                targetState = status,
                contentKey = { if (it is RecognitionStatus.Success) RecognitionPhase.Result else RecognitionPhase.Orb },
                transitionSpec = {
                    (fadeIn(tween(340)) + scaleIn(spring(dampingRatio = 0.82f, stiffness = 260f), initialScale = 0.92f)) togetherWith
                        (fadeOut(tween(170)) + scaleOut(tween(210), targetScale = 0.97f))
                },
                label = "recognitionPhase",
            ) { shown ->
                if (shown is RecognitionStatus.Success) {
                    ResultContent(
                        result = shown.result,
                        accentOn = deep,
                        playState = when {
                            finding -> PlayState.Finding
                            isCurrent && playerPlaying -> PlayState.Playing
                            isCurrent -> PlayState.Paused
                            else -> PlayState.Ready
                        },
                        notFound = notFound,
                        onPlay = { playResult(shown.result) },
                        onAgain = start,
                    )
                } else {
                    OrbContent(
                        status = shown,
                        anchor = anchor,
                        onOrb = { if (shown is RecognitionStatus.Listening) stop() else start() },
                        onTryAgain = start,
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    icon = Icons.Rounded.ChevronLeft,
                    iconSize = 28.dp,
                    kind = GlassKind.Clear,
                    tint = OnColor,
                    onClick = {
                        if (result != null) stop()
                        navController.navigateUp()
                    },
                )
                Spacer(Modifier.weight(1f))
                GlassIconButton(
                    icon = Icons.Rounded.History,
                    kind = GlassKind.Clear,
                    tint = OnColor,
                    contentDescription = stringResource(R.string.recognition_history),
                    onClick = { navController.navigate("recognition_history") },
                )
            }
        }
    }
}

@Composable
private fun OrbContent(
    status: RecognitionStatus,
    anchor: OrbAnchor,
    onOrb: () -> Unit,
    onTryAgain: () -> Unit,
) {
    val headline = when (status) {
        is RecognitionStatus.Listening -> stringResource(R.string.listening)
        is RecognitionStatus.Processing -> stringResource(R.string.recognition_notification_processing)
        is RecognitionStatus.NoMatch -> stringResource(R.string.no_match_found)
        is RecognitionStatus.Error -> stringResource(R.string.recognition_error)
        else -> stringResource(R.string.liquid_tap_to_recognize)
    }
    val message = when (status) {
        is RecognitionStatus.Listening, is RecognitionStatus.Processing -> stringResource(R.string.liquid_recognize_hint)
        is RecognitionStatus.NoMatch -> stringResource(R.string.liquid_recognize_no_match)
        is RecognitionStatus.Error -> status.message
        else -> stringResource(R.string.liquid_recognize_ready)
    }
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RecognitionOrb(
            active = status is RecognitionStatus.Listening || status is RecognitionStatus.Processing,
            onClick = onOrb,
            modifier = Modifier.onPlaced { anchor.orb = it },
        )
        Spacer(Modifier.height(44.dp))
        Text(headline, style = LiquidTypography.title1, color = OnColor, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = LiquidTypography.body, color = OnColorSecondary, textAlign = TextAlign.Center)
        if (status is RecognitionStatus.NoMatch || status is RecognitionStatus.Error) {
            Spacer(Modifier.height(26.dp))
            LiquidButton(
                text = stringResource(R.string.try_again),
                icon = Icons.Rounded.Mic,
                tone = ButtonTone.Glass,
                contentColor = OnColor,
                onClick = onTryAgain,
                height = 50.dp,
            )
        }
    }
}

@Composable
private fun RecognitionOrb(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val energy by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 70f), label = "orbEnergy")
    val clock by rememberInfiniteTransition(label = "orbWave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "orbClock",
    )
    val bars = remember { List(64) { i -> 0.15f + 0.85f * Random(i * 31 + 7).nextFloat().pow(1.3f) } }
    Box(
        modifier
            .size(216.dp)
            .graphicsLayer {
                val s = (1f + 0.025f * energy * sin(clock * 2f * PI.toFloat() * 2f)) * (1f - 0.05f * press.value)
                scaleX = s
                scaleY = s
            }
            .liquidGlass(CircleShape, GlassKind.Clear, tint = Color.White.copy(alpha = 0.16f), pressProgress = { press.value })
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(216.dp)) {
            val n = bars.size
            val r = size.minDimension / 2f
            val inner = r * 0.38f
            val reach = r * 0.44f
            for (i in 0 until n) {
                val seed = i.toFloat() / n
                val theta = 2f * PI.toFloat() * seed
                val w = sin(clock * 2f * PI.toFloat() * (0.55f + 0.7f * seed) + seed * 2f * PI.toFloat())
                val breathe = 0.5f + 0.5f * sin(clock * 2f * PI.toFloat() * 0.3f + theta)
                val amp = (0.14f + 0.12f * breathe) * (1f - energy) + energy * bars[i] * (0.35f + 0.65f * abs(w).pow(1.5f))
                val dir = Offset(sin(theta), -kotlin.math.cos(theta))
                val from = Offset(size.width / 2f, size.height / 2f) + dir * inner
                val to = from + dir * (reach * amp.coerceIn(0f, 1.15f))
                drawLine(
                    Color.White.copy(alpha = 0.32f + 0.5f * amp.coerceIn(0f, 1f)),
                    from,
                    to,
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Icon(Icons.Rounded.GraphicEq, contentDescription = stringResource(R.string.recognize_music), tint = OnColor, modifier = Modifier.size(60.dp))
    }
}

@Composable
private fun Ripples(anchor: OrbAnchor) {
    val transition = rememberInfiniteTransition(label = "ripples")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2100, easing = LinearEasing)),
        label = "ripplePhase",
    )
    Canvas(Modifier.fillMaxSize()) {
        val center = anchor.centerIn() ?: Offset(size.width / 2f, size.height * 0.42f)
        val base = 94.dp.toPx()
        for (i in 0 until 3) {
            val t = (phase + i / 3f) % 1f
            val radius = base * (1f + 1.6f * t)
            drawCircle(Color.White.copy(alpha = 0.20f * (1f - t) * (1f - t)), radius = radius, center = center)
        }
    }
}

private enum class PlayState { Ready, Finding, Playing, Paused }

@Composable
private fun ResultContent(
    result: RecognitionResult,
    accentOn: Color,
    playState: PlayState,
    notFound: Boolean,
    onPlay: () -> Unit,
    onAgain: () -> Unit,
) {
    val art = (result.coverArtHqUrl ?: result.coverArtUrl)?.replace("400x400", "1000x1000")
    val meta = listOfNotNull(
        result.album?.takeIf { it.isNotBlank() && it != result.title },
        result.releaseDate?.take(4)?.takeIf { it.isNotBlank() },
        result.genre?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    val shape = RoundedCornerShape(14.dp)
    val pop = remember { Animatable(0.88f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 220f)) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = PageMargin + 8.dp)
            .padding(top = 76.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Reveal(0) {
            Artwork(
                model = art,
                shape = shape,
                hairline = false,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(1f)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .shadow(30.dp, shape, ambientColor = Color.Black, spotColor = Color.Black),
            )
        }
        Spacer(Modifier.height(28.dp))
        Reveal(90) {
            Text(
                result.title,
                style = LiquidTypography.title1,
                color = OnColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Reveal(140) {
            Text(
                result.artist,
                style = LiquidTypography.title3,
                color = OnColorSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Reveal(190) {
                Text(meta, style = LiquidTypography.footnote, color = Color.White.copy(alpha = 0.58f), textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(32.dp))
        Reveal(260) {
            LiquidButton(
                text = when (playState) {
                    PlayState.Finding -> stringResource(R.string.recognition_finding_song)
                    PlayState.Playing -> stringResource(R.string.pause)
                    PlayState.Paused, PlayState.Ready -> stringResource(R.string.liquid_play)
                },
                icon = if (playState == PlayState.Playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                containerColor = Color.White,
                contentColor = lerp(accentOn, Color.Black, 0.2f),
                height = 56.dp,
                enabled = playState != PlayState.Finding,
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Reveal(320) {
            LiquidButton(
                text = stringResource(R.string.re_listen),
                icon = Icons.Rounded.Mic,
                tone = ButtonTone.Glass,
                contentColor = OnColor,
                height = 52.dp,
                onClick = onAgain,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (notFound) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.recognition_song_not_found),
                style = LiquidTypography.footnote,
                color = OnColorSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun RecognitionResult.toHistory() = RecognitionHistory(
    trackId = trackId,
    title = title,
    artist = artist,
    album = album,
    coverArtUrl = coverArtUrl,
    coverArtHqUrl = coverArtHqUrl,
    genre = genre,
    releaseDate = releaseDate,
    label = label,
    shazamUrl = shazamUrl,
    appleMusicUrl = appleMusicUrl,
    spotifyUrl = spotifyUrl,
    isrc = isrc,
    youtubeVideoId = youtubeVideoId,
    recognizedAt = LocalDateTime.now(),
)

@Composable
fun LiquidRecognitionHistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val colors = Liquid.colors
    val history by database.recognitionHistory().collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    var pageMenu by remember { mutableStateOf(false) }
    var rowMenu by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val todayLabel = stringResource(R.string.liquid_today)
    val yesterdayLabel = stringResource(R.string.liquid_yesterday)
    val weekLabel = stringResource(R.string.liquid_this_week)

    val filtered = remember(history, query) {
        val all = history.orEmpty()
        if (query.isBlank()) all else all.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    }
    val groups = remember(filtered, todayLabel) {
        val today = LocalDate.now()
        filtered.groupBy { item ->
            val date = item.recognizedAt.toLocalDate()
            when {
                date == today -> todayLabel
                date == today.minusDays(1) -> yesterdayLabel
                date >= today.minusDays(7) -> weekLabel
                else -> item.recognizedAt.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            }
        }
    }

    fun play(item: RecognitionHistory) {
        scope.launch {
            val playback = findRecognizedSong(item.title, item.artist, item.youtubeVideoId)
            if (playback == null) {
                android.widget.Toast.makeText(context, R.string.recognition_song_not_found, android.widget.Toast.LENGTH_SHORT).show()
            } else {
                playerConnection?.playQueue(playback.queue)
            }
        }
    }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    LargeTitlePage(
        title = stringResource(R.string.liquid_recognized),
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
        actions = {
            Box {
                GlassIconButton(icon = Icons.Rounded.MoreHoriz, enabled = !history.isNullOrEmpty(), onClick = { pageMenu = true })
                LiquidPullDownMenu(
                    expanded = pageMenu,
                    onDismiss = { pageMenu = false },
                    entries = listOf(
                        MenuAction(stringResource(R.string.liquid_clear_history), icon = Icons.Rounded.Delete, destructive = true) { confirmClear = true },
                    ),
                )
            }
        },
    ) {
        item(key = "search") {
            LiquidSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.liquid_search_recognized),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin, vertical = 6.dp),
            )
        }
        when {
            history == null -> Unit
            filtered.isEmpty() && query.isNotBlank() -> item(key = "none") {
                EmptyState(icon = Icons.Rounded.Search, title = stringResource(R.string.liquid_no_results))
            }
            filtered.isEmpty() -> item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.GraphicEq,
                    title = stringResource(R.string.liquid_recognized_empty),
                    message = stringResource(R.string.liquid_recognized_empty_message),
                    actionLabel = stringResource(R.string.recognize_music),
                    onAction = { navController.navigate("recognition") },
                )
            }
            else -> groups.forEach { (label, items) ->
                item(key = "h_$label") { SectionHeader(title = label) }
                items(items, key = { "rh_${it.id}" }) { item ->
                    Box(Modifier.animateItem()) {
                        SongRow(
                            title = item.title,
                            subtitle = listOf(item.artist, item.recognizedAt.format(timeFormat)).joinToString(" · "),
                            artwork = item.coverArtUrl ?: item.coverArtHqUrl,
                            onClick = { play(item) },
                            onLongClick = { rowMenu = item.id },
                            onMore = { rowMenu = item.id },
                        )
                        Box(Modifier.align(Alignment.CenterEnd).padding(end = PageMargin)) {
                            LiquidPullDownMenu(
                                expanded = rowMenu == item.id,
                                onDismiss = { rowMenu = null },
                                entries = buildList {
                                    add(MenuAction(stringResource(R.string.liquid_play), icon = Icons.Rounded.PlayArrow) { play(item) })
                                    add(
                                        MenuAction(stringResource(R.string.search), icon = Icons.Rounded.Search) {
                                            navController.navigate("search/${URLEncoder.encode("${item.title} ${item.artist}", "UTF-8")}")
                                        }
                                    )
                                    item.shazamUrl?.let { add(MenuAction("Shazam", icon = Icons.Rounded.OpenInNew) { open(it) }) }
                                    item.spotifyUrl?.let { add(MenuAction("Spotify", icon = Icons.Rounded.OpenInNew) { open(it) }) }
                                    add(MenuDivider)
                                    add(
                                        MenuAction(stringResource(R.string.delete_from_history), icon = Icons.Rounded.Delete, destructive = true) {
                                            database.query { deleteRecognitionHistoryById(item.id) }
                                        }
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        LiquidAlert(
            title = stringResource(R.string.liquid_clear_history_title),
            message = stringResource(R.string.liquid_clear_history_message),
            confirmLabel = stringResource(R.string.liquid_clear),
            destructive = true,
            onConfirm = { database.query { clearRecognitionHistory() } },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun Reveal(delay: Int, content: @Composable () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delay > 0) kotlinx.coroutines.delay(delay.toLong())
        t.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 300f))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = t.value.coerceIn(0f, 1f)
            translationY = (1f - t.value) * 36.dp.toPx()
        },
    ) { content() }
}
