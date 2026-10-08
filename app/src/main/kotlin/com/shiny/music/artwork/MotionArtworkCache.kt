package com.shiny.music.artwork

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Motion artwork kept on disk.
 *
 * A clip is a few megabytes, loops for as long as its album plays and plays again every time
 * the album does. Without this it was streamed afresh each time. With it, what has been
 * fetched once (the playlists and the video itself) is read from here afterwards, offline
 * included, and the least recently played clips make way when the space is used up.
 *
 * It lives in the app's cache directory, so the system may clear it when the phone is short
 * of space; nothing depends on a clip being here.
 */
@OptIn(UnstableApi::class)
object MotionArtworkCache {
    private const val MaxBytes = 160L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null

    /** The cache if it has been opened, without opening it: safe to call on the main thread. */
    fun peek(): SimpleCache? = cache

    /** Opens the cache. Reads its index from disk, so not for the main thread. */
    fun open(context: Context): SimpleCache? = cache ?: synchronized(this) {
        cache ?: runCatching {
            val app = context.applicationContext
            SimpleCache(File(app.cacheDir, "motion_artwork"), LeastRecentlyUsedCacheEvictor(MaxBytes), StandaloneDatabaseProvider(app))
        }.getOrNull()?.also { cache = it }
    }
}
