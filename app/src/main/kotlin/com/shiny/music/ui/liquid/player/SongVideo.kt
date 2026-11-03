package com.shiny.music.ui.liquid.player

import android.util.Log
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.shiny.music.models.MediaMetadata
import com.shiny.music.utils.YTPlayerUtils
import com.shiny.music.utils.isLocalMediaId
import com.shiny.music.video.SongVideoMap
import com.shiny.music.video.VideoAligner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/** How the playing song is shown: its artwork, its video in the artwork's place, or its video full screen. */
enum class VideoView { Off, Inline, Full }

/** The video choice, shared by Now Playing and the mini player; it outlives song changes. */
object SongVideoState {
    var view by mutableStateOf(VideoView.Off)
}

/** Where a picture can be drawn. Only one place shows it at a time, the highest one present. */
enum class VideoSlot { Mini, Inline, Full }

/** A video found for a song: its own picture, or its official video, which may need lining up. */
@Immutable
class SongVideo(
    val songId: String,
    val stream: YTPlayerUtils.VideoStream,
    val ownPicture: Boolean,
    val lengthGapMs: Long,
)

private class AlignAnswer(val key: String?, val map: SongVideoMap?, val done: Boolean)

/** A video ready to be shown in step with its song: [videoMsAt] turns a song time into a video time. */
@Immutable
class VideoTrack(val stream: YTPlayerUtils.VideoStream, val videoMsAt: (Long) -> Long)

/**
 * One picture-only decoder follows the music service. Its output moves between the mini player,
 * Now Playing and fullscreen without rebuffering or ever owning audio focus.
 */
@OptIn(UnstableApi::class)
private object SongVideoSession {
    private class Claim(val slot: VideoSlot, val texture: TextureView)

    private var player: ExoPlayer? = null
    private var streamUrl: String? = null
    private var songId: String? = null
    private val claims = ArrayList<Claim>()

    // the surface the picture goes to, and the one that has actually received a frame
    var owner by mutableStateOf<TextureView?>(null)
        private set
    var shownOn by mutableStateOf<TextureView?>(null)
        private set

    fun obtain(context: android.content.Context, stream: YTPlayerUtils.VideoStream, positionMs: Long): ExoPlayer {
        player?.takeIf { streamUrl == stream.url }?.let { return it }
        release()
        streamUrl = stream.url
        return ExoPlayer.Builder(context.applicationContext).build().apply {
            volume = 0f
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            setMediaSource(
                ProgressiveMediaSource.Factory(DefaultHttpDataSource.Factory().setUserAgent(stream.userAgent))
                    .createMediaSource(MediaItem.fromUri(stream.url)),
            )
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    shownOn = owner
                }
            })
            owner?.let { setVideoTextureView(it) }
            seekTo(positionMs.coerceAtLeast(0L))
            prepare()
            player = this
        }
    }

    fun beginSong(nextSongId: String?) {
        if (songId == nextSongId) return
        release()
        songId = nextSongId
    }

    fun claim(slot: VideoSlot, texture: TextureView) {
        if (claims.none { it.texture === texture }) claims += Claim(slot, texture)
        reassign()
    }

    fun unclaim(texture: TextureView) {
        claims.removeAll { it.texture === texture }
        reassign()
    }

    private fun reassign() {
        val top = claims.maxByOrNull { it.slot.ordinal }?.texture
        if (top === owner) return
        owner?.let { player?.clearVideoTextureView(it) }
        owner = top
        shownOn = null
        val video = player ?: return
        if (top != null) {
            video.setVideoTextureView(top)
            // a paused decoder draws nothing on a new surface until it is asked for a frame
            if (!video.isPlaying) video.seekTo(video.currentPosition)
        }
    }

    fun follow(leader: Player?, videoMsAt: (Long) -> Long) {
        val video = player ?: return
        if (leader == null) return
        val target = videoMsAt(leader.currentPosition)
        val length = video.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        // a stretch of the song the video does not have: hold the nearest picture
        val outside = target < 0 || (length != null && target > length)
        val playing = leader.isPlaying && !outside
        if (video.playWhenReady != playing) video.playWhenReady = playing
        val clamped = target.coerceIn(0L, length ?: Long.MAX_VALUE)
        val drift = video.currentPosition - clamped
        if (abs(drift) > HardSyncMs) {
            video.seekTo(clamped + if (playing) SeekLeadMs else 0L)
        }
        val speed = leader.playbackParameters.speed
        video.playbackParameters = if (playing && abs(drift) in (SettledMs + 1)..HardSyncMs) {
            PlaybackParameters(speed * (1f - (drift / CatchUpMs).coerceIn(-MaxNudge, MaxNudge)))
        } else {
            PlaybackParameters(speed)
        }
    }

    fun release() {
        owner?.let { player?.clearVideoTextureView(it) }
        player?.release()
        player = null
        streamUrl = null
        shownOn = null
    }
}

/**
 * Finds a song's picture: a track that is a video plays its own; an audio track borrows its
 * official video, found by title and artist, whatever its length, since the aligner lines it up.
 */
object SongVideoLookup {
    private val cache = LinkedHashMap<String, SongVideo?>(64, 0.75f, true)
    private val lock = Mutex()

    fun cached(song: MediaMetadata): SongVideo? = synchronized(cache) { cache[song.id]?.takeIf { !it.stream.isExpired } }

    suspend fun find(song: MediaMetadata): SongVideo? = lock.withLock {
        synchronized(cache) {
            if (cache.containsKey(song.id) && cache[song.id]?.stream?.isExpired != true) return@withLock cache[song.id]
        }
        val found = runCatching { resolve(song) }.onFailure { Log.w("SongVideo", "lookup failed for ${song.id}", it) }.getOrNull()
        Log.i("SongVideo", "lookup ${song.id} '${song.title}' -> ${found?.stream?.videoId ?: "none"}")
        synchronized(cache) {
            cache[song.id] = found
            while (cache.size > 64) cache.remove(cache.keys.first())
        }
        found
    }

    private suspend fun resolve(song: MediaMetadata): SongVideo? {
        if (song.id.isLocalMediaId()) return null
        if (song.isVideoSong) {
            val own = YTPlayerUtils.resolveVideoStream(song.id).getOrNull() ?: return null
            return SongVideo(song.id, own, ownPicture = true, lengthGapMs = 0L)
        }
        val artists = song.artists.map { normalizeArtist(it.name) }.filter { it.isNotEmpty() }
        val title = normalizeTitle(song.title)
        if (artists.isEmpty() || title.isEmpty()) return null
        val songMs = song.duration * 1000L
        val results = YouTube.search("${song.title} ${song.artists.first().name}", YouTube.SearchFilter.FILTER_VIDEO)
            .getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
        val match = results.take(8)
            .filter { it.musicVideoType == MUSIC_VIDEO_TYPE_OMV }
            .filter { item ->
                val itemTitle = normalizeTitle(item.title)
                val itemArtists = normalizeArtist(item.artists.joinToString(" ") { it.name })
                val titleOk = itemTitle.isNotEmpty() && (itemTitle.contains(title) || title.contains(itemTitle))
                val artistOk = artists.any { itemArtists.contains(it) || it.contains(itemArtists) || itemTitle.contains(it) }
                titleOk && artistOk
            }
            // a full album or a long film is not this song's video
            .filter { songMs <= 0 || (it.duration ?: 0) * 1000L <= songMs * 2 + 60_000L }
            .minByOrNull { if (songMs > 0 && it.duration != null) abs(it.duration!! * 1000L - songMs) else Long.MAX_VALUE / 2 }
            ?: return null
        val stream = YTPlayerUtils.resolveVideoStream(match.id).getOrNull() ?: return null
        val videoMs = stream.durationMs.takeIf { it > 0 } ?: ((match.duration ?: 0) * 1000L)
        val gap = if (songMs > 0 && videoMs > 0) abs(videoMs - songMs) else Long.MAX_VALUE
        return SongVideo(song.id, stream, ownPicture = false, lengthGapMs = gap)
    }

    private val brackets = Regex("""[(\[][^)\]]*[)\]]""")
    private val noise = Regex("""\b(official|music|video|lyrics?|lyrical|audio|hd|4k|remastered|full|song|visualizer|mv)\b""")
    private val feat = Regex("""\b(feat|ft|featuring)\b.*""")
    private val junk = Regex("""[^\p{L}\p{N}]+""")

    private fun normalizeTitle(raw: String): String =
        raw.lowercase().replace(brackets, " ").replace(feat, " ").replace(noise, " ").replace(junk, "")

    private fun normalizeArtist(raw: String): String =
        raw.lowercase().replace(Regex("""vevo|\s-\stopic|official"""), "").replace(junk, "")
}

/** The song's video, if it has one, looked up once the song has settled for a moment. */
@Composable
fun rememberSongVideo(song: MediaMetadata?): SongVideo? {
    LaunchedEffect(SongVideoState.view) {
        if (SongVideoState.view == VideoView.Off) SongVideoSession.release()
    }
    LaunchedEffect(song?.id) {
        SongVideoSession.beginSong(song?.id)
    }
    val found by produceState(song?.let { SongVideoLookup.cached(it) }, song?.id) {
        if (song == null) {
            value = null
            return@produceState
        }
        if (value?.songId == song.id) return@produceState
        value = null
        // skipping through songs should not look up every one passed on the way
        delay(LookupSettleMs)
        value = SongVideoLookup.find(song)
    }
    return found?.takeIf { it.songId == song?.id }
}

/**
 * The video once it can be shown in step, or null. Its own picture and a same-length video show
 * at once; any other waits for the aligner, which only runs while video is switched on.
 */
@Composable
fun rememberVideoTrack(video: SongVideo?): VideoTrack? {
    val on = SongVideoState.view != VideoView.Off
    // the map, and whether the aligner has answered (a miss answers with no map), for one song and video
    val key = video?.let { "${it.songId}>${it.stream.videoId}" }
    fun answer(v: SongVideo?) = AlignAnswer(key, v?.let { VideoAligner.known(it.songId, it.stream.videoId) }, v != null && VideoAligner.isKnown(v.songId, v.stream.videoId))
    val result by produceState(answer(video), key, on) {
        // produceState keeps its value across keys, so start from this song's own answer
        value = answer(video)
        if (video == null || video.ownPicture || !on || value.done) return@produceState
        val map = VideoAligner.align(video.songId, video.stream.videoId, video.stream.audioUrl, video.stream.userAgent)
        value = AlignAnswer(key, map, true)
    }
    if (video == null || !on) return null
    val current = result.takeIf { it.key == key }
    val aligned = current?.map
    val settled = current?.done == true
    return remember(video, aligned, settled) {
        when {
            video.ownPicture -> VideoTrack(video.stream) { it }
            aligned != null -> VideoTrack(video.stream) { aligned.videoMsAt(it) }
            // shown at once, side by side, and moved onto the song the moment the aligner answers
            else -> VideoTrack(video.stream) { it }
        }.also { Log.i("SongVideo", "track ${video.songId} -> ${video.stream.videoId} gap=${video.lengthGapMs} aligned=${aligned != null} settled=$settled") }
    }
}

/**
 * The song's picture, silent, following the music player. [fill] crops it to cover the space,
 * otherwise it fits inside; it never spills past the space it is given.
 */
@OptIn(UnstableApi::class)
@Composable
fun SongVideoSurface(
    track: VideoTrack,
    slot: VideoSlot,
    leader: () -> Player?,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
) {
    val context = LocalContext.current
    val stream = track.stream
    remember(stream.url) {
        val leaderMs = leader()?.currentPosition ?: 0L
        SongVideoSession.obtain(context, stream, track.videoMsAt(leaderMs))
    }
    var texture by remember { mutableStateOf<TextureView?>(null) }
    LaunchedEffect(stream.url, track) {
        while (isActive) {
            if (texture != null && SongVideoSession.owner === texture) SongVideoSession.follow(leader(), track.videoMsAt)
            delay(250)
        }
    }
    val visible = texture != null && SongVideoSession.owner === texture && SongVideoSession.shownOn === texture
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(if (visible) 360 else 120), label = "videoIn")
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val ratio = if (stream.width > 0 && stream.height > 0) stream.width.toFloat() / stream.height else 16f / 9f
        val boxRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else ratio
        val wider = ratio > boxRatio
        val width = if (wider == fill) maxHeight * ratio else maxWidth
        val height = if (wider == fill) maxHeight else maxWidth / ratio
        AndroidView(
            factory = { viewContext ->
                TextureView(viewContext).also { view ->
                    texture = view
                    SongVideoSession.claim(slot, view)
                }
            },
            onRelease = { view ->
                SongVideoSession.unclaim(view)
                if (texture === view) texture = null
            },
            modifier = Modifier
                .requiredSize(width, height)
                .graphicsLayer { this.alpha = alpha },
        )
    }
}

private const val LookupSettleMs = 500L
private const val HardSyncMs = 1_200L
private const val SettledMs = 40L
private const val CatchUpMs = 2_500f
private const val MaxNudge = 0.12f
private const val SeekLeadMs = 250L

@Composable
fun FullVideo(track: VideoTrack, leader: () -> Player?, onClose: () -> Unit) {
    var chrome by remember { mutableStateOf(true) }
    LaunchedEffect(chrome) {
        if (chrome) {
            delay(2_500)
            chrome = false
        }
    }
    val chromeAlpha by animateFloatAsState(if (chrome) 1f else 0f, tween(260), label = "videoChrome")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { chrome = !chrome },
    ) {
        SongVideoSurface(track = track, slot = VideoSlot.Full, leader = leader, fill = false, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .alpha(chromeAlpha)
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.16f), CircleShape)
                .clickable(enabled = chrome, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Close video",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
