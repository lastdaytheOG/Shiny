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
import androidx.compose.ui.unit.IntSize
import androidx.media3.ui.AspectRatioFrameLayout
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import okhttp3.OkHttpClient
import java.util.Locale
import android.view.ViewGroup
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT

/**
 * Which size of a motion clip plays. Apple makes each in some thirty sizes and bitrates, from
 * 310 pixels across to 2048.
 *
 * No preferred codec is set on purpose: the track selector already ranks by what this
 * device's decoders report, so HEVC is chosen where it decodes in hardware and H.264 where it
 * does not.
 *
 * A clip plays at the size the screen shows it ([sharpFor], the pixels it covers): of the
 * sizes there are, the smallest that covers those pixels, at that size's best bitrate. Not
 * adaptive, because an adaptive start is a soft first loop and a second download of the same
 * clip (measured on the cover's square: it began 486 pixels across in a square 933 wide, and
 * stayed there); and not the largest, because pixels the screen does not have cost decoding
 * and megabytes and show nothing. The clip is fetched once and kept (see MotionArtworkCache).
 *
 * Only where the pixels are not known does it adapt to the connection, under a cap.
 */
private fun ExoPlayer.chooseClipSize(context: android.content.Context, sharpFor: IntSize?) {
    val wanted = trackSelectionParameters.buildUpon().setMaxVideoFrameRate(30)
    if (sharpFor != null && sharpFor.width > 0 && sharpFor.height > 0) {
        wanted
            .setViewportSize(sharpFor.width, sharpFor.height, false)
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setForceHighestSupportedBitrate(true)
    } else {
        wanted
            .setViewportSizeToPhysicalDisplaySize(context, true)
            .setMaxVideoSize(1280, 1280)
            .setForceHighestSupportedBitrate(false)
    }
    val parameters = wanted.build()
    if (parameters != trackSelectionParameters) trackSelectionParameters = parameters
}

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
    /** How long a clip that is still being fetched is waited for, at the most. */
    slowConnectionPatienceMs: Long = 25_000L,
    /** The pixels the clip covers: it plays at the size that matches them. Null where they are not known. */
    sharpFor: IntSize? = null,
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
            val network = OkHttpDataSource.Factory(okHttpClient)
            // Through the motion artwork cache where it is open: a clip fetched once is read
            // from disk every time after, and plays offline. A read that fails there falls
            // through to the network rather than failing the clip.
            val cached = com.shiny.music.artwork.MotionArtworkCache.peek()?.let { cache ->
                androidx.media3.datasource.cache.CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(network)
                    .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            }
            DefaultMediaSourceFactory(DefaultDataSource.Factory(context, cached ?: network))
        }
    val exoPlayer =
        remember {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                chooseClipSize(context, sharpFor)
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

    // The same player goes on to play the next clip, which may cover a different box.
    LaunchedEffect(exoPlayer, sharpFor) { exoPlayer.chooseClipSize(context, sharpFor) }

    LaunchedEffect(isPlaying) {
        if (exoPlayer.playWhenReady != isPlaying) {
            exoPlayer.playWhenReady = isPlaying
        }
    }

    // When the clip now loading was asked for, to say how long its first frame took.
    val askedAt = remember { longArrayOf(0L) }

    DisposableEffect(exoPlayer, primary, fallback) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val next =
                        when (currentUrl) {
                            primary -> fallback
                            else -> null
                        }
                    com.shiny.music.artwork.Artworks.log(
                        "clip      FAILED ${error.errorCodeName} (${error.cause?.message}) on $currentUrl -> " +
                            if (next.isNullOrBlank()) "nothing left to try, the still artwork stays" else "trying the plain file $next"
                    )
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
                    if (!isVideoReady) {
                        val format = exoPlayer.videoFormat
                        val size = exoPlayer.videoSize
                        com.shiny.music.artwork.Artworks.log(
                            "clip      ON SCREEN after ${android.os.SystemClock.elapsedRealtime() - askedAt[0]} ms: " +
                                "${size.width}x${size.height} ${if (size.height > size.width) "tall" else "square"} " +
                                "${format?.codecs} ${(format?.bitrate ?: 0) / 1000} kbps ${format?.frameRate} fps " +
                                "(drawn in ${sharpFor?.let { "${it.width}x${it.height}" } ?: "a box of unknown size"} px) from $currentUrl"
                        )
                    }
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
        askedAt[0] = android.os.SystemClock.elapsedRealtime()
        com.shiny.music.artwork.Artworks.log(
            "clip      asked for $normalized as ${if (mimeType == MimeTypes.APPLICATION_M3U8) "HLS" else "MP4"}" +
                (if (com.shiny.music.artwork.MotionArtworkCache.peek() != null) ", through the clip cache" else ", cache not open")
        )
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
    // A clip that is still arriving is another thing: on a slow connection its first piece
    // can take longer than that, and giving up then would leave the still for the whole song.
    // That one is waited for, for as long as the player is still fetching it, up to a limit.
    LaunchedEffect(initial, currentUrl) {
        if (isVideoReady || gaveUp) return@LaunchedEffect
        kotlinx.coroutines.delay(readinessTimeoutMs)
        var waited = readinessTimeoutMs
        while (!isVideoReady && exoPlayer.isLoading && waited < slowConnectionPatienceMs) {
            kotlinx.coroutines.delay(500)
            waited += 500
        }
        if (!isVideoReady) {
            com.shiny.music.artwork.Artworks.log(
                "clip      GAVE UP after $waited ms with no frame (${if (exoPlayer.isLoading) "still fetching" else "nothing arriving"}) on $currentUrl"
            )
            gaveUp = true
        }
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
