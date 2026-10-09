

@file:Suppress("DEPRECATION")

package com.shiny.music.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.SQLException
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.shiny.music.MainActivity
import com.shiny.music.R
import com.shiny.music.constants.AudioNormalizationKey
import com.shiny.music.constants.AudioOffload
import com.shiny.music.constants.AudioQualityKey
import com.shiny.music.constants.AutoDownloadOnLikeKey
import com.shiny.music.constants.AutoLoadMoreKey
import com.shiny.music.constants.AutoSkipNextOnErrorKey
import com.shiny.music.constants.CrossfadeDurationKey
import com.shiny.music.constants.CrossfadeEnabledKey
import com.shiny.music.constants.CrossfadeGaplessKey
import com.shiny.music.constants.DisableLoadMoreWhenRepeatAllKey
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.shiny.music.constants.DiscordActivityNameKey
import com.shiny.music.constants.DiscordActivityTypeKey
import com.shiny.music.constants.DiscordListenAlongButtonKey
import com.shiny.music.constants.DiscordShowWhenPausedKey
import com.shiny.music.constants.DiscordTokenKey
import com.shiny.music.constants.EnableDiscordRPCKey
import com.shiny.music.constants.HideExplicitKey
import com.shiny.music.constants.HideVideoSongsKey
import com.shiny.music.constants.HistoryDuration
import com.shiny.music.constants.MediaSessionConstants.CommandToggleLike
import com.shiny.music.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.shiny.music.constants.MediaSessionConstants.CommandToggleShuffle
import com.shiny.music.constants.MediaSessionConstants.CommandToggleStartRadio
import com.shiny.music.constants.PauseListenHistoryKey
import com.shiny.music.constants.PauseOnMute
import com.shiny.music.constants.PersistentQueueKey
import com.shiny.music.constants.PersistentShuffleAcrossQueuesKey
import com.shiny.music.constants.PlayerVolumeKey

import com.shiny.music.constants.RememberShuffleAndRepeatKey
import com.shiny.music.constants.RepeatModeKey
import com.shiny.music.constants.ResumeOnBluetoothConnectKey
import com.shiny.music.constants.ShowLyricsKey
import com.shiny.music.constants.ShuffleModeKey
import com.shiny.music.constants.ShufflePlaylistFirstKey
import com.shiny.music.constants.PreloadLyricsEnabledKey
import com.shiny.music.constants.PreloadNextSongEnabledKey
import com.shiny.music.constants.PreloadNextSongLimitKey
import com.shiny.music.constants.PreventDuplicateTracksInQueueKey
import com.shiny.music.constants.SimilarContent
import com.shiny.music.constants.SkipSilenceInstantKey
import com.shiny.music.constants.SkipSilenceKey
import com.shiny.music.constants.StopMusicOnTaskClearKey
import com.shiny.music.constants.IpVersionKey
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import java.net.InetAddress
import java.net.Inet4Address
import java.net.Inet6Address
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.Event
import com.shiny.music.db.entities.FormatEntity
import com.shiny.music.db.entities.LyricsEntity
import com.shiny.music.db.entities.RelatedSongMap
import com.shiny.music.db.entities.Song
import com.shiny.music.di.DownloadCache
import com.shiny.music.di.PlayerCache
import com.shiny.music.eq.EqualizerService
import com.shiny.music.eq.audio.CustomEqualizerAudioProcessor
import com.shiny.music.eq.data.EQProfileRepository
import com.shiny.music.extensions.SilentHandler
import com.shiny.music.extensions.collect
import com.shiny.music.extensions.collectLatest
import com.shiny.music.extensions.currentMetadata
import com.shiny.music.extensions.findNextMediaItemById
import com.shiny.music.extensions.mediaItems
import com.shiny.music.extensions.metadata
import com.shiny.music.extensions.setOffloadEnabled
import com.shiny.music.extensions.toEnum
import com.shiny.music.extensions.toMediaItem
import com.shiny.music.playback.toPersistQueue
import com.shiny.music.playback.toQueue
import com.shiny.music.shinymusic.updater.downloadmanager.ShinyNotificationProvider
import com.shiny.music.lyrics.LyricsHelper
import com.shiny.music.lyrics.LyricsPick
import com.shiny.music.models.PersistPlayerState
import com.shiny.music.models.PersistQueue
import com.shiny.music.models.QueueData
import com.shiny.music.models.QueueType
import com.shiny.music.models.toMediaMetadata
import com.shiny.music.playback.audio.SilenceDetectorAudioProcessor
import com.shiny.music.playback.queues.EmptyQueue
import com.shiny.music.playback.queues.ListQueue
import com.shiny.music.playback.queues.Queue
import com.shiny.music.playback.queues.YouTubeQueue
import com.shiny.music.playback.queues.filterExplicit
import com.shiny.music.playback.queues.filterVideoSongs
import com.shiny.music.playback.scrobble.ListenBrainzScrobbler
import com.shiny.music.utils.CoilBitmapLoader
import com.shiny.music.ui.screens.settings.DiscordPresenceManager
import com.shiny.music.utils.NetworkConnectivityObserver

import com.shiny.music.utils.YTPlayerUtils
import com.shiny.music.utils.PlaybackHealth
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.get
import com.shiny.music.utils.reportException
import com.shiny.music.widget.ShinyWidgetManager
import com.shiny.music.widget.MusicWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import com.shiny.music.utils.isLocalMediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import com.shiny.music.utils.getOrNull

private const val INSTANT_SILENCE_SKIP_STEP_MS = 15_000L
private const val INSTANT_SILENCE_SKIP_SETTLE_MS = 350L

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: com.shiny.music.lyrics.LyricsHelper

    /** Songs whose hand-timed lyrics were already looked at again this run (see the lyrics fetch). */
    private val lyricsRechecked = HashSet<String>()

    @Inject
    lateinit var syncUtils: com.shiny.music.utils.SyncUtils

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    @Inject
    lateinit var equalizerService: EqualizerService

    @Inject
    lateinit var eqProfileRepository: EQProfileRepository

    @Inject
    lateinit var widgetManager: com.shiny.music.widget.ShinyWidgetManager

    @Inject
    lateinit var together: com.shiny.music.together.TogetherSession

    @Inject
    lateinit var socialPresencePublisher: com.shiny.music.social.SocialPresencePublisher
    

    private lateinit var audioManager: AudioManager
    // Wi-Fi Lock: Prevents modern Wi-Fi 6/7 routers from putting the Wi-Fi chip into
    // low-power sleep mode while music is actively streaming in the background.
    // Without this, the router's power-saving protocol (Target Wake Time) causes
    // packet delays, leading to audio buffering or playback stopping after the screen turns off.
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    private var wasPlayingBeforeAudioFocusLoss = false
    private var hasAudioFocus = false
    private var reentrantFocusGain = false
    private var callHoldJob: Job? = null
    private var wasPlayingBeforeVolumeMute = false
    private var isPausedByVolumeMute = false
    /**
     * The output the listener chose for Shiny's own playback (Android's id for the audio
     * device), or null while playback follows Android's routing. The audio output sheet
     * observes this; nothing else decides where Shiny plays.
     */
    private val _preferredDeviceId = MutableStateFlow<Int?>(null)
    val preferredDeviceId: kotlinx.coroutines.flow.StateFlow<Int?> = _preferredDeviceId.asStateFlow()

    private var crossfadeEnabled = false
    private var crossfadeDuration = 5000f
    private var crossfadeGapless = true
    private var crossfadeTriggerJob: Job? = null

    /** A secondary player buffered ahead of the trigger so the blend doesn't cold-start. */
    private data class PrebufferedTransition(
        val player: ExoPlayer,
        val targetMediaId: String,
    )
    private var prebuffered: PrebufferedTransition? = null

    private val secondaryPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.tag(TAG).e(error, "Secondary player error")
            secondaryPlayer?.stop()
            secondaryPlayer?.clearMediaItems()
            secondaryPlayer = null
        }
    }

    private var scope = CoroutineScope(Dispatchers.Main) + Job()

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(false)

    private lateinit var audioQuality: com.shiny.music.constants.AudioQuality
    private lateinit var ipVersion: IpVersion

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null

    val currentMediaMetadata = MutableStateFlow<com.shiny.music.models.MediaMetadata?>(null)
    private val currentSong =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat =
        currentMediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    lateinit var playerVolume: MutableStateFlow<Float>
    val isMuted = MutableStateFlow(false)

    private fun restorePlayerVolume(volume: Float): Float =
        if (volume.isNaN() || volume <= 0f) 1f else volume.coerceAtMost(1f)

    fun toggleMute() {
        val newMutedState = !isMuted.value
        isMuted.value = newMutedState
        
        player.volume = if (newMutedState) 0f else playerVolume.value
    }

    fun setMuted(muted: Boolean) {
        isMuted.value = muted
        
        
        player.volume = if (muted) 0f else playerVolume.value
    }

    /**
     * Plays through the output with this id, or through wherever Android routes music when
     * [deviceId] is null. Returns false, changing nothing, if there is no such output any more.
     * Only the route changes: the player, its queue and its position are left as they are.
     */
    fun setPreferredAudioDevice(deviceId: Int?): Boolean {
        val deviceInfo = deviceId?.let { id ->
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
        }
        if (deviceId != null && deviceInfo == null) return false
        player.setPreferredAudioDevice(deviceInfo)
        // A crossfade's incoming player is the one that will be heard next.
        secondaryPlayer?.setPreferredAudioDevice(deviceInfo)
        _preferredDeviceId.value = deviceId
        return true
    }

    /** The chosen output as Android's device, for a player built after the choice was made. */
    private fun preferredAudioDevice(): AudioDeviceInfo? {
        val id = _preferredDeviceId.value ?: return null
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
    }


    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    @Inject
    lateinit var downloadFolderStore: com.shiny.music.downloads.DownloadFolderStore

    lateinit var player: ExoPlayer
        private set
    private var secondaryPlayer: ExoPlayer? = null
    private var fadingPlayer: ExoPlayer? = null
    val isCrossfading = MutableStateFlow(false)

    /** The overlap the running blend was started with, in media milliseconds. */
    private var blendOverlapMs = 0L

    /**
     * Set when the user seeks the incoming track mid-blend. The blend then ends at once:
     * the outgoing track stops and the incoming one plays on at full volume from wherever
     * the user put it, instead of both carrying on as a half-finished mix.
     */
    @Volatile private var blendInterrupted = false
    private var crossfadeJob: Job? = null

    private lateinit var mediaSession: MediaLibrarySession

    
    private val playerInitialized = MutableStateFlow(false)
    val isPlayerReady: kotlinx.coroutines.flow.StateFlow<Boolean> = playerInitialized.asStateFlow()

    
    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow = _playerFlow.asStateFlow()

    private val playerSilenceProcessors = HashMap<Player, SilenceDetectorAudioProcessor>()

    /** Each player's stats listener, so a finished queue's last song can be counted at once. */
    private val playerStatsListeners = HashMap<Player, PlaybackStatsListener>()

    /**
     * Play time already counted for a song whose queue ended, by media id. Media3 only closes a
     * playback session when the next one starts, so the last song of a queue (an album you
     * played to the end, a single local file) stayed uncounted until you played something
     * else, and was lost if the app was closed first. Main thread only.
     */
    private val listenCountedAtEnd = HashMap<String, Long>()


    private val instantSilenceSkipEnabled = MutableStateFlow(false)

    private var isAudioEffectSessionOpened = false
    private var loudnessEnhancer: LoudnessEnhancer? = null
    // Holds the outgoing track's enhancer alive through the crossfade so its normalization
    // isn't stripped mid-fade (which would make a heavily-cut track jump louder as it fades).
    private var fadingLoudnessEnhancer: LoudnessEnhancer? = null


    private var lastPlaybackSpeed = 1.0f
    private var discordUpdateJob: kotlinx.coroutines.Job? = null


    private lateinit var listenBrainz: ListenBrainzScrobbler

    // Cached playback preferences kept in sync with DataStore.
    //
    // Every value here is read from a Player.Listener callback, a player factory or a
    // crossfade step — all of which run on the main thread while audio is playing. The
    // `dataStore.get` these replace is `runBlocking(Dispatchers.IO)`, so each one parked
    // the main thread on a coroutine hop (and, cold, a file read) at exactly the moments
    // where a stall is audible or visible: a track transition, the start of a blend, the
    // first tap that starts playback.
    private var cachedRepeatMode: Int = REPEAT_MODE_OFF
    private var cachedShuffleEnabled: Boolean = false
    private var cachedPreloadEnabled: Boolean = true
    private var cachedPreloadLimit: Int = 1
    private var cachedPreloadLyrics: Boolean = true
    @Volatile private var cachedSkipSilence: Boolean = false
    @Volatile private var cachedSkipSilenceInstant: Boolean = false
    @Volatile private var cachedAudioOffload: Boolean = false
    @Volatile private var cachedCrossfadeEnabled: Boolean = false
    @Volatile private var cachedPersistentQueue: Boolean = true
    @Volatile private var cachedAutoLoadMore: Boolean = true
    @Volatile private var cachedDisableLoadMoreWhenRepeatAll: Boolean = false
    @Volatile private var cachedHideExplicit: Boolean = false
    @Volatile private var cachedHideVideoSongs: Boolean = false
    @Volatile private var cachedDataSaver: Boolean = false
    @Volatile private var cachedShufflePlaylistFirst: Boolean = false
    @Volatile private var cachedPersistShuffleAcrossQueues: Boolean = false
    @Volatile private var cachedHistoryDuration: Float = 30f
    @Volatile private var cachedPauseListenHistory: Boolean = false
    @Volatile private var cachedStopOnTaskClear: Boolean = false

    /** Hides videos when either the explicit preference or Data Saver asks for it. */
    private val cachedHideVideoSongsEffective: Boolean
        get() = cachedHideVideoSongs || cachedDataSaver

    /** In-flight debounced persist of the queue; see [saveQueueToDisk]. */
    private var saveQueueJob: Job? = null

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())

    
    private var originalQueueSize: Int = 0

    private var consecutivePlaybackErr = 0
    private var retryJob: Job? = null
    private var retryCount = 0
    private var silenceSkipJob: Job? = null

    // Read and written from the player's loader thread, the preload coroutine and the main
    // thread. A HashMap here could throw ConcurrentModificationException mid-playback.
    private val songUrlCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    /** [songUrlCache] on disk, so a song replayed after a restart skips its resolve. */
    private lateinit var urlStore: StreamUrlStore

    /** Drops a cached URL from memory and from disk. */
    private fun forgetStreamUrl(key: String) {
        songUrlCache.remove(key)
        urlStore.remove(key)
    }

    /**
     * One resolution per song at a time. Without it, skipping to a song whose prefetch was
     * still resolving started a second full cascade (~600 ms) instead of waiting for the
     * first one, which was usually almost done. Runs in its own supervisor scope so a
     * cancelled waiter (a skipped preload, an abandoned load) doesn't cancel the work
     * another caller is waiting on.
     */
    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightResolves =
        java.util.concurrent.ConcurrentHashMap<String, Deferred<Result<YTPlayerUtils.PlaybackData>>>()

    
    private val bypassCacheForQualityChange = mutableSetOf<String>()

    
    private var currentMediaIdRetryCount = mutableMapOf<String, Int>()
    private val MAX_RETRY_PER_SONG = 3
    private val RETRY_DELAY_MS = 1000L

    
    private val recentlyFailedSongs = mutableSetOf<String>()
    private var failedSongsClearJob: Job? = null
    private var retryBudgetJob: Job? = null

    

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON -> scheduleDiscordPresenceSync()
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            val hasBluetooth = addedDevices?.any { CallAudioPolicy.isMusicHeadphones(it.type) } == true

            if (hasBluetooth && !isPhoneCallActive()) {
                if (dataStore.get(ResumeOnBluetoothConnectKey, false)) {
                    if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
                        player.play()
                    }
                }
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesRemoved(removedDevices)
            // The chosen output has gone (headphones off, cable out): back to Android's routing,
            // rather than holding on to an id that would never match a device again.
            val chosen = _preferredDeviceId.value ?: return
            if (removedDevices?.any { it.id == chosen } == true) setPreferredAudioDevice(null)
        }
    }

    override fun startForegroundService(service: Intent): android.content.ComponentName? {
        return try {
            super.startForegroundService(service)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                Timber.e(e, "Suppressed ForegroundServiceStartNotAllowedException in MusicService")
                null
            } else {
                throw e
            }
        }
    }

    /**
     * Fills the cached preference fields from one read of the whole preference file.
     *
     * Called before anything that depends on them, because the flow collectors that keep
     * them current below cannot have emitted yet at that point. One blocking read, once,
     * at service start replaces roughly a dozen scattered `dataStore.get` calls that each
     * did the same thing — and that kept doing it on every subsequent track change.
     */
    private fun seedCachedPreferences() {
        val prefs = runCatching {
            runBlocking(Dispatchers.IO) { dataStore.data.first() }
        }.getOrNull() ?: return
        fun <T> read(key: androidx.datastore.preferences.core.Preferences.Key<T>, default: T): T =
            runCatching { prefs[key] }.getOrNull() ?: default

        cachedRepeatMode = read(RepeatModeKey, REPEAT_MODE_OFF)
        cachedShuffleEnabled = read(ShuffleModeKey, false)
        cachedSkipSilence = read(SkipSilenceKey, false)
        cachedSkipSilenceInstant = read(SkipSilenceInstantKey, false)
        cachedAudioOffload = read(AudioOffload, false)
        cachedCrossfadeEnabled = read(CrossfadeEnabledKey, false)
        cachedPersistentQueue = read(PersistentQueueKey, true)
        cachedAutoLoadMore = read(AutoLoadMoreKey, true)
        cachedDisableLoadMoreWhenRepeatAll = read(DisableLoadMoreWhenRepeatAllKey, false)
        cachedHideExplicit = read(HideExplicitKey, false)
        cachedHideVideoSongs = read(HideVideoSongsKey, false)
        cachedDataSaver = read(com.shiny.music.constants.DataSaverEnabledKey, false)
        cachedShufflePlaylistFirst = read(ShufflePlaylistFirstKey, false)
        cachedPersistShuffleAcrossQueues = read(PersistentShuffleAcrossQueuesKey, false)
        cachedHistoryDuration = read(HistoryDuration, 30f)
        cachedPauseListenHistory = read(PauseListenHistoryKey, false)
        cachedStopOnTaskClear = read(StopMusicOnTaskClearKey, false)
        cachedPreloadLyrics = read(PreloadLyricsEnabledKey, true)
        cachedPreloadLimit = read(PreloadNextSongLimitKey, 1)
        cachedPreloadEnabled = if (cachedDataSaver) false else read(PreloadNextSongEnabledKey, true)
    }

    /** Keeps [seedCachedPreferences]'s fields current for the life of the service. */
    private fun observeCachedPreferences() {
        dataStore.data.distinctUntilChanged().collect(scope) { prefs ->
            fun <T> read(key: androidx.datastore.preferences.core.Preferences.Key<T>, default: T): T =
                runCatching { prefs[key] }.getOrNull() ?: default

            cachedSkipSilence = read(SkipSilenceKey, false)
            cachedSkipSilenceInstant = read(SkipSilenceInstantKey, false)
            cachedAudioOffload = read(AudioOffload, false)
            cachedCrossfadeEnabled = read(CrossfadeEnabledKey, false)
            cachedPersistentQueue = read(PersistentQueueKey, true)
            cachedAutoLoadMore = read(AutoLoadMoreKey, true)
            cachedDisableLoadMoreWhenRepeatAll = read(DisableLoadMoreWhenRepeatAllKey, false)
            cachedHideExplicit = read(HideExplicitKey, false)
            cachedHideVideoSongs = read(HideVideoSongsKey, false)
            cachedDataSaver = read(com.shiny.music.constants.DataSaverEnabledKey, false)
            cachedShufflePlaylistFirst = read(ShufflePlaylistFirstKey, false)
            cachedPersistShuffleAcrossQueues = read(PersistentShuffleAcrossQueuesKey, false)
            cachedHistoryDuration = read(HistoryDuration, 30f)
            cachedPauseListenHistory = read(PauseListenHistoryKey, false)
            cachedStopOnTaskClear = read(StopMusicOnTaskClearKey, false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        // Before the player is built or any listener can fire: everything downstream reads
        // these instead of blocking on DataStore from the main thread.
        seedCachedPreferences()
        observeCachedPreferences()
        // The sound effects every player's processor reads (Equaliser: 8D, reverb, speed…).
        com.shiny.music.eq.fx.SoundFxEngine.init(this)


        // Workaround for ForegroundServiceStartNotAllowedException
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                Timber.tag(TAG).e("ForegroundServiceStartNotAllowedException caught by MediaSessionService listener")
                reportException(Exception("ForegroundServiceStartNotAllowedException caught by MediaSessionService listener"))
            }
        })
        
        playerInitialized.value = false

        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.music_player),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            val pending = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.music_player))
                .setContentText("")
                .setSmallIcon(R.drawable.ic_launcher_nobg)  
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to create foreground notification")
            reportException(e)
        }

        setMediaNotificationProvider(
            ShinyNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            )
                .apply {
                    setSmallIcon(R.drawable.ic_launcher_nobg)
                },
        )
        player = createExoPlayer()
        _playerFlow.value = player
        player.addListener(this@MusicService)
        // Closes the benchmark's tap-to-audible trace section; see PlaybackTrace.
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioPositionAdvancing(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                playoutStartSystemTimeMs: Long,
            ) {
                com.shiny.music.utils.PlaybackTrace.audible()
                // Measurement only (debug builds plant Timber's tree): device-clock mark for skip timing.
                Timber.tag("fix403").d("audio.advancing item=${player.currentMediaItem?.mediaId}")
            }
        })
        sleepTimer = SleepTimer(scope, player)
        player.addListener(sleepTimer)
        listenBrainz = ListenBrainzScrobbler(this) { scope }.also { it.attach(player) }
        playerInitialized.value = true
        Timber.tag(TAG).d("Player successfully initialized")
        // What friends and a public profile see; sampled on the main thread, sent by the publisher.
        socialPresencePublisher.attach(::socialPlaybackSnapshot)
        together.state
            .map { it.room?.code }
            .distinctUntilChanged()
            .collect(scope) { socialPresencePublisher.notifyChanged() }

        // Slowed, Nightcore and the tempo dialog: the player's speed and pitch follow the sound
        // effects, on every player (a crossfade swaps in a new one), glided rather than jumped.
        combine(
            com.shiny.music.eq.fx.SoundFxEngine.settings,
            com.shiny.music.eq.fx.SoundFxEngine.original,
            com.shiny.music.eq.fx.SoundFxEngine.enabled,
            together.state.map { it.active }.distinctUntilChanged(),
            _playerFlow,
        ) { _, _, _, _, current -> current }
            .collectLatest(scope) { current -> if (current != null) glideToFxParameters(current) }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        abandonAudioFocus()
        setupAudioFocusRequest()

        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
        }
        mediaSession =
            MediaLibrarySession
                .Builder(this, player, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(this, scope))
                .build()
        player.repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_OFF)

        
        if (dataStore.get(RememberShuffleAndRepeatKey, true)) {
            player.shuffleModeEnabled = dataStore.get(ShuffleModeKey, false)
        }

        
        holdSelfController()

        connectivityManager = getSystemService()!!
        connectivityObserver = NetworkConnectivityObserver(this)

        val screenStateFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, screenStateFilter)

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        audioQuality = dataStore.get(AudioQualityKey).toEnum(com.shiny.music.constants.AudioQuality.OPUS)
        ipVersion = dataStore.get(IpVersionKey).toEnum(IpVersion.AUTO)
        com.shiny.music.utils.StreamHttp.ipVersion = ipVersion
        urlStore = StreamUrlStore(this)
        scope.launch(Dispatchers.IO) {
            val restored = urlStore.restore()
            // A resolve that finished meanwhile is newer than what was on disk.
            restored.forEach { (key, entry) -> songUrlCache.putIfAbsent(key, entry) }
            Timber.tag(TAG).d("Restored ${restored.size} stream URLs from disk")
        }
        PlaybackPrewarm.target = prewarmTarget
        playerVolume = MutableStateFlow(restorePlayerVolume(dataStore.get(PlayerVolumeKey, 1f)))



        
        // The EQ bands follow the Equaliser's master switch. A change is crossfaded inside the
        // processor, so nothing here has to seek: the old seek-to-apply put a gap in the audio
        // on every slider move.
        scope.launch {
            combine(eqProfileRepository.activeProfile, com.shiny.music.eq.fx.SoundFxEngine.enabled) { profile, on ->
                profile.takeIf { on }
            }.collect { profile ->
                if (profile != null) equalizerService.applyProfile(profile) else equalizerService.disable()
            }
        }

        scope.launch {
            connectivityObserver.networkStatus.collect { isConnected ->
                isNetworkConnected.value = isConnected
                if (isConnected && waitingForNetworkConnection.value) {
                    triggerRetry()
                }
                
                if (isConnected) scheduleDiscordPresenceSync()
            }
        }

        // Connecting, reconnecting or disconnecting Discord, and its settings, apply right away.
        scope.launch {
            dataStore.data
                .map { prefs ->
                    listOf(
                        runCatching { prefs[EnableDiscordRPCKey] }.getOrNull(),
                        runCatching { prefs[DiscordTokenKey] }.getOrNull(),
                        runCatching { prefs[DiscordShowWhenPausedKey] }.getOrNull(),
                        runCatching { prefs[DiscordListenAlongButtonKey] }.getOrNull(),
                    )
                }
                .distinctUntilChanged()
                .collect { scheduleDiscordPresenceSync(delayMs = 0L) }
        }

        
        var isFirstQualityEmit = true
        scope.launch {
            dataStore.data
                .map { 
                    val qualityStr = it.getOrNull(AudioQualityKey)
                    val quality = qualityStr?.let { value ->
                        com.shiny.music.constants.AudioQuality.entries.find { enumVal -> enumVal.name == value }
                    } ?: com.shiny.music.constants.AudioQuality.OPUS
                    val dataSaver = it[com.shiny.music.constants.DataSaverEnabledKey] ?: false
                    if (dataSaver) com.shiny.music.constants.AudioQuality.OPUS else quality
                }
                .distinctUntilChanged()
                .collect { newQuality ->
                    val oldQuality = audioQuality
                    audioQuality = newQuality

                    
                    if (isFirstQualityEmit) {
                        isFirstQualityEmit = false
                        Timber.tag("MusicService").i("QUALITY INIT: $newQuality")
                        return@collect
                    }

                    Timber.tag("MusicService").i("QUALITY CHANGED: $oldQuality -> $newQuality")

                    Timber.tag("MusicService").i("QUALITY CHANGED: $oldQuality -> $newQuality. Will take effect starting from the next song.")

                    // Clear cache for upcoming songs so they fetch the new quality, keeping the currently playing track's URL cache entry intact.
                    val currentMediaId = player.currentMediaItem?.mediaId
                    val currentCachedEntry = currentMediaId?.let { mediaId ->
                        songUrlCache.filter { it.key.startsWith("${mediaId}_") }
                    }
                    songUrlCache.clear()
                    urlStore.clear()
                    if (currentCachedEntry != null) {
                        songUrlCache.putAll(currentCachedEntry)
                    }

                    // Re-trigger prefetch to fetch the next songs in the new quality. Cancelled first, so
                    // a preload of the same songs still resolving the old quality isn't left running.
                    preloadJob?.cancel()
                    preloadUpcomingItems()
                }
        }

        
        scope.launch {
            dataStore.data
                .map { it.getOrNull(IpVersionKey)?.toEnum(IpVersion.AUTO) ?: IpVersion.AUTO }
                .distinctUntilChanged()
                .collect { newIpVersion ->
                    val oldIpVersion = ipVersion
                    ipVersion = newIpVersion
                    com.shiny.music.utils.StreamHttp.ipVersion = newIpVersion

                    if (isFirstQualityEmit) return@collect

                    Timber.tag("MusicService").i("IP VERSION CHANGED: $oldIpVersion -> $newIpVersion")

                    
                    val mediaId = player.currentMediaItem?.mediaId ?: return@collect
                    val currentPosition = player.currentPosition
                    val currentIndex = player.currentMediaItemIndex
                    val wasPlaying = player.isPlaying

                    
                    forgetStreamUrl("${mediaId}_${audioQuality.name}")

                    
                    player.stop()
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()
                    if (wasPlaying) {
                        player.play()
                    }
                }
        }

        combine(playerVolume, isMuted) { volume, muted ->
            if (muted) 0f else volume
        }.collectLatest(scope) {
            // Mid-blend the ramp applies the new level to both players on its next step;
            // writing it here would jump the incoming track to full for a moment.
            if (!isCrossfading.value) player.volume = it
        }



        currentSong.debounce(1000).collect(scope) { song ->
            updateNotification()
            updateWidgetUI(player.isPlaying)
        }

        // Lyrics for the song that is playing are fetched as soon as it starts, not when the
        // lyrics pane is opened. Waiting for the pane put the whole provider round trip in
        // front of the user every time; this way they are already in the database. Data
        // Saver still opts out.
        //
        // An existing row is kept, with one exception: hand-timed lyrics (LrcLib, KuGou…)
        // saved before those had to wait for the studio-timed sources ran late — by up to
        // 1.6 s on Hindi songs (see LyricsPick). Such a row gets one fresh look per run of
        // the service, and is replaced only by a synced file from a studio-timed source.
        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map {
                !(it[com.shiny.music.constants.DataSaverEnabledKey] ?: false)
            }.distinctUntilChanged(),
        ) { mediaMetadata, fetchAllowed ->
            mediaMetadata to fetchAllowed
        }.collectLatest(scope) { (mediaMetadata, showLyrics) ->
            if (!showLyrics || mediaMetadata == null) return@collectLatest
            val stored = database.lyrics(mediaMetadata.id).first()
            val recheck = stored != null &&
                stored.lyrics.isNotBlank() && stored.lyrics != LyricsEntity.LYRICS_NOT_FOUND &&
                !LyricsPick.isStudioTimed(stored.provider) &&
                lyricsRechecked.add(mediaMetadata.id)
            if (stored == null || recheck) {
                // Search results often carry no duration, and providers match on it —
                // LrcLib in particular returns nothing for duration = -1. The player knows
                // the real length by now, so hand that over instead.
                val metadataForLookup = if (mediaMetadata.duration > 0) mediaMetadata else {
                    val playerDuration = withContext(Dispatchers.Main) {
                        player.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET && it > 0 }
                    }
                    playerDuration?.let { mediaMetadata.copy(duration = (it / 1000).toInt()) } ?: mediaMetadata
                }
                val lyricsWithProvider = lyricsHelper.getLyrics(metadataForLookup)
                val found = lyricsWithProvider.lyrics.orEmpty()
                val better = LyricsPick.isStudioTimed(lyricsWithProvider.providerName) &&
                    LyricsPick.looksSynced(found) && LyricsPick.hasRealTimings(found) &&
                    LyricsPick.fitsRecording(found, metadataForLookup.duration)
                if (stored == null || better) {
                    database.query {
                        upsert(
                            LyricsEntity(
                                id = mediaMetadata.id,
                                lyrics = found,
                                provider = lyricsWithProvider.providerName,
                            ),
                        )
                    }
                }
            }
        }

        dataStore.data
            .map { (it.getOrNull(SkipSilenceKey) ?: false) to (it.getOrNull(SkipSilenceInstantKey) ?: false) }
            .distinctUntilChanged()
            .collectLatest(scope) { (skipSilence, instantSkip) ->
                player.skipSilenceEnabled = skipSilence
                secondaryPlayer?.skipSilenceEnabled = skipSilence

                val enableInstant = skipSilence && instantSkip
                instantSilenceSkipEnabled.value = enableInstant

                playerSilenceProcessors.values.forEach { processor ->
                    processor.instantModeEnabled = enableInstant
                    if (!enableInstant) {
                        processor.resetTracking()
                    }
                }

                if (!enableInstant) {
                    silenceSkipJob?.cancel()
                }
            }

        combine(
            currentFormat,
            dataStore.data
                .map { it.getOrNull(AudioNormalizationKey) ?: true }
                .distinctUntilChanged(),
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) { (format, normalizeAudio) -> setupLoudnessEnhancer()}

        combine(
            dataStore.data.map { it.getOrNull(AudioOffload) ?: false },
            dataStore.data.map { it.getOrNull(CrossfadeEnabledKey) ?: false },
            // Offloaded audio goes straight to the hardware, past every processor: while a
            // sound effect is on, it would simply not be heard.
            combine(com.shiny.music.eq.fx.SoundFxEngine.settings, com.shiny.music.eq.fx.SoundFxEngine.enabled) { fx, on ->
                on && fxNeedsProcessing(fx)
            }.distinctUntilChanged(),
        ) { offloadPref, crossfadeEnabled, fxOn ->
             if (crossfadeEnabled || fxOn) false else offloadPref
        }.distinctUntilChanged()
        .collectLatest(scope) { useOffload ->
             player.setOffloadEnabled(useOffload)
             secondaryPlayer?.setOffloadEnabled(useOffload)
        }



        combine(
            dataStore.data.map { prefs ->
                Triple(
                    prefs[CrossfadeEnabledKey] ?: false,
                    prefs[CrossfadeDurationKey] ?: 5f,
                    prefs[CrossfadeGaplessKey] ?: true
                )
            },
            together.state.map { it.active }.distinctUntilChanged()
        ) { (enabled, duration, gapless), inSession ->
            // A crossfade would put this phone a few seconds off everyone else's.
            Triple(enabled && !inSession, duration, gapless)
        }
            .distinctUntilChanged()
            .collect(scope) { (enabled, duration, gapless) ->
                crossfadeEnabled = enabled
                crossfadeDuration = duration * 1000f
                crossfadeGapless = gapless
                if (enabled) {
                    scheduleCrossfade()
                } else {
                    crossfadeTriggerJob?.cancel()
                    crossfadeTriggerJob = null
                }
            }

        // Keep cached preferences in sync so Player.Listener callbacks can read
        // them without blocking the main thread.
        dataStore.data
            .map { it.getOrNull(RepeatModeKey) ?: REPEAT_MODE_OFF }
            .distinctUntilChanged()
            .collect(scope) { cachedRepeatMode = it }

        dataStore.data
            .map { it.getOrNull(ShuffleModeKey) ?: false }
            .distinctUntilChanged()
            .collect(scope) { cachedShuffleEnabled = it }

        dataStore.data
            .map { 
                val preload = it.getOrNull(PreloadNextSongEnabledKey) ?: true
                val dataSaver = it[com.shiny.music.constants.DataSaverEnabledKey] ?: false
                if (dataSaver) false else preload
            }
            .distinctUntilChanged()
            .collect(scope) { cachedPreloadEnabled = it }

        dataStore.data
            .map { it.getOrNull(PreloadNextSongLimitKey) ?: 1 }
            .distinctUntilChanged()
            .collect(scope) { cachedPreloadLimit = it }

        dataStore.data
            .map { it.getOrNull(PreloadLyricsEnabledKey) ?: true }
            .distinctUntilChanged()
            .collect(scope) { cachedPreloadLyrics = it }


        // Restoring the saved queue used to read and Java-deserialise three files inline,
        // here, on the main thread — and this runs while the app is starting up, because
        // MainActivity binds the service with BIND_AUTO_CREATE. A long queue is a large
        // object graph, and deserialising it is the kind of work that shows up as the app
        // being unresponsive for the first moment after launch.
        //
        // The reads move to IO; only the parts that must touch the player stay on the
        // service's main-thread scope. Restoration was already asynchronous in effect —
        // it waited for `playerInitialized` and then a further second — so nothing that
        // depends on it observes a different order.
        if (cachedPersistentQueue) {
            scope.launch {
                data class Restored(
                    val queue: PersistQueue?,
                    val automix: PersistQueue?,
                    val playerState: PersistPlayerState?,
                    val corrupt: Boolean,
                )

                val restored = withContext(Dispatchers.IO) {
                    var corrupt = false
                    fun <T> readFile(name: String): T? {
                        val file = filesDir.resolve(name)
                        if (!file.exists()) return null
                        return runCatching {
                            file.inputStream().use { fis ->
                                ObjectInputStream(fis).use { ois ->
                                    @Suppress("UNCHECKED_CAST")
                                    ois.readObject() as T
                                }
                            }
                        }.onFailure { error ->
                            Timber.tag(TAG).w(error, "Failed to read $name")
                            corrupt = true
                        }.getOrNull()
                    }
                    Restored(
                        queue = readFile(PERSISTENT_QUEUE_FILE),
                        automix = readFile(PERSISTENT_AUTOMIX_FILE),
                        playerState = readFile(PERSISTENT_PLAYER_STATE_FILE),
                        corrupt = corrupt,
                    )
                }

                if (restored.corrupt) clearPersistedQueueFiles()

                restored.automix?.let { queue ->
                    runCatching { automixItems.value = queue.items.map { it.toMediaItem() } }
                        .onFailure { error ->
                            Timber.tag(TAG).w(error, "Failed to restore automix queue, clearing data")
                            clearPersistedQueueFiles()
                        }
                }

                // The queue the saved position belongs to, once it is back in the player.
                var restoredQueue: Queue? = null
                restored.queue?.let { queue ->
                    runCatching {
                        val persistedQueue = queue.toQueue()
                        playerInitialized.first { it }
                        // Anything started while the files were being read — a deep link, a
                        // song tapped the moment the app appeared — wins over the saved queue.
                        if (isActive && player.mediaItemCount == 0) {
                            // Unprepared: preparing resolved the stream and buffered audio for
                            // a song nobody had asked to hear yet, in the busiest seconds of
                            // the launch. The first request to play prepares it instead.
                            playQueue(queue = persistedQueue, playWhenReady = false, prepare = false)
                            restoredQueue = persistedQueue
                        }
                    }.onFailure { error ->
                        Timber.tag(TAG).w(error, "Failed to restore persisted queue, clearing data")
                        clearPersistedQueueFiles()
                    }
                }

                restored.playerState?.let { playerState ->
                    // Same wait as before: the queue has to be in the player before a
                    // position within it means anything.
                    delay(1000)
                    playerVolume.value = restorePlayerVolume(playerState.volume)
                    // Only into the queue it was saved with. A queue started during that second
                    // would otherwise take the old position and play a different song from the
                    // one just asked for.
                    if (restoredQueue != null && currentQueue === restoredQueue &&
                        playerState.currentMediaItemIndex < player.mediaItemCount
                    ) {
                        player.seekTo(playerState.currentMediaItemIndex, playerState.currentPosition)
                    }
                }
            }
        }

        
        scope.launch {
            while (isActive) {
                delay(30.seconds)
                if (cachedPersistentQueue) {
                    saveQueueToDisk()
                }
            }
        }

        
        scope.launch {
            while (isActive) {
                delay(10.seconds)
                if (cachedPersistentQueue && player.isPlaying) {
                    saveQueueToDisk()
                }
            }
        }
    }

    private fun createExoPlayer(): ExoPlayer {
        val eqProcessor = CustomEqualizerAudioProcessor()
        equalizerService.addAudioProcessor(eqProcessor)

        val silenceProcessor = SilenceDetectorAudioProcessor { handleLongSilenceDetected() }

        // From the cached preference values rather than a blocking DataStore read. This
        // runs on the main thread, and not only at startup: the crossfade path builds a
        // second player mid-playback, so a blocking read here stalled the UI thread right
        // as a transition began.
        silenceProcessor.instantModeEnabled = cachedSkipSilence && cachedSkipSilenceInstant

        val player = ExoPlayer.Builder(this)
            // Audio only: the playback thread sleeps until the audio sink actually needs data
            // instead of waking every 10 ms to check. Measured on the phone as the largest
            // share of plain playback's CPU was that thread's loop.
            .experimentalSetDynamicSchedulingEnabled(true)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setRenderersFactory(createRenderersFactory(eqProcessor, silenceProcessor))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(50_000, 50_000, 750, 2_000)
                    .build()
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .setDeviceVolumeControlEnabled(true)
            .build()

        playerSilenceProcessors[player] = silenceProcessor
        preferredAudioDevice()?.let { player.setPreferredAudioDevice(it) }

        player.apply {
            setOffloadEnabled(
                if (cachedCrossfadeEnabled ||
                    (com.shiny.music.eq.fx.SoundFxEngine.enabled.value && fxNeedsProcessing(com.shiny.music.eq.fx.SoundFxEngine.settings.value))
                ) false
                else cachedAudioOffload
            )
            skipSilenceEnabled = cachedSkipSilence
            addAnalyticsListener(
                PlaybackStatsListener(false, this@MusicService).also { playerStatsListeners[this] = it }
            )
        }

        // Deliberately NOT published to _playerFlow here. This factory also builds the
        // secondary player used for crossfades, which is created up to several seconds
        // before it becomes audible. Publishing it on creation made PlayerConnection move
        // all of its listeners onto that player immediately, so the UI's metadata, queue
        // index and artwork jumped to the *next* track while the current one was still
        // playing — and its position kept coming from the old player, so the lyrics on
        // screen belonged to one song and the playback position to another. The flow is
        // now updated only where the player actually changes: onCreate and the crossfade
        // swap.
        return player
    }

    private fun setupAudioFocusRequest() {
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { focusChange ->
                handleAudioFocusChange(focusChange)
            }
            .setAcceptsDelayedFocusGain(true)
            .build()
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {

            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
                hasAudioFocus = true

                if (wasPlayingBeforeAudioFocusLoss && !player.isPlaying && !reentrantFocusGain) {
                    reentrantFocusGain = true
                    scope.launch {
                        delay(300)
                        if (hasAudioFocus && wasPlayingBeforeAudioFocusLoss && !player.isPlaying) {
                            // Some dialers let go of focus between ringing and the call.
                            // Resuming in that gap put the music over the ringtone.
                            if (isPhoneCallActive()) {
                                holdPlaybackForCall()
                            } else {
                                player.play()
                                wasPlayingBeforeAudioFocusLoss = false
                            }
                        }
                        reentrantFocusGain = false
                    }
                }

                player.volume = if (isMuted.value) 0f else playerVolume.value
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = isPlayingOrAboutTo()
                if (wasPlayingBeforeAudioFocusLoss) {
                    player.pause()
                }
                abandonAudioFocus()
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = isPlayingOrAboutTo()
                if (wasPlayingBeforeAudioFocusLoss) {
                    player.pause()
                }
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.volume = if (isMuted.value) 0f else (playerVolume.value * 0.2f)
                }
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                hasAudioFocus = true
                player.volume = if (isMuted.value) 0f else playerVolume.value
                lastAudioFocusState = focusChange
            }
        }
    }

    private fun isPhoneCallActive(): Boolean = CallAudioPolicy.isPhoneCall(audioManager.mode)

    // A track still loading when a call comes in is not "playing" yet; left alone, it started
    // over the ringtone the moment it finished buffering.
    private fun isPlayingOrAboutTo(): Boolean =
        player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)

    /**
     * Pauses for a ringing or active call and resumes once it is over. The delayed focus
     * grant ([AudioManager.AUDIOFOCUS_GAIN] above) normally does the resuming; the watch
     * below covers a dialer that sets the call mode without taking focus, where no grant
     * ever comes.
     */
    private fun holdPlaybackForCall() {
        wasPlayingBeforeAudioFocusLoss = true
        if (player.playWhenReady) player.pause()
        if (callHoldJob?.isActive == true) return
        callHoldJob = scope.launch {
            // Polled: a mode listener needs API 31, and this loop only lives through a call.
            while (isPhoneCallActive()) delay(1_000)
            delay(300)
            // A focus request still waiting (delayed) resumes through AUDIOFOCUS_GAIN instead.
            if (wasPlayingBeforeAudioFocusLoss && !player.isPlaying && requestAudioFocus()) {
                wasPlayingBeforeAudioFocusLoss = false
                player.play()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        audioFocusRequest?.let { request ->
            val result = audioManager.requestAudioFocus(request)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            return hasAudioFocus
        }
        return false
    }

    private fun abandonAudioFocus() {
        if (hasAudioFocus) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
                hasAudioFocus = false
            }
        }
    }

    /**
     * Acquires a high-performance Wi-Fi lock when playback starts.
     *
     * WIFI_MODE_FULL_HIGH_PERF tells the system to keep the Wi-Fi chip fully
     * active with minimal latency — disabling power-saving sleep cycles.
     * This is called every time [player.isPlaying] becomes true.
     */
    private fun acquireWifiLock() {
        if (wifiLock == null) {
            val wifiManager = applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
            wifiLock = wifiManager?.createWifiLock(
                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "shiny_music:wifi_lock"
            )
        }
        if (wifiLock?.isHeld == false) {
            wifiLock?.acquire()
            Timber.tag(TAG).d("Wi-Fi lock acquired")
        }
    }

    /**
     * Releases the Wi-Fi lock when playback is paused, stopped, or the service is destroyed.
     *
     * Releasing the lock allows the device to return to normal Wi-Fi power-saving
     * behaviour, preserving battery when music is not playing.
     */
    private fun releaseWifiLock() {
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
            Timber.tag(TAG).d("Wi-Fi lock released")
        }
    }

    fun clearPersistedQueueFiles(): Boolean {
        val queueDeleted = runCatching {
            val file = filesDir.resolve(PERSISTENT_QUEUE_FILE)
            if (!file.exists()) true else file.delete()
        }.getOrDefault(false)

        runCatching {
            val file = filesDir.resolve(PERSISTENT_AUTOMIX_FILE)
            if (file.exists()) file.delete()
        }

        runCatching {
            val file = filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE)
            if (file.exists()) file.delete()
        }

        if (!queueDeleted) {
            runCatching {
                filesDir.resolve(PERSISTENT_QUEUE_FILE).writeBytes(byteArrayOf())
            }
        }

        return queueDeleted
    }

    fun hasAudioFocusForPlayback(): Boolean {
        return hasAudioFocus
    }

    private fun waitOnNetworkError() {
        if (waitingForNetworkConnection.value) return

        
        if (retryCount >= MAX_RETRY_COUNT) {
            Timber.tag(TAG).w("Max retry count ($MAX_RETRY_COUNT) reached, stopping playback")
            stopOnError()
            retryCount = 0
            return
        }

        waitingForNetworkConnection.value = true

        
        retryJob?.cancel()
        retryJob = scope.launch {
            
            val delayMs = minOf(3000L * (1 shl retryCount), 30000L)
            Timber.tag(TAG).d("Waiting ${delayMs}ms before retry attempt ${retryCount + 1}/$MAX_RETRY_COUNT")
            delay(delayMs)

            if (isNetworkConnected.value && waitingForNetworkConnection.value) {
                retryCount++
                triggerRetry()
            }
        }
    }

    private fun triggerRetry() {
        waitingForNetworkConnection.value = false
        retryJob?.cancel()

        if (player.currentMediaItem != null) {
            
            
            if (retryCount > 3) {
                Timber.tag(TAG).d("Retry count > 3, attempting to refresh stream URL")
                val currentPosition = player.currentPosition
                player.seekTo(player.currentMediaItemIndex, currentPosition)
            }
            player.prepare()
            
            
        }
    }

    /** Returns whether it moved on to another song; false means it paused instead. */
    private fun skipOnError(): Boolean {

        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()
            player.play()
            return true
        }

        player.pause()
        consecutivePlaybackErr = 0
        return false
    }

    private fun stopOnError() {
        player.pause()
    }

    private fun updateNotification() {
        mediaSession.setCustomLayout(
            listOf(
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            if (currentSong.value?.song?.liked ==
                                true
                            ) {
                                R.string.action_remove_like
                            } else {
                                R.string.action_like
                            },
                        ),
                    )
                    .setIconResId(if (currentSong.value?.song?.liked == true) R.drawable.ic_heart else R.drawable.ic_heart_outline)
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> throw IllegalStateException()
                            },
                        ),
                    ).setIconResId(
                        when (player.repeatMode) {
                            REPEAT_MODE_OFF -> R.drawable.repeat
                            REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> throw IllegalStateException()
                        },
                    ).setSessionCommand(CommandToggleRepeatMode)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setSessionCommand(CommandToggleShuffle)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.start_radio))
                    .setIconResId(R.drawable.radio)
                    .setSessionCommand(CommandToggleStartRadio)
                    .setEnabled(currentSong.value != null)
                    .build(),
            ),
        )
    }

    private suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null,
        isOfflinePlayback: Boolean = false
    ) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: if (isOfflinePlayback) -1 else (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId, null)
                .getOrNull()?.videoDetails)?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else {
                var updatedSong = song.song
                if (song.song.duration == -1) {
                    updatedSong = updatedSong.copy(duration = duration)
                }
                
                if (song.song.isVideo != mediaMetadata.isVideoSong) {
                    updatedSong = updatedSong.copy(isVideo = mediaMetadata.isVideoSong)
                }
                if (updatedSong != song.song) {
                    update(updatedSong)
                }
            }
        }
        if (!isOfflinePlayback && !database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint =
                YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                    ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
    }

    /**
     * @param prepare false leaves a queue without a preload item unprepared until something
     * asks to play it; see [restoredQueueAwaitingPrepare].
     */
    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
        prepare: Boolean = true,
        startPositionMs: Long = 0L,
    ) {
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main) + Job()


        if (!playerInitialized.value) {
            Timber.tag(TAG).w("playQueue called before player initialization, queuing request")
            scope.launch {
                playerInitialized.first { it }
                playQueue(queue, playWhenReady, prepare, startPositionMs)
            }
            return
        }

        restoredQueueAwaitingPrepare = false
        currentQueue = queue
        queueIsPreShuffled = (queue as? ListQueue)?.preShuffled == true
        val generation = ++playQueueGeneration
        autoplayFoundNothingFor = null
        queueTitle = null
        val persistShuffleAcrossQueues = cachedPersistShuffleAcrossQueues
        val previousShuffleEnabled = player.shuffleModeEnabled
        if (!persistShuffleAcrossQueues) {
            player.shuffleModeEnabled = false
        }
        
        originalQueueSize = 0
        if (queue.preloadItem != null) {
            // A start position comes from listen-along and timestamped links.
            player.setMediaItem(queue.preloadItem!!.toMediaItem(), startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        queueLoadsInFlight++
        scope.launch(SilentHandler) {
            try {
                val initialStatus =
                    withContext(Dispatchers.IO) {
                        queue.getInitialStatus()
                            .filterExplicit(cachedHideExplicit)
                            .filterVideoSongs(cachedHideVideoSongsEffective)
                    }
                // Another queue was started while this one loaded. Applying this one now would replace it:
                // a deep link was overwritten by the queue restored at launch this way.
                if (generation != playQueueGeneration) return@launch
                if (queue.preloadItem != null && player.playbackState == STATE_IDLE) return@launch
                if (initialStatus.title != null) {
                    queueTitle = initialStatus.title
                }
                if (initialStatus.items.isEmpty()) return@launch

                originalQueueSize = initialStatus.items.size
                if (queue.preloadItem != null) {
                    val safeIndex = initialStatus.mediaItemIndex.coerceIn(0, (initialStatus.items.size - 1).coerceAtLeast(0))
                    player.addMediaItems(
                        0,
                        initialStatus.items.subList(0, safeIndex)
                    )
                    player.addMediaItems(
                        initialStatus.items.subList(
                            (safeIndex + 1).coerceAtMost(initialStatus.items.size),
                            initialStatus.items.size
                        )
                    )
                } else {
                    val safeIndex = initialStatus.mediaItemIndex.coerceIn(0, (initialStatus.items.size - 1).coerceAtLeast(0))
                    player.setMediaItems(
                        initialStatus.items,
                        safeIndex,
                        initialStatus.position,
                    )
                    if (prepare) {
                        player.prepare()
                    } else {
                        restoredQueueAwaitingPrepare = true
                    }
                    player.playWhenReady = playWhenReady
                }


                if (player.shuffleModeEnabled) {
                    val shufflePlaylistFirst = cachedShufflePlaylistFirst
                    applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                }
            } finally {
                queueLoadsInFlight--
                // The queue is in place: one that starts on its last songs gets what follows now.
                if (isActive && generation == playQueueGeneration) continueWhenRunningOut()
            }
        }
    }

    /**
     * True while the player holds the queue restored at launch and has not been prepared
     * for it. Cleared by the first request to play, or by any new queue. Main thread only.
     */
    private var restoredQueueAwaitingPrepare = false

    /**
     * The current queue came already shuffled (Smart Shuffle), so shuffle mode plays it in
     * its own order instead of reshuffling it. Cleared by the next queue, a radio replacing
     * what is up next, or the user turning shuffle on. Main thread only.
     */
    private var queueIsPreShuffled = false

    /**
     * Counts [playQueue] calls, so a queue whose initial status arrives after a newer one started is
     * dropped. Not [currentQueue]: [ensureUpNext] swaps that for a radio mid-load. Main thread only.
     */
    private var playQueueGeneration = 0

    @Volatile private var fillingUpNext = false

    /**
     * [playQueue] calls whose songs have not arrived yet. A top-up that ran now would add its
     * radio before the queue's own songs, and both would then be queued. Main thread only.
     */
    private var queueLoadsInFlight = 0

    /** The song whose radio last had nothing new to add, so it is not asked for again. Main thread only. */
    private var autoplayFoundNothingFor: String? = null

    /** Songs still to play after the current one, in the order they will play (shuffle included), counted up to [limit]. */
    private fun upcomingInPlayOrder(limit: Int): Int {
        val timeline = player.currentTimeline
        var index = player.currentMediaItemIndex
        if (timeline.isEmpty || index !in 0 until timeline.windowCount) return 0
        var count = 0
        while (count < limit) {
            index = timeline.getNextWindowIndex(index, REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET) break
            count++
        }
        return count
    }

    /**
     * Autoplay without the Queue pane. [ensureUpNext] was only ever called by that pane, so
     * with it closed a playlist, an album or the liked songs simply stopped after the last
     * song. This runs whenever the song or the queue changes: once the queue is down to its
     * last few songs and has no more of its own, it lines up the current song's radio.
     * Not while paused (the queue restored at launch asks for nothing until it is played),
     * and not with a repeat mode on: that queue was asked to come round again.
     */
    private fun continueWhenRunningOut() {
        if (!playerInitialized.value || !cachedAutoLoadMore || together.isFollowing) return
        if (!player.playWhenReady || player.repeatMode != REPEAT_MODE_OFF) return
        if (currentQueue.hasNextPage()) return
        val current = player.currentMetadata?.id ?: return
        // A file on the phone has no radio, and offline there is nothing to ask.
        if (current.isLocalMediaId() || !isNetworkConnected.value) return
        if (current == autoplayFoundNothingFor) return
        if (upcomingInPlayOrder(AUTOPLAY_TOP_UP_BELOW) < AUTOPLAY_TOP_UP_BELOW) ensureUpNext()
    }

    /**
     * Keeps at least [minimum] songs queued after the current one, so Up Next always has
     * something to show. Pages the current queue first; once that has run dry — a single
     * song, the end of an album — tops up with the current song's radio, skipping
     * anything already queued. Only with Autoplay on, and never while a load is in flight.
     */
    fun ensureUpNext(minimum: Int = 15) {
        if (!playerInitialized.value || fillingUpNext || queueLoadsInFlight > 0) return
        // Following a Listen Together host: the room decides what comes next.
        if (together.isFollowing) return
        if (!cachedAutoLoadMore) return
        if (cachedDisableLoadMoreWhenRepeatAll && player.repeatMode == REPEAT_MODE_ALL) return
        val current = player.currentMetadata ?: return
        // In play order: with shuffle on, the song at the last index can be the first to play.
        fun upcoming() = upcomingInPlayOrder(minimum)
        if (upcoming() >= minimum) return
        fillingUpNext = true
        scope.launch(SilentHandler) {
            try {
                val hideExplicit = cachedHideExplicit
                val hideVideos = dataStore.get(HideVideoSongsKey, false) ||
                    dataStore.get(com.shiny.music.constants.DataSaverEnabledKey, false)
                fun queuedIds() = (0 until player.mediaItemCount).mapTo(HashSet()) { player.getMediaItemAt(it).mediaId }

                var pages = 0
                while (upcoming() < minimum && currentQueue.hasNextPage() && pages++ < 3) {
                    val items = withContext(Dispatchers.IO) {
                        currentQueue.nextPage().filterExplicit(hideExplicit).filterVideoSongs(hideVideos)
                    }
                    val queued = queuedIds()
                    val fresh = items.filter { it.mediaId !in queued }
                    if (fresh.isEmpty()) break
                    player.addMediaItems(fresh)
                }

                // A file on the phone has no radio, and offline there is nothing to ask.
                if (upcoming() < minimum && player.currentMetadata?.id == current.id &&
                    !current.id.isLocalMediaId() && isNetworkConnected.value
                ) {
                    val radio = YouTubeQueue(WatchEndpoint(videoId = current.id, playlistId = "RDAMVM${current.id}"), current)
                    val status = withContext(Dispatchers.IO) {
                        radio.getInitialStatus().filterExplicit(hideExplicit).filterVideoSongs(hideVideos)
                    }
                    val queued = queuedIds()
                    val fresh = status.items.filter { it.mediaId !in queued }.take(minimum + 5 - upcoming())
                    if (fresh.isNotEmpty()) {
                        player.addMediaItems(fresh)
                        // Later top-ups continue from the radio once the original queue is spent.
                        if (!currentQueue.hasNextPage()) currentQueue = radio
                    } else {
                        autoplayFoundNothingFor = current.id
                    }
                }

                if (player.shuffleModeEnabled) {
                    applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, cachedShufflePlaylistFirst)
                }
            } finally {
                fillingUpNext = false
            }
        }
    }

    fun startRadioSeamlessly() {
        
        if (!playerInitialized.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player initialization")
            return
        }

        val currentMediaMetadata = player.currentMetadata ?: return

        val currentIndex = player.currentMediaItemIndex
        val currentMediaId = currentMediaMetadata.id

        scope.launch(SilentHandler) {
            
            val radioQueue = YouTubeQueue(
                endpoint = WatchEndpoint(
                    videoId = currentMediaId
                )
            )

            try {
                val initialStatus = withContext(Dispatchers.IO) {
                    radioQueue.getInitialStatus()
                        .filterExplicit(cachedHideExplicit)
                        .filterVideoSongs(cachedHideVideoSongsEffective)
                }

                if (initialStatus.title != null) {
                    queueTitle = initialStatus.title
                }

                
                val radioItems = initialStatus.items.filter { item ->
                    item.mediaId != currentMediaId
                }

                if (radioItems.isNotEmpty()) {
                    val itemCount = player.mediaItemCount

                    if (itemCount > currentIndex + 1) {
                        player.removeMediaItems(currentIndex + 1, itemCount)
                    }
                    queueIsPreShuffled = false

                    player.addMediaItems(currentIndex + 1, radioItems)
                    if (player.shuffleModeEnabled) {
                        val shufflePlaylistFirst = cachedShufflePlaylistFirst
                        applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                    }
                }

                currentQueue = radioQueue
            } catch (e: Exception) {
                
                try {
                    val nextResult = withContext(Dispatchers.IO) {
                        YouTube.next(WatchEndpoint(videoId = currentMediaId)).getOrNull()
                    }
                    nextResult?.relatedEndpoint?.let { relatedEndpoint ->
                        val relatedPage = withContext(Dispatchers.IO) {
                            YouTube.related(relatedEndpoint).getOrNull()
                        }
                        relatedPage?.songs?.let { songs ->
                            val radioItems = songs
                                .filter { it.id != currentMediaId }
                                .map { it.toMediaItem() }
                                .filterExplicit(cachedHideExplicit)
                                .filterVideoSongs(cachedHideVideoSongsEffective)

                            if (radioItems.isNotEmpty()) {
                                val itemCount = player.mediaItemCount
                                if (itemCount > currentIndex + 1) {
                                    player.removeMediaItems(currentIndex + 1, itemCount)
                                }
                                player.addMediaItems(currentIndex + 1, radioItems)
                                if (player.shuffleModeEnabled) {
                                    val shufflePlaylistFirst = cachedShufflePlaylistFirst
                                    applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    
                }
            }
        }
    }

    fun getAutomixAlbum(albumId: String) {
        scope.launch(SilentHandler) {
            YouTube
                .album(albumId)
                .onSuccess {
                    getAutomix(it.album.playlistId)
                }
        }
    }

    fun getAutomix(playlistId: String) {
        if (dataStore.get(SimilarContent, true) &&
            !(cachedDisableLoadMoreWhenRepeatAll && player.repeatMode == REPEAT_MODE_ALL)) {
            scope.launch(SilentHandler) {
                try {
                    
                    YouTube.next(WatchEndpoint(playlistId = playlistId))
                        .onSuccess { firstResult ->
                            YouTube.next(WatchEndpoint(playlistId = firstResult.endpoint.playlistId))
                                .onSuccess { secondResult ->
                                    automixItems.value = secondResult.items.map { song ->
                                        song.toMediaItem()
                                    }
                                }
                                .onFailure {
                                    
                                    if (firstResult.items.isNotEmpty()) {
                                        automixItems.value = firstResult.items.map { song ->
                                            song.toMediaItem()
                                        }
                                    }
                                }
                        }
                        .onFailure {
                            
                            val currentSong = player.currentMetadata
                            if (currentSong != null) {
                                
                                YouTube.next(WatchEndpoint(
                                    videoId = currentSong.id
                                )).onSuccess { radioResult ->
                                    val filteredItems = radioResult.items
                                        .filter { it.id != currentSong.id }
                                        .map { it.toMediaItem() }
                                    if (filteredItems.isNotEmpty()) {
                                        automixItems.value = filteredItems
                                    }
                                }.onFailure {
                                    
                                    YouTube.next(WatchEndpoint(videoId = currentSong.id)).getOrNull()?.relatedEndpoint?.let { relatedEndpoint ->
                                        YouTube.related(relatedEndpoint).onSuccess { relatedPage ->
                                            val relatedItems = relatedPage.songs
                                                .filter { it.id != currentSong.id }
                                                .map { it.toMediaItem() }
                                            if (relatedItems.isNotEmpty()) {
                                                automixItems.value = relatedItems

                                            }
                                        }
                                    }
                                }
                            }
                        }
                } catch (_: Exception) {
                    
                }
            }
        }
    }

    fun addToQueueAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        addToQueue(listOf(item))
    }

    fun playNextAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        playNext(listOf(item))
    }

    fun clearAutomix() {
        automixItems.value = emptyList()
    }

    fun playNext(items: List<MediaItem>) {
        if (player.mediaItemCount == 0 || player.playbackState == STATE_IDLE) {
            player.setMediaItems(items)
            player.prepare()
            player.play()
            return
        }

        
        if (dataStore.get(PreventDuplicateTracksInQueueKey, false)) {
            val itemIds = items.map { it.mediaId }.toSet()
            val indicesToRemove = mutableListOf<Int>()
            val currentIndex = player.currentMediaItemIndex

            for (i in 0 until player.mediaItemCount) {
                if (i != currentIndex && player.getMediaItemAt(i).mediaId in itemIds) {
                    indicesToRemove.add(i)
                }
            }

            
            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
            }
        }

        val insertIndex = player.currentMediaItemIndex + 1
        val shuffleEnabled = player.shuffleModeEnabled

        player.addMediaItems(insertIndex, items)
        player.prepare()

        if (shuffleEnabled) {
            
            val timeline = player.currentTimeline
            if (!timeline.isEmpty) {
                val size = timeline.windowCount
                val currentIndex = player.currentMediaItemIndex

                
                val newIndices = (insertIndex until (insertIndex + items.size)).toSet()

                
                val orderAfter = mutableListOf<Int>()
                var idx = currentIndex
                while (true) {
                    idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, true)
                    if (idx == C.INDEX_UNSET) break
                    if (idx != currentIndex) orderAfter.add(idx)
                }

                val prevList = mutableListOf<Int>()
                var pIdx = currentIndex
                while (true) {
                    pIdx = timeline.getPreviousWindowIndex(pIdx, Player.REPEAT_MODE_OFF, true)
                    if (pIdx == C.INDEX_UNSET) break
                    if (pIdx != currentIndex) prevList.add(pIdx)
                }
                prevList.reverse() 

                val existingOrder = (prevList + orderAfter).filter { it != currentIndex && it !in newIndices }

                
                val nextBlock = (insertIndex until (insertIndex + items.size)).toList()
                val finalOrder = IntArray(size)
                var pos = 0
                finalOrder[pos++] = currentIndex
                nextBlock.forEach { if (it in 0 until size) finalOrder[pos++] = it }
                existingOrder.forEach { if (pos < size) finalOrder[pos++] = it }

                
                if (pos < size) {
                    for (i in 0 until size) {
                        if (!finalOrder.contains(i)) {
                            finalOrder[pos++] = i
                            if (pos == size) break
                        }
                    }
                }

                player.setShuffleOrder(DefaultShuffleOrder(finalOrder, System.currentTimeMillis()))
            }
        }
    }

    fun addToQueue(items: List<MediaItem>) {
        if (dataStore.get(PreventDuplicateTracksInQueueKey, false)) {
            val itemIds = items.map { it.mediaId }.toSet()
            val indicesToRemove = mutableListOf<Int>()
            val currentIndex = player.currentMediaItemIndex

            for (i in 0 until player.mediaItemCount) {
                if (i != currentIndex && player.getMediaItemAt(i).mediaId in itemIds) {
                    indicesToRemove.add(i)
                }
            }

            
            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
            }
        }

        player.addMediaItems(items)

        if (player.shuffleModeEnabled) {
            val shufflePlaylistFirst = cachedShufflePlaylistFirst
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
        player.prepare()
    }

    fun toggleLibrary() {
        scope.launch {
            val songToToggle = currentSong.first()
            songToToggle?.let {
                val isInLibrary = it.song.inLibrary != null
                val token = if (isInLibrary) it.song.libraryRemoveToken else it.song.libraryAddToken

                
                token?.let { feedbackToken ->
                    YouTube.feedback(listOf(feedbackToken))
                }

                
                database.query {
                    update(it.song.toggleLibrary())
                }
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    }

    fun toggleLike() {
        scope.launch {
            val songToToggle = currentSong.first()
            songToToggle?.let {
                val song = it.song.toggleLike()
                database.query {
                    update(song)
                    syncUtils.likeSong(song)

                    
                    if (dataStore.get(AutoDownloadOnLikeKey, false) && song.liked) {
                        
                        val downloadRequest =
                            androidx.media3.exoplayer.offline.DownloadRequest
                                .Builder(song.id, song.id.toUri())
                                .setCustomCacheKey(song.id)
                                .setData(song.title.toByteArray())
                                .build()
                        androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                            this@MusicService,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false
                        )
                    }
                }
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }

    private fun setupLoudnessEnhancer() {
        val audioSessionId = player.audioSessionId

        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            Timber.tag(TAG).w("setupLoudnessEnhancer: invalid audioSessionId ($audioSessionId), cannot create effect yet")
            return
        }

        
        if (loudnessEnhancer == null) {
            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                Timber.tag(TAG).d("LoudnessEnhancer created for sessionId=$audioSessionId")
            } catch (e: Exception) {
                reportException(e)
                loudnessEnhancer = null
                return
            }
        }

        scope.launch {
            try {
                val currentMediaId = withContext(Dispatchers.Main) {
                    player.currentMediaItem?.mediaId
                }

                val normalizeAudio = withContext(Dispatchers.IO) {
                    dataStore.data.map { it.getOrNull(AudioNormalizationKey) ?: true }.first()
                }

                if (normalizeAudio && currentMediaId != null) {
                    val format = withContext(Dispatchers.IO) {
                        database.format(currentMediaId).first()
                    }

                    Timber.tag(TAG).d("Audio normalization enabled: $normalizeAudio")
                    Timber.tag(TAG).d("Format loudnessDb: ${format?.loudnessDb}, perceptualLoudnessDb: ${format?.perceptualLoudnessDb}")

                    
                    val loudness = format?.loudnessDb ?: format?.perceptualLoudnessDb

                    withContext(Dispatchers.Main) {
                        if (loudness != null) {
                            val loudnessDb = loudness.toFloat()
                            val targetGain = (-loudnessDb * 100).toInt()
                            val clampedGain = targetGain.coerceIn(MIN_GAIN_MB, MAX_GAIN_MB)

                            Timber.tag(TAG).d("Calculated raw normalization gain: $targetGain mB (from loudness: $loudnessDb)")

                            try {
                                loudnessEnhancer?.setTargetGain(clampedGain)
                                loudnessEnhancer?.enabled = true
                                Timber.tag(TAG).i("LoudnessEnhancer gain applied: $clampedGain mB")
                            } catch (e: Exception) {
                                Timber.tag(TAG).e(e, "Failed to apply loudness enhancement")
                                reportException(e)
                                releaseLoudnessEnhancer()
                            }
                        } else {
                            loudnessEnhancer?.enabled = false
                            Timber.tag(TAG).w("Normalization enabled but no loudness data available - no normalization applied")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loudnessEnhancer?.enabled = false
                        Timber.tag(TAG).d("setupLoudnessEnhancer: normalization disabled or mediaId unavailable")
                    }
                }
            } catch (e: Exception) {
                reportException(e)
                releaseLoudnessEnhancer()
            }
        }
    }

    private fun releaseLoudnessEnhancer() {
        try {
            loudnessEnhancer?.release()
            Timber.tag(TAG).d("LoudnessEnhancer released")
        } catch (e: Exception) {
            reportException(e)
            Timber.tag(TAG).e(e, "Error releasing LoudnessEnhancer: ${e.message}")
        } finally {
            loudnessEnhancer = null
        }
    }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        setupLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        releaseLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            },
        )
    }

    private var previousMediaItemIndex = C.INDEX_UNSET

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        // Measurement only (debug builds plant Timber's tree): start mark for skip timing.
        Timber.tag("fix403").d("media.transition item=${mediaItem?.mediaId} reason=$reason")
        socialPresencePublisher.notifyChanged()

        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            if (cachedRepeatMode == REPEAT_MODE_ONE &&
                previousMediaItemIndex != C.INDEX_UNSET &&
                previousMediaItemIndex != player.currentMediaItemIndex) {

                player.seekTo(previousMediaItemIndex, 0)
            }
        }
        previousMediaItemIndex = player.currentMediaItemIndex

        lastPlaybackSpeed = -1.0f 

        preloadUpcomingItems()
        setupLoudnessEnhancer()

        discordUpdateJob?.cancel()


        
        
        
        if (cachedAutoLoadMore &&
            !together.isFollowing &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
            currentQueue.hasNextPage() &&
            !(cachedDisableLoadMoreWhenRepeatAll && player.repeatMode == REPEAT_MODE_ALL)
        ) {
            scope.launch(SilentHandler) {
                val mediaItems = withContext(Dispatchers.IO) {
                    currentQueue.nextPage()
                        .filterExplicit(cachedHideExplicit)
                        .filterVideoSongs(cachedHideVideoSongsEffective)
                }
                if (player.playbackState != STATE_IDLE && mediaItems.isNotEmpty()) {
                    player.addMediaItems(mediaItems)
                    if (player.shuffleModeEnabled) {
                        val shufflePlaylistFirst = cachedShufflePlaylistFirst
                        applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                    }
                }
            }
        }
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) continueWhenRunningOut()


        if (cachedPersistentQueue) {
            saveQueueToDisk()
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        
        if (playbackState == Player.STATE_ENDED) {
            if (cachedRepeatMode == REPEAT_MODE_ALL && player.mediaItemCount > 0) {
                player.seekTo(0, 0)
                player.prepare()
                player.play()
            } else {
                countListenAtQueueEnd()
            }
        }

        
        if (cachedPersistentQueue && !isSilenceSkipping) {
            saveQueueToDisk()
        }

        if (playbackState == Player.STATE_IDLE) shutDownIfStoppedWithoutUi()

        if (playbackState == Player.STATE_READY) {
            consecutivePlaybackErr = 0
            retryCount = 0
            waitingForNetworkConnection.value = false
            retryJob?.cancel()

            
            player.currentMediaItem?.mediaId?.let { mediaId ->
                if ((currentMediaIdRetryCount[mediaId] ?: 0) == 0) return@let
                retryBudgetJob?.cancel()
                retryBudgetJob = scope.launch {
                    delay(RETRY_BUDGET_REFILL_MS)
                    if (player.currentMediaItem?.mediaId == mediaId && player.playerError == null) {
                        resetRetryCount(mediaId)
                        Timber.tag(TAG).d("$mediaId played $RETRY_BUDGET_REFILL_MS ms without an error, reset retry count")
                    }
                }
            }
            scheduleCrossfade()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        
        // Whichever path asks to play the queue restored at launch — the app, the
        // notification, a headset button — this is where it finally gets prepared.
        if (playWhenReady && restoredQueueAwaitingPrepare) {
            restoredQueueAwaitingPrepare = false
            if (player.playbackState == STATE_IDLE && player.mediaItemCount > 0) {
                player.prepare()
            }
        }

        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            if (playWhenReady) {
                isPausedByVolumeMute = false
            }

            if (!playWhenReady && !isPausedByVolumeMute) {
                wasPlayingBeforeVolumeMute = false
            }
        }

        if (playWhenReady) {
            setupLoudnessEnhancer()
        }
        if (headless) watchHeadlessPause()
    }

    // The next-song preload waits for audio. Started at the play request, it resolved the next song
    // alongside the one just tapped, and on a cold resume began before it.
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            preloadUpcomingItems()
            // The queue restored at launch, played from its last song.
            continueWhenRunningOut()
        }
        socialPresencePublisher.notifyChanged()
    }

    // A queue started from a tap adds the songs after the first one about a second after that one
    // starts playing, when onIsPlayingChanged has already found nothing to preload.
    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && player.isPlaying) {
            preloadUpcomingItems()
        }
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                EVENT_TIMELINE_CHANGED,
                EVENT_POSITION_DISCONTINUITY
            )
        ) {
            scheduleCrossfade()
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                // Every way playback starts passes through here: a tap, the widget, a network
                // retry, a Bluetooth reconnect, Listen Together. During a call focus is only
                // granted once the call ends, and music that played on regardless was muted
                // by the system and kept the ringtone down with it, so the call went unheard.
                if (isPhoneCallActive() || !requestAudioFocus()) {
                    holdPlaybackForCall()
                } else {
                    openAudioEffectSession()
                }
            } else {
                closeAudioEffectSession()
            }
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
        }

        
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
            updateWidgetUI(player.isPlaying)
            if (player.isPlaying) {
                startWidgetUpdates()
                acquireWifiLock()
            } else {
                stopWidgetUpdates()
                releaseWifiLock()
            }
        }

        
        // Discord presence follows playback: song changes, pause and resume, seeks, buffering, stop.
        if (events.containsAny(
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                EVENT_POSITION_DISCONTINUITY,
            )
        ) {
            scheduleDiscordPresenceSync()
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        if (shuffleModeEnabled) {
            // Turning shuffle on is asking for a new shuffle, Smart Shuffle queue or not.
            queueIsPreShuffled = false
            if (player.mediaItemCount == 0) return

            val shufflePlaylistFirst = cachedShufflePlaylistFirst
            val currentIndex = player.currentMediaItemIndex
            val totalCount = player.mediaItemCount

            applyShuffleOrder(currentIndex, totalCount, shufflePlaylistFirst)
        }

        
        if (dataStore.get(RememberShuffleAndRepeatKey, true)) {
            scope.launch {
                dataStore.edit { settings ->
                    settings[ShuffleModeKey] = shuffleModeEnabled
                }
            }
        }

        
        if (cachedPersistentQueue) {
            saveQueueToDisk()
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }

        
        if (cachedPersistentQueue) {
            saveQueueToDisk()
        }
    }

    
    private fun applyShuffleOrder(
        currentIndex: Int,
        totalCount: Int,
        shufflePlaylistFirst: Boolean
    ) {
        if (totalCount == 0) return

        if (queueIsPreShuffled) {
            // The queue order is the shuffle: songs added later play after it, in turn.
            player.setShuffleOrder(DefaultShuffleOrder(IntArray(totalCount) { it }, System.currentTimeMillis()))
            return
        }

        if (shufflePlaylistFirst && originalQueueSize > 0 && originalQueueSize < totalCount) {
            
            val originalIndices = (0 until originalQueueSize).filter { it != currentIndex }.toMutableList()
            val addedIndices = (originalQueueSize until totalCount).filter { it != currentIndex }.toMutableList()

            originalIndices.shuffle()
            addedIndices.shuffle()

            val shuffledIndices = IntArray(totalCount)
            var pos = 0
            shuffledIndices[pos++] = currentIndex

            if (currentIndex < originalQueueSize) {
                originalIndices.forEach { shuffledIndices[pos++] = it }
                addedIndices.forEach { shuffledIndices[pos++] = it }
            } else {
                (0 until originalQueueSize).shuffled().forEach { shuffledIndices[pos++] = it }
                addedIndices.forEach { shuffledIndices[pos++] = it }
            }
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        } else {
            val shuffledIndices = IntArray(totalCount) { it }
            shuffledIndices.shuffle()
            
            val currentItemIndexInShuffled = shuffledIndices.indexOf(currentIndex)
            if (currentItemIndexInShuffled != -1) { 
                val temp = shuffledIndices[0]
                shuffledIndices[0] = shuffledIndices[currentItemIndexInShuffled]
                shuffledIndices[currentItemIndexInShuffled] = temp
            }
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        if (playbackParameters.speed != lastPlaybackSpeed) {
            lastPlaybackSpeed = playbackParameters.speed
            // The activity's timestamps assume normal speed: publish them again once the speed settles.
            scheduleDiscordPresenceSync(delayMs = 1_000L)
        }
    }

    
    private fun getHttpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }

    
    private fun isExpiredUrlError(error: PlaybackException): Boolean {
        val responseCode = getHttpResponseCode(error)
        return responseCode == 403
    }

    
    private fun isRangeNotSatisfiableError(error: PlaybackException): Boolean {
        val responseCode = getHttpResponseCode(error)
        return responseCode == 416
    }

    
    private fun isPageReloadError(error: PlaybackException): Boolean {
        val errorMessage = error.message?.lowercase() ?: ""
        val causeMessage = error.cause?.message?.lowercase() ?: ""
        val innerCauseMessage = error.cause?.cause?.message?.lowercase() ?: ""

        val reloadKeywords = listOf(
            "page needs to be reloaded",
            "pagina deve essere ricaricata",
            "la pagina deve essere ricaricata",
            "page must be reloaded",
            "reload",
            "ricaricata"
        )

        return reloadKeywords.any { keyword ->
            errorMessage.contains(keyword) ||
            causeMessage.contains(keyword) ||
            innerCauseMessage.contains(keyword)
        }
    }

    /**
     * The network failure anywhere in [error]'s cause chain. Checking only the top level missed
     * nearly all of them: an exception thrown while resolving reaches the player wrapped in
     * UnexpectedLoaderException with code IO_UNSPECIFIED, so an offline skip was treated as
     * cache corruption, retried three times, and skipped, working through the queue.
     */
    private fun networkCauseIn(error: PlaybackException): Throwable? {
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth++ < 12) {
            when {
                cause is java.net.UnknownHostException ||
                    cause is java.net.ConnectException ||
                    cause is java.net.NoRouteToHostException ||
                    cause is java.net.SocketTimeoutException -> return cause
                cause is PlaybackException && (
                    cause.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        cause.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                    ) -> return cause
            }
            cause = cause.cause
        }
        return null
    }

    /** Whether a [SecurityException] is anywhere in [error]'s cause chain: the system refused Shiny the file. */
    private fun securityCauseIn(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth++ < 12) {
            if (cause is SecurityException) return true
            cause = cause.cause
        }
        return false
    }

    private fun isNetworkRelatedError(error: PlaybackException): Boolean {

        if (isExpiredUrlError(error) || isRangeNotSatisfiableError(error) || isPageReloadError(error)) {
            return false
        }
        return error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ||
                networkCauseIn(error) != null
    }

    
    private fun isAudioRendererError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK
    }

    private fun isCacheOrStreamCorruptionError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        
        if (!playerInitialized.value) {
            Timber.tag(TAG).e(error, "Player error occurred but player not initialized")
            return
        }

        val mediaId = player.currentMediaItem?.mediaId
        Timber.tag(TAG).w(error, "Player error occurred for $mediaId: errorCode=${error.errorCode}, message=${error.message}")
        // A resolve failure was already recorded, with its cascade, by resolveStream.
        val resolveFailure = PlaybackHealth.resolveFailureIn(error)
        val isFallbackError = error.message?.contains("fallback", ignoreCase = true) == true
        if (!isFallbackError && resolveFailure == null) {
            reportException(error)
        }
        retryBudgetJob?.cancel()

        // A file on the phone that could not be opened. There is no stream to resolve again and
        // no cache to clear, so the recoveries below only failed the same way three more times,
        // song after song, until the queue gave up; offline, they waited for a connection the
        // file never needed.
        if (mediaId != null && mediaId.isLocalMediaId() &&
            (error as? ExoPlaybackException)?.type == ExoPlaybackException.TYPE_SOURCE
        ) {
            if (error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION || securityCauseIn(error)) {
                // Shiny may not read the phone's audio, so every file would fail the same way.
                // Nothing is skipped: playback rests on this song while the activity asks.
                Timber.tag(TAG).w("No access to the phone's audio for $mediaId, asking for it")
                player.pause()
                PlaybackHealth.record(PlaybackHealth.Event.GaveUp(error.errorCodeName, "noAudioAccess", "stopped"))
                PlaybackHealth.notify(
                    PlaybackHealth.Notice(
                        title = player.currentMediaItem?.mediaMetadata?.title?.toString(),
                        skipped = false,
                        unavailable = false,
                        needsAudioAccess = true,
                    )
                )
                return
            }
            Timber.tag(TAG).w("Local file $mediaId can't be read, not retrying")
            markSongAsFailed(mediaId)
            handleFinalFailure(error, why = "localFile", unavailable = true)
            return
        }

        // Offline: nothing will play until the connection is back, so wait for it rather than
        // spend this song's retries (and then the next song's) failing to resolve. A DNS failure
        // counts even when Android still reports a network; it is what offline looks like here.
        if (!isNetworkConnected.value || networkCauseIn(error) is java.net.UnknownHostException) {
            Timber.tag(TAG).d("No usable network, waiting for connection")
            PlaybackHealth.record(PlaybackHealth.Event.Recovering(error.errorCodeName, "waitForNetwork", retryCount))
            waitOnNetworkError()
            return
        }

        // YouTube refused this video on every client: another cascade would get the same answer.
        if (mediaId != null && resolveFailure?.permanent == true) {
            Timber.tag(TAG).w("Song $mediaId is unavailable (${resolveFailure.status}: ${resolveFailure.reason}), not retrying")
            markSongAsFailed(mediaId)
            handleFinalFailure(error, why = "unavailable", unavailable = true)
            return
        }

        if (mediaId != null && hasExceededRetryLimit(mediaId)) {
            Timber.tag(TAG).w("Song $mediaId has exceeded retry limit, skipping")
            markSongAsFailed(mediaId)
            handleFinalFailure(error, why = resolveFailure?.why ?: "retryLimit")
            return
        }

        // Cached bytes are dropped only by the handlers whose error means they may be bad
        // (416, corruption, reload). A dropped connection or an expired URL leaves them valid,
        // and clearing them made every network blip re-download the song from the start.
        when {
            resolveFailure != null -> {
                Timber.tag(TAG).d("Stream resolution failed (${resolveFailure.why}), resolving again")
                handleGenericIOError(mediaId, error, action = "reresolve", clearCache = false)
                return
            }
            isAudioRendererError(error) -> {
                Timber.tag(TAG).d("AudioTrack error detected (${error.errorCode}), performing safe recovery")
                handleAudioRendererError(mediaId)
                return
            }
            isRangeNotSatisfiableError(error) -> {
                Timber.tag(TAG).d("Range Not Satisfiable (416) detected, performing strict recovery")
                handleRangeNotSatisfiableError(mediaId)
                return
            }
            isExpiredUrlError(error) -> {
                Timber.tag(TAG).d("Expired URL (403) detected, refreshing stream URL")
                handleExpiredUrlError(mediaId, error, action = "refreshUrl")
                return
            }
            isPageReloadError(error) -> {
                Timber.tag(TAG).d("Page reload error detected, performing strict recovery")
                handlePageReloadError(mediaId)
                return
            }
            isNetworkRelatedError(error) -> {
                Timber.tag(TAG).d("Network-related error detected, retrying with the cache kept")
                handleGenericIOError(mediaId, error, action = "network", clearCache = false)
                return
            }
            // Last of the specific checks: IO_UNSPECIFIED, which it matches, is also the code
            // every wrapped loader exception arrives with, so anything above would land here.
            isCacheOrStreamCorruptionError(error) -> {
                Timber.tag(TAG).d("Cache or stream corruption detected, clearing cache and refreshing URL")
                if (mediaId != null) performAggressiveCacheClear(mediaId)
                handleExpiredUrlError(mediaId, error, action = "corruption")
                return
            }
        }

        
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
            Timber.tag(TAG).d("IO error detected (${error.errorCode}), attempting recovery")
            handleGenericIOError(mediaId, error, action = "io", clearCache = true)
            return
        }

        handleFinalFailure(error, why = "unrecoverable")
    }

    private fun recordRecovery(error: PlaybackException?, action: String, mediaId: String) {
        PlaybackHealth.record(
            PlaybackHealth.Event.Recovering(
                errorCode = error?.errorCodeName ?: "none",
                action = action,
                attempt = currentMediaIdRetryCount[mediaId] ?: 0,
            )
        )
    }

    
    private fun performAggressiveCacheClear(mediaId: String) {
        Timber.tag(TAG).d("Performing aggressive cache clear for $mediaId")

        
        forgetStreamUrl("${mediaId}_${audioQuality.name}")


        try {
            playerCache.removeResource(mediaId)
            Timber.tag(TAG).d("Cleared player cache for $mediaId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear player cache for $mediaId")
        }

        
        try {
            YTPlayerUtils.forceRefreshForVideo(mediaId)
            Timber.tag(TAG).d("Cleared decryption caches for $mediaId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear decryption caches for $mediaId")
        }
    }

    
    private fun hasExceededRetryLimit(mediaId: String): Boolean {
        val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
        return currentRetries >= MAX_RETRY_PER_SONG
    }

    
    private fun incrementRetryCount(mediaId: String) {
        val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
        currentMediaIdRetryCount[mediaId] = currentRetries + 1
        Timber.tag(TAG).d("Retry count for $mediaId: ${currentRetries + 1}/$MAX_RETRY_PER_SONG")
    }

    
    private fun resetRetryCount(mediaId: String) {
        currentMediaIdRetryCount.remove(mediaId)
        recentlyFailedSongs.remove(mediaId)
    }

    
    private fun markSongAsFailed(mediaId: String) {
        recentlyFailedSongs.add(mediaId)
        currentMediaIdRetryCount.remove(mediaId)

        
        failedSongsClearJob?.cancel()
        failedSongsClearJob = scope.launch {
            delay(5 * 60 * 1000L) 
            recentlyFailedSongs.clear()
            Timber.tag(TAG).d("Cleared recently failed songs list")
        }
    }

    
    private fun handleAudioRendererError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)
        recordRecovery(player.playerError, "audioTrack", mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            try {

                val wasPlaying = player.playWhenReady
                player.pause()
                Timber.tag(TAG).d("Paused playback due to AudioTrack error")

                
                
                delay(RETRY_DELAY_MS * 3) 

                
                if (!playerInitialized.value) {
                    Timber.tag(TAG).w("Player no longer initialized, aborting AudioTrack recovery")
                    return@launch
                }

                val currentIndex = player.currentMediaItemIndex
                if (currentIndex != C.INDEX_UNSET) {
                    
                    val currentPosition = player.currentPosition
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()

                    Timber.tag(TAG).d("Retrying playback for $mediaId after AudioTrack error")

                    
                    if (wasPlaying) {
                        delay(500) 
                        if (hasAudioFocus && playerInitialized.value) {
                            player.play()
                        }
                    }
                } else {
                    Timber.tag(TAG).w("Invalid media item index during AudioTrack recovery")
                    handleFinalFailure()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error during AudioTrack error recovery")
                handleFinalFailure()
            }
        }
    }

    
    private fun handleRangeNotSatisfiableError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)
        recordRecovery(player.playerError, "range416", mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {

            performAggressiveCacheClear(mediaId)



            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, 0)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after 416 error (from position 0)")
        }
    }

    
    private fun handlePageReloadError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)
        recordRecovery(player.playerError, "pageReload", mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            Timber.tag(TAG).d("Handling page reload error for $mediaId")

            
            performAggressiveCacheClear(mediaId)

            
            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after page reload error")
        }
    }

    
    private fun handleExpiredUrlError(mediaId: String?, error: PlaybackException?, action: String) {
        if (mediaId == null) {
            handleFinalFailure(error)
            return
        }

        incrementRetryCount(mediaId)
        recordRecovery(error, action, mediaId)

        
        forgetStreamUrl("${mediaId}_${audioQuality.name}")
        Timber.tag(TAG).d("Cleared cached URL for $mediaId")

        
        try {
            YTPlayerUtils.forceRefreshForVideo(mediaId)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear decryption caches")
        }

        retryJob?.cancel()
        retryJob = scope.launch {

            
            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after 403 error")
        }
    }

    
    private fun handleGenericIOError(
        mediaId: String?,
        error: PlaybackException?,
        action: String,
        clearCache: Boolean,
    ) {
        if (mediaId == null) {
            handleFinalFailure(error)
            return
        }

        incrementRetryCount(mediaId)
        recordRecovery(error, action, mediaId)
        val attempt = currentMediaIdRetryCount[mediaId] ?: 1

        retryJob?.cancel()
        retryJob = scope.launch {
            if (clearCache) {
                performAggressiveCacheClear(mediaId)
            } else {
                // Only the URL goes: the next load resolves afresh, the audio already on disk stays.
                forgetStreamUrl("${mediaId}_${audioQuality.name}")
            }
            // 0.5 s, 1 s, 2 s: an immediate retry of a flaky connection or a rate-limited
            // resolve mostly hits the same failure again.
            delay(RETRY_DELAY_MS / 2 * (1L shl (attempt - 1).coerceIn(0, 3)))


            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after generic IO error")
        }
    }

    
    /**
     * Gives up on the current song: skips it (the default) or pauses, and tells the listener
     * which song it was. Before, the Liquid UI showed nothing at all, so a failure looked like
     * the app had simply stopped.
     */
    private fun handleFinalFailure(
        error: PlaybackException? = player.playerError,
        why: String = "recoveryFailed",
        unavailable: Boolean = false,
    ) {
        val title = player.currentMediaItem?.mediaMetadata?.title?.toString()
        val skipped = if (dataStore.get(AutoSkipNextOnErrorKey, true)) {
            Timber.tag(TAG).d("All recovery attempts exhausted, auto-skipping to next track")
            skipOnError()
        } else {
            Timber.tag(TAG).d("All recovery attempts exhausted, stopping playback")
            stopOnError()
            false
        }
        PlaybackHealth.record(
            PlaybackHealth.Event.GaveUp(
                errorCode = error?.errorCodeName ?: "none",
                why = why,
                outcome = if (skipped) "skipped" else "stopped",
            )
        )
        PlaybackHealth.notify(PlaybackHealth.Notice(title, skipped = skipped, unavailable = unavailable))
    }

    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
        super.onDeviceVolumeChanged(volume, muted)
        val pauseOnMute = dataStore.get(PauseOnMute, false)

        if ((volume == 0 || muted) && pauseOnMute) {
            if (player.isPlaying) {
                wasPlayingBeforeVolumeMute = true
                isPausedByVolumeMute = true
                player.pause()
            }
        } else if (volume > 0 && !muted && pauseOnMute) {
            if (wasPlayingBeforeVolumeMute && !player.isPlaying) {
                wasPlayingBeforeVolumeMute = false
                isPausedByVolumeMute = false
                player.play()
            }
        }
    }

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource
                    .Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        // Shared with the resolver's probe so playback reuses its connection;
                        // HTTP/3 through Cronet on the Play build, OkHttp otherwise.
                        com.shiny.music.utils.StreamTransport.dataSourceFactory
                    )
            ).setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    
    private var isSilenceSkipping = false

    private fun handleLongSilenceDetected() {
        if (!instantSilenceSkipEnabled.value) return
        if (silenceSkipJob?.isActive == true) return

        silenceSkipJob = scope.launch {
            
            delay(200)
            performInstantSilenceSkip()
        }
    }

    private suspend fun performInstantSilenceSkip() {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        if (duration <= INSTANT_SILENCE_SKIP_STEP_MS) return

        isSilenceSkipping = true
        try {
            var hops = 0
            val silenceProcessor = playerSilenceProcessors[player] ?: return
            while (coroutineContext.isActive && instantSilenceSkipEnabled.value && silenceProcessor.isCurrentlySilent()) {
                val current = player.currentPosition
                val target = (current + INSTANT_SILENCE_SKIP_STEP_MS).coerceAtMost(duration - 500)

                if (target <= current) break

                
                silenceProcessor.resetTracking()
                player.seekTo(target)
                hops++

                if (hops >= 80 || target >= duration - 500) break

                delay(INSTANT_SILENCE_SKIP_SETTLE_MS)
            }
            if (hops > 0) {
                Timber.tag(TAG).d("Silence skip: jumped $hops times")
            }
        } finally {
            isSilenceSkipping = false
        }
    }

    private fun currentPresenceSong(): Song? {
        val mediaId = player.currentMediaItem?.mediaId ?: return null
        return runBlocking(Dispatchers.IO) { database.song(mediaId).firstOrNull() }
    }

    /** The current song for [com.shiny.music.social.SocialPresencePublisher]. Main thread. */
    private fun socialPlaybackSnapshot(): com.shiny.music.social.PlaybackSnapshot? {
        val metadata = player.currentMetadata ?: return null
        return com.shiny.music.social.PlaybackSnapshot(
            trackId = metadata.id,
            title = metadata.title,
            artist = metadata.artists.joinToString { it.name }.ifBlank { null },
            album = metadata.album?.title,
            thumbnail = metadata.thumbnailUrl,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                ?: metadata.duration.takeIf { it > 0 }?.times(1000L),
            positionMs = player.currentPosition,
            isPlaying = player.isPlaying,
            roomCode = together.state.value.room?.code,
        )
    }

    /** Publishes or clears Discord presence to match playback once the player has settled for [delayMs]. */
    private fun scheduleDiscordPresenceSync(delayMs: Long = 750L) {
        discordUpdateJob?.cancel()
        discordUpdateJob = scope.launch {
            delay(delayMs)
            syncDiscordPresence()
        }
    }

    /**
     * Shown while a track plays or buffers, and while paused if the user wants paused songs shown.
     * Cleared when playback is stopped or ended, when paused (by default), and when Discord is
     * turned off or disconnected. Main thread.
     */
    private suspend fun syncDiscordPresence() {
        val state = player.playbackState
        val hasTrack = player.currentMediaItem != null && state != Player.STATE_IDLE && state != Player.STATE_ENDED
        val paused = !player.playWhenReady
        val prefs = withContext(Dispatchers.IO) { dataStore.data.first() }
        val enabled = runCatching { prefs[EnableDiscordRPCKey] }.getOrNull() ?: true
        val connected = !runCatching { prefs[DiscordTokenKey] }.getOrNull().isNullOrBlank()
        val showWhenPaused = runCatching { prefs[DiscordShowWhenPausedKey] }.getOrNull() ?: false

        if (!enabled || !connected || !hasTrack || (paused && !showWhenPaused)) {
            DiscordPresenceManager.stop()
            return
        }
        if (!DiscordPresenceManager.restart()) {
            DiscordPresenceManager.start(
                context = this,
                songProvider = { currentPresenceSong() },
                positionProvider = { player.currentPosition },
                isPausedProvider = { !player.playWhenReady },
            )
        }
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(
            com.shiny.music.downloads.OfflineFileDataSource.Factory(
                this,
                downloadFolderStore,
                DefaultDataSource.Factory(this, createCacheDataSource()),
            )
        ) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")
            if (mediaId.isLocalMediaId()) {
                val localUri = android.net.Uri.parse(mediaId)
                try {
                    contentResolver.openFileDescriptor(localUri, "r")?.close()
                } catch (e: java.io.FileNotFoundException) {
                    throw androidx.media3.common.PlaybackException("Local file deleted", e, androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
                }
                // A SecurityException (the song is in the library, but this install may not read
                // the phone's audio) is left to propagate: as an IOException the loader would
                // retry it for three seconds first. onPlayerError finds it in the cause chain.
                return@Factory dataSpec
            }


            
            var shouldBypassCache = bypassCacheForQualityChange.contains(mediaId)
            var verifyCachedFormat = false
            
            val dbFormat = runBlocking(Dispatchers.IO) { database.format(mediaId).firstOrNull() }
            
            val cachedLength = androidx.media3.datasource.cache.ContentMetadata.getContentLength(downloadCache.getContentMetadata(mediaId))
                .takeIf { it != androidx.media3.common.C.LENGTH_UNSET.toLong() } ?: dbFormat?.contentLength ?: -1L
            val isFullyDownloaded = cachedLength > 0 && downloadCache.isCached(mediaId, 0, cachedLength)

            val activeQualityInCache = songUrlCache.keys.find { it.startsWith("${mediaId}_") }?.substringAfter("_")?.let {
                runCatching { com.shiny.music.constants.AudioQuality.valueOf(it) }.getOrNull()
            }
            val lockedQuality = activeQualityInCache ?: audioQuality


            if (!shouldBypassCache) {
                // A download in the listener's folder. If its file is gone or unreachable this
                // is null and the song streams like any other rather than failing.
                if (downloadFolderStore.readableDocument(mediaId) != null) {
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
                    return@Factory dataSpec.withUri(com.shiny.music.downloads.DownloadFolderStore.playbackUri(mediaId))
                }

                if (isFullyDownloaded) {
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
                    return@Factory dataSpec
                }

                val cachedUrl = usableCachedUrl("${mediaId}_${lockedQuality.name}", dbFormat?.contentLength)

                if (downloadCache.isCached(
                        mediaId,
                        dataSpec.position,
                        if (dataSpec.length >= 0) dataSpec.length else 1
                    )
                ) {
                    cachedUrl?.let {
                        scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
                        return@Factory dataSpec.withUri(it.toUri())
                    }
                    // Fall through to fetch real URL since it's only partially downloaded
                }

                if (playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)) {
                    cachedUrl?.let {
                        scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
                        return@Factory dataSpec.withUri(it.toUri())
                    }
                    // Cached bytes but no URL, as after every restart. Deleting them here re-downloaded
                    // the song on each cold resume; they are checked against the resolved format below.
                    verifyCachedFormat = true
                }

                cachedUrl?.let {
                        scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
                        return@Factory dataSpec.withUri(it.toUri())
                }
            } else {
                Timber.tag("MusicService").i("BYPASSING CACHE for $mediaId due to quality change")
            }

            Timber.tag("MusicService").i("FETCHING STREAM: $mediaId | quality=$lockedQuality")
            val playbackData = com.shiny.music.utils.PlaybackTrace.section(com.shiny.music.utils.PlaybackTrace.RESOLVE) {
                runBlocking(Dispatchers.IO) {
                    resolveStream(mediaId, lockedQuality, source = "play")
                }
            }.getOrElse { throwable ->
                when (throwable) {
                    is PlaybackException -> throw throwable
                    // Already an IOException carrying the cascade; onPlayerError reads it.
                    is com.shiny.music.utils.StreamResolveException -> throw throwable

                    is java.net.ConnectException, is java.net.UnknownHostException -> {
                        throw PlaybackException(
                            getString(R.string.error_no_internet),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                        )
                    }

                    is java.net.SocketTimeoutException -> {
                        throw PlaybackException(
                            getString(R.string.error_timeout),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        )
                    }

                    else -> throw PlaybackException(
                        getString(R.string.error_unknown),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }
            }

            val nonNullPlayback = requireNotNull(playbackData) {
                getString(R.string.error_unknown)
            }
            run {
                val format = nonNullPlayback.format
                if (verifyCachedFormat) {
                    // Reuse cached bytes only if they are this exact stream: another quality or client
                    // serves different bytes under the same key, and mixing them corrupts the audio.
                    // The cache records each stream's total length, which differs between formats.
                    val cachedLength = androidx.media3.datasource.cache.ContentMetadata.getContentLength(playerCache.getContentMetadata(mediaId))
                    if (cachedLength > 0 && cachedLength == format.contentLength) {
                        Timber.tag(TAG).d("Cached audio kept for $mediaId: length $cachedLength matches the resolved format")
                    } else {
                        Timber.tag(TAG).w("Ghost cache entry for $mediaId: cached length $cachedLength, resolved ${format.contentLength}, re-fetching")
                        playerCache.removeResource(mediaId)
                    }
                }
                
                var targetCacheKey = mediaId
                
                if (dbFormat != null && shouldBypassCache) {
                    Timber.tag(TAG).i("Bypassed cache. Using custom cache key to prevent intercept.")
                    targetCacheKey = "${mediaId}_diff"
                }

                val loudnessDb = nonNullPlayback.audioConfig?.loudnessDb
                val perceptualLoudnessDb = nonNullPlayback.audioConfig?.perceptualLoudnessDb

                Timber.tag(TAG).d("Storing format for $mediaId with loudnessDb: $loudnessDb, perceptualLoudnessDb: $perceptualLoudnessDb")
                if (loudnessDb == null && perceptualLoudnessDb == null) {
                    Timber.tag(TAG).w("No loudness data available from YouTube for video: $mediaId")
                }

                if (!isFullyDownloaded || targetCacheKey == mediaId) {
                    database.query {
                        upsert(
                            FormatEntity(
                                id = mediaId,
                                itag = format.itag,
                                mimeType = format.mimeType.split(";")[0],
                                codecs = format.mimeType.substringAfter("codecs=", "\"\"").substringBefore(";").removeSurrounding("\"").takeIf { it.isNotEmpty() } ?: "unknown",
                                bitrate = format.bitrate,
                                sampleRate = format.audioSampleRate,
                                contentLength = format.contentLength ?: 0L,
                                loudnessDb = loudnessDb,
                                perceptualLoudnessDb = perceptualLoudnessDb,
                                playbackUrl = nonNullPlayback.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                            )
                        )
                    }
                }
                scope.launch(Dispatchers.IO) { recoverSong(mediaId, nonNullPlayback) }

                
                if (bypassCacheForQualityChange.remove(mediaId)) {
                    Timber.tag("MusicService").d("Cleared bypass cache flag for $mediaId after fresh fetch")
                }

                val streamUrl = nonNullPlayback.streamUrl

                return@Factory dataSpec.buildUpon().setKey(targetCacheKey).setUri(streamUrl.toUri()).build()
            }
        }
    }

    private fun createMediaSourceFactory() =
        DefaultMediaSourceFactory(
            createDataSourceFactory(),
            androidx.media3.extractor.DefaultExtractorsFactory()
        )

    private fun createRenderersFactory(
        eqProcessor: CustomEqualizerAudioProcessor,
        silenceProcessor: SilenceDetectorAudioProcessor,
    ) =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink
                .Builder(this@MusicService)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    FxAudioProcessorChain(
                        arrayOf(
                            eqProcessor,
                            silenceProcessor,
                        ),
                        SilenceSkippingAudioProcessor(2_000_000, 20_000, 256),
                        SonicAudioProcessor(),
                        com.shiny.music.eq.fx.SoundFxAudioProcessor(),
                    ),
                ).build()
        }

    /** Whether the sound effects need the audio to go through the processors (and not be offloaded). */
    private fun fxNeedsProcessing(fx: com.shiny.music.eq.fx.SoundFxSettings): Boolean =
        fx.needsDsp || fx.speed != 1f || fx.playerPitch != 1f

    /**
     * The speed and pitch the sound effects ask for, or null while Listen Together sets them
     * (everyone in a session plays at the host's speed). A/B's "original" is normal speed.
     */
    private fun fxPlaybackParameters(): PlaybackParameters? {
        if (together.state.value.active) return null
        if (!com.shiny.music.eq.fx.SoundFxEngine.enabled.value) return PlaybackParameters.DEFAULT
        if (com.shiny.music.eq.fx.SoundFxEngine.original.value) return PlaybackParameters.DEFAULT
        val fx = com.shiny.music.eq.fx.SoundFxEngine.settings.value
        return PlaybackParameters(
            fx.speed.coerceIn(com.shiny.music.eq.fx.SoundFxSettings.MIN_SPEED, com.shiny.music.eq.fx.SoundFxSettings.MAX_SPEED),
            fx.playerPitch.coerceIn(0.25f, 4f),
        )
    }

    /**
     * Moves [target]'s speed and pitch to the effects' values. A big change (0.80× → 0.60×)
     * goes in a few short steps rather than one jump; each step is a clean handover in the
     * player's time-stretcher, and the position the UI shows stays right throughout.
     */
    private suspend fun glideToFxParameters(target: ExoPlayer) {
        val wanted = fxPlaybackParameters() ?: return
        val from = try { target.playbackParameters } catch (e: Exception) { return }
        if (from == wanted) return
        val delta = maxOf(kotlin.math.abs(wanted.speed - from.speed), kotlin.math.abs(wanted.pitch - from.pitch))
        val steps = if (target.isPlaying) (delta / 0.05f).toInt().coerceIn(1, 6) else 1
        for (step in 1..steps) {
            val t = step.toFloat() / steps
            target.playbackParameters = PlaybackParameters(
                from.speed + (wanted.speed - from.speed) * t,
                from.pitch + (wanted.pitch - from.pitch) * t,
            )
            if (step < steps) delay(70)
        }
    }

    /** Counts the song a queue just finished on, without waiting for its session to close. */
    private fun countListenAtQueueEnd() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val stats = playerStatsListeners[player]?.playbackStats ?: return
        val playTimeMs = stats.totalPlayTimeMs
        if (playTimeMs < cachedHistoryDuration * 1000f || mediaId in listenCountedAtEnd) return
        listenCountedAtEnd[mediaId] = playTimeMs
        recordListen(mediaId, playTimeMs)
    }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        // Part of this session may already be counted, when its queue ended. Only what was
        // played after that (the song started again, say) is a new listen.
        val alreadyCounted = listenCountedAtEnd.remove(mediaItem.mediaId) ?: 0L
        recordListen(mediaItem.mediaId, playbackStats.totalPlayTimeMs - alreadyCounted)
    }

    private fun recordListen(mediaId: String, playTimeMs: Long) {
        val historyDurationMs = cachedHistoryDuration * 1000f

        if (playTimeMs >= historyDurationMs &&
            !cachedPauseListenHistory
        ) {
            database.query {
                incrementTotalPlayTime(mediaId, playTimeMs)
                // The per-month play count. This table was defined, queried (the "N plays"
                // line on Home reads it through getLifetimePlayCount) and never written,
                // so every count in the app read zero.
                //
                // Counted here rather than anywhere closer to the UI on purpose. This
                // callback comes from Media3's PlaybackStatsListener and fires once per
                // completed playback session with the time actually spent playing —
                // paused time excluded, a seek within the track not a new session, and
                // nothing at all emitted for a recomposition or a screen being reopened.
                // A song only counts once it has genuinely been listened to for the
                // history threshold, which is the same bar the listen history uses.
                try {
                    incrementPlayCount(mediaId)
                } catch (_: SQLException) {
                }
                try {
                    insert(
                        Event(
                            songId = mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playTimeMs,
                        ),
                    )
                } catch (_: SQLException) {
                }
            }
        }

        // Local files have no YouTube history to add to; asking only failed.
        if (playTimeMs >= historyDurationMs && !mediaId.isLocalMediaId()) {
            CoroutineScope(Dispatchers.IO).launch {
                val playbackUrl = database.format(mediaId).first()?.playbackUrl
                    ?: YTPlayerUtils.playerResponseForMetadata(mediaId, null)
                        .getOrNull()?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                playbackUrl?.let {
                    YouTube.registerPlayback(null, playbackUrl)
                        .onFailure {
                            reportException(it)
                        }
                }
            }
        }
    }

    /**
     * Snapshots the queue on the main thread — which is the only thread that may read the
     * player — and writes it on IO, coalescing bursts.
     *
     * This is called from `onMediaItemTransition`, `onPlaybackStateChanged` and several
     * queue mutations, so a single track change used to run three Java-serialisation
     * passes over the entire queue and three file writes *on the main thread*, more than
     * once. On a long queue that is tens of milliseconds of dropped frames at exactly the
     * moment the user is watching the artwork and title change — and it landed on the
     * same thread that must service the audio renderer's callbacks.
     *
     * The snapshot must stay synchronous (ExoPlayer is thread-confined); only the
     * serialisation and the writes move off. [saveQueueJob] collapses a burst of calls
     * into one write, and each new call supersedes an unfinished one because the newer
     * snapshot is strictly more current.
     */
    private fun saveQueueToDisk() {
        val snapshot = queueSnapshot() ?: return
        saveQueueJob?.cancel()
        saveQueueJob = scope.launch(Dispatchers.IO) {
            // Lets a burst of transitions settle into one write instead of three-per-event.
            delay(QUEUE_SAVE_DEBOUNCE_MS)
            writeQueueSnapshot(snapshot)
        }
    }

    /**
     * Reads the player's current state on the calling (main) thread. Null when there is
     * nothing to persist, in which case the persisted files have been cleared instead.
     */
    private fun queueSnapshot(): QueueSnapshot? {
        if (player.mediaItemCount == 0) {
            Timber.tag(TAG).d("Clearing persisted queue - no media items")
            saveQueueJob?.cancel()
            clearPersistedQueueFiles()
            return null
        }

        return try {
            QueueSnapshot(
                queue = currentQueue.toPersistQueue(
                    title = queueTitle,
                    items = player.mediaItems.mapNotNull { it.metadata },
                    mediaItemIndex = player.currentMediaItemIndex,
                    position = player.currentPosition,
                ).let { queue ->
                    // The saved items are in the Smart Shuffle order; this keeps shuffle mode
                    // from reshuffling them after a restart.
                    if (queueIsPreShuffled && queue.queueType is QueueType.LIST) {
                        queue.copy(queueData = QueueData.PreShuffled)
                    } else {
                        queue
                    }
                },
                automix = PersistQueue(
                    title = "automix",
                    items = automixItems.value.mapNotNull { it.metadata },
                    mediaItemIndex = 0,
                    position = 0,
                ),
                playerState = PersistPlayerState(
                    playWhenReady = player.playWhenReady,
                    repeatMode = player.repeatMode,
                    shuffleModeEnabled = player.shuffleModeEnabled,
                    volume = restorePlayerVolume(playerVolume.value),
                    currentPosition = player.currentPosition,
                    currentMediaItemIndex = player.currentMediaItemIndex,
                    playbackState = player.playbackState,
                ),
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error snapshotting queue for save")
            reportException(e)
            null
        }
    }

    private data class QueueSnapshot(
        val queue: PersistQueue,
        val automix: PersistQueue,
        val playerState: PersistPlayerState,
    )

    /** Blocking write of a snapshot; call from IO, or from onDestroy where there is no later. */
    private fun writeQueueSnapshot(snapshot: QueueSnapshot) {
        try {
            val persistQueue = snapshot.queue
            val persistAutomix = snapshot.automix
            val persistPlayerState = snapshot.playerState

            runCatching {
                filesDir.resolve(PERSISTENT_QUEUE_FILE).outputStream().use { fos ->
                    ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(persistQueue)
                    }
                }
                Timber.tag(TAG).d("Queue saved successfully")
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to save queue")
                reportException(it)
            }

            runCatching {
            filesDir.resolve(PERSISTENT_AUTOMIX_FILE).outputStream().use { fos ->
                ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(persistAutomix)
                    }
                }
                Timber.tag(TAG).d("Automix saved successfully")
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to save automix")
                reportException(it)
            }

            runCatching {
                filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).outputStream().use { fos ->
                    ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(persistPlayerState)
                    }
                }
                Timber.tag(TAG).d("Player state saved successfully")
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to save player state")
                reportException(it)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during queue save operation")
            reportException(e)
        }
    }

    override fun onDestroy() {
        isRunning = false
        if (PlaybackPrewarm.target === prewarmTarget) PlaybackPrewarm.target = null
        likelyJob?.cancel()
        releasePrebuffered()

        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            
        }
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        if (cachedPersistentQueue) {
            // Synchronous here, unlike everywhere else: the scope is about to be
            // cancelled, so a debounced write would simply never happen.
            saveQueueJob?.cancel()
            queueSnapshot()?.let { writeQueueSnapshot(it) }
        }
        DiscordPresenceManager.stop()
        socialPresencePublisher.detach()
        connectivityObserver.unregister()
        releaseWifiLock()
        abandonAudioFocus()
        releaseLoudnessEnhancer()
        try {
            fadingLoudnessEnhancer?.release()
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Failed releasing fading enhancer on destroy")
        } finally {
            fadingLoudnessEnhancer = null
        }
        mediaSession.release()
        player.removeListener(this)
        player.removeListener(sleepTimer)
        listenBrainz.release()
        playerSilenceProcessors.remove(player)
        playerStatsListeners.remove(player)
        
        
        
        player.release()
        discordUpdateJob?.cancel()
        resolveScope.cancel()
        releaseSelfController()
        super.onDestroy()
        // A notification posted outside the foreground state is not removed with the service.
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    /**
     * The controller this service connects to itself with. It is a binding like any other, so
     * for as long as it is held the service outlives the app's window: paused in the background,
     * the player and its queue stay as they were. It is also why [stopSelf] alone never ends the
     * service, so [shutDownWithoutUi] lets go of it first.
     */
    private var selfController: ListenableFuture<MediaController>? = null

    private fun holdSelfController() {
        if (selfController != null) return
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        selfController = MediaController.Builder(this, sessionToken).buildAsync()
    }

    private fun releaseSelfController() {
        selfController?.let { MediaController.releaseFuture(it) }
        selfController = null
    }

    /** The app's window was swiped away while a song played on: only the notification is left. */
    private var headless = false

    /** Guards [shutDownWithoutUi] against the player events it causes itself. */
    private var shuttingDown = false

    /** Ends a windowless service that has been left paused; see [watchHeadlessPause]. */
    private var headlessPauseJob: Job? = null

    /**
     * The app's window is open (again). Called by the activity each time it binds, which is
     * also how an instance that was on its way out is put back to work.
     */
    fun onUiAttached() {
        headless = false
        shuttingDown = false
        headlessPauseJob?.cancel()
        setForegroundServiceTimeoutMs(DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS)
        holdSelfController()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Not passed to Media3: its version pauses and calls stopSelf(), which neither ends a
        // service that is bound to itself nor keeps the notification from coming back.
        if (!::player.isInitialized) {
            stopSelf()
            return
        }
        val playing = player.isPlaying ||
            (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        if (playing && !cachedStopOnTaskClear) {
            // The song plays on behind its notification. With no window to come back to, that
            // notification is all there is: once paused it can be swiped away at once (Media3
            // would hold it in place for ten minutes), and swiping it away ends the service.
            headless = true
            setForegroundServiceTimeoutMs(0)
            Timber.tag(TAG).d("App task removed while playing; playback continues")
            return
        }
        Timber.tag(TAG).d("App task removed (playing=$playing); stopping service")
        shutDownWithoutUi()
    }

    /**
     * Ends playback and the service after the app's window has gone: nothing of Shiny is left in
     * the notification shade or the system's media controls.
     */
    private fun shutDownWithoutUi() {
        if (shuttingDown) return
        shuttingDown = true

        // Media3 keeps a notification up for any session it manages whose player still has a
        // queue, playing or not, and posts it again after it has been cancelled. A session that
        // is no longer the service's has none, so this is what takes it down for good; a
        // controller that connects later (the app, Android Auto) hands the session back.
        if (isSessionAdded(mediaSession)) removeSession(mediaSession)

        player.pause()
        player.stop()
        // The queue is kept. If this instance is bound again before it is destroyed, the next
        // play request prepares it, as it does a queue restored at launch.
        restoredQueueAwaitingPrepare = true

        stopForeground(STOP_FOREGROUND_REMOVE)
        val notifications = getSystemService(NotificationManager::class.java)
        notifications?.cancel(NOTIFICATION_ID)
        // Once more behind anything already queued on this thread that would post it again.
        Handler(Looper.getMainLooper()).post { notifications?.cancel(NOTIFICATION_ID) }

        // With its own binding gone the service can be destroyed, and onDestroy() releases the
        // session, which is what removes Shiny from the system's media controls.
        releaseSelfController()
        stopSelf()
    }

    /**
     * With the app's window gone, a paused song can still be resumed from its notification, and
     * on Android versions that do not let a media notification be swiped away that notification
     * would otherwise sit there for good. So a pause the listener chose is given as long as
     * Media3 itself keeps a paused service ready, and then the service ends. A pause that is
     * about to undo itself (a phone call, a Listen Together room) is left alone.
     */
    private fun watchHeadlessPause() {
        headlessPauseJob?.cancel()
        if (!headless || shuttingDown || player.playWhenReady) return
        headlessPauseJob = scope.launch {
            delay(DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS)
            val resumesByItself = wasPlayingBeforeAudioFocusLoss ||
                callHoldJob?.isActive == true ||
                together.state.value.active
            if (headless && !shuttingDown && !player.playWhenReady && !resumesByItself) {
                Timber.tag(TAG).d("Left paused with no app task; stopping service")
                shutDownWithoutUi()
            }
        }
    }

    /**
     * With the app's window gone, a player left stopped is the listener swiping the notification
     * away (Media3 answers that with stop()), or a sleep timer running out: nothing is left to
     * come back to. Checked a moment later, because a stream is also re-prepared through
     * stop(), and a failed one is retried from idle.
     */
    private fun shutDownIfStoppedWithoutUi() {
        if (!headless || shuttingDown) return
        scope.launch {
            delay(HEADLESS_STOP_GRACE_MS)
            if (headless && !shuttingDown &&
                player.playbackState == Player.STATE_IDLE &&
                player.playerError == null &&
                !player.playWhenReady
            ) {
                Timber.tag(TAG).d("Stopped with no app task; stopping service")
                shutDownWithoutUi()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MusicWidgetReceiver.ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_LIKE -> {
                toggleLike()
            }
            MusicWidgetReceiver.ACTION_NEXT -> {
                player.seekToNext()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_PREVIOUS -> {
                player.seekToPrevious()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_UPDATE_WIDGET -> {
                updateWidgetUI(player.isPlaying)
            }
            "com.shiny.music.ACTION_CLEAR_SONG_CACHE" -> {
                val songId = intent.getStringExtra("songId")
                if (songId != null) {
                    songUrlCache.keys.filter { it.startsWith("${songId}_") }.forEach {
                        forgetStreamUrl(it)
                    }
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    
    private fun updateWidgetUI(isPlaying: Boolean) {
        scope.launch {
            try {
                val songData = currentSong.value
                val song = songData?.song
                val songTitle = song?.title ?: getString(R.string.no_song_playing)
                val artistName = songData?.artists?.joinToString(", ") { it.name } ?: getString(R.string.tap_to_open)
                val isLiked = songData?.song?.liked == true

                widgetManager.updateWidgets(
                    title = songTitle,
                    artist = artistName,
                    artworkUri = song?.thumbnailUrl,
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    duration = if (player.duration != C.TIME_UNSET) player.duration else 0,
                    currentPosition = player.currentPosition
                )
            } catch (e: Exception) {
                
            }
        }
    }

    private var widgetUpdateJob: Job? = null

    private fun startWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    updateWidgetUI(true)
                }
                delay(200)
            }
        }
    }

    private fun stopWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = null
    }

    private fun shareSong() {
        val songData = currentSong.value
        val songId = songData?.song?.id ?: return

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${com.shiny.music.social.ShinyLinks.WEB_BASE}/watch?v=$songId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(Intent.createChooser(shareIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    


    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            scheduleCrossfade()
            // Friends listening along start from the new position.
            socialPresencePublisher.notifyChanged()
        }
    }

    /**
     * The level both players are mixed at: the user's volume, muted or ducked as the rest
     * of the service would set it. Read on every ramp step, so a volume change, a mute or a
     * duck during a blend applies to the blend instead of being lost when it ends.
     */
    private fun blendTargetVolume(): Float = when {
        isMuted.value -> 0f
        lastAudioFocusState == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> playerVolume.value * 0.2f
        else -> playerVolume.value
    }

    /**
     * How long a blend out of a track of [trackDurationMs] may last: the chosen crossfade,
     * but never more than a third of the track, so a short song is not mostly fade. Null
     * when that leaves too little to be worth a blend (or the length is unknown).
     */
    private fun effectiveOverlapMs(trackDurationMs: Long): Long? {
        if (trackDurationMs == C.TIME_UNSET || trackDurationMs <= 0) return null
        val overlap = minOf(crossfadeDuration.toLong(), trackDurationMs / 3)
        return overlap.takeIf { it >= MIN_BLEND_MS }
    }

    /**
     * Waits for the prebuffered incoming track to be able to play, building it now if a
     * queue change threw the earlier one away. Gives up — returning false, so the track
     * change happens the ordinary way — if the incoming track fails, or if the outgoing one
     * is about to end before there is time for a real blend. A blend is never started into
     * a player that cannot yet make sound; that is the silence this guards against.
     */
    private suspend fun awaitIncomingReady(outgoingMediaId: String?): Boolean {
        if (prebuffered == null) prebufferSecondaryPlayer()
        var waitedMs = 0L
        while (true) {
            val pb = prebuffered ?: return false
            if (player.currentMediaItem?.mediaId != outgoingMediaId) return false
            if (pb.player.playerError != null) {
                Timber.tag(CROSSFADE_TAG).w("Incoming track failed to prepare; normal transition")
                releasePrebuffered()
                return false
            }
            if (pb.player.playbackState == Player.STATE_READY) {
                if (waitedMs > 0) Timber.tag(CROSSFADE_TAG).d("Incoming track ready after %d ms", waitedMs)
                return true
            }
            val remaining = player.duration - player.currentPosition
            if (remaining < MIN_BLEND_MS) {
                Timber.tag(CROSSFADE_TAG).w("Incoming track not ready with %d ms left; normal transition", remaining)
                releasePrebuffered()
                return false
            }
            delay(READY_POLL_MS)
            waitedMs += READY_POLL_MS
        }
    }

    private fun scheduleCrossfade() {
        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        releasePrebuffered()
        if (!crossfadeEnabled) return
        val overlap = effectiveOverlapMs(player.duration) ?: return
        if (crossfadeGapless && isNextItemGapless()) return
        if (!player.hasNextMediaItem() && player.repeatMode != REPEAT_MODE_ONE) return

        val triggerTime = player.duration - overlap
        if (triggerTime - player.currentPosition <= 0) return

        val targetMediaId = player.currentMediaItem?.mediaId

        crossfadeTriggerJob = scope.launch {
            if (triggerTime - player.currentPosition <= 0) return@launch

            // Poll playback position instead of a wall-clock delay: position freezes on
            // pause, so the trigger can't misfire while paused and get lost.
            var prebufferStarted = false
            while (isActive) {
                if (player.currentMediaItem?.mediaId != targetMediaId) return@launch
                val remaining = triggerTime - player.currentPosition
                if (remaining <= 0) break
                if (!prebufferStarted && remaining <= PREBUFFER_LEAD_MS) {
                    prebufferStarted = true
                    prebufferSecondaryPlayer()
                    Timber.tag(CROSSFADE_TAG).d("Preparing the next track %d ms before the blend", remaining)
                }
                // Coarse until the last second, then fine, so the blend starts within a frame
                // or two of its point and the overlap is the length that was chosen.
                delay(minOf(remaining, if (remaining <= 1_000L) 20L else 250L))
            }
            if (isActive && !(player.isPlaying && player.currentMediaItem?.mediaId == targetMediaId && !sleepTimer.pauseWhenSongEnd)) {
                Timber.tag(CROSSFADE_TAG).d(
                    "Blend point reached but not blending: playing=%s sameTrack=%s sleepAtEnd=%s",
                    player.isPlaying, player.currentMediaItem?.mediaId == targetMediaId, sleepTimer.pauseWhenSongEnd,
                )
            }
            if (isActive && player.isPlaying && player.currentMediaItem?.mediaId == targetMediaId && !sleepTimer.pauseWhenSongEnd) {
                if (!awaitIncomingReady(targetMediaId)) return@launch
                if (!isActive || !player.isPlaying || player.currentMediaItem?.mediaId != targetMediaId) return@launch
                // A late start (the incoming track needed longer) shortens the blend to what is
                // left of the outgoing track, so the fade still ends exactly as it does.
                val remaining = player.duration - player.currentPosition
                startCrossfade(overlapMs = minOf(overlap, remaining).coerceAtLeast(MIN_BLEND_MS))
            }
        }
    }

    private fun isNextItemGapless(): Boolean {
        val currentMediaItem = player.currentMediaItem ?: return false
        if (currentMediaItem.mediaId.isLocalMediaId()) {
            return false // Allow crossfade for local media
        }
        val current = currentMediaItem.mediaMetadata
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return false
        val next = player.getMediaItemAt(nextIndex).mediaMetadata
        return current.albumTitle != null && current.albumTitle == next.albumTitle
    }

    private fun releasePrebuffered() {
        val pb = prebuffered ?: return
        prebuffered = null
        playerSilenceProcessors.remove(pb.player)
        playerStatsListeners.remove(pb.player)
        try {
            pb.player.removeListener(secondaryPlayerListener)
            pb.player.stop()
            pb.player.clearMediaItems()
            pb.player.release()
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Failed to release prebuffered crossfade player")
        }
    }

    /**
     * Builds and prepares the secondary player ahead of the actual trigger, muted and not
     * yet playing, so the blend doesn't have to cold-start a fresh decode/buffer right when
     * it needs to be audible. Adopted by [startCrossfade] if it's still valid by then.
     */
    private fun prebufferSecondaryPlayer() {
        if (isCrossfading.value || secondaryPlayer != null || prebuffered != null) return

        val savedRepeatMode = cachedRepeatMode
        val savedShuffleEnabled = cachedShuffleEnabled
        val targetIndex = if (savedRepeatMode == REPEAT_MODE_ONE) {
            player.currentMediaItemIndex
        } else {
            player.nextMediaItemIndex
        }
        if (targetIndex == C.INDEX_UNSET) return
        val targetMediaId = player.getMediaItemAt(targetIndex).mediaId

        val secPlayer = createExoPlayer()
        secPlayer.addListener(secondaryPlayerListener)

        val itemCount = player.mediaItemCount
        val items = mutableListOf<MediaItem>()
        for (i in 0 until itemCount) items.add(player.getMediaItemAt(i))
        secPlayer.setMediaItems(items)

        secPlayer.seekTo(targetIndex, 0)
        fxPlaybackParameters()?.let { if (it != PlaybackParameters.DEFAULT) secPlayer.playbackParameters = it }
        secPlayer.volume = 0f
        secPlayer.repeatMode = savedRepeatMode
        secPlayer.shuffleModeEnabled = savedShuffleEnabled
        secPlayer.prepare() // playWhenReady left false: buffers ahead without playing.

        prebuffered = PrebufferedTransition(secPlayer, targetMediaId)
    }

    private fun startCrossfade(overlapMs: Long) {
        if (isCrossfading.value) return

        // The cached values, matching what prebufferSecondaryPlayer already used. The two
        // read the same two preferences and must agree, or the prebuffered player is built
        // for one target index and then rejected as stale for another; and these ran
        // `runBlocking` on the main thread at the exact instant the blend was starting.
        val savedRepeatMode = cachedRepeatMode
        val savedShuffleEnabled = cachedShuffleEnabled

        val targetIndex = if (savedRepeatMode == REPEAT_MODE_ONE) {
            player.currentMediaItemIndex
        } else {
            player.nextMediaItemIndex
        }
        if (targetIndex == C.INDEX_UNSET) return
        val targetMediaId = player.getMediaItemAt(targetIndex).mediaId

        // Only a player that was prepared for exactly this track and is ready to sound is
        // blended in. Anything else — stale, failed, still loading — means no blend: the
        // outgoing player simply advances as it would without crossfade. (This used to build
        // a fresh player here and swap to it at once, so a slow stream faded the outgoing
        // track out into nothing.)
        val pb = prebuffered
        if (pb == null || pb.targetMediaId != targetMediaId ||
            pb.player.playbackState != Player.STATE_READY || pb.player.playerError != null
        ) {
            Timber.tag(CROSSFADE_TAG).w("No ready incoming player for %s; normal transition", targetMediaId)
            releasePrebuffered()
            return
        }
        val secPlayer = pb.player
        prebuffered = null

        blendOverlapMs = overlapMs
        blendInterrupted = false
        secondaryPlayer = secPlayer
        secPlayer.playWhenReady = true
        Timber.tag(CROSSFADE_TAG).d(
            "Start: outgoing=%s incoming=%s overlap=%d ms at %d/%d ms",
            player.currentMediaItem?.mediaId, targetMediaId, overlapMs,
            player.currentPosition, player.duration,
        )

        performCrossfadeSwap()

        if (savedShuffleEnabled) {
            val shufflePlaylistFirst = cachedShufflePlaylistFirst
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
    }

    private fun performCrossfadeSwap() {
        isCrossfading.value = true
        val nextPlayer = secondaryPlayer ?: return
        val currentPlayer = player

        fadingPlayer = currentPlayer
        player = nextPlayer
        _playerFlow.value = player
        secondaryPlayer = null

        // The outgoing player keeps its full playlist and keeps advancing in real time
        // while it fades out. If it reaches its own natural end before cleanupCrossfade
        // stops it (trigger-time math off, or the fade loop lagging behind due to a
        // scheduling hiccup), it would auto-advance on its own — playing the next track
        // a second time, or wrapping to track 1 on repeat-all. Truncate its playlist so
        // it has nowhere to advance to; worst case it just stops.
        try {
            val idx = currentPlayer.currentMediaItemIndex
            if (idx != C.INDEX_UNSET && idx + 1 < currentPlayer.mediaItemCount) {
                currentPlayer.removeMediaItems(idx + 1, currentPlayer.mediaItemCount)
            }
            currentPlayer.repeatMode = REPEAT_MODE_OFF
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Failed to truncate fading player's playlist")
        }

        fadingPlayer?.removeListener(this)
        fadingPlayer?.removeListener(sleepTimer)

        
        player.addListener(object : Player.Listener {
            // Follows the user's intent (play/pause), not isPlaying: the incoming player
            // briefly not playing because it is buffering must not pause the outgoing one,
            // or a rebuffer mid-blend would drop both tracks to silence.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (isCrossfading.value && fadingPlayer != null) {
                    try {
                        fadingPlayer?.playWhenReady = playWhenReady
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error syncing fadingPlayer play state")
                    }
                } else {
                    player.removeListener(this)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (!isCrossfading.value) {
                    player.removeListener(this)
                    return
                }
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    blendInterrupted = true
                }
            }
        })

        nextPlayer.removeListener(secondaryPlayerListener)
        nextPlayer.addListener(this)
        nextPlayer.addListener(sleepTimer)

        sleepTimer.player = player
        listenBrainz.attach(player)

        try {
            (mediaSession as MediaSession).player = player
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to swap player in MediaSession")
        }

        // The crossfade swap moves playback to a brand-new ExoPlayer with its own
        // audio session id, but this player's listener was attached after the
        // seek/prepare already happened, so no EVENT_MEDIA_ITEM_TRANSITION fires for
        // it. Without this, the LoudnessEnhancer and system-EQ session stay bound to
        // the outgoing (soon-to-be-released) session, so the incoming track plays
        // without normalization/EQ.
        currentMediaMetadata.value = player.currentMetadata
        val oldSessionId = fadingPlayer?.audioSessionId
        // Keep the current enhancer (still bound to the outgoing session) alive and attached
        // through the fade instead of releasing it, so the outgoing track stays normalized
        // while it fades out. A fresh enhancer for the incoming session is created below.
        // cleanupCrossfade releases this once the fade is done.
        try {
            fadingLoudnessEnhancer?.release()
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Failed releasing stale fading enhancer")
        }
        fadingLoudnessEnhancer = loudnessEnhancer
        loudnessEnhancer = null
        if (isAudioEffectSessionOpened) {
            if (oldSessionId != null && oldSessionId != C.AUDIO_SESSION_ID_UNSET && oldSessionId > 0) {
                sendBroadcast(
                    Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, oldSessionId)
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    },
                )
            }
            isAudioEffectSessionOpened = false
            openAudioEffectSession()
        } else {
            setupLoudnessEnhancer()
        }

        // Never two ramps writing volumes at once. isCrossfading normally prevents a second
        // swap, but an aborted fade can outlive its flag, and two loops fighting over the
        // same player's volume is exactly the "overlapping audio at the wrong volume" this
        // is supposed to avoid.
        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            val duration = blendOverlapMs.coerceAtLeast(MIN_BLEND_MS)
            // The players and the track this ramp was started for. Read once: `player` is a
            // field that a skip, a previous, or another transition can repoint mid-fade, and
            // a ramp that keeps writing into whatever `player` happens to be now would leave
            // a track the user just skipped to sitting at a fraction of full volume.
            val incoming = nextPlayer
            val outgoing = currentPlayer
            val incomingItemId = incoming.currentMediaItem?.mediaId
            // The blend is measured in the incoming track's own playback time, read every
            // ~15ms (small enough that each gain step is inaudible). A wall-clock step count
            // drifted with scheduling and kept fading through a pause or a rebuffer; playback
            // time stands still for both, so the overlap is exactly `duration` of music.
            val incomingStart = try { incoming.currentPosition } catch (e: Exception) { 0L }
            val speed = (try { incoming.playbackParameters.speed } catch (e: Exception) { 1f })
                .takeIf { it > 0f } ?: 1f

            /**
             * Constant-power blend. Both gains come from the *same* position in the
             * blend, so sin²+cos² = 1 and the two tracks summed together hold a steady
             * level all the way through.
             *
             * This previously ran the two curves over disjoint windows — the outgoing
             * one finished at 60% of the blend and the incoming one only began at 40% —
             * which threw the identity away: at the midpoint both tracks sat at 0.26,
             * summing to about -8.7 dB. That is the audible hole between songs. Sharing
             * one position restores the property the sin/cos pair was chosen for, and
             * both curves are still eased, so there is no step or click at either end.
             */
            fun gainIn(x: Float): Float = kotlin.math.sin(x.coerceIn(0f, 1f) * (Math.PI / 2.0).toFloat())
            fun gainOut(x: Float): Float = kotlin.math.cos(x.coerceIn(0f, 1f) * (Math.PI / 2.0).toFloat())

            // True when something other than this fade has taken over playback: the user
            // skipped, went back, or another transition repointed the current player.
            fun superseded(): Boolean =
                player !== incoming ||
                    (incomingItemId != null && incoming.currentMediaItem?.mediaId != incomingItemId)

            var abandoned = false
            var lastLogAt = -1L
            try {
                while (true) {
                    if (!isActive) break
                    if (superseded() || blendInterrupted) {
                        abandoned = true
                        break
                    }

                    // Paused mid-blend: hold the ramp where it is (both players are paused
                    // with it), so resuming continues the blend from the same point.
                    if (!incoming.playWhenReady) {
                        delay(100)
                        continue
                    }

                    val elapsed = ((incoming.currentPosition - incomingStart).coerceAtLeast(0L) / speed).toLong()
                    val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                    val target = blendTargetVolume()
                    val outgoingLeft = try { outgoing.duration - outgoing.currentPosition } catch (e: Exception) { 0L }
                    try {
                        incoming.volume = target * gainIn(progress)
                        // The outgoing track may reach its end a hair before the blend's
                        // last step; it is silent from then on either way.
                        outgoing.volume = if (outgoingLeft <= 0L) 0f else target * gainOut(progress)
                    } catch (e: Exception) { break }

                    if (elapsed - lastLogAt >= 1000L || progress >= 1f) {
                        lastLogAt = elapsed
                        Timber.tag(CROSSFADE_TAG).d(
                            "Blend %.2f: out=%.2f@%d in=%.2f@%d target=%.2f",
                            progress, outgoing.volume, outgoing.currentPosition,
                            incoming.volume, incoming.currentPosition, target,
                        )
                    }

                    if (progress >= 1f) break
                    delay(BLEND_STEP_MS)
                }
            } finally {
                // Whether this ramp is still the one in charge. It is not only when a newer
                // blend has already installed its own outgoing player, in which case this
                // coroutine is a cancelled leftover and must touch nothing that now belongs
                // to the newer blend.
                val ownsBlend = fadingPlayer === outgoing

                // Always safe: this player is the one being replaced either way.
                try {
                    outgoing.volume = 0f
                } catch (e: Exception) {
                    Timber.tag(TAG).d(e, "Crossfade volume reset skipped, player likely released")
                }

                if (ownsBlend) {
                    // Whatever happened — finished, or overtaken by a skip mid-ramp — the
                    // track now playing has to end at full volume. Abandoning the ramp
                    // without this is what left a skipped-to track playing quietly.
                    try {
                        player.volume = blendTargetVolume()
                    } catch (e: Exception) {
                        Timber.tag(TAG).d(e, "Crossfade volume restore skipped, player likely released")
                    }
                }
                Timber.tag(CROSSFADE_TAG).d(
                    if (abandoned) "End: abandoned (%s)" else "End: complete (%s)",
                    when {
                        !abandoned -> "overlap done"
                        superseded() -> "skip or queue change"
                        blendInterrupted -> "seek"
                        else -> "cancelled"
                    },
                )
                blendInterrupted = false
                cleanupCrossfade(outgoing)
            }
        }
    }

    /**
     * Tears down the outgoing side of a finished blend.
     *
     * [expectedFading] is the player the caller's fade was actually responsible for. A
     * fade that was cancelled runs its `finally` one dispatch later, by which time a newer
     * blend may already have installed a different outgoing player — releasing that one
     * would cut the track that is currently fading out. Passing the expected player makes
     * a late cleanup a no-op instead.
     */
    private fun cleanupCrossfade(expectedFading: ExoPlayer?) {
        if (expectedFading != null && fadingPlayer !== expectedFading) {
            Timber.tag(TAG).d("Stale crossfade cleanup ignored; a newer blend owns the fading player")
            return
        }
        try {
            fadingLoudnessEnhancer?.release()
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Failed releasing fading enhancer")
        } finally {
            fadingLoudnessEnhancer = null
        }
        fadingPlayer?.stop()
        fadingPlayer?.clearMediaItems()
        fadingPlayer?.release()
        fadingPlayer = null
        isCrossfading.value = false
        sleepTimer.notifySongTransition()
        // The incoming player was ready before the service started listening to it, so no
        // READY or transition event will arrive to arm the blend at the end of this track.
        // (Not while the service is shutting down, when the fade's cleanup also lands here.)
        if (scope.isActive) {
            scheduleCrossfade()
        }
    }

    companion object {
        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val YOUTUBE_PLAYLIST = "youtube_playlist"
        const val SEARCH = "search"
        const val SHUFFLE_ACTION = "__shuffle__"

        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 512 * 1024L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
        /**
         * How far ahead of the crossfade trigger to start buffering the incoming track. Long
         * enough for a stream to resolve and buffer on a slow connection; the incoming player
         * holds still (muted, not playing) until the blend starts.
         */
        const val PREBUFFER_LEAD_MS = 8000L

        /** The shortest blend worth doing; anything shorter is a plain track change. */
        const val MIN_BLEND_MS = 1000L

        /** How often the blend re-reads positions and sets both volumes. */
        const val BLEND_STEP_MS = 15L

        /** How often a pending blend checks whether the incoming track is ready. */
        const val READY_POLL_MS = 50L

        private const val CROSSFADE_TAG = "Crossfade"
        /**
         * How long a queue save waits for the burst to settle. A track change fires
         * several player events in quick succession and each one asks for a save; this
         * turns that into one write. Short enough that a process death moments later
         * still finds a recent queue on disk.
         */
        const val QUEUE_SAVE_DEBOUNCE_MS = 400L
        const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
        const val MAX_CONSECUTIVE_ERR = 5
        /** Autoplay lines up more music once fewer songs than this are left to play. */
        const val AUTOPLAY_TOP_UP_BELOW = 5
        const val MAX_RETRY_COUNT = 10

        /** How long before YouTube's stated expiry a cached stream URL is dropped. */
        private const val URL_EXPIRY_MARGIN_S = 300

        /** A pooled connection stays open for minutes; re-warming it sooner buys nothing. */
        private const val PRECONNECT_INTERVAL_MS = 60_000L

        /** How many of a screen's likely songs get their first seconds cached. */
        private const val LIKELY_PRECACHE_COUNT = 3
        private const val LIKELY_PRECACHE_DELAY_MS = 2_000L

        /** How long a weak-signal (small) stream URL is reused before a full one is resolved. */
        private const val SMALL_STREAM_TTL_MS = 15 * 60_000L

        /** Below this radio link estimate, mobile data counts as weak (EDGE, UMTS, 1x). */
        private const val WEAK_LINK_KBPS = 1_000

        /**
         * A song's retry budget refills only once it has played this long without an error.
         * Resetting it on every READY let a stream that fails at the same byte offset each time
         * (the ~1 MiB preview cap) retry forever.
         */
        private const val RETRY_BUDGET_REFILL_MS = 30_000L

        /** How long a stopped player is left alone before a windowless service ends; see [shutDownIfStoppedWithoutUi]. */
        private const val HEADLESS_STOP_GRACE_MS = 600L
        
        private const val MAX_GAIN_MB = 300 
        private const val MIN_GAIN_MB = -1500 

        private const val TAG = "MusicService"

        @Volatile
        var isRunning = false
            private set
    }

    /**
     * Resolves [mediaId] at [quality], joining a resolution already in flight for the same song
     * rather than starting another. The result is stored in [songUrlCache] here, so whichever
     * caller started it, the others (and the next track change) find the URL.
     *
     * [source] only labels the health event: `play` or `prefetch`.
     */
    private suspend fun resolveStream(
        mediaId: String,
        quality: com.shiny.music.constants.AudioQuality,
        source: String,
    ): Result<YTPlayerUtils.PlaybackData> {
        val key = "${mediaId}_${quality.name}"
        val created = resolveScope.async(start = CoroutineStart.LAZY) {
            val startedAt = PlaybackHealth.now()
            val result = try {
                YTPlayerUtils.playerResponseForPlayback(
                    videoId = mediaId,
                    audioQuality = quality,
                    connectivityManager = connectivityManager,
                    preferSmallStream = isWeakNetwork(),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            val ms = PlaybackHealth.now() - startedAt
            result.onSuccess { data ->
                if (data.smallStream) {
                    // Picked for a weak signal: kept only long enough to finish this listen, and
                    // never on disk, so the next play on a good connection gets the full stream.
                    songUrlCache[key] = data.streamUrl to minOf(
                        urlExpiry(data.streamExpiresInSeconds),
                        System.currentTimeMillis() + SMALL_STREAM_TTL_MS,
                    )
                    urlStore.remove(key)
                } else {
                    val expiry = urlExpiry(data.streamExpiresInSeconds)
                    songUrlCache[key] = data.streamUrl to expiry
                    urlStore.put(key, data.streamUrl, expiry)
                }
                // A song that later starts from this cached URL skips the player's own format
                // write, so record it here: loudness and the history ping come from it.
                if (source != "play") storeStreamFormat(mediaId, data)
                PlaybackHealth.record(PlaybackHealth.Event.Resolved(source, data.client, ms))
            }.onFailure { e ->
                val failure = PlaybackHealth.resolveFailureIn(e)
                PlaybackHealth.record(
                    PlaybackHealth.Event.ResolveFailed(
                        source = source,
                        why = failure?.why ?: e.javaClass.simpleName,
                        permanent = failure?.permanent == true,
                        status = failure?.status,
                        cascade = failure?.cascade.orEmpty(),
                        ms = ms,
                    )
                )
            }
            result
        }
        val existing = inFlightResolves.putIfAbsent(key, created)
        val deferred = if (existing != null) {
            created.cancel()
            Timber.tag(TAG).d("Joining the resolution already running for $mediaId ($source)")
            existing
        } else {
            created.invokeOnCompletion { inFlightResolves.remove(key, created) }
            created.start()
            created
        }
        return deferred.await()
    }

    /** The format row for a streamed (not downloaded) song, as the playback resolver writes it. */
    private fun storeStreamFormat(mediaId: String, data: YTPlayerUtils.PlaybackData) {
        if (downloadCache.getCachedSpans(mediaId).isNotEmpty() || downloadFolderStore.contains(mediaId)) return
        val format = data.format
        database.query {
            upsert(
                FormatEntity(
                    id = mediaId,
                    itag = format.itag,
                    mimeType = format.mimeType.split(";")[0],
                    codecs = format.mimeType.substringAfter("codecs=", "\"\"").substringBefore(";").removeSurrounding("\"").takeIf { it.isNotEmpty() } ?: "unknown",
                    bitrate = format.bitrate,
                    sampleRate = format.audioSampleRate,
                    contentLength = format.contentLength ?: 0L,
                    loudnessDb = data.audioConfig?.loudnessDb,
                    perceptualLoudnessDb = data.audioConfig?.perceptualLoudnessDb,
                    playbackUrl = data.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
                )
            )
        }
    }

    /**
     * The cached stream URL for [key] if it can be played now: unexpired and, when it came from
     * disk, confirmed by one HEAD that the CDN still serves it to this connection. A URL that
     * fails the check is forgotten, so the caller resolves afresh. Blocking.
     */
    private fun usableCachedUrl(key: String, contentLength: Long?): String? {
        val (url, expiry) = songUrlCache[key] ?: return null
        if (expiry <= System.currentTimeMillis()) return null
        if (!urlStore.needsCheck(key)) return url
        val startedAt = android.os.SystemClock.uptimeMillis()
        val valid = YTPlayerUtils.isStreamUrlStillValid(url, contentLength?.takeIf { it > 0 })
        Timber.tag("fix403").i(
            "stored.url key=$key valid=$valid ms=${android.os.SystemClock.uptimeMillis() - startedAt}"
        )
        if (!valid) {
            forgetStreamUrl(key)
            return null
        }
        urlStore.markChecked(key)
        return url
    }

    /**
     * Mobile data on a 2G/3G-class link: the radio's own downstream estimate, which Android sets
     * per radio technology, is under [WEAK_LINK_KBPS]. Wi-Fi and wired never count.
     *
     * The player's measured throughput is deliberately not used. googlevideo paces audio to
     * roughly the rate it plays at, so the estimate settles near the stream's bitrate whatever
     * the link can do: measured 2026-09-26 on a realme on 5G, 245 kbps against a 145 Mbps link,
     * which put every song on the smallest stream.
     */
    private fun isWeakNetwork(): Boolean = runCatching {
        com.shiny.music.utils.DebugFaults.weakNetworkOverride?.let { return it }
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?: return false
        if (!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) return false
        val linkKbps = caps.linkDownstreamBandwidthKbps
        val weak = linkKbps in 1 until WEAK_LINK_KBPS
        if (weak) Timber.tag("fix403").i("network.weak linkKbps=$linkKbps")
        weak
    }.getOrDefault(false)

    /**
     * When a resolved URL stops being used. A few minutes short of YouTube's own expiry, so a
     * song started from a cached URL doesn't run into the expiry partway through and 403.
     */
    private fun urlExpiry(expiresInSeconds: Int): Long {
        val marginSeconds = minOf(URL_EXPIRY_MARGIN_S, expiresInSeconds / 4)
        return System.currentTimeMillis() + (expiresInSeconds - marginSeconds) * 1000L
    }

    private var preloadJob: kotlinx.coroutines.Job? = null
    private var preloadTargets: List<String> = emptyList()

    @Volatile
    private var lastPreconnectAt = 0L
    private var likelyJob: Job? = null

    /** What screens report through [PlaybackPrewarm]. */
    private val prewarmTarget = object : PlaybackPrewarm.Target {
        override fun touched(mediaId: String?) {
            resolveScope.launch {
                if (mediaId == null || mediaId.isLocalMediaId()) {
                    // Not a song yet (an album, a playlist): one is usually played next.
                    preconnect()
                    return@launch
                }
                val quality = audioQuality
                if (songUrlCache["${mediaId}_${quality.name}"]?.let { it.second > System.currentTimeMillis() } == true) return@launch
                if (isDownloaded(mediaId)) return@launch
                // The tap that follows joins this resolve in resolveStream instead of starting one.
                resolveStream(mediaId, quality, source = "touch")
            }
        }

        override fun likely(mediaIds: List<String>) {
            // The preload setting (off under Data Saver) covers this too.
            if (!cachedPreloadEnabled) return
            likelyJob?.cancel()
            likelyJob = resolveScope.launch {
                // After the screen has settled, not while it is still loading.
                delay(LIKELY_PRECACHE_DELAY_MS)
                Timber.tag(TAG).d("Likely songs: $mediaIds")
                for (mediaId in mediaIds.distinct().take(LIKELY_PRECACHE_COUNT)) {
                    if (mediaId.isLocalMediaId() || isDownloaded(mediaId)) continue
                    // Precache is Wi-Fi only (precacheStart), and a song playing or starting
                    // keeps the connection to itself.
                    if (connectivityManager.isActiveNetworkMetered) return@launch
                    val busy = withContext(Dispatchers.Main) {
                        player.isPlaying || player.playbackState == Player.STATE_BUFFERING
                    }
                    if (busy) {
                        Timber.tag(TAG).d("Likely songs: stopped, the player is busy")
                        return@launch
                    }
                    val quality = audioQuality
                    val known = database.format(mediaId).first()?.contentLength?.takeIf { it > 0 }
                    // A URL from disk is checked now, so the tap doesn't wait for the check.
                    var url = usableCachedUrl("${mediaId}_${quality.name}", known)
                    var length = known
                    if (url == null) {
                        val data = resolveStream(mediaId, quality, source = "likely").getOrNull() ?: continue
                        url = data.streamUrl
                        length = data.format.contentLength
                    }
                    precacheStart(mediaId, url, length)
                }
            }
        }
    }

    private fun isDownloaded(mediaId: String): Boolean {
        if (downloadFolderStore.contains(mediaId)) return true
        val length = androidx.media3.datasource.cache.ContentMetadata.getContentLength(downloadCache.getContentMetadata(mediaId))
        return length > 0 && downloadCache.isCached(mediaId, 0, length)
    }

    /** Opens the `/player` connection ahead of need; at most once per [PRECONNECT_INTERVAL_MS]. */
    private suspend fun preconnect() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPreconnectAt < PRECONNECT_INTERVAL_MS) return
        lastPreconnectAt = now
        val startedAt = android.os.SystemClock.uptimeMillis()
        YouTube.preconnect()
            .onSuccess { Timber.tag("fix403").d("preconnect ms=${android.os.SystemClock.uptimeMillis() - startedAt}") }
    }

    private fun preloadUpcomingItems() {
        val preloadEnabled = cachedPreloadEnabled
        if (!preloadEnabled) return
        // Nothing is fetched ahead until audio is playing: not for the queue restored at launch, and
        // not while the song just requested is still resolving. onIsPlayingChanged calls back in.
        if (!player.isPlaying) return

        val preloadLimit = cachedPreloadLimit
        val preloadLyrics = cachedPreloadLyrics

        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == androidx.media3.common.C.INDEX_UNSET) return

        val limit = kotlin.math.min(preloadLimit, player.mediaItemCount - currentIndex - 1)
        if (limit <= 0) return

        val upcomingMediaIds = mutableListOf<String>()
        for (i in 1..limit) {
            upcomingMediaIds.add(player.getMediaItemAt(currentIndex + i).mediaId)
        }

        // Already preparing exactly these songs: let that finish. Restarting it on every queue edit,
        // resume or rebuffer would cut a pre-cache off part-way and never start it again.
        if (preloadJob?.isActive == true && upcomingMediaIds == preloadTargets) return
        preloadTargets = upcomingMediaIds

        preloadJob?.cancel()
        preloadJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            for (mediaId in upcomingMediaIds) {

                val isFullyDownloaded = downloadCache.getCachedSpans(mediaId).isNotEmpty() ||
                    downloadFolderStore.contains(mediaId)
                if (!mediaId.isLocalMediaId() && !isFullyDownloaded) {
                    val quality = audioQuality
                    val key = "${mediaId}_${quality.name}"
                    kotlin.runCatching {
                        if (mediaId == upcomingMediaIds.first()) {
                            // The next song gets its start on disk too, also when its URL was
                            // already known (a touch, an earlier preload): that is the skip path.
                            val known = database.format(mediaId).first()?.contentLength?.takeIf { it > 0 }
                            val cached = usableCachedUrl(key, known)
                            if (cached != null) {
                                precacheStart(mediaId, cached, known, nextInQueue = true)
                            } else {
                                Timber.tag(TAG).d("Preloading stream for $mediaId")
                                // Stored in songUrlCache by resolveStream, with the playback path's expiry.
                                resolveStream(mediaId, quality, source = "prefetch").getOrNull()?.let { data ->
                                    Timber.tag(TAG).d("Preloaded stream for $mediaId")
                                    precacheStart(mediaId, data.streamUrl, data.format.contentLength, nextInQueue = true)
                                }
                            }
                        } else if (songUrlCache[key]?.let { it.second > System.currentTimeMillis() } != true) {
                            // An expired entry counts as missing. Checking only for the key skipped
                            // the preload once a stored URL was ~5.5 h old, and the skip then paid
                            // a full resolve (~900 ms instead of ~260 ms).
                            Timber.tag(TAG).d("Preloading stream for $mediaId")
                            resolveStream(mediaId, quality, source = "prefetch")
                        }
                    }
                }

                if (preloadLyrics) {
                    val dbLyrics = database.lyrics(mediaId).firstOrNull()
                    if (dbLyrics == null) {
                        Timber.tag(TAG).d("Preloading lyrics for $mediaId")
                        val dbSong = database.song(mediaId).firstOrNull()
                        if (dbSong != null) {
                            kotlin.runCatching {
                                val metadata = com.shiny.music.models.MediaMetadata(
                                    id = dbSong.song.id,
                                    title = dbSong.song.title,
                                    artists = dbSong.artists.map { artist -> com.shiny.music.models.MediaMetadata.Artist(artist.id, artist.name) },
                                    duration = dbSong.song.duration,
                                    thumbnailUrl = dbSong.thumbnailUrl
                                )
                                val lyricsResult = lyricsHelper.getLyrics(metadata)
                                database.query {
                                    upsert(com.shiny.music.db.entities.LyricsEntity(id = mediaId, lyrics = lyricsResult.lyrics ?: "", provider = lyricsResult.providerName))
                                }
                                Timber.tag(TAG).d("Preloaded lyrics for $mediaId")
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Writes the first [CHUNK_LENGTH] bytes of the next song into the player cache, so a skip starts
     * from disk instead of opening a connection and waiting for the first chunk (audit R-08).
     * The preload that calls this is already off under Data Saver. The stream's total length is
     * recorded too, which is what lets a cold resume keep these bytes.
     *
     * On mobile data only the song [nextInQueue] is written, and not on a weak link, where it
     * would compete with the song playing: listening straight through fetches those bytes
     * anyway, so it costs data only when that song is then skipped. Guesses from screens
     * ([PlaybackPrewarm]) stay Wi-Fi only.
     */
    private suspend fun precacheStart(
        mediaId: String,
        streamUrl: String,
        contentLength: Long?,
        nextInQueue: Boolean = false,
    ) {
        if (contentLength == null || contentLength <= 0) {
            Timber.tag(TAG).d("No pre-cache for $mediaId: length unknown")
            return
        }
        if (connectivityManager.isActiveNetworkMetered) {
            val systemDataSaver = connectivityManager.restrictBackgroundStatus ==
                android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            if (!nextInQueue || systemDataSaver || isWeakNetwork()) return
        }
        val length = minOf(CHUNK_LENGTH, contentLength)
        if (playerCache.isCached(mediaId, 0, length)) {
            Timber.tag(TAG).d("No pre-cache for $mediaId: already cached")
            return
        }
        val startedAt = android.os.SystemClock.uptimeMillis()
        try {
            // Bytes of another format under this key must not be mixed with these.
            val cachedLength = androidx.media3.datasource.cache.ContentMetadata.getContentLength(
                playerCache.getContentMetadata(mediaId)
            )
            if (cachedLength != contentLength && playerCache.getCachedSpans(mediaId).isNotEmpty()) {
                playerCache.removeResource(mediaId)
            }
            val dataSource = CacheDataSource.Factory()
                .setCache(playerCache)
                .setUpstreamDataSourceFactory(com.shiny.music.utils.StreamTransport.dataSourceFactory)
                .createDataSource()
            val spec = androidx.media3.datasource.DataSpec.Builder()
                .setUri(streamUrl)
                .setKey(mediaId)
                .setPosition(0)
                .setLength(length)
                .build()
            kotlinx.coroutines.runInterruptible {
                androidx.media3.datasource.cache.CacheWriter(dataSource, spec, null, null).cache()
            }
            playerCache.applyContentMetadataMutations(
                mediaId,
                androidx.media3.datasource.cache.ContentMetadataMutations().also {
                    androidx.media3.datasource.cache.ContentMetadataMutations.setContentLength(it, contentLength)
                },
            )
            Timber.tag(TAG).d("Pre-cached start of $mediaId: $length bytes in ${android.os.SystemClock.uptimeMillis() - startedAt} ms")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Pre-cache of $mediaId failed")
        }
    }
}
