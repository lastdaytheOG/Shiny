package com.music.innertube

import com.music.innertube.models.Context
import com.music.innertube.models.MediaInfo
import com.music.innertube.models.ReturnYouTubeDislikeResponse
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeLocale
import com.music.innertube.models.body.*
import com.music.innertube.models.response.NextResponse
import com.music.innertube.utils.parseCookieString
import com.music.innertube.utils.sha1
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.net.Proxy
import java.io.IOException
import kotlinx.coroutines.delay
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Provide access to InnerTube endpoints.
 * For making HTTP requests, not parsing response.
 */
@OptIn(ExperimentalEncodingApi::class)
class InnerTube {
    private var httpClient = createClient()

    var locale = YouTubeLocale(
        gl = Locale.getDefault().country,
        hl = Locale.getDefault().toLanguageTag()
    )
    var visitorData: String? = null

    /**
     * The account id sent as `onBehalfOfUser`, only while [cookie] actually signs someone in.
     * Naming a user without credentials makes YouTube refuse every request with 401
     * UNAUTHENTICATED (measured 2026-09-30): a sign-in abandoned after the page had saved the
     * id but before the cookie existed left every song unplayable.
     */
    var dataSyncId: String? = null
        get() = field?.takeIf { "SAPISID" in cookieMap }
    var cookie: String? = null
        set(value) {
            field = value
            cookieMap = if (value == null) emptyMap() else parseCookieString(value)
        }
    private var cookieMap = emptyMap<String, String>()

    var proxy: Proxy? = null
        set(value) {
            field = value
            httpClient.close()
            httpClient = createClient()
        }
    
    var proxyAuth: String? = null

    var useLoginForBrowse: Boolean = false
    var ipVersion: com.music.innertube.models.IpVersion = com.music.innertube.models.IpVersion.AUTO

    @OptIn(ExperimentalSerializationApi::class)
    private fun createClient() = HttpClient(OkHttp) {
        expectSuccess = true

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
        }

        install(ContentEncoding) {
            gzip(0.9F)
            deflate(0.8F)
        }

        // Enhanced network configuration for better performance
        engine {
            config {
                dispatcher(SharedHttp.ktorDispatcher())
                // Connection pool settings for better connection reuse
                connectionPool(
                    okhttp3.ConnectionPool(
                        10, // maxIdleConnections
                        5, // keepAliveDuration
                        java.util.concurrent.TimeUnit.MINUTES
                    )
                )
                
                connectTimeout(CONNECT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                readTimeout(SOCKET_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                writeTimeout(SOCKET_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                
                // Enable HTTP/2 for better performance
                protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
                
                retryOnConnectionFailure(true)
                
                // Cache configuration for better performance
                cache(
                    okhttp3.Cache(
                        directory = java.io.File(System.getProperty("java.io.tmpdir"), "http_cache"),
                        maxSize = 50L * 1024L * 1024L // 50 MB
                    )
                )
                
                this@InnerTube.proxy?.let { proxyConfig ->
                    proxy(proxyConfig)
                }
                
                this@InnerTube.proxyAuth?.let { auth ->
                    proxyAuthenticator { _, response ->
                        response.request.newBuilder()
                            .header("Proxy-Authorization", auth)
                            .build()
                    }
                }
            }
        }

        // Request timeout configuration
        // Uploads lift the request limit for themselves (uploadSongData, uploadCustomThumbnail).
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }

        defaultRequest {
            url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
            header("Accept", "application/json")
            // Use the user's locale instead of hardcoding en-US so region-specific
            // catalogs and language-matched recommendations are returned.
            header("Accept-Language", "${locale.hl},${locale.gl};q=0.9,en;q=0.8")
            header("Cache-Control", "no-cache")
        }
    }

    private fun HttpRequestBuilder.ytClient(client: YouTubeClient, setLogin: Boolean = false) {
        contentType(ContentType.Application.Json)
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientId /* Not a typo. The Client-Name header does contain the client id. */)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("X-Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
            append("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
            // Sent to EVERY client, including `loginSupported = false` ones. Withholding it from
            // those (on the theory that an account-bound visitor id without credentials looks like
            // a hijacked session) was tried and measured to be backwards: VISIONOS and
            // ANDROID_VR 1.65.10 — the only clients that currently mint a fully readable stream
            // URL — *require* it. Without one they answer UNPLAYABLE / LOGIN_REQUIRED with zero
            // formats. `dataSyncId` is the genuinely account-scoped identifier and stays gated on
            // `loginSupported` in YouTubeClient.toContext (`onBehalfOfUser`).
            visitorData?.let { append("X-Goog-Visitor-Id", it) }
            if (setLogin && client.loginSupported) {
                cookie?.let { cookie ->
                    append("cookie", cookie)
                    if ("SAPISID" !in cookieMap) return@let
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidHash = sha1("$currentTime ${cookieMap["SAPISID"]} ${YouTubeClient.ORIGIN_YOUTUBE_MUSIC}")
                    append("Authorization", "SAPISIDHASH ${currentTime}_${sapisidHash}")
                }
            }
        }
        userAgent(client.userAgent)
        parameter("prettyPrint", false)
    }

    /**
     * Simple retry wrapper for transient IO errors (socket aborts, resets).
     * Retries the given block up to [maxAttempts] times with exponential backoff, but starts no
     * new attempt once [RETRY_WINDOW_MS] have passed: a request that failed that late timed out on
     * a bad connection, and trying it again only kept a screen spinning (up to 3 × 60 s before).
     * [retryIf] narrows which failures are retried at all; see [withRetryIfUnsent].
     * Cancellation is respected since [delay] will throw if the coroutine is cancelled.
     */
    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelay: Long = 500L,
        factor: Double = 2.0,
        retryIf: (IOException) -> Boolean = { true },
        block: suspend () -> T,
    ): T {
        val startedAt = System.nanoTime()
        var currentDelay = initialDelay
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                attempt++
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                if (attempt >= maxAttempts || elapsedMs >= RETRY_WINDOW_MS || !retryIf(e)) throw e
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
    }

    /**
     * [withRetry] for requests that change something: add, create, delete, like, history, upload.
     * Retried only when the request cannot have reached YouTube because no connection was made.
     * After a timeout or a reset mid-request YouTube may already have applied it, and a second
     * attempt applied it again: a second playlist, a song added twice.
     */
    private suspend fun <T> withRetryIfUnsent(block: suspend () -> T): T =
        withRetry(retryIf = { it.neverSent() }, block = block)

    private fun IOException.neverSent(): Boolean =
        // ConnectException includes ktor's ConnectTimeoutException.
        this is java.net.ConnectException ||
            this is java.net.UnknownHostException ||
            this is java.net.NoRouteToHostException

    private companion object {
        /** A failure later than this after the first attempt started is not retried. */
        const val RETRY_WINDOW_MS = 5_000L

        /**
         * Longest a screen waits on one request. With [RETRY_WINDOW_MS], a page on a dead
         * connection gives up in about 15 s instead of about 3 minutes.
         */
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val CONNECT_TIMEOUT_MS = 10_000L

        /** Longest gap between two packets of one response. */
        const val SOCKET_TIMEOUT_MS = 10_000L
    }

    suspend fun search(
        client: YouTubeClient,
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean? = null,
    ) = withRetry {
        // When [setLogin] is null, fall back to the global browse-login preference.
        // Callers can pass `false` to force an anonymous search (no auth header,
        // no dataSyncId) so the query is NOT recorded in the user's YouTube search
        // history — used for background Spotify→YouTube matching.
        val effectiveLogin = (setLogin ?: useLoginForBrowse) && !cookie.isNullOrEmpty()
        httpClient.post("search") {
            ytClient(client, setLogin = effectiveLogin)
            setBody(
                SearchBody(
                    context = client.toContext(
                        locale,
                        visitorData,
                        if (effectiveLogin) dataSyncId else null
                    ),
                    query = query,
                    params = params
                )
            )
            parameter("continuation", continuation)
            parameter("ctoken", continuation)
        }
    }

    /**
     * Opens (or keeps warm) the TLS connection that `/player` requests use, without asking for
     * anything: a HEAD on the origin. The connection then sits in the pool for the real request.
     */
    suspend fun preconnect() {
        httpClient.head(YouTubeClient.ORIGIN_YOUTUBE_MUSIC) {
            expectSuccess = false
        }
    }

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        signatureTimestamp: Int?,
        poToken: String? = null,
    ) = withRetry {
        httpClient.post("player") {
            ytClient(client, setLogin = true)
            setBody(
                PlayerBody(
                    // Must stay consistent with the X-Goog-Visitor-Id header set in ytClient.
                    context = client.toContext(locale, visitorData, dataSyncId).let {
                        if (client.isEmbedded) {
                            it.copy(
                                thirdParty = Context.ThirdParty(
                                    embedUrl = "https://www.youtube.com/watch?v=${videoId}"
                                )
                            )
                        } else it
                    },
                    videoId = videoId,
                    playlistId = playlistId,
                    playbackContext = if (client.useSignatureTimestamp && signatureTimestamp != null) {
                        PlayerBody.PlaybackContext(
                            PlayerBody.PlaybackContext.ContentPlaybackContext(
                                signatureTimestamp
                            )
                        )
                    } else null,
                    serviceIntegrityDimensions = if (client.useWebPoTokens && poToken != null) {
                        PlayerBody.ServiceIntegrityDimensions(poToken)
                    } else null,
                )
            )
        }
    }

    suspend fun registerPlayback(
        url: String,
        cpn: String,
        playlistId: String?,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
    ) = withRetryIfUnsent {
        httpClient.get(url) {
            ytClient(client, true)
            parameter("ver", "2")
            parameter("c", client.clientName)
            parameter("cpn", cpn)

            if (playlistId != null) {
                parameter("list", playlistId)
                parameter("referrer", "https://music.youtube.com/playlist?list=$playlistId")
            }
        }
    }

    suspend fun browse(
        client: YouTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
        formData: FormData? = null,
    ) = withRetry {
        val effectiveLogin = (setLogin || useLoginForBrowse) && !cookie.isNullOrEmpty()
        httpClient.post("browse") {
            ytClient(client, setLogin = effectiveLogin)
            setBody(
                BrowseBody(
                    context = client.toContext(
                        locale,
                        visitorData,
                        if (effectiveLogin) dataSyncId else null
                    ),
                    browseId = browseId,
                    params = params,
                    continuation = continuation,
                    formData = formData,
                )
            )
        }
    }

    suspend fun next(
        client: YouTubeClient,
        videoId: String?,
        playlistId: String?,
        playlistSetVideoId: String?,
        index: Int?,
        params: String?,
        continuation: String? = null,
    ) = withRetry {
        httpClient.post("next") {
            ytClient(client, setLogin = true)
            setBody(
                NextBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    videoId = videoId,
                    playlistId = playlistId,
                    playlistSetVideoId = playlistSetVideoId,
                    index = index,
                    params = params,
                    continuation = continuation
                )
            )
        }
    }

    suspend fun feedback(
        client: YouTubeClient,
        tokens: List<String>
    ) = withRetryIfUnsent {
        httpClient.post("feedback") {
            ytClient(client, setLogin = true)
            setBody(
                FeedbackBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    feedbackTokens = tokens
                )
            )
        }
    }

    suspend fun getSearchSuggestions(
        client: YouTubeClient,
        input: String,
    ) = withRetry {
        httpClient.post("music/get_search_suggestions") {
            ytClient(client)
            setBody(
                GetSearchSuggestionsBody(
                    context = client.toContext(locale, visitorData, null),
                    input = input
                )
            )
        }
    }

    suspend fun getQueue(
        client: YouTubeClient,
        videoIds: List<String>?,
        playlistId: String?,
    ) = withRetry {
        httpClient.post("music/get_queue") {
            ytClient(client)
            setBody(
                GetQueueBody(
                    context = client.toContext(locale, visitorData, null),
                    videoIds = videoIds,
                    playlistId = playlistId
                )
            )
        }
    }

    suspend fun getTranscript(
        client: YouTubeClient,
        videoId: String,
    ) = withRetry {
        httpClient.post("https://music.youtube.com/youtubei/v1/get_transcript") {
            parameter("key", "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3")
            headers {
                append("Content-Type", "application/json")
            }
            setBody(
                GetTranscriptBody(
                    context = client.toContext(locale, null, null),
                    params = Base64.Default.encode(
                        "\n${11.toChar()}$videoId".encodeToByteArray()
                    )
                )
            )
        }
    }

    suspend fun getSwJsData() = withRetry { httpClient.get("https://music.youtube.com/sw.js_data") }

    suspend fun accountMenu(client: YouTubeClient) = withRetry {
        httpClient.post("account/account_menu") {
            ytClient(client, setLogin = true)
            setBody(AccountMenuBody(client.toContext(locale, visitorData, dataSyncId)))
        }
    }

    suspend fun likeVideo(
        client: YouTubeClient,
        videoId: String,
    ) = withRetryIfUnsent {
        httpClient.post("like/like") {
            ytClient(client, setLogin = true)
            setBody(
                LikeBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    target = LikeBody.Target.VideoTarget(videoId)
                )
            )
        }
    }

    suspend fun unlikeVideo(
        client: YouTubeClient,
        videoId: String,
    ) = withRetryIfUnsent {
        httpClient.post("like/removelike") {
            ytClient(client, setLogin = true)
            setBody(
                LikeBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    target = LikeBody.Target.VideoTarget(videoId)
                )
            )
        }
    }

    suspend fun subscribeChannel(
        client: YouTubeClient,
        channelId: String,
        params: String? = null,
    ) = withRetryIfUnsent {
        httpClient.post("subscription/subscribe") {
            ytClient(client, setLogin = true)
            setBody(
                SubscribeBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    channelIds = listOf(channelId)
                )
            )
        }
    }

    suspend fun unsubscribeChannel(
        client: YouTubeClient,
        channelId: String,
        params: String? = null,
    ) = withRetryIfUnsent {
        httpClient.post("subscription/unsubscribe") {
            ytClient(client, setLogin = true)
            setBody(
                SubscribeBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    channelIds = listOf(channelId)
                )
            )
        }
    }

    suspend fun likePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ) = withRetryIfUnsent {
        httpClient.post("like/like") {
            ytClient(client, setLogin = true)
            setBody(
                LikeBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    target = LikeBody.Target.PlaylistTarget(playlistId)
                )
            )
        }
    }

    suspend fun unlikePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ) = withRetryIfUnsent {
        httpClient.post("like/removelike") {
            ytClient(client, setLogin = true)
            setBody(
                LikeBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    target = LikeBody.Target.PlaylistTarget(playlistId)
                )
            )
        }
    }

    suspend fun addToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoId: String,
    ) = withRetryIfUnsent {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId.removePrefix("VL"),
                    actions = listOf(
                        Action.AddVideoAction(addedVideoId = videoId)
                    )
                )
            )
        }
    }

    suspend fun addPlaylistToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        addPlaylistId: String,
    ) = withRetryIfUnsent {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId.removePrefix("VL"),
                    actions = listOf(
                        Action.AddPlaylistAction(addedFullListId = addPlaylistId)
                    )
                )
            )
        }
    }

    suspend fun removeFromPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoId: String,
        setVideoId: String,
    ) = withRetryIfUnsent {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId.removePrefix("VL"),
                    actions = listOf(
                        Action.RemoveVideoAction(
                            removedVideoId = videoId,
                            setVideoId = setVideoId,
                        )
                    )
                )
            )
        }
    }

    suspend fun moveSongPlaylist(
        client: YouTubeClient,
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
    ) = withRetryIfUnsent {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId,
                    actions = listOf(
                        Action.MoveVideoAction(
                            movedSetVideoIdSuccessor = successorSetVideoId,
                            setVideoId = setVideoId,
                        )
                    )
                )
            )
        }
    }

    suspend fun createPlaylist(
        client: YouTubeClient,
        title: String,
    ) = withRetryIfUnsent {
        httpClient.post("playlist/create") {
            ytClient(client, true)
            setBody(
                CreatePlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    title = title
                )
            )
        }
    }

    suspend fun renamePlaylist(
        client: YouTubeClient,
        playlistId: String,
        name: String,
    ) = withRetryIfUnsent {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId,
                    actions = listOf(
                        Action.RenamePlaylistAction(
                            playlistName = name
                        )
                    )
                )
            )
        }
    }
    
    suspend fun getUploadCustomThumbnailLink(
        client: YouTubeClient,
        contentLength: Int
    ) = withRetry {
        httpClient.post("https://music.youtube.com/playlist_image_upload/playlist_custom_thumbnail") {
            ytClient(client, setLogin = true)
            headers {
                append("X-Goog-Upload-Command", "start")
                append("X-Goog-Upload-Protocol", "resumable")
                append("X-Goog-Upload-Header-Content-Length", contentLength.toString())
            }
        }
    }

    suspend fun uploadCustomThumbnail(
        client: YouTubeClient,
        uploadId: String,
        image: ByteArray,
    ) = withRetryIfUnsent {
        httpClient.post("https://music.youtube.com/playlist_image_upload/playlist_custom_thumbnail") {
            ytClient(client, setLogin = true)
            parameter("upload_id", uploadId)
            parameter("upload_protocol", "resumable")
            headers {
                append("X-Goog-Upload-Command", "upload, finalize")
                append("X-Goog-Upload-Offset", "0")
            }
            setBody(image)
            timeout { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS }
        }
    }

    suspend fun setThumbnailPlaylist(
        client: YouTubeClient,
        playlistId: String,
        blobId: String,
    ) = withRetryIfUnsent {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId,
                    actions = listOf(
                        Action.SetCustomThumbnailAction(
                            addedCustomThumbnail = Action.SetCustomThumbnailAction.AddedCustomThumbnail(
                                playlistScottyEncryptedBlobId = blobId
                            )
                        )
                    )
                )
            )
        }
    }

    suspend fun removeThumbnailPlaylist(
        client: YouTubeClient,
        playlistId: String
    ) = withRetryIfUnsent {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId,
                    actions = listOf(
                        Action.RemoveCustomThumbnailAction()
                    )
                )
            )
        }
    }

    suspend fun deletePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ) = withRetryIfUnsent {
        httpClient.post("playlist/delete") {
            println("deleting $playlistId")
            ytClient(client, setLogin = true)
            setBody(
                PlaylistDeleteBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId
                )
            )
        }
    }

    private suspend fun returnYouTubeDislike(videoId: String) = withRetry {
        httpClient.get("https://returnyoutubedislikeapi.com/Votes?videoId=$videoId") {
            contentType(ContentType.Application.Json)
        }
    }


    /**
     * Initialize a song upload to YouTube Music.
     * Returns the upload URL in the X-Goog-Upload-URL header.
     */
    suspend fun initSongUpload(
        filename: String,
        contentLength: Long
    ) = withRetryIfUnsent {
        val authUser = "0"
        httpClient.post("https://upload.youtube.com/upload/usermusic/http?authuser=$authUser") {
            headers {
                append("X-Goog-Upload-Command", "start")
                append("X-Goog-Upload-Protocol", "resumable")
                append("X-Goog-Upload-Header-Content-Length", contentLength.toString())
                append("X-Goog-AuthUser", authUser)
                append("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                cookie?.let { cookie ->
                    append("cookie", cookie)
                    if ("SAPISID" !in cookieMap) return@let
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidHash = sha1("$currentTime ${cookieMap["SAPISID"]} ${YouTubeClient.ORIGIN_YOUTUBE_MUSIC}")
                    append("Authorization", "SAPISIDHASH ${currentTime}_${sapisidHash}")
                }
            }
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("filename=$filename")
        }
    }

    suspend fun uploadSongData(
        uploadUrl: String,
        data: ByteArray,
        onProgress: ((Float) -> Unit)? = null
    ) = withRetryIfUnsent {
        httpClient.post(uploadUrl) {
            headers {
                append("X-Goog-Upload-Command", "upload, finalize")
                append("X-Goog-Upload-Offset", "0")
                append("X-Goog-AuthUser", "0")
                append("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                cookie?.let { cookie ->
                    append("cookie", cookie)
                    if ("SAPISID" !in cookieMap) return@let
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidHash = sha1("$currentTime ${cookieMap["SAPISID"]} ${YouTubeClient.ORIGIN_YOUTUBE_MUSIC}")
                    append("Authorization", "SAPISIDHASH ${currentTime}_${sapisidHash}")
                }
            }
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(data)
            // A whole song on a phone's upload link takes longer than any screen should wait.
            timeout { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS }
            onUpload { bytesSentTotal, contentLength ->
                contentLength?.let {
                    onProgress?.invoke(bytesSentTotal.toFloat() / it.toFloat())
                }
            }
        }
    }

    /**
     * Delete a privately owned (uploaded) song from YouTube Music.
     */
    suspend fun deletePrivatelyOwnedEntity(entityId: String) = withRetryIfUnsent {
        val context = YouTubeClient.WEB_REMIX.toContext(locale, visitorData, null)
        val requestBody = """{"context":${Json.encodeToString(context)},"entityId":"$entityId"}"""
        httpClient.post("https://music.youtube.com/youtubei/v1/music/delete_privately_owned_entity") {
            contentType(ContentType.Application.Json)
            headers {
                append("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
                append("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                cookie?.let { cookie ->
                    append("cookie", cookie)
                    if ("SAPISID" !in cookieMap) return@let
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidHash = sha1("$currentTime ${cookieMap["SAPISID"]} ${YouTubeClient.ORIGIN_YOUTUBE_MUSIC}")
                    append("Authorization", "SAPISIDHASH ${currentTime}_${sapisidHash}")
                }
            }
            parameter("key", "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3")
            parameter("prettyPrint", false)
            setBody(requestBody)
        }
    }

    suspend fun getMediaInfo(videoId: String): Result<MediaInfo> =
        runCatching {
            val response = next(client = YouTubeClient.WEB, videoId, null, null, null, null, null).body<NextResponse>()

            val baseForInfo =
                response.contents.twoColumnWatchNextResults
                    ?.results
                    ?.results
                    ?.content
                    ?.find {
                        it?.videoSecondaryInfoRenderer != null
                    }?.videoSecondaryInfoRenderer

            val baseForTitle =
                response.contents.twoColumnWatchNextResults
                    ?.results
                    ?.results
                    ?.content
                    ?.find {
                        it?.videoPrimaryInfoRenderer != null
                    }?.videoPrimaryInfoRenderer

            val returnYouTubeDislikeResponse =
                returnYouTubeDislike(videoId).body<ReturnYouTubeDislikeResponse>()

            return@runCatching MediaInfo(
                videoId = videoId,
                title = baseForTitle
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text,
                author = baseForInfo
                    ?.owner
                    ?.videoOwnerRenderer
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text,
                authorId =
                    baseForInfo
                        ?.owner
                        ?.videoOwnerRenderer
                        ?.navigationEndpoint
                        ?.browseEndpoint
                        ?.browseId,
                authorThumbnail =
                    baseForInfo
                        ?.owner
                        ?.videoOwnerRenderer
                        ?.thumbnail
                        ?.thumbnails
                        ?.find {
                            it.height == 48
                        }?.url
                        ?.replace("s48", "s960"),
                description = baseForInfo?.attributedDescription?.content,
                subscribers =
                    baseForInfo
                        ?.owner
                        ?.videoOwnerRenderer
                        ?.subscriberCountText
                        ?.simpleText?.split(" ")?.firstOrNull(),
                uploadDate = baseForTitle?.dateText?.simpleText,
                viewCount = returnYouTubeDislikeResponse.viewCount,
                like = returnYouTubeDislikeResponse.likes,
                dislike = returnYouTubeDislikeResponse.dislikes,
            )

        }


}
