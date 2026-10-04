package com.shiny.music.localmedia

import java.util.Locale

/**
 * What counts as music on this device.
 *
 * Android's audio index holds every sound file on shared storage: songs, but also voice
 * notes, call recordings and notification sounds. Shiny keeps its library to music by
 * skipping a few kinds of *source* that are known not to be music — identified by the
 * flags Android sets on each file and by the folders the apps concerned are documented to
 * write to — rather than by looking for words in file names, and never by the absence of
 * tags (plenty of real songs have no artist or album).
 *
 * Anything not recognised as one of these sources is music. When in doubt, a file stays.
 */
enum class SkippedSource(val key: String) {
    /** Voice notes and audio from messaging apps (WhatsApp, Telegram). */
    Messaging("messaging"),

    /** Voice memos and call recordings, including files in a speech-only codec (AMR). */
    Recordings("recordings"),

    /** Ringtones, notification and alarm sounds, and apps' private files. */
    SystemSounds("system_sounds"),
    ;

    companion object {
        /** Every source is skipped unless the user opts it back in. */
        val Default: Set<SkippedSource> = entries.toSet()

        fun fromKeys(keys: Set<String>): Set<SkippedSource> =
            entries.filter { it.key in keys }.toSet()
    }
}

/** The flags Android's media scanner records for an audio file. */
data class MediaStoreAudioFlags(
    val isRingtone: Boolean = false,
    val isNotification: Boolean = false,
    val isAlarm: Boolean = false,
    /** API 31+; always false below that. */
    val isRecording: Boolean = false,
)

/** Why a file was left out of the library, or null if it is music. */
sealed interface LocalMusicDecision {
    data object Music : LocalMusicDecision
    data class Skipped(val source: SkippedSource) : LocalMusicDecision
    data object ExcludedFolder : LocalMusicDecision
}

object LocalMusicFilter {

    /**
     * Folders the messaging apps write received audio and voice notes to. WhatsApp
     * (`…/WhatsApp/Media/WhatsApp Audio`, `…/WhatsApp Voice Notes`, and the Business app's
     * equivalents — both under `Android/media/<package>/` since Android 11 and at the top
     * of storage before it) and Telegram (`…/Telegram/Telegram Audio`, `Telegram Voice`).
     * Matched as whole folder names, so a user's own "WhatsApp songs" folder is untouched.
     */
    private val MessagingFolders = setOf(
        "whatsapp audio",
        "whatsapp voice notes",
        "whatsapp business audio",
        "whatsapp business voice notes",
        "telegram audio",
        "telegram voice",
    )

    /**
     * Recorder output. `Recordings/` is Android's standard directory for it (API 31,
     * `Environment.DIRECTORY_RECORDINGS`, which also sets IS_RECORDING); `MIUI/sound_recorder`
     * is Xiaomi's recorder, which predates it; `Call recordings` is where dialer apps put calls.
     */
    private val RecordingTopFolders = setOf("recordings", "call recordings", "callrecordings")
    private const val MiuiRecorder = "miui/sound_recorder"

    /** Android's standard directories for system sounds (`Environment.DIRECTORY_*`). */
    private val SystemSoundTopFolders = setOf("ringtones", "notifications", "alarms")

    /** App-private storage: never a user's music, and scanned only on old Android versions. */
    private val AppPrivatePrefixes = listOf("android/data/", "android/obb/")

    /** Speech codecs. No music is distributed in them; recorders and older phones use them. */
    private val SpeechMimeTypes = setOf("audio/amr", "audio/amr-wb", "audio/3gpp-amr")
    private val SpeechExtensions = setOf("amr", "awb")

    /**
     * Decides one file.
     *
     * Order matters: the user's own excluded folders always win; then their "always include"
     * folders, which override the default skips; then the skips themselves.
     *
     * [folder] is the file's folder relative to its storage volume, e.g. `Music/Albums`.
     */
    fun decide(
        folder: String?,
        displayName: String?,
        mimeType: String?,
        flags: MediaStoreAudioFlags,
        skipped: Set<SkippedSource>,
        includedFolders: Set<String>,
        excludedFolders: Set<String>,
    ): LocalMusicDecision {
        val path = normalize(folder)
        if (path != null && excludedFolders.any { matchesExcluded(path, normalize(it)) }) {
            return LocalMusicDecision.ExcludedFolder
        }
        if (path != null && includedFolders.any { isWithin(path, normalize(it)) }) {
            return LocalMusicDecision.Music
        }
        val source = sourceOf(path, displayName, mimeType, flags) ?: return LocalMusicDecision.Music
        return if (source in skipped) LocalMusicDecision.Skipped(source) else LocalMusicDecision.Music
    }

    /** Which non-music source a file belongs to, if any. */
    fun sourceOf(
        folder: String?,
        displayName: String?,
        mimeType: String?,
        flags: MediaStoreAudioFlags,
    ): SkippedSource? {
        val path = normalize(folder).orEmpty()
        val segments = if (path.isEmpty()) emptyList() else path.split('/')

        if (flags.isRingtone || flags.isNotification || flags.isAlarm) return SkippedSource.SystemSounds
        if (AppPrivatePrefixes.any { "$path/".startsWith(it) }) return SkippedSource.SystemSounds
        if (segments.firstOrNull() in SystemSoundTopFolders) return SkippedSource.SystemSounds

        if (segments.any { it in MessagingFolders }) return SkippedSource.Messaging

        if (flags.isRecording) return SkippedSource.Recordings
        if (segments.firstOrNull() in RecordingTopFolders) return SkippedSource.Recordings
        if (path == MiuiRecorder || path.startsWith("$MiuiRecorder/")) return SkippedSource.Recordings
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        val extension = displayName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
        if (mime in SpeechMimeTypes || extension in SpeechExtensions) return SkippedSource.Recordings

        return null
    }

    /** `Music//Albums/` → `music/albums`; blank → null. */
    fun normalize(folder: String?): String? =
        folder
            ?.replace('\\', '/')
            ?.split('/')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString("/")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }

    /**
     * The excluded-folder match Shiny has always used, kept as it was so existing exclusions
     * behave the same: the folder itself, anything under it, or a folder of that name
     * anywhere in the path.
     */
    private fun matchesExcluded(path: String, excluded: String?): Boolean =
        excluded != null && (
            path == excluded ||
                path.startsWith("$excluded/") ||
                path.endsWith("/$excluded") ||
                path.contains("/$excluded/")
            )

    /** Whether [path] is [root] or somewhere inside it. Both already normalised. */
    private fun isWithin(path: String, root: String?): Boolean =
        root != null && (path == root || path.startsWith("$root/"))
}
