


package com.shiny.music.localmedia

import java.util.Locale

/**
 * The local formats Shiny can actually play. Playback is core Media3 on the platform's
 * decoders — no FFmpeg or MIDI extension is bundled — so this is the set those decode.
 */
object SupportedLocalAudio {
    private val supportedExtensions = setOf(
        "aac",
        "amr",
        "flac",
        "m4a",
        "m4b",
        "mka",
        "mp3",
        "mp4",
        "oga",
        "ogg",
        "opus",
        "wav",
        "weba",
        "webm",
        "3ga",
        "3gp",
    )

    private val blockedExtensions = setOf(
        // MIDI needs Media3's MIDI extension; .m4p is FairPlay-protected AAC.
        "mid",
        "m4p",
        "ape",
        "cue",
        "dsd",
        "dsf",
        "dff",
        "midi",
        "iso",
        "tta",
        "wv",
        "wvc",
    )

    private val supportedMimeTypes = setOf(
        "audio/aac",
        "audio/aac-adts",
        "audio/amr",
        "audio/amr-wb",
        "audio/flac",
        "audio/mp4",
        "audio/mpeg",
        "audio/ogg",
        "audio/opus",
        "audio/wav",
        "audio/wave",
        "audio/webm",
        "audio/x-aac",
        "audio/x-flac",
        "audio/x-m4a",
        "audio/x-mka",
        "audio/x-matroska",
        "audio/x-mpeg",
        "audio/x-wav",
        "application/ogg",
        "video/mp4",
        "video/webm",
        "video/3gpp",
    )

    fun isSupported(displayName: String?, mimeType: String?): Boolean {
        val extension = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        if (extension in blockedExtensions) return false
        if (extension in supportedExtensions) return true

        val normalizedMime = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        return normalizedMime in supportedMimeTypes
    }

    fun isSupportedMimeType(mimeType: String?): Boolean {
        val normalizedMime = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        return normalizedMime in supportedMimeTypes || normalizedMime == "audio/*"
    }
}
