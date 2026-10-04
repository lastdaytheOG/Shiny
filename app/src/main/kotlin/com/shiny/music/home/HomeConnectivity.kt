package com.shiny.music.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the device has an internet-capable default network, starting with the answer
 * right now. (The shared `NetworkConnectivityObserver` only reports changes, so a launch
 * without a network would never hear "offline".)
 */
fun Context.homeOnlineFlow(): Flow<Boolean> = callbackFlow {
    val manager = getSystemService(ConnectivityManager::class.java)
    fun current(): Boolean = runCatching {
        manager.activeNetwork?.let(manager::getNetworkCapabilities)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    trySend(current())
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(true)
        }

        override fun onLost(network: Network) {
            trySend(false)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        }
    }
    val registered = runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess
    if (!registered) trySend(true)
    awaitClose { if (registered) runCatching { manager.unregisterNetworkCallback(callback) } }
}.distinctUntilChanged()
