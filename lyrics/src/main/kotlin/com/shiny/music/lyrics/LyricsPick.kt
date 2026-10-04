package com.shiny.music.lyrics

import com.shiny.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.shiny.music.playback.LyricsWithProvider

/**
 * Which of the providers' answers becomes the song's lyrics. The providers all run at once;
 * this is told about each answer as it arrives and says when the choice is final.
 *
 * **Where the timings come from matters more than who answers first.** Some providers
 * serve lyrics timed by the streaming services themselves — Apple Music's TTML behind
 * YouLyPlus, Paxsenix, BetterLyrics and Unison, which agreed with each other to within
 * 10 ms on every song compared. The others serve files people time by hand. Measured on
 * 25 popular Hindi songs as YouTube Music lists them (September 2026), the hand-timed
 * files were consistently *late* against the studio timings: KuGou by 100–360 ms on every
 * song that could be compared, LrcLib by up to half a second on most and by 1.0–1.6 s on
 * others ("Tum Hi Ho", "O Maahi"). Hand timing lags because a person taps when they hear
 * a line start. Taking whichever synced file arrived first let LrcLib or KuGou win the
 * race whenever the studio sources were a moment slower, and the lyrics trailed the voice.
 *
 * So a synced answer from a studio-timed provider wins at once. A hand-timed one is held
 * until every studio-timed provider has answered without one, or until [CommunityGraceMs]
 * has passed since the fetch began — lyrics are fetched when the song starts, well before
 * anyone opens them, so the wait costs nothing visible.
 *
 * A "synced" file must also have real timings. Some providers send plain lyrics dressed as
 * LRC with every line at 00:00; shown as synced, the page would sit on the last line for
 * the whole song. Such a file is used as plain text, with the timestamps taken off.
 */
class LyricsPick(
    /** Every provider asked, in the user's preference order. */
    private val order: List<String>,
    /** The recording's length, for [fitsRecording]. */
    private val durationSeconds: Int,
) {
    private val waitingForStudio = order.filterTo(HashSet(), ::isStudioTimed)
    private val community = mutableListOf<LyricsWithProvider>()
    private val unsynced = mutableListOf<LyricsWithProvider>()
    private var illFitting: LyricsWithProvider? = null
    private var answered = 0

    /** A hand-timed synced result is waiting for the studio-timed providers. */
    val holding: Boolean get() = community.isNotEmpty()

    /**
     * [provider] answered with [lyrics] (null or blank: nothing). Returns the choice once it
     * is final, else null.
     */
    fun answer(provider: String, lyrics: String?): LyricsWithProvider? {
        answered++
        waitingForStudio -= provider
        val text = lyrics?.takeIf { it.isNotBlank() && it != LYRICS_NOT_FOUND }
        if (text != null) {
            val result = LyricsWithProvider(text, provider)
            when {
                !looksSynced(text) -> unsynced += result
                !hasRealTimings(text) -> unsynced += LyricsWithProvider(withoutTimestamps(text), provider)
                // A synced file whose timings belong to another recording. Kept as a last
                // resort — wrong timings still beat no lyrics — but never a winner.
                !fitsRecording(text, durationSeconds) -> if (illFitting == null) illFitting = result
                isStudioTimed(provider) -> return result
                else -> community += result
            }
        }
        if (community.isNotEmpty() && waitingForStudio.isEmpty()) return firstByOrder(community)
        if (answered >= order.size) return fallback()
        return null
    }

    /** The grace period is over: a hand-timed result will do. */
    fun timeUp(): LyricsWithProvider? = firstByOrder(community)

    /** What to use when no provider had a synced file that fits. */
    fun fallback(): LyricsWithProvider =
        firstByOrder(community)
            ?: illFitting
            // Synced-but-ill-fitting still beats a plain text dump, which has no timings.
            ?: firstByOrder(unsynced)
            ?: LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")

    private fun firstByOrder(results: List<LyricsWithProvider>): LyricsWithProvider? =
        results.minByOrNull { order.indexOf(it.providerName).let { i -> if (i < 0) Int.MAX_VALUE else i } }

    companion object {
        /**
         * How long the studio-timed providers get before a hand-timed file is accepted.
         * Measured from the start of the fetch: they answered in 0.1–1.2 s on the test
         * songs, and a server that has not answered by now is usually timing out.
         */
        const val CommunityGraceMs = 3_000L

        /**
         * Providers whose synced lyrics are timed by the streaming services themselves
         * (see the class notes). Provider names as [LyricsProvider.name] gives them.
         */
        private val StudioTimed = setOf("YouLyPlus", "Paxsenix", "BetterLyrics", "Unison")

        fun isStudioTimed(provider: String): Boolean = provider in StudioTimed

        fun looksSynced(lyrics: String): Boolean = lyrics.trimStart().startsWith("[")

        /**
         * Whether the timestamps say anything: at least two distinct times, and no single
         * time shared by most of the lines. One stray header at 00:00 is normal (KuGou puts
         * the title there); a whole song at 00:00 is plain lyrics in disguise.
         */
        fun hasRealTimings(lyrics: String): Boolean {
            val times = runCatching { LyricsUtils.parseLyrics(lyrics).map { it.time } }.getOrNull()
                ?: return false
            if (times.isEmpty()) return false
            if (times.size == 1) return times[0] > 0
            val commonest = times.groupingBy { it }.eachCount().maxOf { it.value }
            return commonest * 2 <= times.size
        }

        /**
         * Whether a synced file's own timings are consistent with the recording being
         * played. The test is only the file's last timestamp against the track's length,
         * both facts already held. A file whose last line is sung after the song has ended
         * is for a longer recording — a spoken intro, an extended edit. The allowance is
         * one-sided on purpose: ending early is normal (outros, instrumental closes) and
         * is not evidence of anything. Nothing here shifts a timestamp.
         */
        fun fitsRecording(lyrics: String, durationSeconds: Int): Boolean {
            // No trustworthy duration for the track: nothing to check against, so accept.
            if (durationSeconds <= 0) return true
            val lastTimestamp = runCatching {
                LyricsUtils.parseLyrics(lyrics).maxOfOrNull { it.time }
            }.getOrNull() ?: return true
            return lastTimestamp <= durationSeconds * 1000L + OverrunAllowanceMs
        }

        /** The words of an LRC file, one line each, for showing it as plain lyrics. */
        fun withoutTimestamps(lyrics: String): String =
            runCatching { LyricsUtils.parseLyrics(lyrics).joinToString("\n") { it.text } }
                .getOrNull()?.takeIf { it.isNotBlank() }
                ?: lyrics.lines().joinToString("\n") { it.replace(Regex("""^(\[[^\]]*\])+"""), "").replace(Regex("<[^>]*>"), "") }

        /**
         * How far past the end of the track a lyrics file's last line may sit before the
         * file is taken to describe a different recording. Slack on a comparison, not a
         * timing correction: a trailing line timed to a fade-out, a rounded stream length,
         * a few seconds of silence trimmed from one upload and not another.
         */
        private const val OverrunAllowanceMs = 20_000L
    }
}
