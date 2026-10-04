package com.shiny.music.ui.liquid.player

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.shiny.music.applecanvas.AppleMusicCanvasProvider
import com.shiny.music.canvas.CanvasArtwork
import com.shiny.music.canvas.TidalCanvasProvider
import com.shiny.music.constants.DataSaverEnabledKey
import com.shiny.music.models.MediaMetadata
import com.shiny.music.utils.rememberPreference
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.player.CanvasArtworkPlaybackCache
import com.shiny.music.ui.player.CanvasArtworkPlayer
import com.shiny.music.ui.player.normalizeCanvasArtistName
import com.shiny.music.ui.player.normalizeCanvasSongTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * The Now Playing cover, alive. When the song has motion artwork (Apple Music's animated
 * covers, or a canvas from the other providers) and [motionVideos] is on, the video loops
 * silently in the cover's place. Otherwise the still cover itself moves: a slow drift and
 * zoom, a liquid ripple flowing through the image, and a band of light passing over it like
 * a reflection on glass (the ripple needs Android 13's runtime shaders; older versions
 * keep the drift and the light).
 *
 * Everything is driven from a frame clock read in the layer and draw phases, so the cover
 * animates without recomposing; and it only runs while [animate] — playing, with the player
 * open — so a paused or hidden player costs nothing.
 */
@Composable
fun LivingArtwork(
    model: Any?,
    metadata: MediaMetadata?,
    animate: Boolean,
    living: Boolean,
    motionVideos: Boolean,
    videoVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    // Motion artwork is a video download nobody asked for, so Data Saver opts out of both
    // the lookup and the playback — the animated poster costs no network at all.
    val dataSaver by rememberPreference(DataSaverEnabledKey, false)
    @Suppress("NAME_SHADOWING") val motionVideos = motionVideos && !dataSaver
    val motion = rememberMotionArtwork(metadata, enabled = motionVideos && videoVisible)

    // Motion video → animated poster → still cover. The video only supersedes the poster
    // once a frame has actually reached the screen, and hands the slot straight back if this
    // device cannot decode the clip, so a failure is indistinguishable from having no video.
    var videoRendered by remember(motion) { mutableStateOf(false) }
    var videoUnavailable by remember(motion) { mutableStateOf(false) }

    val clock = remember { mutableFloatStateOf(0f) }
    // The poster keeps breathing until the video is genuinely on screen — finding a video is
    // not the same as playing one. Once it is, the poster's per-frame work stops.
    val running = animate && living && !videoRendered
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            clock.floatValue = (clock.floatValue + (now - last) / 1_000_000_000f) % 3600f
            last = now
        }
    }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (living) Modifier.livingMotion { clock.floatValue } else Modifier)
        ) {
            Artwork(
                model = model,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(0),
                hairline = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (motion != null && videoVisible && !videoUnavailable) {
            CanvasArtworkPlayer(
                primaryUrl = motion.animated,
                fallbackUrl = motion.videoUrl,
                isPlaying = animate,
                modifier = Modifier.fillMaxSize(),
                onFirstFrame = { videoRendered = true },
                onUnavailable = {
                    videoUnavailable = true
                    videoRendered = false
                },
            )
        }
    }
}

/**
 * Drift, zoom, ripple and sheen, all from [time] seconds. The periods are long and
 * incommensurate so the motion never visibly repeats.
 */
private fun Modifier.livingMotion(time: () -> Float): Modifier = this
    .graphicsLayer {
        val t = time()
        val zoom = 1.06f + 0.035f * sin(t * 2f * PI.toFloat() / 17f)
        scaleX = zoom
        scaleY = zoom
        translationX = size.width * 0.018f * sin(t * 2f * PI.toFloat() / 23f)
        translationY = size.height * 0.014f * sin(t * 2f * PI.toFloat() / 29f + 1.3f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && size.width > 0f) {
            renderEffect = LiquidArtShader.effect(size.width, size.height, t)
        }
    }
    .drawWithContent {
        drawContent()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // A diagonal band of light crossing the cover every ~11 seconds.
            val t = time()
            val phase = ((t / 11f) % 1f) * 2.6f - 0.8f
            val w = size.width
            val h = size.height
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.45f to Color.White.copy(alpha = 0f),
                    0.5f to Color.White.copy(alpha = 0.16f),
                    0.55f to Color.White.copy(alpha = 0f),
                    1f to Color.Transparent,
                    start = Offset(w * (phase - 0.5f), 0f),
                    end = Offset(w * (phase + 0.5f), h),
                )
            )
        }
    }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object LiquidArtShader {
    private const val SRC = """
        uniform shader image;
        uniform float2 size;
        uniform float time;
        half4 main(float2 p) {
            float2 uv = p / size;
            float t = time;
            // A slow, liquid flow: sums of sines at different scales and speeds.
            float2 flow = float2(
                sin(uv.y * 5.0 + t * 0.55) + 0.6 * sin((uv.x + uv.y) * 7.0 - t * 0.42),
                cos(uv.x * 4.5 - t * 0.47) + 0.6 * sin((uv.x - uv.y) * 6.0 + t * 0.36)
            );
            float amp = min(size.x, size.y) * 0.0065;
            float2 q = clamp(p + flow * amp, float2(0.5), size - 0.5);
            half4 c = image.eval(q);
            // A band of light passing diagonally, like a reflection moving across glass.
            float band = (uv.x * 0.75 + uv.y * 0.65) - (fract(t / 11.0) * 2.6 - 0.6);
            float sheen = exp(-band * band * 40.0) * 0.16;
            // A faint caustic shimmer riding the flow.
            float caustic = pow(max(0.0, sin((uv.x * 9.0 + flow.x) + t * 0.8) * sin((uv.y * 8.0 + flow.y) - t * 0.6)), 6.0) * 0.07;
            return half4(c.rgb + half3(sheen + caustic), c.a);
        }
    """
    private val shader by lazy { RuntimeShader(SRC) }

    fun effect(width: Float, height: Float, time: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("time", time)
        return RenderEffect.createRuntimeShaderEffect(shader, "image").asComposeRenderEffect()
    }
}

/**
 * The song's motion artwork, if any provider has one: looked up once per song, cached
 * for the session, and checked against the song's artist and title or album so an
 * unrelated video never plays over the wrong cover.
 */
@Composable
fun rememberMotionArtwork(metadata: MediaMetadata?, enabled: Boolean): CanvasArtwork? {
    val id = metadata?.id
    var found by remember(id) { mutableStateOf(id?.let { CanvasArtworkPlaybackCache.get(it) }) }
    LaunchedEffect(id, enabled) {
        if (!enabled || metadata == null || found != null) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) { runCatching { lookUpMotionArtwork(metadata) }.getOrNull() }
        if (result != null) {
            CanvasArtworkPlaybackCache.put(metadata.id, result)
            found = result
        }
    }
    return if (enabled) found else null
}

private suspend fun lookUpMotionArtwork(metadata: MediaMetadata): CanvasArtwork? {
    val storefront = Locale.getDefault().country.takeIf { it.length == 2 }?.lowercase(Locale.ROOT) ?: "us"
    val album = metadata.album?.title
    val titleRaw = metadata.title
    val artistRaw = metadata.artists.firstOrNull()?.name.orEmpty()
    val title = normalizeCanvasSongTitle(titleRaw)
    val artist = normalizeCanvasArtistName(artistRaw)

    val fetched = linkedSetOf(title to artist, titleRaw to artist, title to artistRaw, titleRaw to artistRaw)
        .filter { (s, a) -> s.isNotBlank() && a.isNotBlank() }
        .firstNotNullOfOrNull { (s, a) ->
            if (!album.isNullOrBlank()) {
                AppleMusicCanvasProvider.getByAlbumArtist(album = album, artist = a, storefront = storefront)
                    ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                    ?.let { return@firstNotNullOfOrNull it }
            }
            TidalCanvasProvider.getBySongArtist(song = s, artist = a, album = album)?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                ?: AppleMusicCanvasProvider.getBySongArtist(song = s, artist = a, album = album, storefront = storefront)
                    ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
        } ?: return null

    fun loose(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return true
        val na = normalizeCanvasSongTitle(a)
        val nb = normalizeCanvasSongTitle(b)
        return a.contains(b, true) || b.contains(a, true) || na.contains(nb, true) || nb.contains(na, true)
    }

    val artistOk = fetched.artist?.let { found ->
        val nf = normalizeCanvasArtistName(found)
        found.contains(artistRaw, true) || artistRaw.contains(found, true) || nf.contains(artist, true) || artist.contains(nf, true)
    } ?: true
    val titleOk = when {
        fetched.albumName != null && !album.isNullOrBlank() -> loose(fetched.albumName!!, album)
        fetched.name != null -> loose(fetched.name!!, titleRaw) || (!album.isNullOrBlank() && loose(fetched.name!!, album))
        else -> true
    }
    return fetched.takeIf { artistOk && titleOk }
}

