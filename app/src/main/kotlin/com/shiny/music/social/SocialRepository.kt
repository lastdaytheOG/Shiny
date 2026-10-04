package com.shiny.music.social

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.shiny.music.BuildConfig
import com.shiny.music.constants.SocialPrivateSessionKey
import com.shiny.music.constants.SocialServerUrlKey
import com.shiny.music.constants.SocialSessionTokenKey
import com.shiny.music.constants.SocialUserJsonKey
import com.shiny.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in Shiny account: session, profile, share settings and friends. Everything is cached in
 * DataStore, so the settings screen shows the account offline; the server is the source of truth.
 */
@Singleton
class SocialRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var serverUrl: String = ShinyLinks.WEB_BASE

    val api = SocialApi { serverUrl }

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _user = MutableStateFlow<SocialUser?>(null)
    val user: StateFlow<SocialUser?> = _user.asStateFlow()

    private val _friends = MutableStateFlow<FriendsResponse?>(null)
    val friends: StateFlow<FriendsResponse?> = _friends.asStateFlow()

    private val _privateSession = MutableStateFlow(false)
    val privateSession: StateFlow<Boolean> = _privateSession.asStateFlow()

    init {
        scope.launch {
            context.dataStore.data.collect { prefs ->
                serverUrl = prefs[SocialServerUrlKey]?.takeIf { BuildConfig.DEBUG && it.isNotBlank() } ?: ShinyLinks.WEB_BASE
                _privateSession.value = prefs[SocialPrivateSessionKey] ?: false
                val token = prefs[SocialSessionTokenKey]?.takeIf { it.isNotBlank() }
                _token.value = token
                if (token == null) {
                    _user.value = null
                    _friends.value = null
                } else if (_user.value == null) {
                    _user.value = prefs[SocialUserJsonKey]?.let { cached ->
                        runCatching { SocialApi.json.decodeFromString(SocialUser.serializer(), cached) }.getOrNull()
                    }
                }
            }
        }
    }

    suspend fun currentToken(): String? =
        _token.value ?: context.dataStore.data.first()[SocialSessionTokenKey]?.takeIf { it.isNotBlank() }

    suspend fun signInWithGoogle(idToken: String): Result<SocialUser> =
        runCatching {
            val session = api.signInWithGoogle(idToken)
            _token.value = session.token
            _user.value = session.user
            context.dataStore.edit {
                it[SocialSessionTokenKey] = session.token
                it[SocialUserJsonKey] = SocialApi.json.encodeToString(SocialUser.serializer(), session.user)
            }
            session.user
        }

    suspend fun refreshAccount(): Result<SocialUser> = authed { api.me(it).user.also { user -> saveUser(user) } }

    suspend fun setUsername(username: String): Result<SocialUser> =
        authed { api.updateMe(it, username = username).user.also { user -> saveUser(user) } }

    suspend fun setShareMode(mode: ShareMode): Result<SocialUser> =
        authed { api.updateMe(it, shareMode = mode).user.also { user -> saveUser(user) } }

    suspend fun setPrivateSession(enabled: Boolean) {
        _privateSession.value = enabled
        context.dataStore.edit { it[SocialPrivateSessionKey] = enabled }
    }

    /** Signs out here even when the server can't be reached; the server session then expires on its own. */
    suspend fun signOut() {
        currentToken()?.let { token -> runCatching { api.signOut(token) } }
        clearLocal()
    }

    suspend fun deleteAccount(): Result<Unit> = authed { api.deleteAccount(it) }.onSuccess { clearLocal() }

    suspend fun refreshFriends(): Result<FriendsResponse> = authed { api.friends(it).also { list -> _friends.value = list } }

    /** "requested", or "friends" when they had already asked you. */
    suspend fun requestFriend(username: String): Result<String> =
        authed { api.requestFriend(it, username.trim().removePrefix("@")).status }.onSuccess { refreshFriends() }

    suspend fun acceptFriend(username: String): Result<String> =
        authed { api.acceptFriend(it, username).status }.onSuccess { refreshFriends() }

    suspend fun removeRequest(username: String): Result<String> =
        authed { api.removeRequest(it, username).status }.onSuccess { refreshFriends() }

    suspend fun removeFriend(username: String): Result<String> =
        authed { api.removeFriend(it, username).status }.onSuccess { refreshFriends() }

    /** The server no longer knows this session (expired, signed out elsewhere, account deleted). */
    suspend fun onSessionRejected() = clearLocal()

    private suspend fun <T> authed(block: suspend (token: String) -> T): Result<T> =
        runCatching {
            val token = currentToken() ?: throw SocialApiException(401, "unauthorized", "Sign in first")
            try {
                block(token)
            } catch (error: SocialApiException) {
                if (error.status == 401) clearLocal()
                throw error
            }
        }

    private suspend fun saveUser(user: SocialUser) {
        _user.value = user
        context.dataStore.edit { it[SocialUserJsonKey] = SocialApi.json.encodeToString(SocialUser.serializer(), user) }
    }

    private suspend fun clearLocal() {
        _token.value = null
        _user.value = null
        _friends.value = null
        context.dataStore.edit {
            it.remove(SocialSessionTokenKey)
            it.remove(SocialUserJsonKey)
        }
    }
}
