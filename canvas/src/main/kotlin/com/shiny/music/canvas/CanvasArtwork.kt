package com.shiny.music.canvas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The end of an image-server address that says its size but not its quality. */
private val plainJpeg = Regex("""(/\d+x\d+[a-z]{0,2})\.jpg$""")

@Serializable
data class CanvasArtwork(
    val name: String? = null,
    val artist: String? = null,
    @SerialName("albumId")
    val albumId: String? = null,
    val albumName: String? = null,
    val static: String? = null,
    val animated: String? = null,
    val videoUrl: String? = null,
    /**
     * The album's portrait motion artwork, where it has one: the framing made for a cover that
     * fills the head of a phone screen (about 3:4), not the square one cut down.
     */
    val tallAnimated: String? = null,
    /**
     * A still frame of [tallAnimated], as an address with `{w}` and `{h}` to fill in. Shown
     * while the video is not (loading, paused off, unavailable), so the stage keeps its shape.
     */
    val tallStill: String? = null,
    /** Width over height of the tall artwork, when the catalogue says. */
    val tallAspect: Float? = null,
    /** The plain MP4 behind one size of [tallAnimated]: what plays if the stream will not. */
    val tallVideoUrl: String? = null,
) {
    val preferredAnimationUrl: String?
        get() = animated ?: videoUrl

    /** Whether there is a clip of either shape: an album may have the portrait one alone. */
    val hasMotion: Boolean
        get() = !preferredAnimationUrl.isNullOrBlank() || !tallAnimated.isNullOrBlank()

    /**
     * [tallStill] at [width] pixels across and in its own shape, or null when there is none.
     * With [best] it is asked for with the least compression the image server gives.
     */
    fun tallStillAt(width: Int, best: Boolean = false): String? {
        val template = tallStill ?: return null
        val aspect = tallAspect?.takeIf { it > 0f } ?: (3f / 4f)
        val height = kotlin.math.ceil(width / aspect).toInt()
        val address = template
            .replace("{w}", width.toString())
            .replace("{h}", height.toString())
            .replace("{c}", "bb")
            .replace("{f}", "jpg")
        return if (best) plainJpeg.replace(address) { "${it.groupValues[1]}-100.jpg" } else address
    }

}
