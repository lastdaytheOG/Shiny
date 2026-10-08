package com.shiny.music.ui.liquid

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The two colours a page takes from a piece of artwork. */
data class ArtworkTones(
    /** A deep colour white type reads on — card panels, page grounds. */
    val deep: Color,
    /** The most characterful colour in the image, for accents and glows. */
    val vivid: Color,
)

private val toneCache = LruCache<String, ArtworkTones>(160)

/**
 * The artwork's colours, extracted once per URL from a tiny decode and cached. Returns
 * [fallback] until the image has been read, then animates to the real tones, so a page
 * never waits on its palette to draw.
 */
@Composable
fun rememberArtworkTones(url: String?, fallback: ArtworkTones): ArtworkTones {
    val context = LocalContext.current
    var tones by remember(url) { mutableStateOf(url?.let(::cachedArtworkTones)) }
    LaunchedEffect(url) {
        if (url == null || tones != null) return@LaunchedEffect
        loadArtworkTones(context, url)?.let { tones = it }
    }
    val target = tones ?: fallback
    val deep by animateColorAsState(target.deep, tween(450), label = "toneDeep")
    val vivid by animateColorAsState(target.vivid, tween(450), label = "toneVivid")
    return ArtworkTones(deep, vivid)
}

/** The tones already extracted for [url], if it has been read before. Never touches the image. */
fun cachedArtworkTones(url: String): ArtworkTones? = toneCache.get(url)

/**
 * Reads the tones of [url] from a tiny decode, off the main thread, and remembers them. Null
 * when the image cannot be read or has no colour worth taking.
 */
suspend fun loadArtworkTones(context: Context, url: String): ArtworkTones? {
    cachedArtworkTones(url)?.let { return it }
    val extracted = withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(112, 112)
                .allowHardware(false)
                .build()
            val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return@runCatching null
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val deepSwatch = palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.dominantSwatch
                ?: palette.mutedSwatch
            val vividSwatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
            val deep = deepSwatch?.rgb?.let { Color(it) } ?: return@runCatching null
            val vivid = vividSwatch?.rgb?.let { Color(it) } ?: deep
            ArtworkTones(deep = deepen(deep), vivid = vivid)
        }.getOrNull()
    }
    if (extracted != null) toneCache.put(url, extracted)
    return extracted
}

/** Pulls a colour down until white text on it clears a comfortable contrast. */
private fun deepen(color: Color): Color {
    var c = color
    var guard = 0
    while (c.luminance() > 0.16f && guard < 8) {
        c = lerp(c, Color.Black, 0.18f)
        guard++
    }
    return c
}

val NeutralTones = ArtworkTones(deep = Color(0xFF2C2C2E), vivid = Color(0xFF8E8E93))
