package com.shiny.music.ui.liquid.player

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.shiny.music.artwork.ArtworkMatcher
import com.shiny.music.artwork.ArtworkSource
import com.shiny.music.artwork.Artworks
import com.shiny.music.canvas.CanvasArtwork
import com.shiny.music.constants.DataSaverEnabledKey
import com.shiny.music.models.MediaMetadata
import com.shiny.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/*
 * Apple's artwork, as Now Playing shows it.
 *
 * The song's own artwork is on screen from the first frame; nothing here is waited for. What
 * these add arrives afterwards, and only once it is in memory and ready to draw, so the change
 * is one picture giving way to another and never a blank: Apple's cover in place of a video's
 * still, and the portrait still of an album's motion artwork for the Poster.
 *
 * A picture is asked for at the number of pixels it will cover, so it reaches the glass
 * without being resampled, and never drawn larger than it is. The pictures themselves go
 * through Shiny's image loader like every other cover, so they are kept in its memory and disk
 * caches and are there offline once seen.
 */

/** A cover to show large, the same cover small for reading its colours, and its shape. */
internal data class StageCover(val large: String, val small: String, val aspect: Float = 1f)

/** Addresses already decoded into memory this session: these can be shown without a wait. */
private val warm = ConcurrentHashMap.newKeySet<String>()

private suspend fun loaded(context: Context, address: String): Boolean {
    if (address in warm) return true
    val result = runCatching { context.imageLoader.execute(ImageRequest.Builder(context).data(address).build()) }.getOrNull()
    return (result is SuccessResult).also { if (it) warm += address }
}

/**
 * The first of [candidates] that actually loads, now in the image loader's memory; null when
 * none does. This is the check that a picture exists at the size and quality asked for: an
 * address is only ever shown after it has loaded, and the next best is tried when it has not.
 */
private suspend fun warmUp(context: Context, candidates: List<StageCover>): StageCover? {
    val cover = candidates.firstOrNull { it.large in warm }
        ?: candidates.firstOrNull { loaded(context, it.large) }
        ?: return null
    // The small one too: the stage's colours are read from it, and should turn with the cover.
    loaded(context, cover.small)
    return cover
}

private fun appleCovers(address: String, sizePx: Int): List<StageCover> {
    val large = ArtworkMatcher.sized(address, sizePx) ?: return emptyList()
    val small = ArtworkMatcher.sized(address, 200) ?: return emptyList()
    return listOf(StageCover(large, small))
}

/**
 * Apple's cover for [metadata], [sizePx] across, once it has been found and is ready to show;
 * null until then, and for a song that keeps its own (one that already has a real cover, or a
 * file on the phone).
 *
 * After the playing song is settled, the song [next] names is looked up as well, so its cover
 * is there the moment it starts. Does nothing unless [enabled], and nothing under Data Saver.
 */
@Composable
internal fun rememberAppleCover(
    metadata: MediaMetadata?,
    next: () -> MediaMetadata?,
    enabled: Boolean,
    sizePx: Int,
): StageCover? {
    val context = LocalContext.current
    val dataSaver by rememberPreference(DataSaverEnabledKey, false)
    val active = enabled && !dataSaver
    val resolver = remember(context) { Artworks.resolver(context) }
    val id = metadata?.id
    var cover by remember(id, active, sizePx) {
        // From memory only: a cover already found and already decoded shows with the song.
        val ready = if (active && metadata != null) {
            resolver.result(Artworks.query(metadata)).takeIf { it.source == ArtworkSource.APPLE }
                ?.apple?.staticArtwork?.let { appleCovers(it, sizePx) }?.firstOrNull { it.large in warm }
        } else {
            null
        }
        mutableStateOf(ready)
    }
    LaunchedEffect(id, active, sizePx) {
        if (!active || metadata == null) return@LaunchedEffect
        suspend fun find(song: MediaMetadata): StageCover? = withContext(Dispatchers.IO) {
            resolver.appleCover(Artworks.query(song))?.let { warmUp(context, appleCovers(it, sizePx)) }
        }
        if (cover == null) cover = find(metadata)
        // The queue fills in a moment after a song starts; by then what follows is known.
        delay(2_500)
        next()?.let { find(it) }
    }
    return cover
}

/**
 * The portrait still of [motion], [widthPx] across and in its own shape, once it is in
 * memory; null when the album has no portrait motion artwork, or it is not [enabled]. It is
 * the Poster's whole picture, drawn pixel for pixel, so it is asked for all but uncompressed
 * first and as the ordinary file if that will not load.
 */
@Composable
internal fun rememberTallPoster(motion: CanvasArtwork?, enabled: Boolean, widthPx: Int): StageCover? {
    val context = LocalContext.current
    val wanted = remember(motion, enabled, widthPx) {
        if (!enabled || motion?.tallAnimated == null) return@remember emptyList()
        val small = motion.tallStillAt(240) ?: return@remember emptyList()
        val aspect = motion.tallAspect?.takeIf { it in 0.5f..1f } ?: 0.75f
        listOfNotNull(motion.tallStillAt(widthPx, best = true), motion.tallStillAt(widthPx))
            .distinct().map { StageCover(it, small, aspect) }
    }
    var poster by remember(wanted) { mutableStateOf(wanted.firstOrNull { it.large in warm }) }
    LaunchedEffect(wanted) {
        if (wanted.isNotEmpty() && poster == null) poster = withContext(Dispatchers.IO) { warmUp(context, wanted) }
    }
    return poster
}
