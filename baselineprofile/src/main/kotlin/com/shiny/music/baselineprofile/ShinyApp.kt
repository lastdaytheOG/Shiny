package com.shiny.music.baselineprofile

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/*
 * How the benchmarks drive Shiny. Everything here goes through the UI, intents the app already
 * handles, or system services — none of it needs hooks in the app beyond PlaybackTrace's sections.
 */

/** Id of the app APK under test, supplied by the build (see build.gradle.kts). */
val targetPackage: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId")
        ?.takeIf { it.isNotEmpty() }
        ?: error("targetAppId instrumentation argument is missing")

const val MAIN_ACTIVITY = "com.shiny.music.MainActivity"

/** Trace section names; must match `com.shiny.music.utils.PlaybackTrace`. */
const val TAP_TO_AUDIBLE_SECTION = "ShinyTapToAudible"
const val RESOLVE_SECTION = "ShinyResolve"

/** A song with a stable id, played once so the app has a queue to restore. */
const val SEED_VIDEO_ID = "Rr1Cdli5nE8"

/** The debug build shares the device during local runs; it must not hold audio focus or a session. */
const val DEBUG_PACKAGE = "com.shiny.music.debug"

val device: UiDevice
    get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

private val audioManager: AudioManager
    get() = InstrumentationRegistry.getInstrumentation().context.getSystemService(AudioManager::class.java)

fun UiDevice.shell(command: String): String = executeShellCommand(command)

/**
 * Compilation modes to run, from the `shiny.compilation` instrumentation argument (comma-separated
 * `None`, `BaselineProfile`). `BaselineProfile` requires a generated profile in the APK: Macrobenchmark
 * fails the run rather than silently measuring without it.
 */
fun compilationModesUnderTest(): List<String> =
    InstrumentationRegistry.getArguments().getString("shiny.compilation")
        ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: listOf("None", "BaselineProfile")

fun compilationModeFor(name: String): CompilationMode = when (name) {
    "None" -> CompilationMode.None()
    "BaselineProfile" -> CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)
    else -> error("Unknown compilation mode $name")
}

fun UiDevice.grantNotificationPermission() {
    // Otherwise the permission prompt the app raises on first start covers the UI under test.
    shell("pm grant $targetPackage android.permission.POST_NOTIFICATIONS")
}

fun UiDevice.isAppRunning(): Boolean = shell("pidof $targetPackage").isNotBlank()

fun UiDevice.launchApp() {
    shell("am start -W -n $targetPackage/$MAIN_ACTIVITY")
}

/**
 * Walks past the first-run screens — onboarding, the appearance welcome, What's New — until the
 * tab bar is showing with nothing on top of it.
 */
fun UiDevice.dismissFirstRunScreens(timeoutMs: Long = 60_000) {
    val labels = listOf("Skip", "Get Started", "Start listening", "Continue", "Next")
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < deadline) {
        val button = labels.firstNotNullOfOrNull { findObject(By.text(it)) }
        if (button != null) {
            runCatching { button.click() }
        } else if (hasObject(By.desc("Home"))) {
            return
        }
        SystemClock.sleep(500)
    }
    error("The Home tab never appeared")
}

/** Polls the audio mixer until music output is (or is no longer) active. */
fun waitForMusic(active: Boolean, timeoutMs: Long): Boolean {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < deadline) {
        if (audioManager.isMusicActive == active) return true
        SystemClock.sleep(50)
    }
    return false
}

fun UiDevice.pausePlayback() {
    pressKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE)
    check(waitForMusic(active = false, timeoutMs = 15_000)) { "Playback did not stop" }
    // The service writes the queue a short debounce after the state change; leave it time to land.
    SystemClock.sleep(1_500)
}

/** Plays [videoId] through a deep link, then pauses once it is audible, leaving it as the queue. */
fun UiDevice.seedQueue(videoId: String = SEED_VIDEO_ID) {
    shell(
        "am start -a android.intent.action.VIEW " +
            "-d https://music.youtube.com/watch?v=$videoId -n $targetPackage/$MAIN_ACTIVITY"
    )
    check(waitForMusic(active = true, timeoutMs = 120_000)) { "The seed song never became audible" }
    pausePlayback()
}

/**
 * The mini player's play/pause button. It carries no label, so it is found by shape: the lowest
 * wide clickable bar above the tab bar that holds exactly two buttons (play/pause, then next).
 */
fun UiDevice.miniPlayerPlayPause(): UiObject2? {
    val tabTop = findObject(By.desc("Home"))?.visibleBounds?.top ?: return null
    return findObjects(By.clickable(true))
        .asSequence()
        .filter { it.visibleBounds.bottom <= tabTop && it.visibleBounds.width() > displayWidth / 2 }
        .mapNotNull { bar ->
            val buttons = bar.findObjects(By.clickable(true)).filter { it.visibleBounds != bar.visibleBounds }
            if (buttons.size == 2) bar to buttons else null
        }
        .maxByOrNull { (bar, _) -> bar.visibleBounds.bottom }
        ?.second
        ?.minByOrNull { it.visibleBounds.left }
}

fun UiDevice.waitForMiniPlayerPlayPause(timeoutMs: Long = 30_000): UiObject2 {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < deadline) {
        miniPlayerPlayPause()?.let { return it }
        SystemClock.sleep(100)
    }
    error("The mini player never appeared")
}

/**
 * Opens Now Playing from the mini player and steps through Lyrics and Queue, then closes it.
 * The Liquid player sheet is not in the accessibility tree UiAutomator reads, so it is driven by
 * position: the mini player sits just above the tab bar, and the Lyrics and Queue buttons are the
 * outer ends of the utility row at the foot of the player.
 */
fun UiDevice.visitNowPlaying() {
    val w = displayWidth
    val h = displayHeight
    click((w * 0.44f).toInt(), (h * 0.887f).toInt())
    SystemClock.sleep(3_000)
    click((w * 0.126f).toInt(), (h * 0.957f).toInt()) // Lyrics
    SystemClock.sleep(3_000)
    click((w * 0.873f).toInt(), (h * 0.957f).toInt()) // Queue
    SystemClock.sleep(2_000)
    pressBack() // Queue -> Stage
    SystemClock.sleep(1_000)
    pressBack() // Stage -> collapsed
    waitForIdle()
}

/** Opens search results for a one-word [query] through the app's play-from-search intent. */
fun UiDevice.openSearchResults(query: String) {
    shell(
        "am start -a android.media.action.MEDIA_PLAY_FROM_SEARCH " +
            "--es query $query -n $targetPackage/$MAIN_ACTIVITY"
    )
    checkNotNull(wait(Until.findObject(By.text(query)), 20_000)) { "Search results for $query never opened" }
}

/** The first song row in the visible search results (song rows have a "Song · …" subtitle). */
fun UiDevice.firstSongResult(timeoutMs: Long = 30_000): UiObject2 {
    // The results can re-render between finding the subtitle and walking up to its row, which threw
    // StaleObjectException mid-run (warmProcessTapToAudible, 14 September); look the row up again.
    repeat(3) {
        val subtitle = checkNotNull(wait(Until.findObject(By.textStartsWith("Song ")), timeoutMs)) {
            "No song rows in the search results"
        }
        try {
            return checkNotNull(generateSequence(subtitle) { it.parent }.firstOrNull { it.isClickable }) {
                "The song row is not clickable"
            }
        } catch (e: androidx.test.uiautomator.StaleObjectException) {
            SystemClock.sleep(200)
        }
    }
    error("The search results kept re-rendering")
}
