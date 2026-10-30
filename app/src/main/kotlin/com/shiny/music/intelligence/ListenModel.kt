package com.shiny.music.intelligence

enum class EndReason { Finished, SkippedNext, SkippedBack, Replaced, Repeated, Stopped, Failed }

enum class ListenKind {
    Accidental,
    Interrupted,
    Skipped,
    Partial,
    Meaningful,
    Completed,
    ;

    val positive: Boolean get() = this == Meaningful || this == Completed
}

enum class Selection { Manual, QueueAdvance, Autoplay }

object ListenClassifier {
    const val NEVER_HEARD_MS = 1_000L
    const val ACCIDENTAL_MS = 5_000L
    const val SKIP_BURST_MS = 8_000L
    const val SKIP_SHARE = 0.30
    const val SKIP_CEILING_MS = 60_000L
    const val MEANINGFUL_SHARE = 0.50
    const val MEANINGFUL_MS = 120_000L
    const val COMPLETED_SHARE = 0.90

    fun completion(listenedMs: Long, durationMs: Long): Double =
        if (durationMs <= 0L) 0.0 else (listenedMs.toDouble() / durationMs).coerceIn(0.0, 1.0)

    fun classify(
        listenedMs: Long,
        durationMs: Long,
        reason: EndReason,
        selection: Selection,
        inSkipBurst: Boolean = false,
    ): ListenKind {
        val share = completion(listenedMs, durationMs)
        val userLeft = reason == EndReason.SkippedNext || reason == EndReason.SkippedBack
        return when {
            listenedMs < NEVER_HEARD_MS -> ListenKind.Interrupted
            reason == EndReason.Finished && (share >= MEANINGFUL_SHARE || (durationMs <= 0L && listenedMs >= ACCIDENTAL_MS)) -> ListenKind.Completed
            reason == EndReason.Repeated && share >= MEANINGFUL_SHARE -> ListenKind.Completed
            share >= COMPLETED_SHARE -> ListenKind.Completed
            share >= MEANINGFUL_SHARE || listenedMs >= MEANINGFUL_MS -> ListenKind.Meaningful
            reason == EndReason.Failed -> ListenKind.Interrupted
            reason == EndReason.Replaced || reason == EndReason.Stopped -> {
                if (listenedMs >= SKIP_CEILING_MS) ListenKind.Partial else ListenKind.Interrupted
            }
            !userLeft -> if (listenedMs >= ACCIDENTAL_MS) ListenKind.Partial else ListenKind.Interrupted
            reason == EndReason.SkippedBack && listenedMs < ACCIDENTAL_MS -> ListenKind.Accidental
            listenedMs < ACCIDENTAL_MS && (selection == Selection.Manual || inSkipBurst) -> ListenKind.Accidental
            share < SKIP_SHARE && listenedMs < SKIP_CEILING_MS -> ListenKind.Skipped
            else -> ListenKind.Partial
        }
    }
}
