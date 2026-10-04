

package com.shiny.music.ui.utils

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    
    
    
    
    if (this.contains("i.ytimg.com")) {
        return this.replace(
            Regex("(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.jpg"),
            ytImgVariantFor(maxOf(width ?: 0, height ?: 0))
        )
    }

    
    if (this.contains("googleusercontent.com") && this.contains("=w")) {
        val baseUrl = this.split("=w")[0]
        val size = googleSizeFor(maxOf(width ?: 0, height ?: 0))
        return "$baseUrl=w$size-h$size"
    }

    
    if (this.contains("yt3.ggpht.com")) {
        
        val baseUrl = this.split("=")[0].split("-s")[0]
        return "$baseUrl=s${width ?: height}"
    }

    
    "https://lh\\d\\.googleusercontent\\.com/.*".toRegex().matchEntire(this)?.let {
        val size = googleSizeFor(maxOf(width ?: 0, height ?: 0))
        return "${this.split("=")[0]}=w$size-h$size"
    }

    return this
}

/**
 * The `i.ytimg.com` still that is at least as large as [requested] pixels on its longest
 * side, since those are served as a fixed set of sizes rather than an arbitrary one.
 *
 * The named variants and their long edges: `default` 120, `mqdefault` 320, `hqdefault`
 * 480, `sddefault` 640, `maxresdefault` 1280.
 *
 * This used to be a single cut at 1200: anything below it — which is every caller in the
 * app except one — was served `hqdefault`, a 480x360 image. Asking for 1080 and getting
 * 480 is why some artwork looked soft the whole time it was on screen, and why artwork
 * that had already been fetched small for a list stayed soft when the same track opened
 * full-screen. Album covers hosted on googleusercontent were unaffected, which is why it
 * only ever showed on *some* tracks: the ones whose art comes from a video still.
 *
 * `maxresdefault` does not exist for every video and 404s when it does not; callers that
 * display it fall back to `hqdefault` on error, so asking for it costs nothing when it is
 * missing and gains a sharp image whenever it is there.
 */
private fun ytImgVariantFor(requested: Int): String = when {
    requested <= 0 -> "hqdefault.jpg"
    requested <= 120 -> "default.jpg"
    requested <= 320 -> "mqdefault.jpg"
    requested <= 480 -> "hqdefault.jpg"
    requested <= 640 -> "sddefault.jpg"
    else -> "maxresdefault.jpg"
}

/**
 * The googleusercontent size served for a request of [requested] pixels: a few fixed sizes,
 * so the same cover asked for by different screens is mostly one download. A list row or a
 * small circle (240 px or less) gets 226, YouTube Music's own row size; it was served 500
 * like a tile, several times the pixels a 50 dp row can show.
 */
private fun googleSizeFor(requested: Int): Int = when {
    requested >= 1000 -> 1200
    requested in 1..240 -> 226
    else -> 500
}
