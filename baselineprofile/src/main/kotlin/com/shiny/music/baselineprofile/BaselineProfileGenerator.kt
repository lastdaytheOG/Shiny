package com.shiny.music.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline and startup profiles shipped in
 * `app/src/main/generated/baselineProfiles`.
 *
 * Run with `./gradlew :app:generateBaselineProfile` and an API 33+ device or emulator attached.
 * The journeys cover what a listener does first: open the app, look at Home, search, play.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /** Launch to Home only, so the startup profile (DEX layout) holds just the launch path. */
    @Test
    fun startup() = rule.collect(
        packageName = targetPackage,
        maxIterations = 6,
        stableIterations = 2,
        includeInStartupProfile = true,
    ) {
        device.shell("am force-stop $DEBUG_PACKAGE")
        device.grantNotificationPermission()
        pressHome()
        startActivityAndWait()
        device.dismissFirstRunScreens()
        device.wait(Until.hasObject(By.desc("Home")), 10_000)
    }

    /** Home scroll, then search and play: the resolver, player, Now Playing and list paths. */
    @Test
    fun listeningJourney() = rule.collect(
        packageName = targetPackage,
        maxIterations = 5,
        stableIterations = 2,
        includeInStartupProfile = false,
    ) {
        device.shell("am force-stop $DEBUG_PACKAGE")
        device.grantNotificationPermission()
        pressHome()
        startActivityAndWait()
        device.dismissFirstRunScreens()

        device.wait(Until.findObject(By.scrollable(true)), 10_000)?.let { feed ->
            feed.setGestureMargin(device.displayWidth / 5)
            feed.fling(Direction.DOWN)
            device.waitForIdle()
            feed.fling(Direction.UP)
        }

        device.openSearchResults("Radiohead")
        device.firstSongResult().click()
        check(waitForMusic(active = true, timeoutMs = 120_000)) { "The song never became audible" }
        // While it plays: Now Playing and each of its modes, so the living artwork, the lyrics
        // ticker and the queue are compiled ahead of time rather than interpreted on first open.
        device.visitNowPlaying()
        device.pausePlayback()

        // The other two tabs, which the journey above never reaches.
        listOf("New", "Library").forEach { tab ->
            device.findObject(By.text(tab))?.click() ?: return@forEach
            device.waitForIdle()
            // Looked up again for each fling: these pages re-render as their content arrives,
            // which leaves a list found before the first fling stale by the second.
            listOf(Direction.DOWN, Direction.UP).forEach { direction ->
                runCatching {
                    device.wait(Until.findObject(By.scrollable(true)), 10_000)?.let { list ->
                        list.setGestureMargin(device.displayWidth / 5)
                        list.fling(direction)
                    }
                }
                device.waitForIdle()
            }
        }
    }
}
