package com.shiny.music.social

import android.app.Activity
import android.content.Context

/** The FOSS build has no Google Play services, so it can't sign in with Google. */
object GoogleSignIn {
    class Cancelled : Exception("Sign-in was cancelled")

    fun isConfigured(context: Context): Boolean = false

    suspend fun requestIdToken(activity: Activity): Result<String> =
        Result.failure(UnsupportedOperationException("Sign in with Google needs the Play Store version of Shiny."))
}
