package com.shiny.music.playback

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.Event
import com.shiny.music.extensions.metadata
import com.shiny.music.stats.LiveListens
import com.shiny.music.stats.PlayCounter
import com.shiny.music.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class ListenRecorder(
    private val player: Player,
    private val database: MusicDatabase,
    private val thresholdMs: () -> Long,
    allowed: Boolean,
    private val onFinished: (String) -> Unit,
) : Player.Listener {
    private var timer: Job? = null
    private var released = false
    private val counter = PlayCounter<MediaItem>(
        clock = SystemClock::elapsedRealtime,
        thresholdMs = thresholdMs,
        allowed = allowed,
        save = ::save,
        finished = { play ->
            onFinished(play.songId)
            writes.launch { LiveListens.remove(play) }
        },
    )

    init {
        player.addListener(this)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
            mediaItem != null && mediaItem.mediaId == counter.play?.songId
        ) return
        if (mediaItem == null) counter.finish() else counter.start(mediaItem.mediaId, mediaItem)
        schedule()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying && counter.play == null) player.currentMediaItem?.let { counter.start(it.mediaId, it) }
        counter.setPlaying(isPlaying)
        schedule()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState != Player.STATE_ENDED) return
        counter.finish()
        schedule()
    }

    fun setAllowed(allowed: Boolean) {
        if (released) return
        counter.setAllowed(allowed)
        schedule()
    }

    fun release() {
        if (released) return
        released = true
        player.removeListener(this)
        timer?.cancel()
        counter.finish()
    }

    private fun schedule() {
        timer?.cancel()
        if (counter.untilCheckpointMs() == null) return
        timer = timers.launch {
            while (true) {
                delay(counter.untilCheckpointMs() ?: break)
                counter.checkpoint()
            }
        }
    }

    private fun save(play: PlayCounter.Play<MediaItem>, heardMs: Long) {
        val metadata = play.item.metadata
        val at = LocalDateTime.now()
        LiveListens.add(play)
        writes.launch {
            runCatching {
                val first = play.eventId == null
                database.withTransaction {
                    if (getSongByIdBlocking(play.songId) == null) metadata?.let { insert(it) }
                    val id = play.eventId
                    if (id == null) {
                        val created = libraryDao.insertListen(Event(songId = play.songId, timestamp = at, playTime = heardMs))
                        incrementTotalPlayTime(play.songId, heardMs)
                        play.eventId = created
                    } else {
                        libraryDao.listenPlayTime(id)?.let { saved ->
                            libraryDao.setListenPlayTime(id, heardMs)
                            incrementTotalPlayTime(play.songId, heardMs - saved)
                        }
                    }
                }
                if (first) database.incrementPlayCount(play.songId)
            }.onFailure(::reportException)
        }
    }

    private companion object {
        val timers = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    }
}
