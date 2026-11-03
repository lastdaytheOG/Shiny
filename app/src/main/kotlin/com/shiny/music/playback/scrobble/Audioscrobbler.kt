package com.shiny.music.playback.scrobble

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.shiny.music.BuildConfig
import com.shiny.music.constants.DataSaverEnabledKey
import com.shiny.music.constants.LastFmScrobbleKey
import com.shiny.music.constants.LastFmSessionKey
import com.shiny.music.constants.LastFmUsernameKey
import com.shiny.music.constants.LibreFmScrobbleKey
import com.shiny.music.constants.LibreFmSessionKey
import com.shiny.music.constants.LibreFmUsernameKey
import com.shiny.music.extensions.metadata
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.getOrNull
import com.shiny.music.utils.isLocalMediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

class ScrobbleException(val code: Int, message: String) : Exception(message) {
    val isWrongLogin get() = code == 4
    val isSignedOut get() = code == 9
    val isTemporary get() = code in setOf(-1, 8, 11, 16, 29)
}

data class ScrobbleSession(val name: String, val key: String)

// one server that speaks last.fm's 2.0 api; calls are signed with md5 of the sorted params
class AudioscrobblerApi(
    private val root: String,
    private val apiKey: String,
    private val secret: String,
    // libre.fm only takes the old md5 login token, not a plain password
    private val tokenLogin: Boolean,
) {
    val isConfigured get() = apiKey.isNotEmpty() && secret.isNotEmpty()

    suspend fun signIn(username: String, password: String): ScrobbleSession {
        val login = if (tokenLogin) mapOf("authToken" to md5(username.lowercase(Locale.ROOT) + md5(password))) else mapOf("password" to password)
        val json = call("auth.getMobileSession", mapOf("username" to username) + login)
        val s = json.optJSONObject("session") ?: throw ScrobbleException(-1, "No session")
        return ScrobbleSession(s.optString("name", username), s.getString("key"))
    }

    suspend fun nowPlaying(sk: String, track: ScrobblePlay) {
        call("track.updateNowPlaying", track.params(null), sk)
    }

    suspend fun scrobble(sk: String, plays: List<ScrobblePlay>) {
        val params = HashMap<String, String>()
        plays.take(50).forEachIndexed { i, p ->
            params.putAll(p.params(i))
            params["timestamp[$i]"] = p.startedAtSec.toString()
        }
        call("track.scrobble", params, sk)
    }

    private suspend fun call(method: String, params: Map<String, String>, sk: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val signed = HashMap(params).apply {
            put("method", method)
            put("api_key", apiKey)
            sk?.let { put("sk", it) }
        }
        val sig = md5(signed.toSortedMap().entries.joinToString("") { it.key + it.value } + secret)
        val body = (signed + mapOf("api_sig" to sig, "format" to "json")).entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val conn = (URL(root).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Shiny")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(text) }.getOrNull()
        if (json != null && json.has("error")) throw ScrobbleException(json.optInt("error", -1), json.optString("message"))
        if (conn.responseCode !in 200..299 || json == null) throw ScrobbleException(-1, "Server answered ${conn.responseCode}")
        json
    }

    private fun ScrobblePlay.params(i: Int?): Map<String, String> {
        val s = i?.let { "[$it]" }.orEmpty()
        return buildMap {
            put("artist$s", artist)
            put("track$s", track)
            if (album.isNotBlank()) put("album$s", album)
            if (durationSec > 0) put("duration$s", durationSec.toString())
        }
    }

    companion object {
        fun md5(text: String): String = MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

data class ScrobblePlay(val artist: String, val track: String, val album: String, val durationSec: Int, val startedAtSec: Long) {
    fun toJson() = JSONObject().put("a", artist).put("t", track).put("al", album).put("d", durationSec).put("s", startedAtSec)

    companion object {
        fun fromJson(o: JSONObject) = ScrobblePlay(o.getString("a"), o.getString("t"), o.optString("al"), o.optInt("d"), o.getLong("s"))
    }
}

enum class ScrobbleSite(
    val label: String,
    val sessionKey: Preferences.Key<String>,
    val usernameKey: Preferences.Key<String>,
    val enabledKey: Preferences.Key<Boolean>,
    val profilePage: String,
    val signUpUrl: String,
    val api: AudioscrobblerApi,
) {
    LASTFM(
        "Last.fm", LastFmSessionKey, LastFmUsernameKey, LastFmScrobbleKey,
        "https://www.last.fm/user/", "https://www.last.fm/join",
        AudioscrobblerApi("https://ws.audioscrobbler.com/2.0/", BuildConfig.LASTFM_API_KEY, BuildConfig.LASTFM_SECRET, tokenLogin = false),
    ),
    LIBREFM(
        "Libre.fm", LibreFmSessionKey, LibreFmUsernameKey, LibreFmScrobbleKey,
        "https://libre.fm/user/", "https://libre.fm/register.php",
        // libre.fm doesn't register apps, any key is taken
        AudioscrobblerApi("https://libre.fm/2.0/", AudioscrobblerApi.md5("shiny-music"), AudioscrobblerApi.md5("shiny-music-secret"), tokenLogin = true),
    ),
}

// sends what's playing and what was listened to, to every service that's signed in and on.
// plays that can't be sent wait on the phone and go with the next one that can
class AudioscrobblerScrobbler(
    private val context: Context,
    private val scope: () -> CoroutineScope,
) : Player.Listener {
    private var player: Player? = null
    private var sessions: Map<ScrobbleSite, String> = emptyMap()
    private var wake: Job? = null
    private val tracker = ListenTracker(SystemClock::elapsedRealtime, System::currentTimeMillis, ::send)
    private val pending = context.getSharedPreferences("scrobble_pending", Context.MODE_PRIVATE)

    init {
        scope().launch {
            context.dataStore.data
                .map { prefs ->
                    if (prefs.getOrNull(DataSaverEnabledKey) == true) {
                        emptyMap()
                    } else {
                        ScrobbleSite.entries.mapNotNull { site ->
                            val sk = prefs.getOrNull(site.sessionKey)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            if (prefs.getOrNull(site.enabledKey) == false) null else site to sk
                        }.toMap()
                    }
                }
                .distinctUntilChanged()
                .collect {
                    sessions = it
                    sync()
                }
        }
    }

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
        sync(newPlay = reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) = sync()

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) sync()
    }

    private fun sync(newPlay: Boolean = false) {
        wake?.cancel()
        wake = null
        val current = player
        val track = if (sessions.isEmpty()) null else current?.track()
        val waitMs = tracker.update(track, playing = current?.isPlaying == true, newPlay = newPlay) ?: return
        wake = scope().launch {
            delay(waitMs)
            sync()
        }
    }

    private fun Player.track(): ListenTracker.Track? {
        val item = currentMediaItem ?: return null
        if (item.mediaId.isLocalMediaId()) return null
        val m = item.metadata ?: return null
        val length = duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: (m.duration * 1000L).coerceAtLeast(0L)
        // the services want the main artist, not everyone credited
        return ListenTracker.Track(id = item.mediaId, title = m.title, artists = m.artists.firstOrNull()?.name.orEmpty(), album = m.album?.title.orEmpty(), durationMs = length)
    }

    private fun send(submission: ListenTracker.Submission) {
        val t = submission.track
        if (t.artists.isBlank() || t.title.isBlank()) return
        val active = sessions
        scope().launch(Dispatchers.IO) {
            active.forEach { (site, sk) ->
                when (submission) {
                    is ListenTracker.Submission.PlayingNow -> runCatching {
                        site.api.nowPlaying(sk, ScrobblePlay(t.artists, t.title, t.album, (t.durationMs / 1000).toInt(), System.currentTimeMillis() / 1000))
                    }
                    is ListenTracker.Submission.Listen -> {
                        val play = ScrobblePlay(t.artists, t.title, t.album, (t.durationMs / 1000).toInt(), submission.startedAtMs / 1000)
                        val queue = load(site) + play
                        try {
                            queue.chunked(50).forEach { site.api.scrobble(sk, it) }
                            save(site, emptyList())
                        } catch (e: ScrobbleException) {
                            // only keep what's worth sending again
                            save(site, if (e.isTemporary || e.isSignedOut) queue.takeLast(500) else emptyList())
                            // the account took back shiny's access: sign out so it stops trying
                            if (e.isSignedOut) context.dataStore.edit { it.remove(site.sessionKey) }
                        } catch (e: IOException) {
                            save(site, queue.takeLast(500))
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    private fun load(site: ScrobbleSite): List<ScrobblePlay> = runCatching {
        val a = JSONArray(pending.getString(site.name, "[]"))
        (0 until a.length()).map { ScrobblePlay.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun save(site: ScrobbleSite, plays: List<ScrobblePlay>) {
        pending.edit().putString(site.name, JSONArray(plays.map { it.toJson() }).toString()).apply()
    }
}
