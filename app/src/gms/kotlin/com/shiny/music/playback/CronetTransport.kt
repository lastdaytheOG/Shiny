package com.shiny.music.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cronet.CronetDataSource
import com.google.android.gms.net.CronetProviderInstaller
import com.shiny.music.utils.StreamTransport
import org.chromium.net.CronetEngine
import org.chromium.net.CronetProvider
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors

/**
 * Puts stream traffic on HTTP/3 through the Cronet that Google Play Services ships, so the APK
 * carries no network stack of its own. Until the provider is installed — or if it never is (no
 * Play Services, an old one, an error) — [StreamTransport] stays on OkHttp; nothing waits for it.
 */
@OptIn(UnstableApi::class)
object CronetTransport {

    /** Builds the engine off the main thread and runs Cronet's callbacks. */
    private val executor = Executors.newSingleThreadExecutor()

    fun install(context: Context) {
        val app = context.applicationContext
        runCatching {
            CronetProviderInstaller.installProvider(app)
                .addOnSuccessListener(executor) { build(app) }
                .addOnFailureListener { Timber.tag("fix403").i("cronet unavailable: ${it.message}") }
        }.onFailure { Timber.tag("fix403").i("cronet unavailable: ${it.message}") }
    }

    private fun build(context: Context) {
        runCatching {
            // Only a real Cronet; the "fallback" provider wraps HttpURLConnection and would be a
            // step down from OkHttp.
            val provider = CronetProvider.getAllProviders(context)
                .filter { it.isEnabled && it.name != CronetProvider.PROVIDER_NAME_FALLBACK }
                .maxByOrNull { if (it.name == CronetProvider.PROVIDER_NAME_APP_PACKAGED) 0 else 1 }
                ?: error("no native provider")
            // Remembers which hosts spoke QUIC, so the next process opens them on QUIC at once
            // instead of learning it again over TCP.
            val storage = File(context.cacheDir, "cronet").apply { mkdirs() }
            val engine: CronetEngine = provider.createBuilder()
                .enableQuic(true)
                .enableHttp2(true)
                .enableBrotli(true)
                .setStoragePath(storage.absolutePath)
                .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK_NO_HTTP, 1024 * 1024)
                .build()
            StreamTransport.cronet = CronetDataSource.Factory(engine, executor)
                .setConnectionTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
            Timber.tag("fix403").i("cronet ready: ${provider.name} ${provider.version}")
        }.onFailure { Timber.tag("fix403").w("cronet build failed: ${it.message}") }
    }
}
