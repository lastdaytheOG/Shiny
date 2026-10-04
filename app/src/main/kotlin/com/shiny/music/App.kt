

package com.shiny.music
import com.shiny.music.R
import com.shiny.music.BuildConfig

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import com.music.innertube.YouTube
import com.music.innertube.models.IpVersion
import com.music.innertube.models.YouTubeLocale
import com.music.kugou.KuGou
import com.shiny.music.constants.*
import com.shiny.music.di.ApplicationScope
import com.shiny.music.extensions.toEnum
import com.shiny.music.extensions.toInetSocketAddress
import com.shiny.music.utils.CrashHandler
import com.shiny.music.utils.AppContextHolder
import com.shiny.music.utils.PreferencesSnapshot
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.reportException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import timber.log.Timber
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.Locale
import javax.inject.Inject
import com.shiny.music.utils.getOrNull

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun startForegroundService(service: Intent): android.content.ComponentName? {
        return try {
            super.startForegroundService(service)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                Timber.e(e, "Suppressed ForegroundServiceStartNotAllowedException in App")
                null
            } else {
                throw e
            }
        }
    }

    private fun isCrashProcess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName() == "$packageName:crash"
        }
        val pid = android.os.Process.myPid()
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (processInfo in manager.runningAppProcesses ?: emptyList()) {
            if (processInfo.pid == pid) {
                return processInfo.processName == "$packageName:crash"
            }
        }
        return false
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Before the content providers, so Crashlytics (started by one) wraps it and records
        // every crash before the crash screen shows. See CrashHandler.
        CrashHandler.install(this)
    }

    override fun onCreate() {
        super.onCreate()

        if (isCrashProcess()) return

        // Removed destructive database deletion to preserve user data

        CrashHandler.installForegroundServiceGuard()

        
        AppContextHolder.initialize(this)
        com.shiny.music.utils.cipher.CipherDeobfuscator.initialize(this)

        // Started as early as possible in the process: every composable preference read
        // now comes from this snapshot, so the sooner it holds real values the smaller
        // the window in which the UI would render defaults. MainActivity holds the
        // splash until it has landed.
        PreferencesSnapshot.start(this, applicationScope)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Handled exceptions and playback health to Crashlytics/Analytics (Play build only).
        // Off the main thread so it adds nothing to cold start; anything reported in the few
        // milliseconds before it lands is only logged, as before.
        applicationScope.launch(Dispatchers.IO) {
            com.shiny.music.telemetry.Telemetry.install(this@App)
        }

        // HTTP/3 for streams on the Play build; streams use OkHttp until (and unless) it is ready.
        com.shiny.music.playback.CronetTransport.install(this)

        applicationScope.launch(Dispatchers.IO) {
            cachedCoilCacheSize = dataStore.data.map {
                it.getOrNull(MaxImageCacheSizeKey) ?: DEFAULT_IMAGE_CACHE_MB
            }.first()
        }

        applicationScope.launch {
            initializeSettings()
            observeSettingsChanges()
        }
    }

    private suspend fun initializeSettings() {
        val settings = dataStore.data.first()
        val locale = Locale.getDefault()
        val languageTag = locale.language

        val currentAudioQuality = settings[AudioQualityKey]?.toEnum(defaultValue = AudioQuality.OPUS) ?: AudioQuality.OPUS
        val currentDownloadQuality = settings[DownloadQualityKey]?.toEnum(defaultValue = DownloadQuality.YOUTUBE) ?: DownloadQuality.YOUTUBE
        YouTube.locale = YouTubeLocale(
            gl = settings[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.country.takeIf { it in CountryCodeToName }
                ?: "US",
            hl = settings[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )

        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }

        if (settings[ProxyEnabledKey] == true) {
            val username = settings[ProxyUsernameKey].orEmpty()
            val password = settings[ProxyPasswordKey].orEmpty()
            val type = settings[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP)

            if (username.isNotEmpty() || password.isNotEmpty()) {
                if (type == Proxy.Type.HTTP) {
                    YouTube.proxyAuth = Credentials.basic(username, password)
                } else {
                    Authenticator.setDefault(object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication =
                            PasswordAuthentication(username, password.toCharArray())
                    })
                }
            }
            try {
                settings[ProxyUrlKey]?.let {
                    YouTube.proxy = Proxy(type, it.toInetSocketAddress())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@App, getString(R.string.failed_to_parse_proxy), Toast.LENGTH_SHORT).show()
                }
                reportException(e)
            }
        }

        YouTube.useLoginForBrowse = settings[UseLoginForBrowse] ?: true
        YouTube.ipVersion = settings[IpVersionKey]?.toEnum(defaultValue = IpVersion.AUTO) ?: IpVersion.AUTO

        // Set playback engine preference
        val engineName = settings[com.shiny.music.constants.PlaybackEngineKey]
        com.shiny.music.utils.YTPlayerUtils.playbackEngine = try {
            if (engineName != null) com.shiny.music.constants.PlaybackEngine.valueOf(engineName)
            else com.shiny.music.constants.PlaybackEngine.AUTO
        } catch (_: Exception) { com.shiny.music.constants.PlaybackEngine.AUTO }

        val channel = NotificationChannel(
            "updates",
            getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.update_channel_desc)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun observeSettingsChanges() {
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it.getOrNull(VisitorDataKey) }
                .distinctUntilChanged()
                .collect { visitorData ->
                    YouTube.visitorData = visitorData?.takeIf { it != "null" }
                        ?: YouTube.visitorData().getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        }
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it.getOrNull(DataSyncIdKey) }
                .distinctUntilChanged()
                .collect { dataSyncId ->
                    YouTube.dataSyncId = dataSyncId?.let {
                        it.takeIf { !it.contains("||") }
                            ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                            ?: it.substringAfter("||")
                    }
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it.getOrNull(InnerTubeCookieKey) }
                .distinctUntilChanged()
                .collect { cookie ->
                    try {
                        YouTube.cookie = cookie
                    } catch (e: Exception) {
                        Timber.e(e, "Could not parse cookie. Clearing existing cookie.")
                        forgetAccount(this@App)
                    }
                }
        }



        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { Triple(it.getOrNull(ContentCountryKey), it.getOrNull(ContentLanguageKey), it.getOrNull(AppLanguageKey)) }
                .distinctUntilChanged()
                .collect { (contentCountry, contentLanguage, appLanguage) ->
                    val systemLocale = Locale.getDefault()
                    val effectiveAppLocale = appLanguage
                        ?.takeUnless { it == SYSTEM_DEFAULT }
                        ?.let { Locale.forLanguageTag(it) }
                        ?: systemLocale

                    YouTube.locale = YouTubeLocale(
                        gl = contentCountry?.takeIf { it != SYSTEM_DEFAULT }
                            ?: effectiveAppLocale.country.takeIf { it in CountryCodeToName }
                            ?: systemLocale.country.takeIf { it in CountryCodeToName }
                            ?: "US",
                        hl = contentLanguage?.takeIf { it != SYSTEM_DEFAULT }
                            ?: effectiveAppLocale.toLanguageTag().takeIf { it in LanguageCodeToName }
                            ?: effectiveAppLocale.language.takeIf { it in LanguageCodeToName }
                            ?: "en"
                    )
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it.getOrNull(IpVersionKey) }
                .distinctUntilChanged()
                .collect { ipVersion ->
                    YouTube.ipVersion = ipVersion?.toEnum(defaultValue = IpVersion.AUTO) ?: IpVersion.AUTO
                }
        }
    }

    @Volatile
    private var cachedCoilCacheSize: Int? = null

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // Order matters: the value this class already holds, then the preference snapshot
        // (started at the very top of onCreate), and only then a blocking read. Coil builds
        // its loader lazily on whichever thread first asks for an image — often the main
        // thread, during the first frame — so the blocking path is the last resort, not
        // the default it used to be whenever the async field had not landed yet.
        val cacheSize = cachedCoilCacheSize
            ?: PreferencesSnapshot.read(MaxImageCacheSizeKey, DEFAULT_IMAGE_CACHE_MB)
                .takeIf { PreferencesSnapshot.isLoaded }
            ?: runBlocking {
                dataStore.data.map {
                    it.getOrNull(MaxImageCacheSizeKey) ?: DEFAULT_IMAGE_CACHE_MB
                }.first()
            }
        return ImageLoader.Builder(this).apply {
            // Covers load over the app's shared HTTP threads and connections (see SharedHttp)
            // instead of a client and thread pool of Coil's own.
            components {
                add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = { com.music.innertube.SharedHttp.client }))
            }
            crossfade(true)
            allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            
            memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            if (cacheSize == 0) {
                diskCachePolicy(CachePolicy.DISABLED)
            } else {
                diskCache(
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("coil"))
                        .maxSizeBytes(cacheSize * 1024 * 1024L)
                        .build()
                )
            }
        }.build()
    }

    companion object {
        init {
            // Before any coroutine runs: idle I/O workers live three minutes instead of one,
            // so each burst of work (a song change, a screen of covers) reuses the threads
            // the last one started rather than creating and retiring a new set every time.
            System.setProperty("kotlinx.coroutines.scheduler.keep.alive.sec", "180")
        }

        /**
         * Default ceiling for Coil's on-disk image cache, in megabytes.
         *
         * Everything this app caches as an image is cover art, at most 1200x1200 and
         * typically 150-250 KB. 256 MB is on the order of a thousand covers — far more
         * than a listening history reaches — while the previous 512 MB default was simply
         * half a gigabyte of the user's storage that nothing was ever going to fill with
         * anything useful. Users who want more can raise it in Settings > Storage, and a
         * value already saved there is untouched by this: only the default changed.
         */
        const val DEFAULT_IMAGE_CACHE_MB = 256

        suspend fun forgetAccount(context: Context) {
            Timber.d("forgetAccount: Starting logout process")

            
            Timber.d("forgetAccount: Clearing DataStore preferences")
            context.dataStore.edit { settings ->
                settings.remove(InnerTubeCookieKey)
                settings.remove(VisitorDataKey)
                settings.remove(DataSyncIdKey)
                settings.remove(AccountNameKey)
                settings.remove(AccountEmailKey)
                settings.remove(AccountChannelHandleKey)
            }
            Timber.d("forgetAccount: DataStore preferences cleared")

            
            Timber.d("forgetAccount: Clearing YouTube object auth state")
            Timber.d("forgetAccount: Before - cookie=${YouTube.cookie?.take(50)}, visitorData=${YouTube.visitorData?.take(20)}, dataSyncId=${YouTube.dataSyncId?.take(20)}")
            YouTube.cookie = null
            YouTube.visitorData = null
            YouTube.dataSyncId = null
            Timber.d("forgetAccount: After - cookie=${YouTube.cookie}, visitorData=${YouTube.visitorData}, dataSyncId=${YouTube.dataSyncId}")

            
            Timber.d("forgetAccount: Clearing WebView CookieManager")
            withContext(Dispatchers.Main) {
                android.webkit.CookieManager.getInstance().apply {
                    removeAllCookies { removed ->
                        Timber.d("forgetAccount: CookieManager.removeAllCookies callback: removed=$removed")
                    }
                    flush()
                }
            }
            Timber.d("forgetAccount: Logout process complete")
        }
    }
}
