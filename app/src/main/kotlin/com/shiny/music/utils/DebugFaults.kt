package com.shiny.music.utils

import com.shiny.music.BuildConfig

/**
 * Switches for measuring the fast-start paths on an emulator, set from a shell and read live:
 *
 * ```
 * adb shell setprop debug.shiny.slow VISIONOS:3000   # delay that client's first /player request (ms)
 * adb shell setprop debug.shiny.weaknet 1            # 1: treat the connection as weak; 0: never weak
 * adb shell setprop debug.shiny.hedge 0              # no hedged second request (the old behaviour)
 * adb shell setprop debug.shiny.cronet 0             # streams on OkHttp even when Cronet is ready
 * ```
 *
 * Debug builds only: in release every switch reads as unset.
 */
object DebugFaults {

    private val get by lazy {
        runCatching { Class.forName("android.os.SystemProperties").getMethod("get", String::class.java) }.getOrNull()
    }

    private fun prop(name: String): String? {
        if (!BuildConfig.DEBUG) return null
        return runCatching { get?.invoke(null, name) as? String }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Extra delay before [clientName]'s first stream request, from `debug.shiny.slow=CLIENT:ms`. */
    fun slowClientDelayMs(clientName: String): Long {
        val (client, ms) = prop("debug.shiny.slow")?.split(':')?.takeIf { it.size == 2 } ?: return 0
        return if (client == clientName) ms.toLongOrNull() ?: 0 else 0
    }

    /** true/false forces the weak-network decision; null leaves it to the measurement. */
    val weakNetworkOverride: Boolean? get() = when (prop("debug.shiny.weaknet")) { "1" -> true; "0" -> false; else -> null }
    val hedgeDisabled: Boolean get() = prop("debug.shiny.hedge") == "0"
    val cronetDisabled: Boolean get() = prop("debug.shiny.cronet") == "0"
}
