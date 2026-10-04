

package com.shiny.music.localmedia

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.shiny.music.R
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.AlbumArtistMap
import com.shiny.music.db.entities.AlbumEntity
import com.shiny.music.db.entities.ArtistEntity
import com.shiny.music.db.entities.FormatEntity
import com.shiny.music.db.entities.Song
import com.shiny.music.db.entities.SongAlbumMap
import com.shiny.music.db.entities.SongArtistMap
import com.shiny.music.db.entities.SongEntity
import com.shiny.music.constants.LocalSongsHiddenIdsKey
import com.shiny.music.constants.LocalSongsScanStampKey
import com.shiny.music.utils.dataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class LocalSongScanConfig(
    val minimumDurationSeconds: Int = 0,
    val excludedFolders: Set<String> = emptySet(),
    /** Non-music sources left out of the library. See [LocalMusicFilter]. */
    val skippedSources: Set<SkippedSource> = SkippedSource.Default,
    /** Folders that are always music, overriding [skippedSources]. */
    val includedFolders: Set<String> = emptySet(),
) {
    /**
     * Everything about this configuration that changes what a scan finds, as one string,
     * so a change of settings can be told from an unchanged one without rescanning.
     */
    val signature: String
        get() = listOf(
            sanitizedMinimumDurationSeconds.toString(),
            skippedSources.map(SkippedSource::key).sorted().joinToString(","),
            deduplicateFolderEntries(includedFolders).map { it.lowercase(Locale.ROOT) }.sorted().joinToString(","),
            sanitizedExcludedFolders.map { it.lowercase(Locale.ROOT) }.sorted().joinToString(","),
        ).joinToString(";")

    val sanitizedMinimumDurationSeconds: Int
        get() = minimumDurationSeconds.coerceAtLeast(0)

    val sanitizedExcludedFolders: Set<String>
        get() = deduplicateFolderEntries(excludedFolders)

    companion object {
        private val DuplicateSlashRegex = Regex("/+")

        fun normalizeFolderEntry(raw: String): String {
            return raw
                .trim()
                .replace('\\', '/')
                .replace(DuplicateSlashRegex, "/")
                .trim('/')
        }

        fun deduplicateFolderEntries(entries: Iterable<String>): Set<String> {
            val deduplicated = linkedMapOf<String, String>()
            entries.forEach { entry ->
                val normalized = normalizeFolderEntry(entry)
                if (normalized.isNotEmpty()) {
                    deduplicated.putIfAbsent(normalized.lowercase(Locale.ROOT), normalized)
                }
            }
            return deduplicated.values.toSet()
        }
    }
}

data class LocalSongScanSummary(
    val scannedSongs: Int,
    val removedSongs: Int,
    /** Audio files left out as not music (voice notes, recordings, system sounds). */
    val skippedFiles: Int = 0,
    /** Left out, but kept in the database because the user had played, liked or saved them. */
    val keptHidden: Int = 0,
)

class LocalSongScanner
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    /**
     * Brings the local library in line with the device.
     *
     * A file that is gone from the device is removed, as it always was. A file that is
     * still there but is not music under the current settings is removed too — unless the
     * user has played, liked, saved or playlisted it, in which case its row is kept (so its
     * history and playlist places survive) and it is only hidden from On This Device.
     *
     * [automatic] marks a scan Shiny started on its own. Such a scan never empties the
     * library: an index that suddenly reports no audio at all is far more likely to be
     * storage that is not mounted yet than a device with no music left.
     */
    suspend fun scanDevice(
        scanConfig: LocalSongScanConfig = LocalSongScanConfig(),
        automatic: Boolean = false,
    ): LocalSongScanSummary = withContext(Dispatchers.IO) {
        val snapshot = queryTracks(scanConfig)
        if (automatic && snapshot.indexedFiles == 0 && database.localSongIds().isNotEmpty()) {
            Timber.tag(TAG).w("Automatic scan saw an empty audio index; leaving the library as it is")
            return@withContext LocalSongScanSummary(0, 0)
        }
        var summary = LocalSongScanSummary(0, 0)
        var hiddenIds: Set<String> = emptySet()
        database.withTransaction {
            val existingLocalIds = localSongIds()
            val scannedIds = snapshot.tracks.map(LocalTrackRecord::id)
            val scannedIdSet = scannedIds.toSet()
            val notScanned = existingLocalIds.filterNot(scannedIdSet::contains)

            // Still on the device but not music now: keep what the user has touched.
            val nowExcluded = notScanned.filter(snapshot.leftOutIds::contains)
            val kept = nowExcluded.chunked(SqlBatchSize).flatMap { referencedSongIds(it) }.toSet()
            val removedIds = notScanned.filterNot(kept::contains)
            // Deleted after the new rows are written, below: a file that was moved or renamed
            // is back under a new id, and its history has to move there before the old row
            // goes, because deleting the row cascades to its plays.
            val removedWithHistory = removedIds.chunked(SqlBatchSize).flatMap { songIdsWithEvents(it) }
            val removedSongs = loadSongs(removedWithHistory)
            hiddenIds = kept

            val existingSongs = loadSongs(scannedIds)
            val existingArtists = loadArtists(snapshot.artists.map(LocalArtistRecord::id))
            val existingAlbums = loadAlbums(snapshot.albums.map(LocalAlbumRecord::id))

            snapshot.artists.forEach { artist ->
                val existingArtist = existingArtists[artist.id]
                insert(
                    ArtistEntity(
                        id = artist.id,
                        name = artist.name,
                        thumbnailUrl = existingArtist?.thumbnailUrl,
                        channelId = null,
                        lastUpdateTime = existingArtist?.lastUpdateTime ?: LocalDateTime.now(),
                        bookmarkedAt = existingArtist?.bookmarkedAt,
                        isLocal = true,
                    ),
                )
            }

            snapshot.albums.forEach { album ->
                val existingAlbum = existingAlbums[album.id]
                insert(
                    AlbumEntity(
                        id = album.id,
                        playlistId = null,
                        title = album.title,
                        year = album.year ?: existingAlbum?.year,
                        thumbnailUrl = album.thumbnailUrl ?: existingAlbum?.thumbnailUrl?.takeIf {
                            !it.startsWith("content://media/external/audio/media/")
                        },
                        themeColor = existingAlbum?.themeColor,
                        songCount = album.songCount,
                        duration = album.duration,
                        explicit = false,
                        lastUpdateTime = LocalDateTime.now(),
                        bookmarkedAt = existingAlbum?.bookmarkedAt,
                        likedDate = existingAlbum?.likedDate,
                        inLibrary = existingAlbum?.inLibrary,
                        isLocal = true,
                    ),
                )
            }

            snapshot.albums.map(LocalAlbumRecord::id).distinct().chunked(SqlBatchSize).forEach(::deleteAlbumArtistMapsByAlbumIds)
            snapshot.albums.forEach { album ->
                album.artistIds.forEachIndexed { index, artistId ->
                    insert(
                        AlbumArtistMap(
                            albumId = album.id,
                            artistId = artistId,
                            order = index,
                        ),
                    )
                }
            }

            snapshot.tracks.forEach { track ->
                val existingSong = existingSongs[track.id]?.song
                insert(
                    SongEntity(
                        id = track.id,
                        title = track.title,
                        duration = track.durationSeconds,
                        thumbnailUrl = track.thumbnailUrl ?: existingSong?.thumbnailUrl?.takeIf {
                            !it.startsWith("content://media/external/audio/media/")
                        },
                        albumId = track.albumId,
                        albumName = track.albumName,
                        explicit = existingSong?.explicit ?: false,
                        year = track.year ?: existingSong?.year,
                        date = existingSong?.date,
                        dateModified = track.dateModified ?: existingSong?.dateModified,
                        liked = existingSong?.liked ?: false,
                        likedDate = existingSong?.likedDate,
                        totalPlayTime = existingSong?.totalPlayTime ?: 0L,
                        inLibrary = null,
                        dateDownload = existingSong?.dateDownload,
                        isLocal = true,
                    ),
                )
                upsert(
                    FormatEntity(
                        id = track.id,
                        itag = -1,
                        mimeType = track.mimeType,
                        codecs = "",
                        bitrate = 0,
                        sampleRate = null,
                        contentLength = track.sizeBytes,
                        loudnessDb = null,
                        perceptualLoudnessDb = null,
                        playbackUrl = null,
                    ),
                )
                deleteSongArtistMaps(track.id)
                track.artists.forEachIndexed { index, artist ->
                    insert(
                        SongArtistMap(
                            songId = track.id,
                            artistId = artist.id,
                            position = index,
                        ),
                    )
                }
                deleteSongAlbumMaps(track.id)
                track.albumId?.let { albumId ->
                    insert(
                        SongAlbumMap(
                            songId = track.id,
                            albumId = albumId,
                            index = 0,
                        ),
                    )
                }
            }

            val existingIdSet = existingLocalIds.toSet()
            val moves = LocalHistoryRelink.match(
                gone = removedSongs.values.map { song ->
                    LocalHistoryRelink.Track(
                        id = song.song.id,
                        title = song.song.title,
                        artist = song.artists.joinToString { it.name },
                        durationSeconds = song.song.duration,
                    )
                },
                arrived = snapshot.tracks.filterNot { it.id in existingIdSet }.map { track ->
                    LocalHistoryRelink.Track(
                        id = track.id,
                        title = track.title,
                        artist = track.artists.joinToString { it.name },
                        durationSeconds = track.durationSeconds,
                    )
                },
            )
            moves.forEach { (fromId, toId) ->
                moveEvents(fromId, toId)
                movePlayCounts(fromId, toId)
                addTotalPlayTimeFrom(fromId, toId)
            }
            if (moves.isNotEmpty()) Timber.tag(TAG).d("Moved the history of %d relocated files", moves.size)
            removedIds.chunked(SqlBatchSize).forEach(::deleteSongsByIds)

            pruneLocalAlbums()
            pruneLocalArtists()
            pruneFormats()
            prunePlayCounts()

            summary = LocalSongScanSummary(
                scannedSongs = snapshot.tracks.size,
                removedSongs = removedIds.size,
                skippedFiles = snapshot.skippedFiles,
                keptHidden = kept.size,
            )
        }
        val stamp = scanStamp(scanConfig, snapshot.fingerprint)
        context.dataStore.edit { prefs ->
            prefs[LocalSongsHiddenIdsKey] = hiddenIds
            prefs[LocalSongsScanStampKey] = stamp
        }
        Timber.tag(TAG).d(
            "Scan: %d songs, %d skipped as not music, %d removed, %d kept hidden (automatic=%s)",
            summary.scannedSongs, summary.skippedFiles, summary.removedSongs, summary.keptHidden, automatic,
        )
        return@withContext summary
    }

    /**
     * Whether the device or the settings have changed since the last completed scan.
     *
     * Reads only the audio index's ids and modification times — a light pass compared to a
     * scan, and one that is blind to photos and videos, so taking a picture does not make
     * the library rescan.
     */
    suspend fun needsScan(scanConfig: LocalSongScanConfig): Boolean = withContext(Dispatchers.IO) {
        val stored = context.dataStore.data.first()[LocalSongsScanStampKey]
        val current = runCatching { scanStamp(scanConfig, audioIndexFingerprint()) }.getOrNull()
            ?: return@withContext false
        stored != current
    }

    private fun scanStamp(scanConfig: LocalSongScanConfig, fingerprint: String): String =
        "v$FilterVersion|$fingerprint|${scanConfig.signature}"

    /**
     * Audio rows that can be played at all: non-empty, with a length, finished writing and
     * not in the trash. Deliberately independent of the scan settings, so the fingerprint
     * a scan records and the one [needsScan] computes always describe the same rows; the
     * minimum length is applied per file instead, where a file it rules out can be kept.
     */
    private fun playableAudioSelection(): String = buildList {
        add("${MediaStore.Audio.Media.SIZE} > 0")
        add("${MediaStore.Audio.Media.DURATION} > 0")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add("${MediaStore.MediaColumns.IS_PENDING} = 0")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add("is_trashed = 0")
        }
    }.joinToString(" AND ")

    /** Count, identity and latest modification of every playable indexed audio file. */
    private fun audioIndexFingerprint(): String {
        var count = 0
        var hash = 1L
        var latest = 0L
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATE_MODIFIED),
            playableAudioSelection(),
            null,
            "${MediaStore.Audio.Media._ID} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val modified = cursor.getLong(1)
                count++
                hash = hash * 31 + id
                hash = hash * 31 + modified
                if (modified > latest) latest = modified
            }
        }
        return "$count:$hash:$latest"
    }

        private suspend fun loadSongs(ids: List<String>): Map<String, Song> =
        ids.chunked(SqlBatchSize)
            .flatMap { chunk -> database.getSongsByIds(chunk) }
            .associateBy { item -> item.song.id }

        private suspend fun loadArtists(ids: List<String>): Map<String, ArtistEntity> =
        ids.distinct().chunked(SqlBatchSize)
            .flatMap { chunk -> database.getArtistEntitiesByIds(chunk) }
            .associateBy { item -> item.id }

        private suspend fun loadAlbums(ids: List<String>): Map<String, AlbumEntity> =
        ids.distinct().chunked(SqlBatchSize)
            .flatMap { chunk -> database.getAlbumEntitiesByIds(chunk) }
            .associateBy { item -> item.id }

    @Suppress("DEPRECATION")
    private fun queryTracks(scanConfig: LocalSongScanConfig): LocalScanSnapshot {
        val sanitizedMinimumDurationMs = scanConfig.sanitizedMinimumDurationSeconds.toLong() * 1000L
        val excludedFolders = scanConfig.sanitizedExcludedFolders
        val includedFolders = LocalSongScanConfig.deduplicateFolderEntries(scanConfig.includedFolders)
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ARTIST_ID)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.IS_RINGTONE)
            add(MediaStore.Audio.Media.IS_NOTIFICATION)
            add(MediaStore.Audio.Media.IS_ALARM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(MediaStore.Audio.Media.IS_RECORDING)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                add(MediaStore.MediaColumns.DATA)
            }
        }.toTypedArray()
        val selection = playableAudioSelection()

        val unknownArtist = context.getString(R.string.unknown_artist)
        val unknownTitle = context.getString(R.string.unknown)
        val tracks = mutableListOf<LocalTrackRecord>()
        val leftOut = mutableSetOf<String>()
        var skippedFiles = 0
        var indexedFiles = 0
        var hash = 1L
        var latest = 0L
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media._ID} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val yearIndex = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val dataPathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val ringtoneIndex = cursor.getColumnIndex(MediaStore.Audio.Media.IS_RINGTONE)
            val notificationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.IS_NOTIFICATION)
            val alarmIndex = cursor.getColumnIndex(MediaStore.Audio.Media.IS_ALARM)
            val recordingIndex = cursor.getColumnIndex(MediaStore.Audio.Media.IS_RECORDING)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idIndex)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
                val id = contentUri.toString()
                indexedFiles++
                // The same fingerprint needsScan() computes, taken from this pass for free.
                val modifiedSeconds = cursor.getLong(dateModifiedIndex)
                hash = hash * 31 + mediaId
                hash = hash * 31 + modifiedSeconds
                if (modifiedSeconds > latest) latest = modifiedSeconds

                val folder = resolveFolderPath(
                    relativePath = cursor.getStringOrNull(relativePathIndex),
                    absolutePath = cursor.getStringOrNull(dataPathIndex),
                )
                val displayName = cursor.getString(displayNameIndex)
                val mimeType = cursor.getString(mimeTypeIndex)?.takeIf(String::isNotBlank) ?: "audio/*"
                val decision = LocalMusicFilter.decide(
                    folder = folder,
                    displayName = displayName,
                    mimeType = mimeType,
                    flags = MediaStoreAudioFlags(
                        isRingtone = cursor.getIntOrNull(ringtoneIndex) == 1,
                        isNotification = cursor.getIntOrNull(notificationIndex) == 1,
                        isAlarm = cursor.getIntOrNull(alarmIndex) == 1,
                        isRecording = cursor.getIntOrNull(recordingIndex) == 1,
                    ),
                    skipped = scanConfig.skippedSources,
                    includedFolders = includedFolders,
                    excludedFolders = excludedFolders,
                )
                if (decision != LocalMusicDecision.Music) {
                    if (decision is LocalMusicDecision.Skipped) skippedFiles++
                    leftOut += id
                    continue
                }
                if (!SupportedLocalAudio.isSupported(displayName, mimeType)) {
                    leftOut += id
                    continue
                }
                if (sanitizedMinimumDurationMs > 0L && cursor.getLong(durationIndex) < sanitizedMinimumDurationMs) {
                    leftOut += id
                    continue
                }
                val artistValue = normalizeArtistName(cursor.getString(artistIndex), unknownArtist)
                val splitArtists = splitArtistNames(artistValue).ifEmpty { listOf(unknownArtist) }
                val mediaStoreArtistId = cursor.getLongOrNull(artistIdIndex)
                val artists = splitArtists.mapIndexed { index, name ->
                    LocalArtistRecord(
                        id = buildArtistId(mediaStoreArtistId, name, index, splitArtists.size),
                        name = name,
                    )
                }
                val mediaStoreAlbumId = cursor.getLongOrNull(albumIdIndex)
                val albumName = normalizeAlbumName(cursor.getString(albumIndex))
                val title = normalizeTitle(
                    title = cursor.getString(titleIndex),
                    displayName = displayName,
                    fallback = unknownTitle,
                )
                tracks += LocalTrackRecord(
                    id = id,
                    title = title,
                    artists = artists,
                    albumId = albumName?.let {
                        buildAlbumId(
                            mediaStoreAlbumId = mediaStoreAlbumId,
                            albumName = it,
                            primaryArtistId = artists.firstOrNull()?.id,
                        )
                    },
                    albumName = albumName,
                    durationSeconds = (cursor.getLong(durationIndex).coerceAtLeast(0L) / 1000L)
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt(),
                    year = cursor.getIntOrNull(yearIndex)?.takeIf { it > 0 },
                    dateModified = cursor.getLong(dateModifiedIndex)
                        .takeIf { it > 0L }
                        ?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault()) },
                    sizeBytes = cursor.getLong(sizeIndex).coerceAtLeast(0L),
                    mimeType = mimeType,
                    thumbnailUrl = mediaStoreAlbumId?.takeIf { it > 0 }?.let {
                        ContentUris.withAppendedId(AlbumArtUri, it).toString()
                    },
                )
            }
        }

        val albums = tracks
            .filter { !it.albumId.isNullOrBlank() && !it.albumName.isNullOrBlank() }
            .groupBy { it.albumId!! }
            .map { (albumId, albumTracks) ->
                LocalAlbumRecord(
                    id = albumId,
                    title = albumTracks.first().albumName.orEmpty(),
                    year = albumTracks.mapNotNull(LocalTrackRecord::year).maxOrNull(),
                    thumbnailUrl = albumTracks.mapNotNull(LocalTrackRecord::thumbnailUrl).firstOrNull(),
                    songCount = albumTracks.size,
                    duration = albumTracks.sumOf(LocalTrackRecord::durationSeconds),
                    artistIds = albumTracks.flatMap { track -> track.artists.map(LocalArtistRecord::id) }.distinct(),
                )
            }

        return LocalScanSnapshot(
            tracks = tracks,
            artists = tracks.flatMap(LocalTrackRecord::artists).distinctBy(LocalArtistRecord::id),
            albums = albums,
            leftOutIds = leftOut,
            skippedFiles = skippedFiles,
            indexedFiles = indexedFiles,
            fingerprint = "$indexedFiles:$hash:$latest",
        )
    }

    private fun normalizeTitle(title: String?, displayName: String?, fallback: String): String {
        return title?.trim()?.takeIf { it.isNotBlank() }
            ?: displayName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun normalizeArtistName(rawArtist: String?, fallback: String): String {
        val normalized = rawArtist?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
        return normalized ?: fallback
    }

    private fun normalizeAlbumName(rawAlbum: String?): String? {
        return rawAlbum?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
    }

    private fun splitArtistNames(rawArtist: String): List<String> {
        return rawArtist
            .split(ArtistSeparators)
            .map(String::trim)
            .filter(String::isNotBlank)
            .ifEmpty { listOf(rawArtist) }
    }

    private fun buildArtistId(
        mediaStoreArtistId: Long?,
        artistName: String,
        index: Int,
        totalArtists: Int,
    ): String {
        val stableId = mediaStoreArtistId?.takeIf { it > 0L }
        return if (stableId != null && totalArtists == 1) {
            "LOCAL_ARTIST_$stableId"
        } else {
            "LOCAL_ARTIST_${stableHash("$artistName|$index")}"
        }
    }

    private fun buildAlbumId(
        mediaStoreAlbumId: Long?,
        albumName: String,
        primaryArtistId: String?,
    ): String {
        val stableId = mediaStoreAlbumId?.takeIf { it > 0L }
        return if (stableId != null) {
            "LOCAL_ALBUM_$stableId"
        } else {
            "LOCAL_ALBUM_${stableHash("$albumName|$primaryArtistId")}"
        }
    }

    private fun stableHash(source: String): String {
        return UUID.nameUUIDFromBytes(source.toByteArray(StandardCharsets.UTF_8))
            .toString()
            .replace("-", "")
    }

    private fun resolveFolderPath(relativePath: String?, absolutePath: String?): String? {
        val relativeFolder = LocalSongScanConfig.normalizeFolderEntry(relativePath.orEmpty())
        if (relativeFolder.isNotEmpty()) {
            return relativeFolder
        }

        // Before Android 10 there is only the absolute path. Strip the storage root
        // (/storage/emulated/0, /storage/XXXX-XXXX, /sdcard) so folders compare the same way
        // RELATIVE_PATH does.
        val absoluteFolder = absolutePath
            ?.replace('\\', '/')
            ?.substringBeforeLast('/', missingDelimiterValue = "")
            .orEmpty()
        val relativeToVolume = StorageRootRegex.replace(absoluteFolder, "")
        return LocalSongScanConfig.normalizeFolderEntry(relativeToVolume).takeIf(String::isNotEmpty)
    }

    private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getLong(columnIndex) else null
    }

    private fun android.database.Cursor.getIntOrNull(columnIndex: Int): Int? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getInt(columnIndex) else null
    }

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getString(columnIndex) else null
    }

    private data class LocalScanSnapshot(
        val tracks: List<LocalTrackRecord>,
        val artists: List<LocalArtistRecord>,
        val albums: List<LocalAlbumRecord>,
        /** Files that are on the device but not in the library under these settings. */
        val leftOutIds: Set<String>,
        val skippedFiles: Int,
        /** Every audio row the index returned, music or not. */
        val indexedFiles: Int,
        val fingerprint: String,
    )

    private data class LocalTrackRecord(
        val id: String,
        val title: String,
        val artists: List<LocalArtistRecord>,
        val albumId: String?,
        val albumName: String?,
        val durationSeconds: Int,
        val year: Int?,
        val dateModified: LocalDateTime?,
        val sizeBytes: Long,
        val mimeType: String,
        val thumbnailUrl: String?,
    )

    private data class LocalArtistRecord(
        val id: String,
        val name: String,
    )

    private data class LocalAlbumRecord(
        val id: String,
        val title: String,
        val year: Int?,
        val thumbnailUrl: String?,
        val songCount: Int,
        val duration: Int,
        val artistIds: List<String>,
    )

    private companion object {
        const val TAG = "LocalSongScanner"

        /**
         * Bumped whenever what counts as music changes, so every library is rescanned once
         * under the new rules. 2: music-only discovery (skipped sources, speech codecs,
         * MIDI and DRM files dropped from the supported formats).
         */
        const val FilterVersion = 2
        val StorageRootRegex = Regex("^/?(storage/emulated/\\d+|storage/[^/]+|sdcard|mnt/sdcard)(/|$)", RegexOption.IGNORE_CASE)
        val AlbumArtUri: Uri = Uri.parse("content://media/external/audio/albumart")
        val ArtistSeparators = Regex("[,;/&]")
        const val SqlBatchSize = 900
    }
}
