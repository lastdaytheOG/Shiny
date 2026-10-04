package com.shiny.music.utils

import com.music.innertube.YouTube
import com.music.innertube.models.IpVersion
import com.shiny.music.BuildConfig
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * The one HTTP client for stream traffic: the resolver's URL probe ([YTPlayerUtils]), ExoPlayer's
 * playback requests and the next-song pre-cache (both in `MusicService`).
 *
 * The probe and playback used to build separate clients, so the TLS connection the probe had just
 * opened to the stream's host was never reused and playback opened its own (audit B7: 435 ms cold
 * against 35 ms reused). Sharing the pool alone would not fix that: OkHttp only hands a pooled
 * connection to a request whose Address matches, and the Address includes the Dns and proxy
 * authenticator instances. Callers that need other timeouts use `client.newBuilder()`, which keeps
 * the pool, the Dns and the authenticator.
 */
object StreamHttp {

    /** Mirrors the IP version preference. `MusicService` keeps it current. */
    @Volatile
    var ipVersion: IpVersion = IpVersion.AUTO

    private val dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            return when (ipVersion) {
                IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                IpVersion.AUTO -> addresses
            }
        }
    }

    val client: OkHttpClient by lazy {
        com.music.innertube.SharedHttp.client.newBuilder()
            .dns(dns)
            .proxy(YouTube.proxy)
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }
            .apply { if (BuildConfig.DEBUG) eventListenerFactory { ConnectionLog() } }
            .build()
    }

    /** Debug builds only: whether each request opened a new connection or reused a pooled one. */
    private class ConnectionLog : EventListener() {
        private var opened = false

        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
            opened = true
        }

        override fun connectionAcquired(call: Call, connection: Connection) {
            val request = call.request()
            Timber.tag("fix403").d("http.connection ${request.method} host=${request.url.host} reused=${!opened}")
        }
    }
}
