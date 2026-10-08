package com.shiny.music.canvas

import java.net.URI

/**
 * Reading Apple's motion artwork streams.
 *
 * A motion artwork address is an HLS master playlist: a list of the same clip at several
 * sizes and codecs. Each of those is itself a playlist whose media is one MP4 file, named by
 * its `EXT-X-MAP` line. So a clip can be played as HLS (the player picks a size), or the MP4
 * behind one chosen size can be played directly, which is one plain file that caches whole.
 *
 * Nothing here touches the network: it only reads playlist text.
 */
object HlsMotion {
    /** One size of the clip in a master playlist. */
    data class Variant(
        val playlistUrl: String,
        val bandwidth: Int,
        val width: Int?,
        val codecs: String,
        val height: Int? = null,
    ) {
        /** Taller than wide: the portrait clip. Anything else is the square one. */
        val tall: Boolean get() = width != null && height != null && height > width
    }

    private val attribute = Regex("""([A-Z0-9-]+)=(?:"([^"]*)"|([^,]*))(?:,|$)""")
    private val mapUri = Regex("""URI="([^"]+)"""")

    /** The variants a master playlist at [masterUrl] lists; empty when [text] is not a master playlist. */
    fun variants(masterUrl: String, text: String): List<Variant> {
        val lines = text.lines().map { it.trim() }
        val found = ArrayList<Variant>()
        for ((index, line) in lines.withIndex()) {
            if (!line.startsWith("#EXT-X-STREAM-INF:")) continue
            val attributes = attribute.findAll(line.substringAfter(':'))
                .associate { it.groupValues[1] to it.groupValues[2].ifEmpty { it.groupValues[3] } }
            val playlist = lines.drop(index + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: continue
            val width = attributes["RESOLUTION"]?.substringBefore('x')?.toIntOrNull()
            val height = attributes["RESOLUTION"]?.substringAfter('x', "")?.toIntOrNull()
            val bandwidth = (attributes["AVERAGE-BANDWIDTH"] ?: attributes["BANDWIDTH"])?.toIntOrNull() ?: 0
            found += Variant(resolve(masterUrl, playlist), bandwidth, width, attributes["CODECS"].orEmpty(), height)
        }
        return found
    }

    /**
     * The variant to play as a plain file where the clip is the picture itself and is drawn
     * the width of the screen (the portrait one): H.264, the narrowest size that is at least
     * [minWidth] across, at that size's lowest bitrate. [standard]'s 600 pixels would be drawn
     * enlarged there. Where nothing is that wide, the widest there is.
     */
    fun sharp(variants: List<Variant>, minWidth: Int = 1000): Variant? {
        if (variants.isEmpty()) return null
        val candidates = variants.filter { "avc1" in it.codecs.lowercase() }.ifEmpty { variants }
        val wide = candidates.filter { (it.width ?: 0) >= minWidth }
        return if (wide.isNotEmpty()) {
            wide.minWith(compareBy({ it.width }, { it.bandwidth }))
        } else {
            candidates.maxWith(compareBy({ it.width ?: 0 }, { it.bandwidth }))
        }
    }

    /**
     * The variant to play as a plain file: H.264 where there is one (every phone decodes it),
     * and of those the one nearest 600 pixels across, which is what a cover the width of a
     * phone needs. Null when there are no variants.
     */
    fun standard(variants: List<Variant>): Variant? {
        if (variants.isEmpty()) return null
        val candidates = variants.filter { "avc1" in it.codecs.lowercase() }.ifEmpty { variants }
        val preferred = candidates.filter { it.width != null && it.width in 480..720 }
        if (preferred.isNotEmpty()) {
            return preferred.minWith(compareBy({ kotlin.math.abs((it.width ?: 600) - 600) }, { it.bandwidth }))
        }
        val ordered = candidates.sortedBy { it.bandwidth }
        return if (ordered.size >= 3) ordered[ordered.size / 3] else ordered.first()
    }

    /** The MP4 a variant playlist at [playlistUrl] plays, or null when it names none. */
    fun directVideoUrl(playlistUrl: String, text: String): String? {
        for (line in text.lines()) {
            val stripped = line.trim()
            if (stripped.startsWith("#EXT-X-MAP:")) {
                mapUri.find(stripped)?.let { return resolve(playlistUrl, it.groupValues[1]) }
            }
        }
        return text.lines().map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") && ".mp4" in it }
            ?.let { resolve(playlistUrl, it) }
    }

    private fun resolve(base: String, reference: String): String =
        runCatching { URI(base).resolve(reference).toString() }.getOrDefault(reference)
}
