package com.shiny.music.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.AesFlushingCipher
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import com.shiny.music.di.DownloadCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where finished downloads live once the listener has chosen a download folder.
 *
 * ExoPlayer's DownloadManager still does the downloading — queueing, retries, progress and
 * the notification all stay as they were — into the private download cache. That cache is
 * now only a staging area: when a download completes, [adopt] writes it out as one file per
 * song in the chosen folder (Storage Access Framework, no broad storage permission) and
 * drops the private copy, so a permanent download no longer grows Shiny's own storage.
 *
 * With no folder chosen nothing changes: downloads stay in the private cache, exactly as
 * before, and every download made before a folder was chosen keeps playing from there
 * until the listener moves it with [moveToCurrentFolder].
 *
 * The files are streamed songs, so they are written in Shiny's offline format (AES-CTR,
 * seekable, keyed by song id) rather than as audio other apps can open. That keeps a
 * download what it was in the private cache — an offline copy for Shiny — and not an
 * export of streamed audio. Export is only offered for the listener's own files.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Singleton
class DownloadFolderStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @DownloadCache private val downloadCache: SimpleCache,
) {
    /** One downloaded song in a folder: the folder it went to, its file, its size. */
    data class Entry(val tree: String, val document: String, val length: Long)

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _folder = MutableStateFlow(prefs.getString(KEY_FOLDER, null)?.toUri())

    /** The folder new downloads go to, or null for Shiny's own storage. */
    val folder: StateFlow<Uri?> = _folder

    private val _entries = MutableStateFlow(loadEntries())

    /** Every song whose download lives in a folder, by song id. */
    val entries: StateFlow<Map<String, Entry>> = _entries

    /** Files already opened once this session, so playback does not query the provider per seek. */
    private val verified = ConcurrentHashMap.newKeySet<String>()

    fun contains(id: String): Boolean = _entries.value.containsKey(id)

    fun documentFor(id: String): Uri? = _entries.value[id]?.document?.toUri()

    /**
     * The song's file if it can actually be opened right now. A file deleted from the folder,
     * or a folder whose permission was revoked, returns null so playback falls back to streaming
     * instead of failing.
     */
    fun readableDocument(id: String): Uri? {
        val uri = documentFor(id) ?: return null
        if (id in verified) return uri
        val readable = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
        if (!readable) return null
        verified += id
        return uri
    }

    /** Called when an offline file failed to open, so the next attempt re-checks it. */
    fun forgetVerified(id: String) {
        verified -= id
    }

    /** Makes [tree] the folder for new downloads. The caller has taken its persistable permission. */
    fun setFolder(tree: Uri) {
        val known = prefs.getStringSet(KEY_TREES, emptySet()).orEmpty() + tree.toString()
        prefs.edit {
            putString(KEY_FOLDER, tree.toString())
            putStringSet(KEY_TREES, known)
        }
        _folder.value = tree
        releaseUnusedPermissions()
    }

    /** New downloads go back to Shiny's own storage. Files already in a folder stay there. */
    fun useAppStorage() {
        prefs.edit { remove(KEY_FOLDER) }
        _folder.value = null
        releaseUnusedPermissions()
    }

    /** Whether Shiny can still write to [tree]: the permission is held and the folder exists. */
    fun isAccessible(tree: Uri? = _folder.value): Boolean {
        tree ?: return true
        val held = context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        if (!held) return false
        return runCatching {
            DocumentFile.fromTreeUri(context, tree)?.let { it.exists() && it.canWrite() } == true
        }.getOrDefault(false)
    }

    /**
     * Moves a just-completed download from the private cache into the current folder.
     * Returns false, leaving the private copy in place and playable, when there is no folder,
     * the folder cannot be reached, or the copy did not complete.
     */
    suspend fun adopt(id: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { adoptLocked(id) }
    }

    /** Removes a song's file from its folder, when its download is removed. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entry = _entries.value[id] ?: return@withLock
            deleteDocument(entry.document.toUri())
            remove(id)
            releaseUnusedPermissions()
        }
    }

    /**
     * The explicit migration: brings every download in [ids] that is not in the current
     * folder — whether it sits in Shiny's storage or in a previously chosen folder — into it.
     * A source is only deleted after its copy has been written and its size checked.
     */
    suspend fun moveToCurrentFolder(
        ids: Collection<String>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): MoveResult = withContext(Dispatchers.IO) {
        val tree = _folder.value ?: return@withContext MoveResult(0, ids.size)
        val pending = ids.filter { id -> _entries.value[id]?.tree != tree.toString() }
        var moved = 0
        pending.forEachIndexed { index, id ->
            val ok = mutex.withLock {
                val entry = _entries.value[id]
                if (entry == null) adoptLocked(id) else relocateLocked(id, entry, tree)
            }
            if (ok) moved++
            onProgress(index + 1, pending.size)
        }
        MoveResult(moved, pending.size - moved)
    }

    data class MoveResult(val moved: Int, val failed: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _moving = MutableStateFlow<Pair<Int, Int>?>(null)

    /** Progress of a move started with [startMove], as (done, total); null when none is running. */
    val moving: StateFlow<Pair<Int, Int>?> = _moving

    private val _lastMove = MutableStateFlow<MoveResult?>(null)

    /** How the most recent move ended, for the page that started it to report. */
    val lastMove: StateFlow<MoveResult?> = _lastMove

    /**
     * Runs [moveToCurrentFolder] on the store's own scope, so leaving the settings page part
     * way through does not abandon it.
     */
    fun startMove(ids: Collection<String>) {
        if (_moving.value != null || ids.isEmpty()) return
        _lastMove.value = null
        _moving.value = 0 to ids.size
        scope.launch {
            val result = runCatching {
                moveToCurrentFolder(ids) { done, total -> _moving.value = done to total }
            }.getOrElse { MoveResult(0, ids.size) }
            _moving.value = null
            _lastMove.value = result
        }
    }

    fun consumeLastMove() {
        _lastMove.value = null
    }

    fun totalBytes(tree: String? = null): Long =
        _entries.value.values.filter { tree == null || it.tree == tree }.sumOf { it.length }

    private fun adoptLocked(id: String): Boolean {
        val tree = _folder.value ?: return false
        if (!isAccessible(tree)) return false
        val length = ContentMetadata.getContentLength(downloadCache.getContentMetadata(id))
        if (length <= 0 || !downloadCache.isCached(id, 0, length)) return false
        val directory = DocumentFile.fromTreeUri(context, tree) ?: return false
        val previous = _entries.value[id]

        val document = runCatching { directory.createFile(MIME_TYPE, fileName(id)) }.getOrNull() ?: return false
        val written = runCatching {
            context.contentResolver.openOutputStream(document.uri, "w")!!.use { out -> encryptFromCache(id, out) }
        }.onFailure { Timber.tag(TAG).w(it, "Could not write download $id to the folder") }.getOrDefault(-1L)

        if (written != length) {
            deleteDocument(document.uri)
            return false
        }
        put(id, Entry(tree.toString(), document.uri.toString(), length))
        previous?.let { deleteDocument(it.document.toUri()) }
        // The folder copy is verified; the private one is now only duplicate storage.
        runCatching { downloadCache.removeResource(id) }
        releaseUnusedPermissions()
        return true
    }

    private fun relocateLocked(id: String, entry: Entry, tree: Uri): Boolean {
        if (!isAccessible(tree)) return false
        val directory = DocumentFile.fromTreeUri(context, tree) ?: return false
        val document = runCatching { directory.createFile(MIME_TYPE, fileName(id)) }.getOrNull() ?: return false
        // The bytes are already in Shiny's format, keyed by song id, so they copy as they are.
        val written = runCatching {
            context.contentResolver.openInputStream(entry.document.toUri())!!.use { input ->
                context.contentResolver.openOutputStream(document.uri, "w")!!.use { out -> input.copyTo(out, BUFFER) }
            }
        }.onFailure { Timber.tag(TAG).w(it, "Could not move download $id") }.getOrDefault(-1L)

        if (written != entry.length) {
            deleteDocument(document.uri)
            return false
        }
        put(id, Entry(tree.toString(), document.uri.toString(), entry.length))
        deleteDocument(entry.document.toUri())
        verified -= id
        releaseUnusedPermissions()
        return true
    }

    private fun encryptFromCache(id: String, out: OutputStream): Long {
        val source = CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheWriteDataSinkFactory(null)
            .createDataSource()
        val cipher = AesFlushingCipher(Cipher.ENCRYPT_MODE, KEY, id, 0)
        val buffer = ByteArray(BUFFER)
        var total = 0L
        try {
            source.open(DataSpec.Builder().setUri(id.toUri()).setKey(id).build())
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                cipher.updateInPlace(buffer, 0, read)
                out.write(buffer, 0, read)
                total += read
            }
        } finally {
            source.close()
        }
        return total
    }

    private fun deleteDocument(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            .onFailure { Timber.tag(TAG).w("Could not delete $uri: ${it.message}") }
    }

    /**
     * Gives back folder permissions nothing needs any more: a folder that is neither the
     * current one nor holding a download. Only folders this store was given are touched.
     */
    private fun releaseUnusedPermissions() {
        val current = _folder.value?.toString()
        val inUse = _entries.value.values.mapTo(mutableSetOf()) { it.tree }
        val known = prefs.getStringSet(KEY_TREES, emptySet()).orEmpty()
        val unused = known.filter { it != current && it !in inUse }
        if (unused.isEmpty()) return
        unused.forEach { tree ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    tree.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs.edit { putStringSet(KEY_TREES, known - unused.toSet()) }
    }

    private fun put(id: String, entry: Entry) {
        prefs.edit { putString(ENTRY_PREFIX + id, "${entry.tree}\n${entry.document}\n${entry.length}") }
        _entries.update { it + (id to entry) }
    }

    private fun remove(id: String) {
        prefs.edit { remove(ENTRY_PREFIX + id) }
        _entries.update { it - id }
        verified -= id
    }

    private fun loadEntries(): Map<String, Entry> =
        prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(ENTRY_PREFIX) || value !is String) return@mapNotNull null
            val parts = value.split('\n')
            val length = parts.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
            key.removePrefix(ENTRY_PREFIX) to Entry(parts[0], parts[1], length)
        }.toMap()

    companion object {
        private const val TAG = "DownloadFolder"
        private const val PREFS = "download_folder"
        private const val KEY_FOLDER = "folder"
        private const val KEY_TREES = "granted_trees"
        private const val ENTRY_PREFIX = "e:"
        private const val MIME_TYPE = "application/octet-stream"
        private const val BUFFER = 64 * 1024

        /** The scheme playback uses to ask for a song's folder copy. */
        const val SCHEME = "shiny-offline"

        /**
         * The key for Shiny's offline format. It makes the files Shiny's own rather than
         * loose audio; it is not meant as, and is not, a secret.
         */
        internal val KEY: ByteArray =
            MessageDigest.getInstance("SHA-256").digest("com.shiny.music/offline/v1".toByteArray()).copyOf(16)

        fun playbackUri(id: String): Uri = Uri.Builder().scheme(SCHEME).opaquePart(id).build()

        private fun fileName(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".shiny"

        /**
         * "Music/SHINY" for a folder on the device, "SD card · Music" for one on removable
         * storage — the way a person would describe it, not the document id behind it.
         */
        fun displayPath(tree: Uri): String = runCatching {
            val documentId = DocumentsContract.getTreeDocumentId(tree)
            val volume = documentId.substringBefore(':')
            val path = documentId.substringAfter(':', "").trim('/')
            when {
                volume.equals("primary", ignoreCase = true) -> path.ifBlank { "Internal storage" }
                documentId.startsWith("raw:") -> documentId.removePrefix("raw:").substringAfter("/storage/emulated/0/")
                path.isBlank() -> "SD card"
                else -> "SD card · $path"
            }
        }.getOrDefault(tree.lastPathSegment ?: tree.toString())
    }
}
