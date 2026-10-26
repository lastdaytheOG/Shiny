package com.shiny.music.downloads

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import com.music.innertube.SharedHttp
import com.shiny.music.db.MusicDatabase
import com.shiny.music.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Covers of downloaded songs, kept in app storage beside the audio. The image cache lives in
 * the cache folder, which Android and phone cleaners empty whenever they like; a download has
 * to keep its cover through that and show it offline.
 */
object DownloadCovers {
    private const val DirName = "download_covers"
    private const val CoverPx = 1200
    private const val ReloadAfterMs = 10_000L

    private val songByCover = ConcurrentHashMap<String, String>()

    @Volatile
    private var database: MusicDatabase? = null

    @Volatile
    private var loadedAt = 0L

    private fun dir(context: Context) = File(context.applicationContext.filesDir, DirName)

    private fun fileFor(context: Context, songId: String) = File(dir(context), "$songId.jpg")

    /** One name for every size of the same picture, so a list row finds the cover the player asked for. */
    internal fun key(url: String): String? {
        if (!url.startsWith("http")) return null
        return when {
            "i.ytimg.com" in url -> url.substringBefore('?').substringBeforeLast('/')
            "googleusercontent.com" in url || "ggpht.com" in url -> url.substringBefore('=')
            else -> url.substringBefore('?')
        }
    }

    private suspend fun reload(database: MusicDatabase): List<com.shiny.music.db.entities.SongCover>? {
        val covers = runCatching { database.downloadedCovers() }.getOrNull() ?: return null
        songByCover.clear()
        covers.forEach { cover -> key(cover.thumbnailUrl)?.let { songByCover[it] = cover.id } }
        loadedAt = System.currentTimeMillis()
        return covers
    }

    private suspend fun file(context: Context, url: String): File? {
        val key = key(url) ?: return null
        songByCover[key]?.let { id -> fileFor(context, id).takeIf { it.exists() }?.let { return it } }
        val database = database ?: return null
        if (System.currentTimeMillis() - loadedAt < ReloadAfterMs) return null
        reload(database)
        return songByCover[key]?.let { id -> fileFor(context, id).takeIf { it.exists() } }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= CoverPx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun candidates(url: String): List<String> {
        val large = url.resize(CoverPx, CoverPx)
        return if ("maxresdefault.jpg" in large) listOf(large, large.replace("maxresdefault.jpg", "hqdefault.jpg")) else listOf(large)
    }

    suspend fun save(context: Context, songId: String, url: String?): Boolean = withContext(Dispatchers.IO) {
        val key = url?.let(::key) ?: return@withContext false
        songByCover[key] = songId
        val target = fileFor(context, songId)
        if (target.exists()) return@withContext true
        for (address in candidates(url)) {
            val saved = runCatching {
                val bytes = SharedHttp.client.newCall(Request.Builder().url(address).build()).execute().use { response ->
                    response.body?.takeIf { response.isSuccessful }?.bytes()
                } ?: return@runCatching false
                val bitmap = decode(bytes) ?: return@runCatching false
                target.parentFile?.mkdirs()
                val pending = File(target.parentFile, "$songId.tmp")
                pending.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                bitmap.recycle()
                pending.renameTo(target) || run { pending.delete(); false }
            }.onFailure { Timber.tag("DownloadCovers").d("could not save %s: %s", address, it.toString()) }.getOrDefault(false)
            if (saved) return@withContext true
        }
        false
    }

    /** Saves covers missing for songs already downloaded and drops those of removed downloads. */
    suspend fun sync(context: Context, database: MusicDatabase, online: Boolean) = withContext(Dispatchers.IO) {
        this@DownloadCovers.database = database
        val covers = reload(database) ?: return@withContext
        val ids = covers.mapTo(HashSet()) { it.id }
        dir(context).listFiles()?.forEach { file ->
            if (file.name.endsWith(".tmp") || file.name.removeSuffix(".jpg") !in ids) file.delete()
        }
        if (!online) return@withContext
        covers.filterNot { fileFor(context, it.id).exists() }.forEach { save(context, it.id, it.thumbnailUrl) }
    }

    fun attach(database: MusicDatabase) {
        this.database = database
    }

    /** Loads the saved cover when the cover's own address cannot be loaded: offline, or after the image cache was cleared. */
    class Fallback(private val context: Context) : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val result = chain.proceed()
            if (result !is ErrorResult) return result
            val url = chain.request.data as? String ?: return result
            val saved = withContext(Dispatchers.IO) { file(context, url) } ?: return result
            return chain.withRequest(chain.request.newBuilder().data(saved).build()).proceed()
        }
    }
}
