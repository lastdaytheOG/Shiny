package com.shiny.music.utils

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Looper
import com.shiny.music.BuildConfig
import com.shiny.music.ui.screens.CrashActivity
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * Shows [CrashActivity] (in its own `:crash` process) when the app crashes, then ends the
 * process.
 *
 * Installed from `App.attachBaseContext`, before any content provider runs, so Firebase
 * Crashlytics — which starts from its provider — wraps it: a crash is recorded by Crashlytics
 * first and then arrives here. It used to be installed in `App.onCreate`, after Crashlytics,
 * where it replaced Crashlytics' handler and never passed a crash on, so not one real crash
 * ever reached Crashlytics; only handled exceptions did.
 */
class CrashHandler private constructor(
    private val application: Application,
    /** The system's handler, for when showing the crash screen itself fails. */
    private val next: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashLog = buildCrashLog(throwable)
            Timber.e(throwable, "App crashed")

            val intent = Intent(application, CrashActivity::class.java).apply {
                putExtra(EXTRA_CRASH_LOG, crashLog)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            application.startActivity(intent)

            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        } catch (e: Exception) {
            Timber.e(e, "Error handling crash")
            next?.uncaughtException(thread, throwable)
        }
    }

    private fun buildCrashLog(throwable: Throwable): String {
        val stackTrace = StringWriter().apply {
            throwable.printStackTrace(PrintWriter(this))
        }.toString()

        return buildString {
            appendLine("Shiny Crash Report")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Device: ${Build.MODEL}")
            appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            appendLine("=".repeat(50))
            appendLine("Stacktrace:")
            appendLine("=".repeat(50))
            appendLine()
            append(stackTrace)
        }
    }

    companion object {
        const val EXTRA_CRASH_LOG = "crash_log"

        /** From `App.attachBaseContext`, in every process: see the class comment for why then. */
        fun install(application: Application) {
            val handler = CrashHandler(application, Thread.getDefaultUncaughtExceptionHandler())
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }

        /**
         * From `App.onCreate` in the main process, so it sits outside Crashlytics: an exception
         * the app survives must not be recorded as a crash. See [ForegroundServiceGuard].
         */
        fun installForegroundServiceGuard() {
            val next = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(ForegroundServiceGuard(next))
        }
    }
}

/**
 * Keeps the app running through a [android.app.ForegroundServiceStartNotAllowedException]
 * (Android 12+ refuses a foreground service started from the background, and a media session
 * can hit that on its own): on the main thread the looper is restarted, on another thread that
 * thread just ends. Every other exception goes on to [next] — Crashlytics, then [CrashHandler].
 */
private class ForegroundServiceGuard(
    private val next: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (!throwable.isForegroundServiceStartNotAllowed()) {
            next?.uncaughtException(thread, throwable)
            return
        }
        Timber.e(throwable, "Suppressed ForegroundServiceStartNotAllowedException in CrashHandler")
        if (thread != Looper.getMainLooper().thread) return
        while (true) {
            try {
                Looper.loop()
            } catch (e: Throwable) {
                if (e.isForegroundServiceStartNotAllowed()) {
                    Timber.e(e, "Suppressed another ForegroundServiceStartNotAllowedException in CrashHandler")
                } else {
                    next?.uncaughtException(thread, e)
                    return
                }
            }
        }
    }

    private fun Throwable.isForegroundServiceStartNotAllowed(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            this is android.app.ForegroundServiceStartNotAllowedException
}
