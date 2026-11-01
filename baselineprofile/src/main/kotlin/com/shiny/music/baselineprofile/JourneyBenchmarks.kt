package com.shiny.music.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMetricApi::class, ExperimentalMacrobenchmarkApi::class)
@RunWith(AndroidJUnit4::class)
class JourneyBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private val keepCompiled = CompilationMode.Ignore()
    private val iterations: Int
        get() = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("shiny.iterations")?.toInt() ?: 10

    private fun homeSections() = listOf(
        TraceSectionMetric("ShinyHomeReady", TraceSectionMetric.Mode.First),
        TraceSectionMetric("ShinyHomeLoad", TraceSectionMetric.Mode.First),
        TraceSectionMetric("ShinySnapshotBuild", TraceSectionMetric.Mode.First),
        TraceSectionMetric("ShinyFeedBuild", TraceSectionMetric.Mode.Sum),
        TraceSectionMetric("ShinyFeedBuild", TraceSectionMetric.Mode.Count),
    )

    private fun MacrobenchmarkScope.home() {
        killProcess()
        startActivityAndWait()
        device.dismissFirstRunScreens()
        device.waitForIdle()
        SystemClock.sleep(1_000)
    }

    private fun scrollList(direction: Direction) {
        device.wait(Until.findObject(By.scrollable(true)), 10_000)?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(direction)
        }
        device.waitForIdle()
    }

    @Test
    fun coldStart() = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric()) + homeSections(),
        compilationMode = keepCompiled,
        startupMode = StartupMode.COLD,
        iterations = iterations,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.desc("Home")), 10_000)
        SystemClock.sleep(4_000)
    }

    @Test
    fun warmStart() = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = keepCompiled,
        startupMode = StartupMode.WARM,
        iterations = iterations,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    @Test
    fun hotStart() = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = keepCompiled,
        startupMode = StartupMode.HOT,
        iterations = iterations,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    @Test
    fun homeScroll() = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = keepCompiled,
        startupMode = null,
        iterations = iterations,
        setupBlock = { home() },
    ) {
        scrollList(Direction.DOWN)
        scrollList(Direction.DOWN)
        scrollList(Direction.UP)
        scrollList(Direction.UP)
    }

    @Test
    fun libraryScroll() = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = keepCompiled,
        startupMode = null,
        iterations = iterations,
        setupBlock = {
            home()
            device.findObject(By.desc("Library"))?.click() ?: device.findObject(By.text("Library"))?.click()
            SystemClock.sleep(1_000)
        },
    ) {
        scrollList(Direction.DOWN)
        scrollList(Direction.UP)
    }

    @Test
    fun tabSwitching() = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = keepCompiled,
        startupMode = null,
        iterations = iterations,
        setupBlock = { home() },
    ) {
        listOf("Discover", "Library", "Home", "Library", "Discover", "Home").forEach { tab ->
            (device.findObject(By.desc(tab)) ?: device.findObject(By.text(tab)))?.click()
            SystemClock.sleep(700)
        }
    }

    @Test
    fun searchResults() {
        var iteration = 0
        rule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = keepCompiled,
            startupMode = StartupMode.WARM,
            iterations = iterations,
            setupBlock = { pressHome() },
        ) {
            device.openSearchResults(PlaybackBenchmark.WARM_QUERIES[iteration++ % PlaybackBenchmark.WARM_QUERIES.size])
            device.firstSongResult()
        }
    }

    @Test
    fun playerControls() = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(
            TraceSectionMetric("ShinyControlPause", TraceSectionMetric.Mode.First),
            TraceSectionMetric("ShinyControlResume", TraceSectionMetric.Mode.First),
            FrameTimingMetric(),
        ),
        compilationMode = keepCompiled,
        startupMode = StartupMode.WARM,
        iterations = iterations,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.waitForMiniPlayerPlayPause()
        },
    ) {
        device.waitForMiniPlayerPlayPause().click()
        SystemClock.sleep(1_200)
        device.waitForMiniPlayerPlayPause().click()
        SystemClock.sleep(1_200)
    }
}
