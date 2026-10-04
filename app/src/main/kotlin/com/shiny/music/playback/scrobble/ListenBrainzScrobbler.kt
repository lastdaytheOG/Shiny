package com.shiny.music.playback.scrobble

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.shiny.music.constants.DataSaverEnabledKey
import com.shiny.music.constants.ListenBrainzEnabledKey
import com.shiny.music.constants.ListenBrainzTokenKey
import com.shiny.music.extensions.metadata
import com.shiny.music.ui.screens.settings.ListenBrainzManager
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.getOrNull
import com.shiny.music.utils.isLocalMediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Tells ListenBrainz what is playing. [ListenTracker] holds the rules; this feeds it the
 * player's state and sends what it asks for.
 *
 * While scrobbling is off (the setting, a missing token, or the data saver) the tracker is
 * told that nothing is playing, so no timer runs and nothing is sent. Local files are never
 * sent. Main thread only, like the player it listens to.
 */
class ListenBrainzScrobbler(
    private val context: Context,
    private val scope: () -> CoroutineScope,
) : Player.Listener {
    private var player: Player? = null

    /** The user's token, or null while scrobbling is off. */
    private var token: String? = null
    private var wake: Job? = null
    private val tracker = ListenTracker(SystemClock::elapsedRealtime, System::currentTimeMillis, ::send)

    init {
        scope().launch {
            context.dataStore.data
                .map { prefs ->
                    val on = prefs.getOrNull(ListenBrainzEnabledKey) == true &&
                        prefs.getOrNull(DataSaverEnabledKey) != true
                    prefs.getOrNull(ListenBrainzTokenKey)?.trim()?.takeIf { on && it.isNotEmpty() }
                }
                .distinctUntilChanged()
                .collect {
                    token = it
                    sync()
                }
        }
    }

    /**
     * Follows [next] from now on. The service hands playback to a second player for a
     * crossfade, and what that player holds is a new play even when it is the same song.
     */
    fun attach(next: Player) {
        val previous = player
        if (previous === next) return
        previous?.removeListener(this)
        player = next
        next.addListener(this)
        sync(newPlay = previous != null)
    }

    fun release() {
        wake?.cancel()
        wake = null
        player?.removeListener(this)
        player = null
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // A changed queue under the same song is still the same play.
        sync(newPlay = reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) = sync()

    override fun onPlaybackStateChanged(playbackState: Int) {
        // The length is known from here on, and with it how long the listen needs.
        if (playbackState == Player.STATE_READY) sync()
    }

    private fun sync(newPlay: Boolean = false) {
        wake?.cancel()
        wake = null
        val current = player
        val track = if (token == null) null else current?.scrobbleTrack()
        val waitMs = tracker.update(track, playing = current?.isPlaying == true, newPlay = newPlay) ?: return
        wake = scope().launch {
            delay(waitMs)
            sync()
        }
    }

    private fun Player.scrobbleTrack(): ListenTracker.Track? {
        val item = currentMediaItem ?: return null
        if (item.mediaId.isLocalMediaId()) return null
        val metadata = item.metadata ?: return null
        val length = duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: (metadata.duration * 1000L).coerceAtLeast(0L)
        return ListenTracker.Track(
            id = item.mediaId,
            title = metadata.title,
            artists = metadata.artists.joinToString(", ") { it.name },
            album = metadata.album?.title.orEmpty(),
            durationMs = length,
        )
    }

    private fun send(submission: ListenTracker.Submission) {
        val token = token ?: return
        val track = submission.track
        val positionMs = player?.currentPosition ?: 0L
        scope().launch {
            when (submission) {
                is ListenTracker.Submission.PlayingNow -> ListenBrainzManager.submitPlayingNow(
                    context, token, track.title, track.artists, track.album, track.durationMs, positionMs,
                )
                is ListenTracker.Submission.Listen -> ListenBrainzManager.submitFinished(
                    context, token, track.title, track.artists, track.album, track.durationMs,
                    startMs = submission.startedAtMs, endMs = submission.endedAtMs,
                )
            }
        }
    }
}
