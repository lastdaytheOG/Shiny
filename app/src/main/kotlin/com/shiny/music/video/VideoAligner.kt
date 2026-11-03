// VideoAligner.kt

package com.shiny.music.video

import android.net.Uri
import android.os.SystemClock
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import com.shiny.music.utils.YTPlayerUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

/**
 * Works out where each moment of a song sits in its music video by listening to both, so a
 * video with an intro, a skit or a cut still plays in step. Null when they are not the same
 * recording (a live cut, a remix) or either could not be read.
 */
@OptIn(UnstableApi::class)
object VideoAligner {
    private class Found(val map: SongVideoMap?, val atMs: Long)

    // its own quiet thread: listening must never compete with the music being decoded
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "video-aligner").apply { priority = Thread.NORM_PRIORITY - 2; isDaemon = true }
    }.asCoroutineDispatcher()
    private val prints = LruCache<String, AudioPrints>(6)
    private val found = LruCache<String, Found>(32)
    private val lock = Mutex()

    fun known(songId: String, videoId: String): SongVideoMap? = fresh("$songId>$videoId")?.map

    fun isKnown(songId: String, videoId: String): Boolean = fresh("$songId>$videoId") != null

    suspend fun align(songId: String, videoId: String, videoAudioUrl: String?, videoUserAgent: String): SongVideoMap? {
        val key = "$songId>$videoId"
        fresh(key)?.let { return it.map }
        return withContext(dispatcher) {
            lock.withLock {
                fresh(key)?.let { return@withLock it.map }
                val map = run {
                    val song = printsOf(songId) {
                        YTPlayerUtils.resolveListeningAudio(songId).getOrNull()
                    } ?: return@run null
                    val video = printsOf(videoId) {
                        videoAudioUrl?.let { it to videoUserAgent } ?: YTPlayerUtils.resolveListeningAudio(videoId).getOrNull()
                    } ?: return@run null
                    AudioMatcher.match(song, video)
                }
                android.util.Log.i("SongVideo", "$songId in $videoId: ${map ?: "no match"}")
                found.put(key, Found(map, SystemClock.elapsedRealtime()))
                map
            }
        }
    }

    // a match is kept; a miss is asked again after a while, since a link may simply have failed
    private fun fresh(key: String): Found? =
        found.get(key)?.takeIf { it.map != null || SystemClock.elapsedRealtime() - it.atMs < MissRetryMs }

    private suspend fun printsOf(id: String, source: suspend () -> Pair<String, String>?): AudioPrints? {
        prints.get(id)?.let { return it }
        val (url, userAgent) = source() ?: return null
        return try {
            val printer = AudioPrinter()
            val factory = DefaultHttpDataSource.Factory().setUserAgent(userAgent).setAllowCrossProtocolRedirects(true)
            withTimeoutOrNull(ListenTimeoutMs) {
                StreamPcmDecoder.decode(factory, Uri.parse(url), "align:$id", MaxRecordingUs) { pcm, channels, rate, us ->
                    printer.feed(pcm, channels, rate, us)
                }
            } ?: return null
            printer.finish()?.also { prints.put(id, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SongVideo", "couldn't listen to $id", e)
            null
        }
    }

    private const val MaxRecordingUs = 15 * 60_000_000L
    private const val ListenTimeoutMs = 90_000L
    private const val MissRetryMs = 10 * 60_000L
}
