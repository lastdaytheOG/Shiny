package com.shiny.music.ui.liquid.player

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.unit.IntSize
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import com.shiny.music.artwork.Artworks
import com.shiny.music.canvas.CanvasArtwork
import com.shiny.music.constants.DataSaverEnabledKey
import com.shiny.music.models.MediaMetadata
import com.shiny.music.utils.rememberPreference
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.player.CanvasArtworkPlaybackCache
import com.shiny.music.ui.player.CanvasArtworkPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
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
    dissolve: Boolean = false,
    /** Play the album's portrait motion artwork, where it has one, instead of the square one. */
    tall: Boolean = false,
    /** The pixels the artwork covers, where the caller knows them; otherwise they are measured here. See [CanvasArtworkPlayer]. */
    sharpPx: IntSize? = null,
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
    // A clip that would not play is not written off for as long as the song lasts: it is tried
    // again each time the player is opened, when the connection that failed it may be back.
    LaunchedEffect(videoVisible) {
        if (videoVisible && videoUnavailable) videoUnavailable = false
    }

    // The made-up motion is only ever for a cover that has none of its own, and only for
    // whoever turned it on. An album with a real clip in this shape is left to that clip: the
    // still under it is not drifted or zoomed, before the clip arrives or after.
    val ownClip = motion != null && !videoUnavailable &&
        (if (tall) !motion.tallAnimated.isNullOrBlank() else !motion.preferredAnimationUrl.isNullOrBlank())
    @Suppress("NAME_SHADOWING") val living = living && !ownClip

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

    // The pixels the cover covers, where the caller has not said (the landscape player): the
    // largest it has been, so a smaller clip is never sent for when it shrinks. A clip chosen
    // without them started 486 pixels across in a cover nine hundred wide.
    var ownPx by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier.onSizeChanged { if (it.width > ownPx.width) ownPx = it }) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (living) Modifier.livingMotion { clock.floatValue } else Modifier)
        ) {
            Artwork(
                model = model,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(0),
                hairline = false,
                dissolve = dissolve,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Not before the cover has been measured (a frame): the clip's size is chosen from it,
        // and one chosen without it would be fetched and then changed.
        val coverPx = sharpPx ?: ownPx.takeIf { it.width > 0 && it.height > 0 }
        if (motion != null && videoVisible && !videoUnavailable && coverPx != null) {
            val portrait = motion.tallAnimated?.takeIf { tall }
            CanvasArtworkPlayer(
                primaryUrl = portrait ?: motion.animated,
                // Each shape falls back to its own plain file, never to the other shape cut down
                // to fit: a portrait clip in the square, or the square one hung as the poster.
                fallbackUrl = if (portrait != null) motion.tallVideoUrl else motion.videoUrl,
                sharpFor = coverPx,
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
 * The song's motion artwork, if it has any: found once through the artwork resolver (which
 * remembers the answer on disk, and shares one lookup between everything asking at once) and
 * held here by song for the session. A song with none is the ordinary case.
 */
@Composable
fun rememberMotionArtwork(metadata: MediaMetadata?, enabled: Boolean): CanvasArtwork? {
    val context = LocalContext.current
    val id = metadata?.id
    var found by remember(id) {
        mutableStateOf(
            id?.let { CanvasArtworkPlaybackCache.get(it) }
                ?: metadata?.let { Artworks.resolver(context).known(Artworks.query(it))?.motion }
        )
    }
    LaunchedEffect(id, enabled) {
        if (!enabled || metadata == null) return@LaunchedEffect
        found?.let {
            Artworks.log("lookup    '${metadata.title}' -> already known: square=${it.preferredAnimationUrl != null} tall=${it.tallAnimated != null}")
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.IO) {
            // Opened here, off the main thread, so the player finds it ready.
            com.shiny.music.artwork.MotionArtworkCache.open(context)
            Artworks.resolver(context).motion(Artworks.query(metadata))
        }
        Artworks.log(
            "lookup    '${metadata.title}' -> " + (result?.let { "square=${it.preferredAnimationUrl != null} tall=${it.tallAnimated != null}" } ?: "no motion artwork")
        )
        if (result != null) {
            CanvasArtworkPlaybackCache.put(metadata.id, result)
            found = result
        }
    }
    return if (enabled) found else null
}
