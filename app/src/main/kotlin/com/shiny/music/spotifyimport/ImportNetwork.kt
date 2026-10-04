package com.shiny.music.spotifyimport

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.shiny.music.spotify.Spotify
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.IOException

/**
 * Whether this failure is the phone having no usable network for a while rather than
 * anything wrong with the request, so the same request can simply be sent again.
 *
 * The one a long import meets is "Unable to resolve host api-partner.spotify.com: No address
 * associated with hostname". That is not Spotify being down: it is the DNS answer Android
 * gives an app whose network it has cut (screen off, Doze, battery saver, an OEM's
 * background limits) and the answer a brief drop or a Wi-Fi to mobile hand-over gives.
 * A 5xx is Spotify's own brief failure and is treated the same way.
 */
internal fun Throwable.isTransientNetworkFailure(): Boolean =
    generateSequence(this) { it.cause }.take(8).any { cause ->
        cause is IOException ||
            (cause is Spotify.SpotifyException && cause.statusCode in 500..599)
    }

/**
 * Whether the default network can carry this app's traffic right now, and on every change.
 * Emits nothing while there is no default network at all, which callers read as "not yet".
 *
 * "Blocked" is the app's own traffic being cut while the device is online (API 29+ reports
 * it); a plain connectivity check says online throughout, which is how a retry used to fire
 * straight back into the same DNS failure.
 */
internal fun Context.usableNetworkFlow(): Flow<Boolean> = callbackFlow {
    val manager = getSystemService(ConnectivityManager::class.java)
    if (manager == null) {
        trySend(true)
        awaitClose {}
        return@callbackFlow
    }
    // Written and read only on the ConnectivityManager callback thread.
    var hasInternet = false
    var blocked = false
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            trySend(hasInternet && !blocked)
        }

        override fun onBlockedStatusChanged(network: Network, isBlocked: Boolean) {
            blocked = isBlocked
            trySend(hasInternet && !blocked)
        }

        override fun onLost(network: Network) {
            hasInternet = false
            trySend(false)
        }
    }
    val registered = runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess
    if (!registered) trySend(true)
    awaitClose { if (registered) runCatching { manager.unregisterNetworkCallback(callback) } }
}.distinctUntilChanged()
