package com.shiny.music.together

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.shiny.music.R
import com.shiny.music.constants.ListenTogetherUsernameKey
import com.shiny.music.constants.TogetherDeviceIdKey
import com.shiny.music.constants.TogetherLatencyKey
import com.shiny.music.constants.TogetherModeKey
import com.shiny.music.constants.TogetherResumeKey
import com.shiny.music.extensions.currentMetadata
import com.shiny.music.extensions.getCurrentQueueIndex
import com.shiny.music.extensions.getQueueWindows
import com.shiny.music.extensions.metadata
import com.shiny.music.extensions.toMediaItem
import com.shiny.music.models.MediaMetadata
import com.shiny.music.playback.PlaybackGate
import com.shiny.music.playback.PlayerConnection
import com.shiny.music.playback.TransportAction
import com.shiny.music.playback.queues.ListQueue
import com.shiny.music.playback.queues.Queue
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Listen Together, the engine.
 *
 * The host's phone conducts: whatever it plays is published to the room as a position at a
 * moment of server time, together with the upcoming songs and who added them. A guest either
 * plays along on their own phone — loading the same song and holding it within a few tens of
 * milliseconds of the host by bending the playback speed — or keeps their phone as a remote
 * while the host's speaker plays. Picking a song as a guest adds it to the shared queue; the
 * host's phone puts it in its player, credited to whoever added it.
 */
@Singleton
class TogetherSession @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaybackGate {

    enum class Phase { Idle, Starting, Joining, Waiting, Live }

    enum class Ending { Left, HostEnded, Removed, Blocked, Declined, Full, NoSession, Unreachable }

    data class State(
        val phase: Phase = Phase.Idle,
        val code: String? = null,
        val room: TogetherRoom? = null,
        val meId: String? = null,
        /** Live, but the connection dropped and is coming back. */
        val reconnecting: Boolean = false,
        /** While waiting to be let in: the host's name. */
        val waitingFor: String? = null,
        /** Why the last session attempt or session ended, for the lobby to say. */
        val ending: Ending? = null,
        /** When [ending] happened, so the lobby tells each failure once. */
        val endedAt: Long = 0L,
        val mode: String = MODE_PHONE,
        /** A guest who paused on their own phone; the room plays on without them. */
        val locallyPaused: Boolean = false,
        /** How far this phone is from the room (following guests only). */
        val driftMs: Long? = null,
        val unreadChat: Int = 0,
        val inviteUrl: String? = null,
    ) {
        val isLive: Boolean get() = phase == Phase.Live && room != null
        val isHost: Boolean get() = isLive && room?.hostId == meId
        val isGuest: Boolean get() = isLive && room?.hostId != meId
        val me: TogetherMember? get() = room?.members?.firstOrNull { it.id == meId }
        val host: TogetherMember? get() = room?.members?.firstOrNull { it.id == room.hostId }
        val listeners: Int get() = room?.members?.count { it.connected } ?: 0
        val canControl: Boolean get() = isHost || (isGuest && room?.settings?.guestsCanControl == true)
        val canAdd: Boolean get() = isHost || (isGuest && room?.settings?.guestsCanAdd == true)

        /** This phone plays the room's music. */
        val following: Boolean get() = isGuest && mode == MODE_PHONE

        /** In, or on the way in. */
        val active: Boolean get() = phase != Phase.Idle
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val http = TogetherSocket.httpClient()
    private val api = TogetherApi(http)
    val clock = TogetherClock()
    private val socket = TogetherSocket(http, scope, clock)

    private val _state = MutableStateFlow(State(mode = savedMode()))
    val state: StateFlow<State> = _state.asStateFlow()

    private val _reactions = MutableSharedFlow<TogetherReaction>(extraBufferCapacity = 64)
    val reactions: SharedFlow<TogetherReaction> = _reactions

    /** Short things to tell the person: "Ana added Espresso", "You're the host now". */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val notices: SharedFlow<String> = _notices

    private var playerConnection: PlayerConnection? = null
    private var token: String? = null
    private var hostKey: String? = null
    private var base: String? = null
    private var joinFailures = 0

    /** Who added each song the host's player holds, by track id. */
    private val attribution = HashMap<String, TogetherPerson>()

    // Following
    private var driftJob: Job? = null
    private var applyingUntil = 0L
    private var savedParameters: PlaybackParameters? = null
    private var loadingTrackId: String? = null
    private var loadStartedAt = 0L
    private var localNoticeFor: String? = null

    // Hosting
    private var publishPlaybackJob: Job? = null
    private var publishQueueJob: Job? = null
    private var heartbeatJob: Job? = null
    private var lastPublishedQueue: List<Pair<String, String?>>? = null

    private var reactionKey = 0L
    var chatOpen = false
        set(value) {
            field = value
            if (value) _state.update { it.copy(unreadChat = 0) }
        }

    val notifications = TogetherNotifications(context)

    init {
        scope.launch { socket.events.collect(::onSocketEvent) }
    }

    val serverConfigured: Boolean get() = TogetherServer.base(context) != null

    val displayName: String
        get() = context.dataStore.get(ListenTogetherUsernameKey, "").trim().ifEmpty { defaultName() }

    /** Invites live on Shiny's site (shinymusic.in/j/CODE), whichever server runs the session. */
    fun inviteUrl(code: String): String = com.shiny.music.social.ShinyLinks.room(code)

    /** Starts a session with whatever is playing now. */
    fun start() {
        if (_state.value.active) return
        val server = TogetherServer.base(context) ?: return fail(Ending.Unreachable)
        base = server
        token = null
        joinFailures = 0
        _state.value = State(phase = Phase.Starting, mode = MODE_PHONE)
        scope.launch {
            runCatching { api.create(server) }
                .onSuccess { created ->
                    hostKey = created.hostKey
                    _state.update { it.copy(code = created.code, inviteUrl = inviteUrl(created.code)) }
                    connect(server, created.code)
                }
                .onFailure {
                    Timber.tag(TAG).w(it, "create failed")
                    fail(Ending.Unreachable)
                }
        }
    }

    /** Joins by code (any case, spaces and dashes ignored). */
    fun join(rawCode: String, resumeToken: String? = null) {
        val code = normalizeCode(rawCode) ?: return fail(Ending.NoSession)
        val current = _state.value
        if (current.active && current.code == code) return
        if (current.active) leave()
        val server = TogetherServer.base(context) ?: return fail(Ending.Unreachable)
        base = server
        hostKey = null
        token = resumeToken
        joinFailures = 0
        _state.value = State(
            phase = Phase.Joining,
            code = code,
            mode = savedMode(),
            inviteUrl = inviteUrl(code),
        )
        connect(server, code)
    }

    suspend fun preview(rawCode: String): TogetherPreview? {
        val code = normalizeCode(rawCode) ?: return null
        val server = TogetherServer.base(context) ?: return null
        return runCatching { api.preview(server, code) }.getOrNull()
    }

    /** Leaves; the room carries on without you (and hands itself on if you hosted). */
    fun leave() {
        if (!_state.value.active) return
        send("leave")
        finish(Ending.Left)
    }

    /** Ends the session for everyone. Host only. */
    fun end() {
        if (!_state.value.isHost) return
        send("end")
        finish(Ending.Left)
    }

    fun cancelJoin() {
        if (_state.value.phase == Phase.Live) return
        finish(null)
    }

    fun setMode(mode: String) {
        if (mode != MODE_PHONE && mode != MODE_REMOTE) return
        scope.launch(Dispatchers.IO) { context.dataStore.edit { it[TogetherModeKey] = mode } }
        val was = _state.value
        _state.update { it.copy(mode = mode, locallyPaused = false) }
        if (was.isLive) send("mode") { put("mode", mode) }
        if (was.isGuest) {
            if (mode == MODE_PHONE) startFollowing() else stopFollowing(pause = true)
        }
    }

    fun rename(name: String) {
        val clean = name.trim().take(24)
        scope.launch(Dispatchers.IO) { context.dataStore.edit { it[ListenTogetherUsernameKey] = clean } }
        if (_state.value.isLive && clean.isNotEmpty()) send("rename") { put("name", clean) }
    }

    fun add(songs: List<MediaMetadata>, next: Boolean) {
        val st = _state.value
        if (!st.isLive || songs.isEmpty()) return
        if (!st.canAdd) {
            notice(context.getString(R.string.together_notice_adding_off))
            return
        }
        val shareable = songs.filter { isShareable(it.id) }.take(25)
        if (st.isHost) {
            // The host's own picks go straight into the player; the queue publish tells the room.
            val connection = playerConnection ?: return
            val me = TogetherPerson(id = st.meId, name = st.me?.name ?: displayName)
            val items = shareable.map { song ->
                attribution[song.id] = me
                song.copy(suggestedBy = me.name).toMediaItem()
            }
            insertIntoHostQueue(items, next)
            notice(
                if (items.size == 1) context.getString(if (next) R.string.together_notice_added_next else R.string.together_notice_added, shareable.first().title)
                else context.getString(R.string.together_notice_added_many, items.size)
            )
            publishQueueSoon()
            return
        }
        shareable.forEach { song ->
            send("add") {
                put("track", song.toTogetherTrack().toJson())
                put("next", next)
            }
        }
    }

    /**
     * Plays [song] for the whole room now, for the host or a guest the host lets drive. What
     * was playing stays behind it and Up Next keeps its order; the song is credited like any
     * other pick. Anyone else can only put it next.
     */
    fun playNow(song: MediaMetadata) {
        val st = _state.value
        if (!st.isLive) return
        if (!st.canControl) {
            add(listOf(song), next = true)
            return
        }
        if (!isShareable(song.id)) return
        if (st.isHost) {
            val connection = playerConnection ?: return
            val player = connection.player
            val me = TogetherPerson(id = st.meId, name = st.me?.name ?: displayName)
            attribution[song.id] = me
            val item = song.copy(suggestedBy = me.name).toMediaItem()
            if (player.mediaItemCount == 0) {
                connection.playQueue(ListQueue(title = context.getString(R.string.listen_together), items = listOf(item)))
            } else {
                // Straight into the player, as with every host pick (see insertIntoHostQueue).
                val at = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
                player.addMediaItem(at, item)
                player.seekTo(at, 0L)
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.playWhenReady = true
            }
            notice(context.getString(R.string.together_notice_playing, song.title))
            publishQueueSoon()
            return
        }
        // A guest who drives: the song goes in next, then the room skips to it. The server
        // hands both to the host in the order they were sent (the add is stored first, and
        // a Durable Object takes no new message while it stores), so the skip lands on it.
        if (!st.canAdd) {
            notice(context.getString(R.string.together_notice_adding_off))
            return
        }
        playingNowId = song.id
        add(listOf(song), next = true)
        control("next")
    }

    /** A guest's play-now pick, so its "added" echo says it is playing rather than next. */
    private var playingNowId: String? = null

    fun remove(item: TogetherQueueItem) = send("remove") { put("uid", item.uid) }

    fun vote(trackId: String, on: Boolean) = send("vote") {
        put("trackId", trackId)
        put("on", on)
    }

    fun voteSkip(on: Boolean = true) = send("skip") { put("on", on) }

    fun react(emoji: String) {
        if (emoji !in TogetherReactions) return
        send("react") { put("emoji", emoji) }
    }

    fun chat(text: String, replyTo: TogetherChatMessage? = null) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        send("chat") {
            put("text", clean.take(500))
            if (replyTo != null) put("replyTo", buildJsonObject {
                put("id", replyTo.id)
                put("name", replyTo.from.name)
                put("text", replyTo.text.take(140))
            })
        }
    }

    /** Room-wide transport, for the host or a guest the host lets drive. */
    fun control(action: String, positionMs: Long? = null) {
        val st = _state.value
        if (!st.isLive) return
        if (st.isHost) {
            applyControl(action, positionMs)
            return
        }
        if (!st.canControl) {
            notice(context.getString(R.string.together_notice_host_controls))
            return
        }
        send("control") {
            put("action", action)
            if (positionMs != null) put("positionMs", positionMs)
        }
    }

    /** A following guest who paused on their own jumps back to where the room is. */
    fun catchUp() {
        _state.update { it.copy(locallyPaused = false) }
        driftTick(force = true)
    }

    fun approve(id: String) {
        send("approve") { put("id", id) }
        notifications.cancelRequest(id)
    }

    fun deny(id: String, block: Boolean = false) {
        send("deny") {
            put("id", id)
            put("block", block)
        }
        notifications.cancelRequest(id)
    }

    fun removeMember(id: String, block: Boolean) = send("kick") {
        put("id", id)
        put("block", block)
    }

    fun makeHost(id: String) = send("host") { put("id", id) }

    fun updateSettings(
        approval: String? = null,
        guestsCanAdd: Boolean? = null,
        guestsCanControl: Boolean? = null,
        votesReorder: Boolean? = null,
    ) = send("settings") {
        approval?.let { put("approval", it) }
        guestsCanAdd?.let { put("guestsCanAdd", it) }
        guestsCanControl?.let { put("guestsCanControl", it) }
        votesReorder?.let { put("votesReorder", it) }
    }

    /** The activity's player connection, or null when it goes away. */
    fun attach(connection: PlayerConnection?) {
        if (playerConnection === connection) return
        playerConnection?.let { old ->
            old.player.removeListener(hostListener)
            old.player.removeListener(guestListener)
            if (old.gate === this) old.gate = null
        }
        playerConnection = connection
        if (connection == null) return
        connection.gate = this
        val st = _state.value
        if (st.isHost) becomeHost(announce = false)
        else if (st.following) startFollowing()
        if (!st.active) resumeIfRecent()
    }

    /** True while this phone follows the room, so the service's own queue top-ups stay out. */
    val isFollowing: Boolean get() = _state.value.following

    override fun interceptPlay(queue: Queue): Boolean {
        if (!_state.value.isGuest) return false
        scope.launch {
            val song = queue.preloadItem ?: runCatching {
                withContext(Dispatchers.IO) { queue.getInitialStatus() }
            }.getOrNull()?.let { status -> status.items.getOrNull(status.mediaItemIndex)?.metadata }
            if (song != null) add(listOf(song), next = false)
        }
        return true
    }

    override fun interceptEnqueue(items: List<MediaItem>, next: Boolean): Boolean {
        if (!_state.value.isGuest) return false
        add(items.mapNotNull { it.metadata }, next)
        return true
    }

    override fun interceptTransport(action: TransportAction, positionMs: Long): Boolean {
        val st = _state.value
        if (!st.isGuest) return false
        val player = playerConnection?.player
        if (action == TransportAction.Radio) {
            notice(context.getString(R.string.together_notice_radio))
            return true
        }
        if (st.canControl) {
            val playing = st.room?.playback?.playing == true
            when (action) {
                TransportAction.Toggle -> control(if (playing) "pause" else "play")
                TransportAction.Play -> control("play")
                TransportAction.Pause -> control("pause")
                TransportAction.Seek -> control("seek", positionMs)
                TransportAction.Next -> control("next")
                TransportAction.Previous -> control("prev")
                TransportAction.Radio -> Unit
            }
            return true
        }
        if (!st.following || player == null) {
            notice(context.getString(R.string.together_notice_host_controls))
            return true
        }
        when (action) {
            TransportAction.Pause -> pauseForMe()
            TransportAction.Play -> catchUp()
            TransportAction.Toggle -> if (st.locallyPaused || !player.playWhenReady) catchUp() else pauseForMe()
            else -> notice(context.getString(R.string.together_notice_vote_skip))
        }
        return true
    }

    /** A following guest steps out for a moment; the room plays on. */
    fun pauseForMe() {
        _state.update { it.copy(locallyPaused = true) }
        applying { playerConnection?.player?.playWhenReady = false }
        setRate(1f)
    }

    private fun connect(server: String, code: String) {
        clock.reset()
        socket.hello = ::helloFrame
        socket.open(TogetherServer.socketUrl(server, code))
    }

    private fun helloFrame(): JsonObject = buildJsonObject {
        put("t", "hello")
        put("v", 1)
        put("name", displayName)
        put("device", deviceId())
        put("mode", _state.value.mode)
        token?.let { put("token", it) }
        if (token == null) hostKey?.let { put("hostKey", it) }
    }

    private fun onSocketEvent(event: TogetherSocket.Event) {
        when (event) {
            is TogetherSocket.Event.Opened -> joinFailures = 0
            is TogetherSocket.Event.Dropped -> onDropped(event)
            is TogetherSocket.Event.Message -> runCatching { onMessage(event.type, event.body) }
                .onFailure { Timber.tag(TAG).w(it, "bad ${event.type}") }
        }
    }

    private fun onDropped(event: TogetherSocket.Event.Dropped) {
        val st = _state.value
        if (!st.active) return
        if (!event.retrying) {
            // A deliberate close from the server arrives after its explanation (removed,
            // declined, ended); only an unexplained one needs saying here.
            if (st.active) finish(if (st.phase == Phase.Live) Ending.Unreachable else Ending.NoSession)
            return
        }
        if (st.phase == Phase.Live) {
            _state.update { it.copy(reconnecting = true) }
        } else if (++joinFailures >= 4) {
            finish(Ending.Unreachable)
        }
    }

    private fun onMessage(type: String, body: JsonObject) {
        when (type) {
            "welcome" -> onWelcome(body)
            "waiting" -> _state.update {
                it.copy(phase = Phase.Waiting, waitingFor = body["host"]?.jsonPrimitive?.contentOrNull)
            }
            "denied" -> finish(
                when (body.str("reason")) {
                    "blocked" -> Ending.Blocked
                    "full" -> Ending.Full
                    else -> Ending.Declined
                }
            )
            "room" -> onRoom(body)
            "playback" -> {
                val playback = body.decode("playback", TogetherPlayback.serializer()) ?: return
                updateRoom { it.copy(playback = playback) }
                if (_state.value.following) applyPlayback()
            }
            "queue" -> {
                val queue = body.decode("queue", ListSerializer(TogetherQueueItem.serializer())) ?: return
                updateRoom { it.copy(queue = queue) }
                if (_state.value.following) mirrorQueue()
            }
            "votes" -> {
                val votes = body.decode("votes", MapSerializer(String.serializer(), ListSerializer(String.serializer()))) ?: return
                updateRoom { it.copy(votes = votes) }
                if (_state.value.isHost) reorderByVotes()
            }
            "skip" -> {
                val skip = body.decode("skip", TogetherSkip.serializer()) ?: return
                updateRoom { it.copy(skip = skip) }
                if (body["passed"]?.jsonPrimitive?.booleanOrNull == true) {
                    notice(context.getString(R.string.together_notice_skipped))
                }
            }
            "react" -> {
                val emoji = body.str("emoji") ?: return
                val from = body["from"]?.jsonObject
                val fromId = from?.get("id")?.jsonPrimitive?.contentOrNull
                _reactions.tryEmit(
                    TogetherReaction(
                        emoji = emoji,
                        from = from?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
                        mine = fromId == _state.value.meId,
                        key = ++reactionKey,
                    )
                )
            }
            "chat" -> {
                val message = body.decode("message", TogetherChatMessage.serializer()) ?: return
                updateRoom { it.copy(chat = (it.chat + message).takeLast(80)) }
                if (!chatOpen && message.from.id != _state.value.meId) {
                    _state.update { it.copy(unreadChat = it.unreadChat + 1) }
                }
            }
            "add" -> onHostAdd(body)
            "remove" -> onHostRemove(body)
            "control" -> {
                val action = body.str("action") ?: return
                applyControl(action, body["positionMs"]?.jsonPrimitive?.longOrNull)
                val who = body["from"]?.takeIf { it is JsonObject }?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                if (who != null && action in listOf("next", "prev")) {
                    notice(context.getString(R.string.together_notice_changed_by, who))
                }
            }
            "added" -> {
                val track = body["track"]?.jsonObject
                val title = track?.get("title")?.jsonPrimitive?.contentOrNull ?: return
                val next = body["next"]?.jsonPrimitive?.booleanOrNull == true
                if (next && track["id"]?.jsonPrimitive?.contentOrNull == playingNowId) {
                    playingNowId = null
                    notice(context.getString(R.string.together_notice_playing, title))
                    return
                }
                notice(context.getString(if (next) R.string.together_notice_added_next else R.string.together_notice_added, title))
            }
            "request" -> {
                val id = body.str("id") ?: return
                val name = body.str("name") ?: return
                updateRoom { it.copy(requests = (it.requests.filterNot { r -> r.id == id } + TogetherRequest(id, name))) }
                notifications.showRequest(id, name)
            }
            "request.cancel" -> {
                val id = body.str("id") ?: return
                updateRoom { it.copy(requests = it.requests.filterNot { r -> r.id == id }) }
                notifications.cancelRequest(id)
            }
            "notice" -> if (body.str("kind") == "host_changed") {
                val name = body.str("name") ?: return
                if (name != displayName) notice(context.getString(R.string.together_notice_new_host, name))
            }
            "removed" -> finish(if (body["blocked"]?.jsonPrimitive?.booleanOrNull == true) Ending.Blocked else Ending.Removed)
            "ended" -> finish(Ending.HostEnded)
            "error" -> onServerError(body)
        }
    }

    private fun onWelcome(body: JsonObject) {
        val you = body["you"]?.jsonObject ?: return
        val room = body.decode("room", TogetherRoom.serializer()) ?: return
        body["s"]?.jsonPrimitive?.longOrNull?.let { clock.seed(it, System.currentTimeMillis()) }
        token = you.str("token")
        val meId = you.str("id")
        val wasHost = _state.value.isHost
        val firstWelcome = _state.value.phase != Phase.Live
        _state.update {
            it.copy(
                phase = Phase.Live,
                code = room.code,
                room = room,
                meId = meId,
                reconnecting = false,
                waitingFor = null,
                ending = null,
                inviteUrl = inviteUrl(room.code),
            )
        }
        saveResume()
        val st = _state.value
        if (st.isHost) becomeHost(announce = !firstWelcome && !wasHost)
        else becomeGuest()
        if (st.isHost) st.room?.requests?.forEach { notifications.showRequest(it.id, it.name) }
    }

    private fun onRoom(body: JsonObject) {
        val before = _state.value
        val hostId = body.str("hostId")
        val settings = body.decode("settings", TogetherSettings.serializer())
        val members = body.decode("members", ListSerializer(TogetherMember.serializer()))
        val requests = body.decode("requests", ListSerializer(TogetherRequest.serializer()))
        val skip = body.decode("skip", TogetherSkip.serializer())
        updateRoom { room ->
            room.copy(
                hostId = hostId ?: room.hostId,
                settings = settings ?: room.settings,
                members = members ?: room.members,
                requests = requests ?: room.requests,
                skip = skip ?: room.skip,
            )
        }
        val after = _state.value
        if (!before.isHost && after.isHost) becomeHost(announce = true)
        else if (before.isHost && !after.isHost) becomeGuest()
        else if (after.isHost && settings != null && settings.votesReorder && before.room?.settings?.votesReorder == false) reorderByVotes()
        // Requests the host already dealt with elsewhere leave the shade too.
        val gone = before.room?.requests.orEmpty().map { it.id } - after.room?.requests.orEmpty().map { it.id }.toSet()
        gone.forEach(notifications::cancelRequest)
    }

    private fun onServerError(body: JsonObject) {
        val code = body.str("code")
        val message = body.str("message")
        when (code) {
            "no_room" -> finish(Ending.NoSession)
            "ended" -> finish(Ending.HostEnded)
            "not_allowed", "slow_down", "bad_track" -> message?.let(::notice)
            else -> Timber.tag(TAG).d("server error $code: $message")
        }
    }

    private fun finish(ending: Ending?) {
        val wasFollowing = _state.value.following
        socket.close()
        socket.hello = null
        stopHosting()
        stopFollowing(pause = false)
        if (wasFollowing) restoreParameters()
        notifications.cancelAll()
        clearResume()
        token = null
        hostKey = null
        attribution.clear()
        lastPublishedQueue = null
        _state.value = State(ending = ending, endedAt = System.currentTimeMillis(), mode = savedMode())
    }

    private fun fail(ending: Ending) {
        _state.value = State(ending = ending, endedAt = System.currentTimeMillis(), mode = savedMode())
    }

    private fun becomeHost(announce: Boolean) {
        stopFollowing(pause = false)
        restoreParameters()
        val connection = playerConnection ?: return
        connection.player.removeListener(hostListener)
        connection.player.addListener(hostListener)
        // Everyone should see the same Up Next, in the order it will play.
        if (connection.player.shuffleModeEnabled) connection.player.shuffleModeEnabled = false
        lastPublishedQueue = null
        publishPlayback()
        publishQueue()
        startHeartbeat()
        if (announce) notice(context.getString(R.string.together_notice_you_host))
    }

    private fun stopHosting() {
        playerConnection?.player?.removeListener(hostListener)
        publishPlaybackJob?.cancel()
        publishQueueJob?.cancel()
        heartbeatJob?.cancel()
    }

    private val hostListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (!_state.value.isHost) return
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                )
            ) publishPlaybackSoon()
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                publishQueueSoon()
            }
        }
    }

    private fun publishPlaybackSoon() {
        publishPlaybackJob?.cancel()
        publishPlaybackJob = scope.launch {
            delay(40)
            publishPlayback()
        }
    }

    private fun publishQueueSoon() {
        publishQueueJob?.cancel()
        publishQueueJob = scope.launch {
            delay(300)
            publishQueue()
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(5_000)
                val player = playerConnection?.player ?: continue
                if (_state.value.isHost && player.playWhenReady && player.playbackState == Player.STATE_READY) publishPlayback()
            }
        }
    }

    private fun publishPlayback() {
        val st = _state.value
        if (!st.isHost) return
        val player = playerConnection?.player ?: return
        val metadata = player.currentMetadata
        // The player's own length when it knows it: listings round, and some are simply wrong.
        val playerDuration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        val track = metadata?.let {
            it.toTogetherTrack(local = !isShareable(it.id)).shareableId()
                .let { t -> if (playerDuration != null) t.copy(durationMs = playerDuration) else t }
        }
        val playback = TogetherPlayback(
            track = track,
            playing = track != null && player.playWhenReady,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            at = clock.serverNow(),
            rate = player.playbackParameters.speed,
        )
        updateRoom { it.copy(playback = playback) }
        send("playback") {
            put("track", track?.toJson() ?: kotlinx.serialization.json.JsonNull)
            put("playing", playback.playing)
            put("buffering", playback.buffering)
            put("positionMs", playback.positionMs)
            put("at", playback.at)
            put("rate", playback.rate)
        }
    }

    private fun publishQueue() {
        if (!_state.value.isHost) return
        val player = playerConnection?.player ?: return
        val windows = player.getQueueWindows()
        val current = player.getCurrentQueueIndex()
        val upcoming = windows.drop(current + 1).take(100).mapNotNull { it.mediaItem.metadata }
        val signature = upcoming.map { it.id to attributionFor(it)?.name }
        if (signature == lastPublishedQueue) return
        lastPublishedQueue = signature
        send("queue") {
            put("items", buildJsonArray {
                upcoming.forEach { song ->
                    add(buildJsonObject {
                        put("track", song.toTogetherTrack(local = !isShareable(song.id)).shareableId().toJson())
                        attributionFor(song)?.let { person ->
                            put("addedBy", buildJsonObject {
                                person.id?.let { put("id", it) }
                                put("name", person.name)
                            })
                        }
                    })
                }
            })
        }
    }

    private fun attributionFor(song: MediaMetadata): TogetherPerson? =
        attribution[song.id] ?: song.suggestedBy?.let { TogetherPerson(name = it) }

    /** A guest's pick arrives: into the host's player, credited to them. */
    private fun onHostAdd(body: JsonObject) {
        if (!_state.value.isHost) return
        val track = body.decode("track", TogetherTrack.serializer()) ?: return
        val by = body.decode("addedBy", TogetherPerson.serializer())
        val next = body["next"]?.jsonPrimitive?.booleanOrNull == true
        val connection = playerConnection ?: return
        by?.let { attribution[track.id] = it }
        insertIntoHostQueue(listOf(track.toMediaMetadata(addedBy = by?.name).toMediaItem()), next)
        if (by != null && by.id != _state.value.meId) {
            notice(context.getString(R.string.together_notice_guest_added, by.name, track.title))
        }
        publishQueueSoon()
    }

    /**
     * Straight into the player, never through the service's play-next: that one treats an
     * unprepared player as empty and would replace the host's whole queue with the pick.
     */
    private fun insertIntoHostQueue(items: List<MediaItem>, next: Boolean) {
        val connection = playerConnection ?: return
        val player = connection.player
        if (items.isEmpty()) return
        if (player.mediaItemCount == 0) {
            connection.playQueue(ListQueue(title = context.getString(R.string.listen_together), items = items))
            return
        }
        if (next) {
            player.addMediaItems((player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount), items)
        } else {
            player.addMediaItems(items)
        }
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
    }

    private fun onHostRemove(body: JsonObject) {
        if (!_state.value.isHost) return
        val trackId = body.str("trackId") ?: return
        val player = playerConnection?.player ?: return
        val index = ((player.currentMediaItemIndex + 1) until player.mediaItemCount)
            .firstOrNull { player.getMediaItemAt(it).mediaId == trackId } ?: return
        player.removeMediaItem(index)
        publishQueueSoon()
    }

    private fun applyControl(action: String, positionMs: Long?) {
        val connection = playerConnection ?: return
        val player = connection.player
        when (action) {
            "play" -> {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.playWhenReady = true
            }
            "pause" -> player.playWhenReady = false
            "seek" -> positionMs?.let { player.seekTo(it.coerceAtLeast(0L)) }
            "next" -> if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.playWhenReady = true
            }
            "prev" -> if (player.currentPosition > 3_000 || !player.hasPreviousMediaItem()) {
                player.seekTo(0)
            } else {
                player.seekToPreviousMediaItem()
            }
        }
        publishPlaybackSoon()
    }

    /** Songs with votes move up, most first; everything else keeps its place. */
    private fun reorderByVotes() {
        val st = _state.value
        val room = st.room ?: return
        if (!st.isHost || !room.settings.votesReorder) return
        val player = playerConnection?.player ?: return
        if (player.shuffleModeEnabled) player.shuffleModeEnabled = false
        val start = player.currentMediaItemIndex + 1
        if (start <= 0 || start >= player.mediaItemCount) return
        val ids = (start until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val desired = ids.indices.sortedWith(
            compareByDescending<Int> { room.votes[ids[it]]?.size ?: 0 }.thenBy { it }
        )
        if (desired == ids.indices.toList()) return
        val order = ids.indices.toMutableList()
        desired.forEachIndexed { target, original ->
            val at = order.indexOf(original)
            if (at != target) {
                player.moveMediaItem(start + at, start + target)
                order.add(target, order.removeAt(at))
            }
        }
        publishQueueSoon()
    }

    private fun becomeGuest() {
        stopHosting()
        if (_state.value.mode == MODE_PHONE) startFollowing() else stopFollowing(pause = true)
    }

    private fun startFollowing() {
        val connection = playerConnection ?: return
        val player = connection.player
        if (savedParameters == null) savedParameters = player.playbackParameters
        player.removeListener(guestListener)
        player.addListener(guestListener)
        localNoticeFor = null
        applyPlayback()
        driftJob?.cancel()
        driftJob = scope.launch {
            while (isActive) {
                val inSync = abs(_state.value.driftMs ?: Long.MAX_VALUE) <= TogetherSyncPolicy.InSyncMs
                delay(if (inSync) 1_000L else 350L)
                driftTick()
            }
        }
    }

    private fun stopFollowing(pause: Boolean) {
        driftJob?.cancel()
        driftJob = null
        loadingTrackId = null
        val player = playerConnection?.player
        player?.removeListener(guestListener)
        if (pause && player != null && player.currentMediaItem?.mediaId == _state.value.room?.playback?.track?.id) {
            applying { player.playWhenReady = false }
        }
        restoreParameters()
        _state.update { it.copy(driftMs = null) }
    }

    private fun restoreParameters() {
        val saved = savedParameters ?: return
        savedParameters = null
        playerConnection?.player?.let { player ->
            if (player.playbackParameters != saved) applying { player.playbackParameters = saved }
        }
    }

    /** The room's song and state onto this phone. */
    private fun applyPlayback() {
        val st = _state.value
        if (!st.following) return
        val connection = playerConnection ?: return
        val player = connection.player
        val playback = st.room?.playback
        val track = playback?.track
        if (track == null) {
            applying { player.playWhenReady = false }
            return
        }
        if (track.local) {
            applying { player.playWhenReady = false }
            if (localNoticeFor != track.id) {
                localNoticeFor = track.id
                notice(context.getString(R.string.together_notice_local_song))
            }
            return
        }
        if (player.currentMediaItem?.mediaId != track.id) {
            loadTrack(track, playback)
        } else {
            driftTick(force = true)
        }
    }

    private fun loadTrack(track: TogetherTrack, playback: TogetherPlayback) {
        val connection = playerConnection ?: return
        val player = connection.player
        if (loadingTrackId == track.id && SystemClock.elapsedRealtime() - loadStartedAt < 8_000) return
        loadingTrackId = track.id
        loadStartedAt = SystemClock.elapsedRealtime()
        val room = _state.value.room
        val addedBy = room?.let { findAddedBy(it, track.id) }
        val items = listOf(track.toMediaMetadata(addedBy).toMediaItem()) + mirrorItems(room)
        val start = targetPosition(playback).coerceIn(0L, (track.durationMs - 1_000L).coerceAtLeast(0L))
        applying {
            player.setMediaItems(items, 0, start)
            player.prepare()
            player.playWhenReady = playback.playing && !_state.value.locallyPaused
        }
        runCatching {
            connection.service.queueTitle = context.getString(R.string.together_queue_title, _state.value.host?.name ?: "")
        }
    }

    private fun findAddedBy(room: TogetherRoom, trackId: String): String? =
        room.queue.firstOrNull { it.track.id == trackId }?.addedBy?.name

    private fun mirrorItems(room: TogetherRoom?): List<MediaItem> =
        room?.queue.orEmpty()
            .filterNot { it.track.local }
            .take(30)
            .map { it.track.toMediaMetadata(it.addedBy?.name).toMediaItem() }

    /** The room's Up Next behind the current song, so this phone preloads and shows it. */
    private fun mirrorQueue() {
        val st = _state.value
        if (!st.following) return
        val player = playerConnection?.player ?: return
        val current = player.currentMediaItemIndex
        if (current < 0 || player.currentMediaItem?.mediaId != st.room?.playback?.track?.id) return
        val want = mirrorItems(st.room)
        val have = (current + 1 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        if (want.map { it.mediaId } == have) return
        applying {
            if (player.mediaItemCount > current + 1) player.removeMediaItems(current + 1, player.mediaItemCount)
            if (current > 0) player.removeMediaItems(0, current)
            player.addMediaItems(want)
        }
    }

    private fun targetPosition(playback: TogetherPlayback): Long =
        TogetherSyncPolicy.expectedPosition(playback, clock.serverNow()) + latencyMs()

    private fun driftTick(force: Boolean = false) {
        val st = _state.value
        if (!st.following) return
        val playback = st.room?.playback ?: return
        val track = playback.track ?: return
        if (track.local) return
        val player = playerConnection?.player ?: return

        if (player.currentMediaItem?.mediaId != track.id) {
            // This phone finished the song a moment before the host and moved on to the
            // room's next one: wait for the host rather than jumping back to the end.
            val roomEnding = track.durationMs > 0 && targetPosition(playback) >= track.durationMs - 4_000
            if (roomEnding && player.currentMediaItem?.mediaId == st.room?.queue?.firstOrNull()?.track?.id) return
            loadTrack(track, playback)
            return
        }
        loadingTrackId = null

        val roomPlaying = playback.playing && !playback.buffering
        if (!roomPlaying) {
            setRate(1f)
            if (player.playWhenReady) applying { player.playWhenReady = false }
            val want = playback.positionMs + latencyMs()
            if (abs(player.currentPosition - want) > 1_200) applying { player.seekTo(want) }
            _state.update { it.copy(driftMs = 0) }
            return
        }
        if (st.locallyPaused) {
            setRate(1f)
            return
        }
        if (!player.playWhenReady) applying {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.playWhenReady = true
        }
        if (player.playbackState != Player.STATE_READY) return

        val expected = targetPosition(playback)
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        if (duration != null && expected >= duration - 400) {
            // The room is at (or past) the end of this song; the host's next one is on its way.
            setRate(1f)
            return
        }
        val actual = player.currentPosition
        val correction = TogetherSyncPolicy.correction(actual, expected, player.playbackParameters.speed)
        if (correction != TogetherSyncPolicy.Correction.None) {
            Timber.tag(TAG).d("drift ${actual - expected} ms (rtt ${clock.rttMs}) -> $correction")
        }
        when (correction) {
            TogetherSyncPolicy.Correction.None -> setRate(1f)
            is TogetherSyncPolicy.Correction.Rate -> setRate(correction.speed)
            is TogetherSyncPolicy.Correction.Seek -> {
                setRate(1f)
                applying { player.seekTo(correction.toMs.coerceAtLeast(0L)) }
            }
        }
        if (force || st.driftMs == null || abs((st.driftMs) - (actual - expected)) > 10) {
            _state.update { it.copy(driftMs = actual - expected) }
        }
    }

    private fun setRate(speed: Float) {
        val player = playerConnection?.player ?: return
        val current = player.playbackParameters
        if (abs(current.speed - speed) < 0.004f && current.pitch == 1f) return
        applying { player.playbackParameters = PlaybackParameters(speed, 1f) }
    }

    /** Things the person does to this phone's player behind the session's back. */
    private val guestListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val st = _state.value
            if (!st.following || isApplying()) return
            val roomPlaying = st.room?.playback?.playing == true
            if (!playWhenReady && roomPlaying && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                _state.update { it.copy(locallyPaused = true) }
            } else if (playWhenReady && st.locallyPaused) {
                _state.update { it.copy(locallyPaused = false) }
                driftTick(force = true)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val st = _state.value
            if (!st.following || isApplying()) return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
            // A skip from the notification or headphones: back to what the room is playing.
            scope.launch { applyPlayback() }
        }
    }

    private inline fun applying(block: () -> Unit) {
        applyingUntil = SystemClock.uptimeMillis() + 400
        block()
    }

    private fun isApplying() = SystemClock.uptimeMillis() < applyingUntil

    private fun saveResume() {
        val st = _state.value
        val code = st.code ?: return
        val tok = token ?: return
        val serverBase = base ?: return
        scope.launch(Dispatchers.IO) {
            context.dataStore.edit { it[TogetherResumeKey] = "$code|$tok|${System.currentTimeMillis()}|$serverBase" }
        }
    }

    private fun clearResume() {
        scope.launch(Dispatchers.IO) { context.dataStore.edit { it.remove(TogetherResumeKey) } }
    }

    private fun resumeIfRecent() {
        val saved = context.dataStore.get(TogetherResumeKey, "")
        val parts = saved.split("|")
        if (parts.size < 4) return
        val at = parts[2].toLongOrNull() ?: return
        if (System.currentTimeMillis() - at > ResumeWindowMs) {
            clearResume()
            return
        }
        if (parts[3] != TogetherServer.base(context)) return
        join(parts[0], resumeToken = parts[1])
    }

    private inline fun send(type: String, crossinline fields: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}): Boolean =
        socket.send(buildJsonObject {
            put("t", type)
            fields()
        })

    private fun updateRoom(transform: (TogetherRoom) -> TogetherRoom) {
        _state.update { st -> st.room?.let { st.copy(room = transform(it)) } ?: st }
    }

    private fun notice(text: String) {
        _notices.tryEmit(text)
    }

    private fun latencyMs(): Long = context.dataStore.get(TogetherLatencyKey, 0).toLong()

    private fun savedMode(): String = context.dataStore.get(TogetherModeKey, MODE_PHONE).takeIf { it == MODE_REMOTE } ?: MODE_PHONE

    private fun deviceId(): String {
        val existing = context.dataStore.get(TogetherDeviceIdKey, "")
        if (existing.isNotEmpty()) return existing
        val fresh = UUID.randomUUID().toString()
        scope.launch(Dispatchers.IO) { context.dataStore.edit { it[TogetherDeviceIdKey] = fresh } }
        return fresh
    }

    /** Your YouTube Music first name, else the phone's own name ("Ana's Pixel"), else the model. */
    private fun defaultName(): String {
        val account = context.dataStore.get(com.shiny.music.constants.AccountNameKey, "").trim()
            .split(" ").firstOrNull().orEmpty()
        if (account.isNotEmpty() && account != "Guest") return account.take(24)
        val deviceName = runCatching {
            android.provider.Settings.Global.getString(context.contentResolver, "device_name")
        }.getOrNull()?.trim().orEmpty()
        if (deviceName.isNotEmpty()) return deviceName.take(24)
        return android.os.Build.MODEL?.trim().orEmpty().ifEmpty { "Guest" }.take(24)
    }

    private fun TogetherTrack.toJson(): JsonObject = TogetherJson.encodeToJsonElement(TogetherTrack.serializer(), this).jsonObject

    /** A local file's id is a content URI; the room only needs something stable to call it. */
    private fun TogetherTrack.shareableId(): TogetherTrack =
        if (local) copy(id = "local-" + abs(id.hashCode()), thumbnail = null) else this

    private fun <T> JsonObject.decode(key: String, serializer: kotlinx.serialization.KSerializer<T>): T? =
        get(key)?.let { runCatching { TogetherJson.decodeFromJsonElement(serializer, it) }.getOrNull() }

    private fun JsonObject.str(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    companion object {
        private const val TAG = "ShinyTogether"
        private const val ResumeWindowMs = 3 * 60_000L

        private val CodeAlphabet = Regex("[A-HJ-NP-Z2-9]{6}")

        /** "abc-123", " ABC 123 " and a pasted invite link all come out as ABC123. */
        fun normalizeCode(raw: String): String? {
            val fromLink = Regex("""(?:/j/|[?&](?:code|room)=)([A-Za-z0-9]{6})""").find(raw)?.groupValues?.get(1)
            val code = (fromLink ?: raw.filter { it.isLetterOrDigit() }).uppercase()
            return code.takeIf { CodeAlphabet.matches(it) }
        }

        fun isShareable(id: String) = Regex("[A-Za-z0-9_-]{11}").matches(id)
    }
}
