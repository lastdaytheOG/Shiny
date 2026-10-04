package com.shiny.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import okhttp3.OkHttpClient
import java.util.Locale
import android.view.ViewGroup
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT

@Composable
fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    /** Called the first time a frame actually reaches the screen. */
    onFirstFrame: () -> Unit = {},
    /**
     * Called when this device cannot play the clip at all — every source errored, or nothing
     * decoded within [readinessTimeoutMs]. Callers use it to keep their still artwork.
     */
    onUnavailable: () -> Unit = {},
    readinessTimeoutMs: Long = 6_000L,
) {
    val context = LocalContext.current
    val primary = primaryUrl?.takeIf { it.isNotBlank() }
    val fallback = fallbackUrl?.takeIf { it.isNotBlank() }
    val initial = primary ?: fallback ?: return
    var currentUrl by remember(initial) { mutableStateOf(initial) }
    var isVideoReady by remember(initial) { mutableStateOf(false) }
    var gaveUp by remember(initial) { mutableStateOf(false) }
    var videoAspectRatio by remember(initial) { mutableStateOf(1f) }

    val okHttpClient =
        remember {
            OkHttpClient
                .Builder()
                .proxy(YouTube.proxy)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val isYouTubeMediaHost =
                        host.endsWith("googlevideo.com") ||
                            host.endsWith("googleusercontent.com") ||
                            host.endsWith("youtube.com") ||
                            host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("ytimg.com")

                    if (!isYouTubeMediaHost) return@addInterceptor chain.proceed(request)

                    val clientParam = request.url.queryParameter("c")?.trim().orEmpty()
                    val isWeb =
                        clientParam.startsWith("WEB", ignoreCase = true) ||
                            clientParam.startsWith("WEB_REMIX", ignoreCase = true) ||
                            request.url.toString().contains("c=WEB", ignoreCase = true)

                    val userAgent =
                        when {
                            clientParam.startsWith("WEB", ignoreCase = true) ||
                                clientParam.startsWith("WEB_REMIX", ignoreCase = true) -> YouTubeClient.USER_AGENT_WEB

                            clientParam.startsWith("IOS", ignoreCase = true) -> YouTubeClient.IOS.userAgent

                            clientParam.startsWith("ANDROID_VR", ignoreCase = true) -> YouTubeClient.ANDROID_VR_NO_AUTH.userAgent

                            clientParam.startsWith("ANDROID", ignoreCase = true) -> YouTubeClient.MOBILE.userAgent

                            else -> YouTubeClient.USER_AGENT_WEB
                        }

                    val builder = request.newBuilder().header("User-Agent", userAgent)
                    if (isWeb) {
                        builder.header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                        builder.header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
                    }

                    chain.proceed(builder.build())
                }
                .build()
        }
    val mediaSourceFactory =
        remember(okHttpClient) {
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    context,
                    OkHttpDataSource.Factory(okHttpClient),
                ),
            )
        }
    val exoPlayer =
        remember {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                // No preferred codec is set on purpose: the track selector already ranks by
                // what this device's decoders report, so HEVC is chosen where it decodes and
                // H.264 where it does not. Forcing the highest bitrate used to override that
                // toward the heaviest variant — the one least likely to decode and the most
                // expensive to run for a decorative loop. A cap is enough: this plays inside
                // the artwork square, so anything beyond 720p is battery spent on pixels
                // nobody can see.
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(1280, 1280)
                    .setMaxVideoFrameRate(30)
                    .setForceHighestSupportedBitrate(false)
                    .build()
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    false,
                )
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = isPlaying
            }
        }

    LaunchedEffect(isPlaying) {
        if (exoPlayer.playWhenReady != isPlaying) {
            exoPlayer.playWhenReady = isPlaying
        }
    }

    DisposableEffect(exoPlayer, primary, fallback) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val next =
                        when (currentUrl) {
                            primary -> fallback
                            else -> null
                        }
                    if (!next.isNullOrBlank()) {
                        currentUrl = next
                        isVideoReady = false 
                    } else {
                        // Nothing left to try: tell the caller so it can keep its own artwork
                        // instead of leaving an invisible player buffering behind it.
                        gaveUp = true
                    }
                }

                override fun onRenderedFirstFrame() {
                    isVideoReady = true
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoAspectRatio = videoSize.width.toFloat() / videoSize.height
                    }
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(currentUrl, exoPlayer) {
        val normalized = currentUrl.trim()
        val mimeType =
            when {
                normalized.contains(".m3u8", ignoreCase = true) || 
                normalized.lowercase(Locale.ROOT).split('?').first().endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
                normalized.lowercase(Locale.ROOT).contains(".mp4") -> MimeTypes.VIDEO_MP4
                primary != null && currentUrl == primary -> {
                    
                    
                    if (normalized.contains("apple.com") || normalized.contains("music.apple") || !normalized.contains(".mp4")) {
                        MimeTypes.APPLICATION_M3U8
                    } else {
                        MimeTypes.VIDEO_MP4
                    }
                }
                fallback != null && currentUrl == fallback -> MimeTypes.VIDEO_MP4
                else -> MimeTypes.APPLICATION_M3U8
            }

        val mediaItem =
            MediaItem.Builder()
                .setUri(normalized)
                .setMimeType(mimeType)
                .build()

        exoPlayer.stop()
        isVideoReady = false
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = isPlaying
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(isVideoReady) {
        if (isVideoReady) onFirstFrame()
    }

    // A clip that never decodes produces no error and no frame — it just buffers forever,
    // invisible at alpha 0 while the decoder and the socket stay open. Give it a deadline.
    LaunchedEffect(initial, currentUrl) {
        if (isVideoReady || gaveUp) return@LaunchedEffect
        kotlinx.coroutines.delay(readinessTimeoutMs)
        if (!isVideoReady) gaveUp = true
    }

    LaunchedEffect(gaveUp) {
        if (gaveUp) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            onUnavailable()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasAlpha"
    )

    AndroidView(
        factory = { viewContext ->
            AspectRatioFrameLayout(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                
                val textureView = TextureView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                }
                addView(textureView)
                exoPlayer.setVideoTextureView(textureView)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { view ->
            view.setAspectRatio(videoAspectRatio)
        },
        modifier = modifier.alpha(alpha),
    )
}
