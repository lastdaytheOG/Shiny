package com.shiny.music.discord

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.shiny.music.BuildConfig
import com.shiny.music.constants.DiscordAvatarUrlKey
import com.shiny.music.constants.DiscordClientIdKey
import com.shiny.music.constants.DiscordNameKey
import com.shiny.music.constants.DiscordRefreshTokenKey
import com.shiny.music.constants.DiscordTokenExpiresAtKey
import com.shiny.music.constants.DiscordTokenKey
import com.shiny.music.constants.DiscordUsernameKey
import com.shiny.music.utils.dataStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class DiscordAuthorizationSession(
    val state: String,
    val codeVerifier: String,
    val authorizationUri: Uri,
)

data class DiscordAccount(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
)

data class DiscordAuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long,
    val account: DiscordAccount?,
)

/** What Discord says about a token its Gateway refused. */
enum class DiscordTokenCheck {
    /** Discord's API accepts it, so the Gateway refuses it for another reason. */
    VALID,

    /** It had expired; a refreshed one is stored. */
    REFRESHED,

    /** Revoked or expired for good; the session is cleared and settings ask to reconnect. */
    REVOKED,

    /** Couldn't tell, e.g. no network. */
    UNKNOWN,
}

class DiscordHttpException(
    val status: Int,
    body: String,
) : IOException("Discord request failed with HTTP $status: $body")

object DiscordAuthCoordinator {
    val redirects =
        MutableSharedFlow<Uri>(
            replay = 1,
            extraBufferCapacity = 1,
        )

    fun emit(uri: Uri) {
        redirects.tryEmit(uri)
    }
}

object DiscordOAuthRepository {
    private const val AUTHORIZATION_ENDPOINT = "https://discord.com/oauth2/authorize"
    private const val TOKEN_ENDPOINT = "https://discord.com/api/oauth2/token"
    private const val CURRENT_USER_ENDPOINT = "https://discord.com/api/v10/users/@me"
    private const val CURRENT_AUTHORIZATION_ENDPOINT = "https://discord.com/api/v10/oauth2/@me"
    private const val REDIRECT_PATH = "/authorize/callback"
    private const val REQUEST_TIMEOUT_MS = 12_000
    private const val EXPIRY_SKEW_MS = 60_000L

    /**
     * `sdk.social_layer_presence` is Discord's scope for Rich Presence from an app (the Social SDK's
     * default presence scopes are `openid sdk.social_layer_presence`): it covers the Gateway session
     * and the activity updates. `identify` reads the account shown in settings.
     */
    private const val SCOPES = "openid identify sdk.social_layer_presence"

    private val json = Json { ignoreUnknownKeys = true }
    private val secureRandom = SecureRandom()

    val applicationId: Long
        get() = BuildConfig.DISCORD_APPLICATION_ID_LONG

    /**
     * Discord's mobile redirect form: one slash after the scheme and no host. It must match a redirect
     * listed under OAuth2 in the Developer Portal character for character.
     */
    val redirectUri: String
        get() = "${BuildConfig.DISCORD_REDIRECT_SCHEME}:$REDIRECT_PATH"

    fun createAuthorizationSession(): DiscordAuthorizationSession {
        val state = randomUrlSafeString(byteCount = 32)
        val verifier = randomUrlSafeString(byteCount = 64)
        val challenge = sha256Base64Url(verifier)

        val uri =
            Uri
                .parse(AUTHORIZATION_ENDPOINT)
                .buildUpon()
                .appendQueryParameter("client_id", BuildConfig.DISCORD_APPLICATION_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("scope", SCOPES)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .build()

        DiscordLog.i("OAuth started (application ${BuildConfig.DISCORD_APPLICATION_ID}, redirect $redirectUri, scopes $SCOPES)")
        return DiscordAuthorizationSession(
            state = state,
            codeVerifier = verifier,
            authorizationUri = uri,
        )
    }

    suspend fun completeAuthorization(
        context: Context,
        session: DiscordAuthorizationSession,
        redirect: Uri,
    ): Result<DiscordAuthSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                DiscordLog.i("OAuth redirect received")
                require(redirect.scheme == BuildConfig.DISCORD_REDIRECT_SCHEME) {
                    "Unexpected Discord redirect scheme"
                }
                // Exactly [redirectUri] plus its query: no host, so `scheme://authorize/callback` is refused.
                require(redirect.authority.isNullOrEmpty() && redirect.path == REDIRECT_PATH) {
                    "Unexpected Discord redirect target"
                }
                require(redirect.getQueryParameter("state") == session.state) {
                    "Discord authorization state mismatch"
                }

                redirect.getQueryParameter("error")?.let { error ->
                    val description = redirect.getQueryParameter("error_description")
                    throw IllegalStateException(description ?: error)
                }

                val code =
                    requireNotNull(redirect.getQueryParameter("code")) {
                        "Discord authorization code is missing"
                    }

                val token = exchangeAuthorizationCode(code, session.codeVerifier)
                val account = runCatching { fetchAccount(token.accessToken) }.getOrNull()
                val authSession = token.toAuthSession(account)
                storeSession(context, authSession)
                DiscordLog.i(
                    "OAuth success" + account?.username?.let { " (@$it)" }.orEmpty() +
                        "; granted scopes: ${token.scope ?: "not reported"}",
                )
                authSession
            }.onFailure { DiscordLog.w("OAuth failed: ${it.message ?: it.javaClass.simpleName}") }
        }

    /**
     * The stored access token, refreshed when it is about to expire. Null when there is no session,
     * it was issued to another Discord application, or it expired and couldn't be refreshed.
     */
    suspend fun getValidAccessToken(context: Context): String? =
        withContext(Dispatchers.IO) {
            if (migrateLegacySession(context)) return@withContext null

            val prefs = context.dataStore.data.first()
            val currentToken = prefs[DiscordTokenKey]?.trim().orEmpty()
            if (currentToken.isEmpty()) return@withContext null

            val expiresAt = prefs[DiscordTokenExpiresAtKey] ?: 0L
            val now = System.currentTimeMillis()
            if (expiresAt == 0L || now + EXPIRY_SKEW_MS < expiresAt) return@withContext currentToken

            val refreshToken = prefs[DiscordRefreshTokenKey]?.trim().orEmpty()
            val refresh = if (refreshToken.isEmpty()) null else refreshAccessToken(context, refreshToken)
            refresh?.getOrNull()?.let { return@withContext it.accessToken }

            val error = refresh?.exceptionOrNull()
            val refused = error is DiscordHttpException && error.status in 400..401
            if (refused || (refresh == null && now >= expiresAt)) {
                DiscordLog.w("Discord session expired and couldn't be refreshed; reconnect required")
                invalidateSession(context)
                return@withContext null
            }
            // The refresh failed on the network: the token itself may still have a moment left.
            currentToken.takeIf { now < expiresAt }
        }

    /**
     * Sessions saved before SHINY had its own Discord application were issued to another one and
     * can't be used. Clears their tokens but keeps the account name, so settings offer "Reconnect".
     *
     * @return true when a session was cleared
     */
    suspend fun migrateLegacySession(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            val prefs = context.dataStore.data.first()
            if (prefs[DiscordTokenKey].isNullOrBlank() || prefs[DiscordClientIdKey] == BuildConfig.DISCORD_APPLICATION_ID) {
                return@withContext false
            }
            DiscordLog.w("Stored Discord session was issued to another application; reconnect required")
            invalidateSession(context)
            true
        }

    /** After the Gateway refused [accessToken]: does Discord's API still accept it, and with which scopes? */
    suspend fun checkToken(
        context: Context,
        accessToken: String,
    ): DiscordTokenCheck =
        withContext(Dispatchers.IO) {
            try {
                val info = json.decodeFromString<AuthorizationInfoResponse>(getJson(CURRENT_AUTHORIZATION_ENDPOINT, accessToken))
                DiscordLog.i("Discord API accepts the session (application ${info.application?.id}, scopes: ${info.scopes.joinToString(" ")})")
                DiscordTokenCheck.VALID
            } catch (e: DiscordHttpException) {
                if (e.status != 401) {
                    DiscordLog.w("Session check failed with HTTP ${e.status}")
                    return@withContext DiscordTokenCheck.UNKNOWN
                }
                val refreshToken = context.dataStore.data.first()[DiscordRefreshTokenKey]?.trim().orEmpty()
                val refresh = if (refreshToken.isEmpty()) null else refreshAccessToken(context, refreshToken)
                val error = refresh?.exceptionOrNull()
                when {
                    refresh?.isSuccess == true -> {
                        DiscordLog.i("Discord session refreshed")
                        DiscordTokenCheck.REFRESHED
                    }

                    refresh == null || (error is DiscordHttpException && error.status in 400..401) -> {
                        DiscordLog.w("Discord session revoked; reconnect required")
                        invalidateSession(context)
                        DiscordTokenCheck.REVOKED
                    }

                    else -> {
                        DiscordLog.w("Discord session refresh failed", error)
                        DiscordTokenCheck.UNKNOWN
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiscordLog.w("Session check failed", e)
                DiscordTokenCheck.UNKNOWN
            }
        }

    suspend fun fetchAccount(accessToken: String): DiscordAccount =
        withContext(Dispatchers.IO) {
            val response =
                getJson(
                    url = CURRENT_USER_ENDPOINT,
                    bearerToken = accessToken,
                )
            val userInfo = json.decodeFromString<UserInfoResponse>(response)
            val userId =
                userInfo.id
                    ?: userInfo.sub
                    ?: ""
            val username =
                userInfo.preferredUsername
                    ?: userInfo.username
                    ?: userId
            val displayName =
                userInfo.nickname
                    ?: userInfo.globalName
                    ?: userInfo.name
                    ?: username

            DiscordAccount(
                id = userId,
                username = username,
                displayName = displayName,
                avatarUrl =
                    userInfo.picture?.takeIf { it.isNotBlank() }
                        ?: buildAvatarUrl(
                            userId = userId,
                            avatarHash = userInfo.avatar,
                            discriminator = userInfo.discriminator,
                        ),
            )
        }

    /** Disconnect: forgets the session and the account. */
    suspend fun clearSession(context: Context) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs.remove(DiscordTokenKey)
                prefs.remove(DiscordRefreshTokenKey)
                prefs.remove(DiscordTokenExpiresAtKey)
                prefs.remove(DiscordClientIdKey)
                prefs.remove(DiscordUsernameKey)
                prefs.remove(DiscordNameKey)
                prefs.remove(DiscordAvatarUrlKey)
            }
        }
    }

    /** Forgets the tokens but keeps the account name and avatar: settings then ask to reconnect. */
    suspend fun invalidateSession(context: Context) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs.remove(DiscordTokenKey)
                prefs.remove(DiscordRefreshTokenKey)
                prefs.remove(DiscordTokenExpiresAtKey)
                prefs.remove(DiscordClientIdKey)
            }
        }
    }

    private suspend fun refreshAccessToken(
        context: Context,
        refreshToken: String,
    ): Result<DiscordAuthSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token =
                    postForm(
                        url = TOKEN_ENDPOINT,
                        params =
                            mapOf(
                                "client_id" to BuildConfig.DISCORD_APPLICATION_ID,
                                "grant_type" to "refresh_token",
                                "refresh_token" to refreshToken,
                            ),
                    ).let { json.decodeFromString<TokenResponse>(it) }

                val account = runCatching { fetchAccount(token.accessToken) }.getOrNull()
                val session = token.toAuthSession(account, fallbackRefreshToken = refreshToken)
                storeSession(context, session)
                DiscordLog.i("Discord token refreshed")
                session
            }
        }

    private fun exchangeAuthorizationCode(
        code: String,
        codeVerifier: String,
    ): TokenResponse =
        postForm(
            url = TOKEN_ENDPOINT,
            params =
                mapOf(
                    "client_id" to BuildConfig.DISCORD_APPLICATION_ID,
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to redirectUri,
                    "code_verifier" to codeVerifier,
                ),
        ).let { json.decodeFromString(it) }

    private suspend fun storeSession(
        context: Context,
        session: DiscordAuthSession,
    ) {
        context.dataStore.edit { prefs ->
            prefs[DiscordTokenKey] = session.accessToken
            session.refreshToken?.takeIf { it.isNotBlank() }?.let {
                prefs[DiscordRefreshTokenKey] = it
            }
            prefs[DiscordTokenExpiresAtKey] = session.expiresAtMillis
            prefs[DiscordClientIdKey] = BuildConfig.DISCORD_APPLICATION_ID
            session.account?.let { account ->
                prefs[DiscordUsernameKey] = account.username
                prefs[DiscordNameKey] = account.displayName
                prefs[DiscordAvatarUrlKey] = account.avatarUrl.orEmpty()
            }
        }
    }

    private fun buildAvatarUrl(
        userId: String,
        avatarHash: String?,
        discriminator: String?,
    ): String? {
        if (userId.isBlank()) {
            return null
        }

        val normalizedAvatarHash = avatarHash?.takeIf { it.isNotBlank() }
        if (normalizedAvatarHash != null) {
            val extension = if (normalizedAvatarHash.startsWith("a_")) "gif" else "png"
            return "https://cdn.discordapp.com/avatars/$userId/$normalizedAvatarHash.$extension?size=256"
        }

        val defaultIndex =
            discriminator
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?.rem(5)
                ?: userId.toLongOrNull()?.let { ((it shr 22) % 6L).toInt() }
                ?: 0
        return "https://cdn.discordapp.com/embed/avatars/$defaultIndex.png"
    }

    private fun TokenResponse.toAuthSession(
        account: DiscordAccount?,
        fallbackRefreshToken: String? = null,
    ): DiscordAuthSession {
        val expiresInMillis = expiresInSeconds.coerceAtLeast(0L) * 1000L
        val expiresAt =
            if (expiresInMillis > 0L) {
                System.currentTimeMillis() + expiresInMillis
            } else {
                0L
            }

        return DiscordAuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken ?: fallbackRefreshToken,
            expiresAtMillis = expiresAt,
            account = account,
        )
    }

    private fun postForm(
        url: String,
        params: Map<String, String>,
    ): String {
        val body =
            params.entries.joinToString(separator = "&") { (key, value) ->
                "${key.urlEncode()}=${value.urlEncode()}"
            }
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = REQUEST_TIMEOUT_MS
                readTimeout = REQUEST_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
            }

        connection.outputStream.use { output ->
            output.write(body.toByteArray(Charsets.UTF_8))
        }

        return connection.readResponse()
    }

    private fun getJson(
        url: String,
        bearerToken: String,
    ): String {
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = REQUEST_TIMEOUT_MS
                readTimeout = REQUEST_TIMEOUT_MS
                setRequestProperty("Authorization", "Bearer $bearerToken")
                setRequestProperty("Accept", "application/json")
            }

        return connection.readResponse()
    }

    private fun HttpURLConnection.readResponse(): String {
        val status = responseCode
        val stream = if (status in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        disconnect()

        if (status !in 200..299) {
            throw DiscordHttpException(status, body)
        }

        return body
    }

    private fun randomUrlSafeString(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    private fun sha256Base64Url(value: String): String {
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.US_ASCII))
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(digest)
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token")
        val accessToken: String,
        @SerialName("refresh_token")
        val refreshToken: String? = null,
        @SerialName("expires_in")
        val expiresInSeconds: Long = 0L,
        @SerialName("scope")
        val scope: String? = null,
    )

    @Serializable
    private data class AuthorizationInfoResponse(
        @SerialName("scopes")
        val scopes: List<String> = emptyList(),
        @SerialName("application")
        val application: AuthorizedApplication? = null,
    )

    @Serializable
    private data class AuthorizedApplication(
        @SerialName("id")
        val id: String? = null,
    )

    @Serializable
    private data class UserInfoResponse(
        @SerialName("id")
        val id: String? = null,
        @SerialName("sub")
        val sub: String? = null,
        @SerialName("avatar")
        val avatar: String? = null,
        @SerialName("picture")
        val picture: String? = null,
        @SerialName("discriminator")
        val discriminator: String? = null,
        @SerialName("preferred_username")
        val preferredUsername: String? = null,
        @SerialName("name")
        val name: String? = null,
        @SerialName("nickname")
        val nickname: String? = null,
        @SerialName("username")
        val username: String? = null,
        @SerialName("global_name")
        val globalName: String? = null,
    )
}
