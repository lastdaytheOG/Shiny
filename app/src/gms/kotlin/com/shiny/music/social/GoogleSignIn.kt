package com.shiny.music.social

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Sign in with Google through Credential Manager. The ID token is exchanged for a Shiny session by the server. */
object GoogleSignIn {
    class Cancelled : Exception("Sign-in was cancelled")

    fun isConfigured(context: Context): Boolean = webClientId(context) != null

    suspend fun requestIdToken(activity: Activity): Result<String> =
        runCatching {
            // Missing only when the build had no google-services.json (or shrank the ID away,
            // see res/raw/keep_google_sign_in.xml). Say so plainly; the file name means nothing to listeners.
            val clientId =
                webClientId(activity)
                    ?: error("Google sign-in isn't available in this version of Shiny. Update Shiny and try again.")
            val request =
                GetCredentialRequest.Builder()
                    .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
                    .build()
            val credential =
                try {
                    CredentialManager.create(activity).getCredential(activity, request).credential
                } catch (_: GetCredentialCancellationException) {
                    throw Cancelled()
                } catch (_: NoCredentialException) {
                    error("Add a Google account to this phone first.")
                }
            check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                "Google returned an unexpected credential"
            }
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        }

    // Generated from google-services.json by the Google Services plugin once Google sign-in is enabled in
    // Firebase. Looked up by name so the app still builds with a configuration that doesn't have it yet.
    private fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }
}
