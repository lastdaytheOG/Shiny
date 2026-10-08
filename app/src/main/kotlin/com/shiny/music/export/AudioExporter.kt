package com.shiny.music.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import android.net.ConnectivityManager
import androidx.datastore.preferences.core.edit
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.music.innertube.YouTube
import com.shiny.music.constants.AudioQuality
import com.shiny.music.constants.ExportedSongIdsKey
import com.shiny.music.db.MusicDatabase
import com.shiny.music.di.DownloadCache
import com.shiny.music.di.PlayerCache
import com.shiny.music.downloads.DownloadFolderStore
import com.shiny.music.downloads.OfflineFileDataSource
import com.shiny.music.playback.RangeChunkedDataSource
import com.shiny.music.ui.utils.resize
import com.shiny.music.utils.YTPlayerUtils
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.isLocalMediaId
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.OkHttpClient
import okhttp3.Request as HttpRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Export as MP3, for every song Shiny can play.
 *
 * A file on this device is copied as it is when it is already an MP3, and converted once
 * otherwise, keeping its tags and cover. A streamed song is taken from the best copy there
 * is — its download (decrypted on the way out), a complete copy in the download or player
 * cache, or else fetched fresh the way downloads are — and converted, tagged from the
 * library and given its artwork.
 *
 * Then the system's own save dialog picks the destination and handles duplicate names,
 * and the result is offered to Android's share sheet through [FileProvider], so the
 * receiving app never sees a private path.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object AudioExporter {

    data class Request(
        val songId: String,
        val title: String,
        val artist: String,
        val album: String?,
        /** Artwork for a streamed song the library doesn't have yet (a search result). */
        val thumbnailUrl: String? = null,
    )

    sealed interface State {
        data object Idle : State
        /** [progress] is how much of a streamed song has arrived, when that is known. */
        data class Preparing(val title: String, val progress: Float? = null) : State
        /** Prepared; waiting for the listener to choose where to save it. */
        data class ReadyToSave(val file: File, val fileName: String) : State
        data class Saving(val fileName: String) : State
        data class Done(val file: File, val fileName: String) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var ffmpegSessionId: Long? = null

    private var lastRequest: Request? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    const val UNSUPPORTED_SOURCE = "Export isn't available for this source."

    /** Every song can be exported: files on this device and streamed songs alike. */
    fun isEligible(songId: String): Boolean = songId.isNotBlank()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun database(): MusicDatabase
        fun folderStore(): DownloadFolderStore

        @DownloadCache
        fun downloadCache(): SimpleCache

        @PlayerCache
        fun playerCache(): SimpleCache
    }

    /** Same routing as downloads (proxy included), for the stream and the artwork. */
    private val http: OkHttpClient by lazy {
        com.music.innertube.SharedHttp.client.newBuilder()
            .proxy(YouTube.proxy)
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder().header("Proxy-Authorization", auth).build()
                } ?: response.request
            }
            .build()
    }

    /**
     * Starts an export. Returns false, doing nothing, when the song is not exportable, so
     * the caller can say so.
     */
    fun start(context: Context, request: Request): Boolean {
        if (!isEligible(request.songId)) return false
        if (_state.value is State.Preparing || _state.value is State.Saving) return true
        val appContext = context.applicationContext
        lastRequest = request
        _state.value = State.Preparing(request.title)
        job = scope.launch {
            _state.value = try {
                prepare(appContext, request)
            } catch (e: CancellationException) {
                State.Idle
            } catch (e: ExportException) {
                State.Failed(e.message)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Export failed")
                State.Failed("Something went wrong while preparing this song. Please try again.")
            }
        }
        return true
    }

    /** Runs the last export again, after a failure. */
    fun retry(context: Context) {
        val request = lastRequest ?: return dismiss()
        _state.value = State.Idle
        start(context, request)
    }

    fun cancel() {
        ffmpegSessionId?.let { runCatching { FFmpegKit.cancel(it) } }
        job?.cancel()
        _state.value = State.Idle
    }

    /** The save dialog was closed without choosing a place. */
    fun saveCancelled() {
        _state.value = State.Idle
    }

    fun dismiss() {
        _state.value = State.Idle
    }

    /** Writes the prepared MP3 to the document the listener created in the save dialog. */
    fun save(context: Context, destination: Uri) {
        val ready = _state.value as? State.ReadyToSave ?: return
        val appContext = context.applicationContext
        _state.value = State.Saving(ready.fileName)
        job = scope.launch {
            _state.value = try {
                val output = appContext.contentResolver.openOutputStream(destination, "w")
                    ?: throw ExportException(DESTINATION_UNAVAILABLE)
                output.use { out -> ready.file.inputStream().use { it.copyTo(out) } }
                lastRequest?.let { rememberExported(appContext, it.songId) }
                // The save dialog may have renamed it ("Song (1).mp3") rather than overwrite.
                val savedName = runCatching {
                    appContext.contentResolver.query(destination, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                }.getOrNull() ?: ready.fileName
                State.Done(ready.file, savedName)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Could not save the export")
                runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, destination) }
                State.Failed(
                    when {
                        e.isOutOfSpace() -> "There isn't enough free space there to save this song."
                        e is SecurityException || e is FileNotFoundException -> DESTINATION_UNAVAILABLE
                        e is ExportException -> e.message
                        else -> "The song couldn't be saved there. Try another location."
                    },
                )
            }
        }
    }

    /** Keeps the library's record of exported songs, which it has always read. */
    private suspend fun rememberExported(context: Context, songId: String) {
        runCatching {
            context.dataStore.edit { preferences ->
                val current = preferences[ExportedSongIdsKey].orEmpty().split(',').filter(String::isNotBlank)
                preferences[ExportedSongIdsKey] = (listOf(songId) + current.filterNot { it == songId }).take(1000).joinToString(",")
            }
        }
    }

    /** Offers the exported MP3 to Android's share sheet. */
    fun share(context: Context, file: File, fileName: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MP3_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, fileName)
            clipData = android.content.ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private suspend fun prepare(context: Context, request: Request): State =
        if (request.songId.isLocalMediaId()) prepareLocal(context, request) else prepareStreamed(context, request)

    /** Only the most recent export is kept, for the share sheet to read. */
    private fun freshWorkDir(context: Context): File =
        File(context.cacheDir, "export").apply {
            deleteRecursively()
            mkdirs()
        }

    private suspend fun prepareLocal(context: Context, request: Request): State = withContext(Dispatchers.IO) {
        val source = request.songId.toUri()
        val resolver = context.contentResolver
        val workDir = freshWorkDir(context)

        val mimeType = runCatching { resolver.getType(source) }.getOrNull()
        val size = runCatching {
            resolver.openAssetFileDescriptor(source, "r")?.use { it.length }
        }.getOrElse { throw it.asSourceError() } ?: throw ExportException(SOURCE_UNAVAILABLE)

        if (size > 0 && StatFs(context.cacheDir.path).availableBytes < size * 3 + 16L * 1024 * 1024) {
            throw ExportException("There isn't enough free space on this device to prepare this song.")
        }

        val tags = readTags(context, source)
        val fileName = exportFileName(
            artist = tags.artist ?: request.artist,
            title = tags.title ?: request.title,
        )
        val output = File(workDir, fileName)

        val isMp3 = mimeType.equals(MP3_MIME, ignoreCase = true) ||
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) == "mp3"
        if (isMp3) {
            // Already MP3: the listener's file, byte for byte, tags and cover included.
            copySource(context, source, output)
            ensureActive()
            return@withContext State.ReadyToSave(output, fileName)
        }

        val input = File(workDir, "source")
        copySource(context, source, input)
        ensureActive()
        val cover = tags.cover?.let { bitmap ->
            File(workDir, "cover.jpg").also { file ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            }
        } ?: coverFromMediaStore(context, source)?.let { bitmap ->
            File(workDir, "cover.jpg").also { file ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            }
        }

        transcode(input, cover, tags, request, output)
        input.delete()
        cover?.delete()
        State.ReadyToSave(output, fileName)
    }

    /** Converts [input] to a tagged MP3 at [output], with [cover] as its artwork. */
    private suspend fun transcode(input: File, cover: File?, tags: Tags, request: Request, output: File) {
        val args = buildList {
            addAll(listOf("-y", "-i", input.absolutePath))
            if (cover != null) addAll(listOf("-i", cover.absolutePath))
            addAll(listOf("-map", "0:a:0"))
            if (cover != null) {
                addAll(listOf("-map", "1:v:0", "-c:v", "mjpeg", "-disposition:v", "attached_pic"))
                addAll(listOf("-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (front)"))
            }
            // VBR V0: transparent for almost everything, about 245 kbit/s.
            addAll(listOf("-c:a", "libmp3lame", "-q:a", "0", "-id3v2_version", "3", "-map_metadata", "0"))
            tags.title?.let { addAll(listOf("-metadata", "title=$it")) }
            tags.artist?.let { addAll(listOf("-metadata", "artist=$it")) }
            tags.album?.let { addAll(listOf("-metadata", "album=$it")) }
            tags.albumArtist?.let { addAll(listOf("-metadata", "album_artist=$it")) }
            tags.year?.let { addAll(listOf("-metadata", "date=$it")) }
            tags.track?.let { addAll(listOf("-metadata", "track=$it")) }
            if (tags.title == null) addAll(listOf("-metadata", "title=${request.title}"))
            if (tags.artist == null && request.artist.isNotBlank()) addAll(listOf("-metadata", "artist=${request.artist}"))
            if (tags.album == null && !request.album.isNullOrBlank()) addAll(listOf("-metadata", "album=${request.album}"))
            add(output.absolutePath)
        }

        val session = FFmpegSession.create(args.toTypedArray())
        ffmpegSessionId = session.sessionId
        try {
            FFmpegKitConfig.ffmpegExecute(session)
        } finally {
            ffmpegSessionId = null
        }
        coroutineContext.ensureActive()
        if (ReturnCode.isCancel(session.returnCode)) throw CancellationException()
        if (!ReturnCode.isSuccess(session.returnCode) || !output.exists() || output.length() == 0L) {
            Timber.tag(TAG).w("FFmpeg failed (%s): %s", session.returnCode, session.output?.takeLast(2000))
            throw ExportException("This song couldn't be converted. The file may be damaged or in a format Shiny can't read.")
        }
    }

    private suspend fun prepareStreamed(context: Context, request: Request): State = withContext(Dispatchers.IO) {
        val deps = EntryPointAccessors.fromApplication(context, Dependencies::class.java)
        val workDir = freshWorkDir(context)
        val song = runCatching { deps.database().song(request.songId).firstOrNull() }.getOrNull()

        val input = File(workDir, "source")
        fetchStreamedAudio(context, deps, request, input)
        ensureActive()

        val coverUrl = (song?.song?.thumbnailUrl ?: request.thumbnailUrl)?.resize(1200, 1200)
        val cover = coverUrl?.let { downloadCover(it, File(workDir, "cover.jpg")) }
        val tags = Tags(
            title = song?.song?.title?.takeIf(String::isNotBlank) ?: request.title,
            artist = song?.artists?.joinToString(", ") { it.name }?.takeIf(String::isNotBlank)
                ?: request.artist.takeIf(String::isNotBlank),
            album = song?.album?.title ?: song?.song?.albumName ?: request.album?.takeIf(String::isNotBlank),
            albumArtist = null,
            year = (song?.album?.year ?: song?.song?.year)?.toString(),
            track = null,
            cover = null,
        )
        val fileName = exportFileName(artist = tags.artist, title = tags.title ?: request.title)
        val output = File(workDir, fileName)
        transcode(input, cover, tags, request, output)
        input.delete()
        cover?.delete()
        State.ReadyToSave(output, fileName)
    }

    /**
     * Writes the song's audio (as YouTube serves it: Opus or AAC) to [target], from the best
     * copy there is. A copy that fails part-way is dropped and the next one tried.
     */
    private suspend fun fetchStreamedAudio(context: Context, deps: Dependencies, request: Request, target: File) {
        val id = request.songId

        // 1. Downloaded to the chosen folder: the encrypted .shiny file, decrypted as it is read.
        val store = deps.folderStore()
        if (store.readableDocument(id) != null) {
            val spec = DataSpec.Builder().setUri(DownloadFolderStore.playbackUri(id)).setKey(id).build()
            if (tryCopy(OfflineFileDataSource(context, store, PlaceholderDataSource.INSTANCE), spec, target, request.title, null)) return
        }

        // 2. A complete copy in the download cache (downloads without a folder) or the player cache.
        for (cache in listOf(deps.downloadCache(), deps.playerCache())) {
            val length = runCatching { ContentMetadata.getContentLength(cache.getContentMetadata(id)) }
                .getOrDefault(C.LENGTH_UNSET.toLong())
            if (length <= 0 || !runCatching { cache.isCached(id, 0, length) }.getOrDefault(false)) continue
            val spec = DataSpec.Builder().setUri("shiny-export://$id".toUri()).setKey(id).build()
            // No upstream: read what is cached, never top it up.
            if (tryCopy(CacheDataSource(cache, null), spec, target, request.title, length)) return
        }

        // 3. Fetched fresh, the way downloads fetch it.
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: throw ExportException(NETWORK_UNAVAILABLE)
        val playback = YTPlayerUtils.playerResponseForPlayback(
            videoId = id,
            audioQuality = AudioQuality.OPUS,
            connectivityManager = connectivity,
        ).getOrElse { error ->
            Timber.tag(TAG).w(error, "Could not resolve %s for export", id)
            throw ExportException(NETWORK_UNAVAILABLE)
        }
        val length = playback.format.contentLength?.takeIf { it > 0 }
        length?.let { ensureSpace(context, it) }
        val spec = DataSpec.Builder().setUri(playback.streamUrl.toUri()).build()
        val source = RangeChunkedDataSource.Factory(OkHttpDataSource.Factory(http)).createDataSource()
        if (!tryCopy(source, spec, target, request.title, length)) throw ExportException(NETWORK_UNAVAILABLE)
    }

    /** Copies [spec] from [source] into [target]; false (and nothing left behind) if it fails. */
    private suspend fun tryCopy(source: DataSource, spec: DataSpec, target: File, title: String, knownLength: Long?): Boolean {
        try {
            copyDataSource(source, spec, target, title, knownLength)
            return target.length() > 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExportException) {
            throw e
        } catch (e: Exception) {
            if (e.isOutOfSpace()) throw ExportException("There isn't enough free space on this device to prepare this song.")
            Timber.tag(TAG).w(e, "Export source failed: %s", spec.uri.scheme)
            target.delete()
            return false
        }
    }

    private suspend fun copyDataSource(source: DataSource, spec: DataSpec, target: File, title: String, knownLength: Long?) {
        try {
            val opened = source.open(spec)
            val total = knownLength ?: opened.takeIf { it != C.LENGTH_UNSET.toLong() && it > 0 }
            target.outputStream().buffered().use { out ->
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                var shown = -1
                while (true) {
                    coroutineContext.ensureActive()
                    val read = source.read(buffer, 0, buffer.size)
                    if (read == C.RESULT_END_OF_INPUT) break
                    out.write(buffer, 0, read)
                    copied += read
                    if (total != null) {
                        // Whole percents only: the panel doesn't need a new state per buffer.
                        val percent = (copied * 100 / total).toInt().coerceIn(0, 100)
                        if (percent != shown) {
                            shown = percent
                            _state.value = State.Preparing(title, percent / 100f)
                        }
                    }
                }
            }
        } finally {
            runCatching { source.close() }
        }
    }

    /** The song's artwork as a JPEG FFmpeg can embed, or null when there is none to get. */
    private fun downloadCover(url: String, target: File): File? = runCatching {
        val bytes = http.newCall(HttpRequest.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        } ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        target
    }.getOrNull()

    private fun ensureSpace(context: Context, bytes: Long) {
        if (StatFs(context.cacheDir.path).availableBytes < bytes * 3 + 16L * 1024 * 1024) {
            throw ExportException("There isn't enough free space on this device to prepare this song.")
        }
    }

    private fun copySource(context: Context, source: Uri, target: File) {
        try {
            val input = context.contentResolver.openInputStream(source) ?: throw ExportException(SOURCE_UNAVAILABLE)
            input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
        } catch (e: ExportException) {
            throw e
        } catch (e: Exception) {
            if (e.isOutOfSpace()) throw ExportException("There isn't enough free space on this device to prepare this song.")
            throw e.asSourceError()
        }
    }

    private data class Tags(
        val title: String?,
        val artist: String?,
        val album: String?,
        val albumArtist: String?,
        val year: String?,
        val track: String?,
        val cover: Bitmap?,
    )

    private fun readTags(context: Context, source: Uri): Tags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, source)
            fun key(k: Int) = retriever.extractMetadata(k)?.trim()?.takeIf(String::isNotEmpty)
            Tags(
                title = key(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = key(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = key(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = key(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                year = key(MediaMetadataRetriever.METADATA_KEY_YEAR),
                track = key(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                cover = retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) },
            )
        } catch (e: Exception) {
            // Unreadable tags are not a reason to refuse the export; FFmpeg decides that.
            Tags(null, null, null, null, null, null, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** The album art MediaStore has for the file when the file itself has none. */
    private fun coverFromMediaStore(context: Context, source: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching { context.contentResolver.loadThumbnail(source, Size(1000, 1000), null) }.getOrNull()
    }

    private fun Throwable.asSourceError(): ExportException = when (this) {
        is SecurityException -> ExportException("Shiny doesn't have permission to read this song any more.")
        is FileNotFoundException -> ExportException(SOURCE_UNAVAILABLE)
        else -> ExportException("This song's file couldn't be read. It may be damaged.")
    }

    private fun Throwable.isOutOfSpace(): Boolean =
        this is IOException && (message?.contains("ENOSPC") == true || message?.contains("No space left", ignoreCase = true) == true)

    private class ExportException(override val message: String) : Exception(message)

    private const val TAG = "AudioExport"
    const val MP3_MIME = "audio/mpeg"
    private const val SOURCE_UNAVAILABLE = "This song's file isn't on this device any more."
    private const val NETWORK_UNAVAILABLE = "This song couldn't be downloaded for export. Check your connection and try again."
    private const val DESTINATION_UNAVAILABLE = "Shiny couldn't save to that location. Choose another one."
}

/**
 * "Artist - Song.mp3", safe on every Android filesystem: no reserved characters, no control
 * characters, no trailing dots or spaces, and short enough for FAT's 255-byte limit.
 */
fun exportFileName(artist: String?, title: String): String {
    val base = listOfNotNull(artist?.takeIf(String::isNotBlank), title.takeIf(String::isNotBlank))
        .joinToString(" - ")
        .ifBlank { "Song" }
    val cleaned = base
        .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trimEnd('.', ' ')
        .ifBlank { "Song" }
    var name = cleaned
    while (name.toByteArray(Charsets.UTF_8).size > 240) name = name.dropLast(1)
    return "${name.trimEnd('.', ' ')}.mp3"
}
