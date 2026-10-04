package com.shiny.music.utils

import androidx.media3.common.C
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.music.innertube.YouTube
import com.music.innertube.models.IpVersion

/**
 * Where stream bytes travel: the resolver's probe, ExoPlayer's reads and the pre-caches all
 * open their connections through [factory], so they share one connection pool whichever stack
 * serves it.
 *
 * The Play build installs Cronet into [cronet] (see `CronetTransport` in the gms source set):
 * googlevideo speaks HTTP/3, and QUIC rides out the packet loss and network handovers of
 * mobile data that stall a TCP connection. Cronet does its own DNS and has no proxy support,
 * so it is only used when neither the proxy nor the IP-version preference is set; with either,
 * and on FOSS builds, streams stay on [StreamHttp]'s OkHttp client as before.
 */
@OptIn(UnstableApi::class)
object StreamTransport {

    /** A Cronet-backed factory once one is ready; null until then, and always on FOSS. */
    @Volatile
    var cronet: HttpDataSource.Factory? = null

    private val okHttp: HttpDataSource.Factory by lazy { OkHttpDataSource.Factory(StreamHttp.client) }

    private val cronetAllowed: Boolean
        get() = YouTube.proxy == null && StreamHttp.ipVersion == IpVersion.AUTO && !DebugFaults.cronetDisabled

    fun factory(): HttpDataSource.Factory = cronet?.takeIf { cronetAllowed } ?: okHttp

    /** For logs: which stack a request made now would use. */
    val name: String get() = if (factory() === okHttp) "okhttp" else "cronet"

    /** Chooses the stack per data source, so a Cronet engine that arrives later is picked up. */
    val dataSourceFactory: DataSource.Factory = DataSource.Factory { factory().createDataSource() }

    /**
     * A HEAD for [length] bytes at [position] of [url]. Returns the HTTP status, or null when no
     * status arrived at all (timeout, reset, no route).
     */
    fun head(url: String, position: Long, length: Long, cookie: String?): Int? {
        val source = factory().createDataSource()
        cookie?.let { source.setRequestProperty("Cookie", it) }
        val spec = DataSpec.Builder()
            .setUri(url)
            .setHttpMethod(DataSpec.HTTP_METHOD_HEAD)
            .setPosition(position)
            .setLength(if (length > 0) length else C.LENGTH_UNSET.toLong())
            .build()
        return try {
            source.open(spec)
            source.responseCode.takeIf { it > 0 } ?: 200
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            e.responseCode
        } catch (e: HttpDataSource.HttpDataSourceException) {
            null
        } finally {
            runCatching { source.close() }
        }
    }
}
