package com.shiny.music.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tap to audible playback, read from the app's `ShinyTapToAudible` trace section: it opens when
 * the tap reaches the player and closes when ExoPlayer first reports the audio position advancing.
 * `ShinyResolve` (stream lookup on the loader thread) is reported alongside it.
 *
 * Every run starts from cleared app data, so caches, the stored signature timestamp and the
 * audio cache are the same for every compilation mode. Setup then plays one seed song, which pays
 * the once-per-install timestamp extraction outside the measurements.
 *
 * Network time dominates these numbers, so compare medians across runs made close together.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(Parameterized::class)
class PlaybackBenchmark(private val compilation: String) {

    @get:Rule
    val rule = MacrobenchmarkRule()

    private val metrics = listOf(
        TraceSectionMetric(TAP_TO_AUDIBLE_SECTION, TraceSectionMetric.Mode.First),
        TraceSectionMetric(RESOLVE_SECTION, TraceSectionMetric.Mode.Sum),
    )

    @Before
    fun clearedInstall() {
        device.shell("am force-stop $DEBUG_PACKAGE")
        device.shell("pm clear $targetPackage")
        device.grantNotificationPermission()
        device.launchApp()
        device.dismissFirstRunScreens()
        device.seedQueue()
        device.shell("am force-stop $targetPackage")
    }

    /**
     * Open the app and press play on the restored song straight away: a new process, the launch
     * still settling, nothing resolved yet. The song's audio is in the player cache from earlier
     * iterations, as it is for a listener resuming yesterday's queue.
     */
    @Test
    fun freshProcessTapToAudible() = rule.measureRepeated(
        packageName = targetPackage,
        metrics = metrics,
        compilationMode = compilationModeFor(compilation),
        iterations = ITERATIONS,
        setupBlock = {
            killProcess()
            startActivityAndWait()
            device.waitForMiniPlayerPlayPause()
        },
    ) {
        device.waitForMiniPlayerPlayPause().click()
        awaitAudibleThenPause()
    }

    /**
     * With the app already running and warmed by an earlier play, tap a song it has never
     * resolved: a different search's top song each iteration, so nothing is cached or preloaded.
     */
    @Test
    fun warmProcessTapToAudible() {
        var iteration = 0
        rule.measureRepeated(
            packageName = targetPackage,
            metrics = metrics,
            compilationMode = compilationModeFor(compilation),
            iterations = ITERATIONS,
            setupBlock = {
                if (!device.isAppRunning()) {
                    startActivityAndWait()
                    device.dismissFirstRunScreens()
                    // Warm-up play: resolver, connections and player code all exercised once.
                    device.waitForMiniPlayerPlayPause().click()
                    check(waitForMusic(active = true, timeoutMs = 120_000)) { "Warm-up play never became audible" }
                    device.pausePlayback()
                }
                device.openSearchResults(WARM_QUERIES[iteration++ % WARM_QUERIES.size])
                device.firstSongResult()
                // Let the results settle so the tap lands on the row that is actually shown.
                SystemClock.sleep(1_000)
            },
        ) {
            device.firstSongResult().click()
            awaitAudibleThenPause()
        }
    }

    private fun awaitAudibleThenPause() {
        check(waitForMusic(active = true, timeoutMs = 120_000)) { "Playback never became audible" }
        // The trace section closes on ExoPlayer's first audio-position callback, which lands just
        // after the mixer reports activity; keep the capture open until it has.
        SystemClock.sleep(1_000)
        device.pausePlayback()
    }

    companion object {
        const val ITERATIONS = 8

        /** One-word queries (the intent is sent unquoted) whose top songs are all different. */
        val WARM_QUERIES = listOf(
            "Radiohead", "Adele", "Coldplay", "Metallica", "Eminem", "Shakira", "ABBA", "Beyonce",
            "Queen", "Muse",
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = compilationModesUnderTest()
    }
}
