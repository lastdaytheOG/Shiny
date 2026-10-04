package com.shiny.music.ui.liquid.appearance

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.score.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The accent Shiny takes from the playing artwork.
 *
 * Read once per artwork from a small decode, ranked for how well each colour would work
 * as a theme colour, then pulled into the luminance band where it reads on both themes
 * ([AppearanceColor.sanitizeAccent]). The answer is cached by URL, so a song change to
 * an already-seen cover costs a map lookup.
 */
object ArtworkAccent {

    /** Marks artwork that decoded fine but has no colour worth using (a black-and-white cover). */
    private const val NoColour = 0

    private val cache = LruCache<String, Int>(96)

    /**
     * The accent for [url], or null when it has no usable colour or could not be read —
     * the caller falls back to Shiny Rose. A failed read is not cached, so the same cover
     * gets another chance once the network is back.
     */
    suspend fun of(context: Context, url: String): Color? {
        cache.get(url)?.let { return it.toAccent() }
        val argb = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    // Palette resamples to ~112px anyway; decoding the full cover was waste.
                    .size(128, 128)
                    .allowHardware(false)
                    .build()
                val bitmap = context.imageLoader.execute(request).image?.toBitmap()
                    ?: return@runCatching null
                val population = Palette.from(bitmap)
                    .maximumColorCount(16)
                    .generate()
                    .swatches
                    .associate { it.rgb to it.population }
                // Score's own fallback is Google blue; ask for a sentinel instead so a grey
                // cover means "no colour" rather than a colour nobody chose.
                val best = Score.score(population, 1, NoColour, true).firstOrNull() ?: NoColour
                if (best == NoColour) NoColour else AppearanceColor.sanitizeAccent(best)
            }.getOrNull()
        } ?: return null
        cache.put(url, argb)
        logAppearance(if (argb == NoColour) "Artwork accent: none usable" else "Artwork accent updated")
        return argb.toAccent()
    }

    private fun Int.toAccent(): Color? = if (this == NoColour) null else Color(this)
}
