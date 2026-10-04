package com.shiny.music.ui.liquid.launch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Choreographer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import com.shiny.music.R
import com.shiny.music.utils.reportException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread

/**
 * Decodes the first screen's artwork, one still image, on its own thread.
 *
 * Started only once the app's first frame is ready (see MainActivity), so it never competes
 * with the start-up, while the system splash still covers the screen. [release] hands the
 * pixels back as soon as the artwork is gone rather than whenever a GC next runs.
 */
object LaunchArt {
    private val _image = MutableStateFlow<ImageBitmap?>(null)
    val image: StateFlow<ImageBitmap?> = _image.asStateFlow()

    /** Bumped by [release], so a decode that finishes after it is thrown away. */
    @Volatile
    private var generation = 0

    fun load(context: Context) {
        val resources = context.applicationContext.resources
        val token = generation
        thread(name = "LaunchArt", isDaemon = true) {
            val decoded = runCatching {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                }
                BitmapFactory.decodeResource(resources, R.drawable.splash_artwork, options).asImageBitmap()
            }.onFailure(::reportException).getOrNull()
            if (token == generation) _image.value = decoded else decoded?.asAndroidBitmap()?.recycle()
        }
    }

    /** Main thread only. */
    fun release() {
        generation++
        val released = _image.value ?: return
        _image.value = null
        // Three frames on, the artwork has left the composition and no frame in flight draws it.
        val choreographer = Choreographer.getInstance()
        choreographer.postFrameCallback {
            choreographer.postFrameCallback {
                choreographer.postFrameCallback { released.asAndroidBitmap().recycle() }
            }
        }
    }
}

/**
 * The first screen: the dove painting with the logo set in a pane of liquid glass, as one
 * still image. Composed once the system splash starts to leave, shown for a moment, then it
 * fades into the app, which has been loading underneath all along. A tap ends it early.
 *
 * [onCoversApp] says when the image hides the app completely, so the app can skip drawing
 * beneath it; it turns false one frame before the fade, so the app is back underneath first.
 */
@Composable
fun LaunchArtwork(
    image: ImageBitmap,
    onCoversApp: (Boolean) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fade = remember { Animatable(1f) }
    var skipRequested by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(onFinished)
    val coversApp by rememberUpdatedState(onCoversApp)

    LaunchedEffect(Unit) {
        coversApp(true)
        withTimeoutOrNull(HOLD_MS) { snapshotFlow { skipRequested }.first { it } }
        coversApp(false)
        withFrameNanos { }
        fade.animateTo(0f, tween(FADE_MS, easing = LinearEasing))
        finish()
    }

    Image(
        bitmap = image,
        contentDescription = null,
        // The logo sits at the image's exact centre, so a centred crop keeps it centred on
        // every screen shape.
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
        filterQuality = FilterQuality.Low,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            // Nothing reaches the app underneath while the artwork is up; a tap ends it.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.type == PointerEventType.Release) skipRequested = true
                    }
                }
            },
    )
}

private const val HOLD_MS = 1_200L
private const val FADE_MS = 220
