package com.shiny.music.discord

import timber.log.Timber

/**
 * Discord's connection lifecycle under one logcat tag: `adb logcat -s ShinyDiscord`. Timber only
 * has a tree in debug builds, so release builds log nothing.
 *
 * Never pass tokens, authorization codes, OAuth state or PKCE values.
 */
internal object DiscordLog {
    const val TAG = "ShinyDiscord"

    fun i(message: String) {
        Timber.tag(TAG).i("[Discord] %s", message)
    }

    fun w(message: String, error: Throwable? = null) {
        Timber.tag(TAG).w(error, "[Discord] %s", message)
    }
}
