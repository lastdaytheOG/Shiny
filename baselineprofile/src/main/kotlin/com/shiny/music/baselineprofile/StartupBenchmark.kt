package com.shiny.music.baselineprofile

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Cold start to the first rendered frame (`timeToInitialDisplayMs`), plus frame timing until the
 * Home tab bar is on screen.
 *
 * The app is in its everyday state: first-run screens done and a restored queue in the mini
 * player, since restoring that queue is part of what a real launch does.
 */
@RunWith(Parameterized::class)
class StartupBenchmark(private val compilation: String) {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun everydayState() {
        device.shell("am force-stop $DEBUG_PACKAGE")
        device.grantNotificationPermission()
        device.launchApp()
        device.dismissFirstRunScreens()
        if (runCatching { device.waitForMiniPlayerPlayPause(timeoutMs = 8_000) }.isFailure) {
            device.seedQueue()
        }
        device.shell("am force-stop $targetPackage")
    }

    @Test
    fun coldStart() = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = compilationModeFor(compilation),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.desc("Home")), 10_000)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = compilationModesUnderTest()
    }
}
