package com.shiny.music.ui.liquid.settings

import android.content.Intent
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import com.shiny.music.App
import com.shiny.music.LocalDatabase
import com.shiny.music.LocalDownloadUtil
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.constants.AutoDownloadOnLikeKey
import com.shiny.music.constants.MaxImageCacheSizeKey
import com.shiny.music.constants.MaxSongCacheSizeKey
import com.shiny.music.downloads.DownloadFolderStore
import com.shiny.music.extensions.tryOrNull
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidAlert
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.utils.formatFileSize
import com.shiny.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.ByteString.Companion.encodeUtf8
import timber.log.Timber

/** The cache limits Shiny offers, in MB. `-1` is unlimited, `0` turns the cache off. */
private val SongCacheSizes = listOf(0, 512, 1024, 2048, 4096, 8192, -1)
private val ImageCacheSizes = listOf(0, 128, 256, 512, 1024, 2048)

/**
 * Downloads and cache, kept apart on purpose.
 *
 * A download is something the listener asked Shiny to keep: it lives in the download folder
 * they chose and stays until they remove it. Cache is Shiny's own working space, filled as
 * they listen and trimmed on its own. The page never calls both "storage", because the one
 * question people bring here — "will this delete my music?" — depends on which one it is.
 */
@OptIn(ExperimentalCoilApi::class, DelicateCoilApi::class)
@Composable
fun StorageSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val downloadUtil = LocalDownloadUtil.current
    val folderStore = downloadUtil.folderStore

    val service = LocalPlayerConnection.current?.service
    val playerCache = service?.playerCache
    val downloadCache = downloadUtil.downloadCache
    val imageDiskCache = remember(context) { context.imageLoader.diskCache }

    var maxSongCacheSize by rememberPreference(MaxSongCacheSizeKey, 1024)
    var maxImageCacheSize by rememberPreference(MaxImageCacheSizeKey, App.DEFAULT_IMAGE_CACHE_MB)
    var autoDownloadOnLike by rememberPreference(AutoDownloadOnLikeKey, false)

    val folder by folderStore.folder.collectAsState()
    val entries by folderStore.entries.collectAsState()
    val moving by folderStore.moving.collectAsState()
    val lastMove by folderStore.lastMove.collectAsState()
    val downloads by downloadUtil.downloads.collectAsState()

    var privateDownloadSize by remember { mutableLongStateOf(0L) }
    var songCacheSize by remember { mutableLongStateOf(0L) }
    var imageCacheSize by remember { mutableLongStateOf(0L) }
    var folderAccessible by remember { mutableStateOf(true) }

    // One poll for the sizes, and only while this screen is composed.
    LaunchedEffect(playerCache, downloadCache, imageDiskCache, folder) {
        while (isActive) {
            privateDownloadSize = tryOrNull { downloadCache.cacheSpace } ?: 0L
            songCacheSize = tryOrNull { playerCache?.cacheSpace } ?: 0L
            imageCacheSize = tryOrNull { imageDiskCache?.size } ?: 0L
            folderAccessible = kotlinx.coroutines.withContext(Dispatchers.IO) { folderStore.isAccessible(folder) }
            delay(1000)
        }
    }

    LaunchedEffect(maxImageCacheSize) {
        SingletonImageLoader.reset()
        if (maxImageCacheSize == 0) {
            scope.launch(Dispatchers.IO) { imageDiskCache?.clear() }
        }
    }
    LaunchedEffect(maxSongCacheSize) {
        if (maxSongCacheSize == 0) {
            scope.launch(Dispatchers.IO) {
                playerCache?.keys?.forEach { key -> playerCache.removeResource(key) }
            }
        }
    }

    LaunchedEffect(lastMove) {
        val result = lastMove ?: return@LaunchedEffect
        val message = when {
            result.failed == 0 -> "Moved ${songCount(result.moved)} to your download folder"
            result.moved == 0 -> "Those downloads couldn't be moved. They're still playable where they are."
            else -> "Moved ${songCount(result.moved)}. ${songCount(result.failed)} couldn't be moved and stayed where they were."
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        folderStore.consumeLastMove()
    }

    var moveEverythingHere by remember { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val granted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { Timber.w(it, "Download folder permission refused") }.isSuccess
        if (granted) {
            folderStore.setFolder(uri)
            // Everything already downloaded follows, so all offline songs live in one folder.
            moveEverythingHere = true
        } else {
            Toast.makeText(context, "Shiny couldn't get access to that folder. Try another one.", Toast.LENGTH_LONG).show()
        }
    }
    val chooseFolder: () -> Unit = {
        // Android won't hand the Download folder itself to any app ("To protect your privacy,
        // choose another folder"), only folders inside it. So Shiny makes Download/Shiny and
        // opens the picker there: one tap on "Use this folder".
        val start = folder ?: run {
            val made = ensureShinyDownloadFolder(context)
            Toast.makeText(
                context,
                if (made) "Tap “Use this folder” to keep downloads in Download › Shiny"
                else "Open Download, make a folder (like “Shiny”) and use that — Android won't allow Download itself",
                Toast.LENGTH_LONG,
            ).show()
            DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                if (made) "primary:${Environment.DIRECTORY_DOWNLOADS}/$ShinyFolderName" else "primary:${Environment.DIRECTORY_DOWNLOADS}",
            )
        }
        runCatching { folderPicker.launch(start) }.onFailure {
            Timber.e(it, "No folder picker")
            Toast.makeText(context, "This device doesn't have a folder picker.", Toast.LENGTH_LONG).show()
        }
    }

    // What is downloaded, and where each download physically is.
    val completedIds = remember(downloads) {
        downloads.values.filter { it.state == Download.STATE_COMPLETED }.map { it.request.id }.toSet()
    }
    val currentTree = folder?.toString()
    val inCurrentFolder = completedIds.count { entries[it]?.tree == currentTree && currentTree != null }
    val notInFolder = if (folder == null) emptyList() else completedIds.filter { entries[it]?.tree != currentTree }
    val notInFolderBytes = notInFolder.sumOf { entries[it]?.length ?: 0L } +
        if (notInFolder.any { it !in entries }) privateDownloadSize else 0L
    val downloadBytes = privateDownloadSize + entries.values.sumOf { it.length }

    LaunchedEffect(moveEverythingHere, folder) {
        if (!moveEverythingHere || folder == null) return@LaunchedEffect
        moveEverythingHere = false
        if (notInFolder.isNotEmpty()) folderStore.startMove(notInFolder)
    }

    var confirmClearDownloads by remember { mutableStateOf(false) }
    var confirmClearSongCache by remember { mutableStateOf(false) }
    var confirmClearImageCache by remember { mutableStateOf(false) }

    SettingsPage(title = "Downloads & Storage", navController = navController) {
        item(key = "downloads") {
            SettingsSection(
                title = "Downloads",
                footer = "Downloads are songs you asked Shiny to keep offline. They stay until you " +
                    "remove them, and they're saved in Shiny's own format, so only Shiny plays them.",
            ) {
                SettingsRow(
                    title = "Download folder",
                    subtitle = folder?.let { DownloadFolderStore.displayPath(it) }
                        ?: "Shiny's storage on this device",
                    trailing = {
                        Text(
                            text = if (folder == null) "Choose" else "Change",
                            style = LiquidTypography.body.copy(fontWeight = FontWeight.Medium),
                            color = Liquid.colors.accent,
                        )
                    },
                    onClick = chooseFolder,
                )
                if (folder != null && !folderAccessible) {
                    SettingsRow(
                        title = "Folder unavailable",
                        subtitle = "Shiny can't reach this folder any more, so new downloads stay in " +
                            "Shiny's storage. Choose it again, or pick another.",
                        destructive = true,
                        onClick = chooseFolder,
                    )
                }
                SettingsValueRow(
                    title = "Downloaded songs",
                    value = if (completedIds.isEmpty()) "None" else "${songCount(completedIds.size)} · ${formatFileSize(downloadBytes)}",
                )
                if (folder != null && notInFolder.isNotEmpty()) {
                    val progress = moving
                    SettingsActionRow(
                        title = if (progress != null) {
                            "Moving ${progress.first} of ${progress.second}…"
                        } else {
                            "Move ${songCount(notInFolder.size)} into this folder"
                        },
                        subtitle = if (progress != null) {
                            "Bringing your downloads into one folder. You can keep listening."
                        } else {
                            "${formatFileSize(notInFolderBytes)} still outside this folder — they play from " +
                                "where they are. Tap to try moving them again."
                        },
                        enabled = progress == null && folderAccessible,
                        onClick = { folderStore.startMove(notInFolder) },
                    )
                } else if (folder == null && completedIds.isEmpty()) {
                    SettingsNote("Choose a folder so downloads are kept there instead of in Shiny's own storage.")
                }
                SettingsToggleRow(
                    title = "Download liked songs",
                    subtitle = "Keep every song you like available offline",
                    checked = autoDownloadOnLike,
                    onCheckedChange = { autoDownloadOnLike = it },
                    divider = false,
                )
            }
        }

        item(key = "cache") {
            SettingsSection(
                title = "Cache",
                footer = "Cache is temporary. Shiny fills it as you listen and clears the oldest items " +
                    "on its own. Clearing it never removes a download.",
            ) {
                SettingsValueRow(
                    title = "Song cache",
                    subtitle = "Songs you've played recently, so replays don't use data",
                    value = formatFileSize(songCacheSize),
                )
                SettingsValueRow(
                    title = "Artwork cache",
                    subtitle = "Covers and artist photos",
                    value = formatFileSize(imageCacheSize),
                )
                SettingsActionRow(
                    title = "Clear song cache",
                    onClick = { confirmClearSongCache = true },
                    enabled = songCacheSize > 0,
                )
                SettingsActionRow(
                    title = "Clear artwork cache",
                    subtitle = "Covers of downloaded songs are kept",
                    onClick = { confirmClearImageCache = true },
                    enabled = imageCacheSize > 0,
                    divider = false,
                )
            }
        }

        item(key = "limits") {
            SettingsSection(title = "Cache limits") {
                SettingsChoiceRow(
                    title = "Song cache",
                    options = SongCacheSizes,
                    selected = SongCacheSizes.firstOrNull { it == maxSongCacheSize } ?: 1024,
                    label = { cacheLabel(it) },
                    onSelect = { maxSongCacheSize = it },
                )
                SettingsChoiceRow(
                    title = "Artwork cache",
                    options = ImageCacheSizes,
                    selected = ImageCacheSizes.firstOrNull { it == maxImageCacheSize } ?: App.DEFAULT_IMAGE_CACHE_MB,
                    label = { cacheLabel(it) },
                    onSelect = { maxImageCacheSize = it },
                    divider = false,
                )
            }
        }

        item(key = "management") {
            SettingsSection(title = "Download management") {
                SettingsActionRow(
                    title = "Remove all downloads",
                    subtitle = if (inCurrentFolder > 0) "Deletes the files in your download folder too" else null,
                    onClick = { confirmClearDownloads = true },
                    destructive = true,
                    enabled = completedIds.isNotEmpty() || downloadBytes > 0,
                    divider = false,
                )
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }

    if (confirmClearDownloads) {
        LiquidAlert(
            title = "Remove all downloads?",
            message = "Every downloaded song is deleted from this device and from your download folder. " +
                "Your library and playlists aren't changed.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                confirmClearDownloads = false
                androidx.media3.exoplayer.offline.DownloadService.sendRemoveAllDownloads(
                    context,
                    com.shiny.music.playback.ExoDownloadService::class.java,
                    false,
                )
            },
            onDismiss = { confirmClearDownloads = false },
        )
    }
    if (confirmClearSongCache) {
        LiquidAlert(
            title = "Clear the song cache?",
            message = "Songs you play again will be streamed once more. Downloads aren't affected.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                confirmClearSongCache = false
                scope.launch(Dispatchers.IO) {
                    playerCache?.keys?.forEach { key -> playerCache.removeResource(key) }
                }
            },
            onDismiss = { confirmClearSongCache = false },
        )
    }
    if (confirmClearImageCache) {
        LiquidAlert(
            title = "Clear the artwork cache?",
            message = "Covers are fetched again as you browse. Artwork for downloaded songs is kept.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                confirmClearImageCache = false
                scope.launch(Dispatchers.IO) {
                    // Keep the covers of downloaded songs: they are the one case where a
                    // cleared cache cannot be refilled offline.
                    val preserved = mutableSetOf<String>()
                    runCatching { database.downloadedSongsByNameAsc().first() }.getOrNull()?.forEach { song ->
                        song.thumbnailUrl?.let { preserved += it.encodeUtf8().sha256().hex() }
                        song.album?.thumbnailUrl?.let { preserved += it.encodeUtf8().sha256().hex() }
                    }
                    val directory = imageDiskCache?.directory?.toFile()
                    if (directory != null && directory.isDirectory) {
                        directory.listFiles()?.forEach { file ->
                            if (file.isFile && !file.name.startsWith("journal") &&
                                preserved.none { hash -> file.name.startsWith(hash) }
                            ) {
                                file.delete()
                            }
                        }
                    }
                }
            },
            onDismiss = { confirmClearImageCache = false },
        )
    }
}

private const val ShinyFolderName = "Shiny"

/**
 * Makes Download/Shiny if it isn't there. A plain mkdirs works where the app may write to shared
 * storage; elsewhere (Android 10 and up) MediaStore creates the folder for a file placed in it,
 * and the file is removed again straight away.
 */
private fun ensureShinyDownloadFolder(context: Context): Boolean {
    @Suppress("DEPRECATION")
    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ShinyFolderName)
    if (dir.isDirectory) return true
    if (runCatching { dir.mkdirs() }.getOrDefault(false) && dir.isDirectory) return true
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return runCatching {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "shiny-folder.tmp")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$ShinyFolderName")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        context.contentResolver.delete(uri, null, null)
        true
    }.getOrDefault(false)
}

internal fun songCount(count: Int): String = if (count == 1) "1 song" else "$count songs"

private fun cacheLabel(mb: Int): String = when (mb) {
    0 -> "Off"
    -1 -> "Unlimited"
    else -> formatFileSize(mb * 1024L * 1024L)
}
