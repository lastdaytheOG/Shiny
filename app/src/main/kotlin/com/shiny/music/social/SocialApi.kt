package com.shiny.music.social

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@Serializable
data class SocialUser(
    val id: String,
    val username: String? = null,
    val name: String? = null,
    val email: String? = null,
    val avatar: String? = null,
    val shareMode: String = ShareMode.Friends.wire,
    val profileUrl: String? = null,
    val badgeUrl: String? = null,
)

enum class ShareMode(val wire: String) {
    Off("off"),
    Friends("friends"),
    Public("public"),
    ;

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: Friends
    }
}

@Serializable
data class NowPlaying(
    val trackId: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val thumbnail: String? = null,
    val durationMs: Long? = null,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val roomCode: String? = null,
    val updatedAt: Long = 0L,
    val listenUrl: String? = null,
)

@Serializable
data class Friend(
    val username: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val nowPlaying: NowPlaying? = null,
)

@Serializable
data class FriendsResponse(
    val friends: List<Friend> = emptyList(),
    val incoming: List<Friend> = emptyList(),
    val outgoing: List<Friend> = emptyList(),
)

@Serializable
data class PresenceUpdate(
    val trackId: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val thumbnail: String? = null,
    val durationMs: Long? = null,
    val positionMs: Long = 0L,
    val isPlaying: Boolean,
    val roomCode: String? = null,
)

@Serializable
data class SessionResponse(val token: String, val user: SocialUser)

@Serializable
data class UserResponse(val user: SocialUser)

@Serializable
data class StatusResponse(val status: String)

@Serializable
private data class ApiError(val error: String? = null, val message: String? = null)

/** A non-2xx answer from the social server; [message] is written for people. */
class SocialApiException(val status: Int, val code: String?, message: String) : IOException(message)

/** HTTP client for the Shiny social server (`/server`). Every call runs on [Dispatchers.IO]. */
class SocialApi(private val baseUrl: () -> String) {
    private val client =
        com.music.innertube.SharedHttp.client.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

    /**
     * The server's clock minus this phone's, from the last response's `Date` header. Presence
     * timestamps are on the server's clock; a phone set a few minutes off would otherwise call
     * every friend's song stale, or none of them.
     */
    @Volatile
    var serverClockOffsetMs: Long = 0L
        private set

    suspend fun signInWithGoogle(idToken: String): SessionResponse =
        call("POST", "/v1/auth/google", body = buildJsonObject { put("idToken", idToken) })

    suspend fun signOut(token: String) {
        call<JsonElement>("DELETE", "/v1/auth/session", token)
    }

    suspend fun me(token: String): UserResponse = call("GET", "/v1/me", token)

    suspend fun updateMe(token: String, username: String? = null, shareMode: ShareMode? = null): UserResponse =
        call(
            "PATCH",
            "/v1/me",
            token,
            buildJsonObject {
                username?.let { put("username", it) }
                shareMode?.let { put("shareMode", it.wire) }
            },
        )

    suspend fun deleteAccount(token: String) {
        call<JsonElement>("DELETE", "/v1/me", token)
    }

    suspend fun putPresence(token: String, presence: PresenceUpdate) {
        call<JsonElement>("PUT", "/v1/presence", token, json.encodeToJsonElement(PresenceUpdate.serializer(), presence))
    }

    suspend fun clearPresence(token: String) {
        call<JsonElement>("DELETE", "/v1/presence", token)
    }

    suspend fun friends(token: String): FriendsResponse = call("GET", "/v1/friends", token)

    suspend fun requestFriend(token: String, username: String): StatusResponse =
        call("POST", "/v1/friends/requests", token, buildJsonObject { put("username", username) })

    suspend fun acceptFriend(token: String, username: String): StatusResponse =
        call("POST", "/v1/friends/requests/${encode(username)}/accept", token)

    suspend fun removeRequest(token: String, username: String): StatusResponse =
        call("DELETE", "/v1/friends/requests/${encode(username)}", token)

    suspend fun removeFriend(token: String, username: String): StatusResponse =
        call("DELETE", "/v1/friends/${encode(username)}", token)

    /** Stores a playlist on the server and answers with its link. Needs no account. */
    suspend fun sharePlaylist(name: String, songs: List<SharedSong>): SharedPlaylistLink =
        call(
            "POST",
            "/v1/playlists",
            body = json.encodeToJsonElement(SharedPlaylistUpload.serializer(), SharedPlaylistUpload(name, songs)),
        )

    suspend fun sharedPlaylist(id: String): SharedPlaylist = call("GET", "/v1/playlists/${encode(id)}")

    private suspend inline fun <reified T> call(
        method: String,
        path: String,
        token: String? = null,
        body: JsonElement? = null,
    ): T =
        withContext(Dispatchers.IO) {
            val requestBody =
                when {
                    body != null -> body.toString().toRequestBody(JSON_TYPE)
                    method == "POST" || method == "PUT" || method == "PATCH" -> "{}".toRequestBody(JSON_TYPE)
                    else -> null
                }
            val request =
                Request.Builder()
                    .url(baseUrl().trimEnd('/') + path)
                    .method(method, requestBody)
                    .header("Accept", "application/json")
                    .apply { if (token != null) header("Authorization", "Bearer $token") }
                    .build()
            client.newCall(request).execute().use { response ->
                response.headers.getDate("Date")?.let { serverClockOffsetMs = it.time - System.currentTimeMillis() }
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = runCatching { json.decodeFromString(ApiError.serializer(), text) }.getOrNull()
                    throw SocialApiException(response.code, error?.error, error?.message ?: "The server answered ${response.code}")
                }
                json.decodeFromString<T>(text)
            }
        }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        internal val json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
    }
}
