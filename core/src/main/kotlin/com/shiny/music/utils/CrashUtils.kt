package com.shiny.music.utils

import timber.log.Timber

// Set from a background thread at startup and read from any thread.
@Volatile
var exceptionReporter: ((Throwable) -> Unit)? = null

fun reportException(throwable: Throwable) {
    Timber.e(throwable)
    exceptionReporter?.invoke(throwable)
}
