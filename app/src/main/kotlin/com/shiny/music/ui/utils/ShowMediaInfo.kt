package com.shiny.music.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.MediaInfo
import com.shiny.music.LocalDatabase
import com.shiny.music.LocalDownloadUtil
import com.shiny.music.R
import com.shiny.music.db.entities.Album
import com.shiny.music.db.entities.FormatEntity
import com.shiny.music.db.entities.Song
import com.shiny.music.models.MediaMetadata
import com.shiny.music.ui.component.LocalBottomSheetPageState
import com.shiny.music.ui.component.shimmer.ShimmerHost
import com.shiny.music.ui.component.shimmer.TextPlaceholder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The song-information page: what Shiny actually knows about one song, and nothing it
 * does not.
 *
 * Every fact comes from a real source — the library row, the stream format Shiny played,
 * YouTube's own counts, or, for a file on this device, the file's tags and its audio
 * track — and a fact with no source is left out rather than shown as "Unknown" or "N/A".
 *
 * All state is keyed on [videoId], so opening the page for a second song never shows the
 * first song's details while the second loads.
 *
 * [fallback] is the metadata the caller already holds, used only for what the library
 * does not have yet (a song that has never been saved).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShowMediaInfo(videoId: String, fallback: MediaMetadata? = null) {
    if (videoId.isBlank()) return

    val windowInsets = WindowInsets.systemBars
    val database = LocalDatabase.current
    val context = LocalContext.current
    val sheetState = LocalBottomSheetPageState.current
    val downloadUtil = LocalDownloadUtil.current
    val isLocalFile = remember(videoId) { videoId.startsWith("content://") }

    val song by remember(videoId) { database.song(videoId) }.collectAsState(initial = null)
    val format by remember(videoId) { database.format(videoId) }.collectAsState(initial = null)
    val albumId = song?.song?.albumId ?: song?.album?.id
    val album by remember(albumId) { albumId?.let { database.album(it) } ?: flowOf(null) }
        .collectAsState(initial = null)
    val download by remember(videoId) { downloadUtil.getDownload(videoId) }.collectAsState(initial = null)

    // YouTube's own counts and description exist only for streamed songs.
    var info by remember(videoId) { mutableStateOf<MediaInfo?>(null) }
    var infoLoading by remember(videoId) { mutableStateOf(!isLocalFile) }
    LaunchedEffect(videoId) {
        if (isLocalFile) return@LaunchedEffect
        info = withContext(Dispatchers.IO) { YouTube.getMediaInfo(videoId).getOrNull() }
        infoLoading = false
    }

    // A local file describes itself: its tags and its audio track, read off the main thread.
    var fileFacts by remember(videoId) { mutableStateOf<LocalFileFacts?>(null) }
    LaunchedEffect(videoId) {
        if (!isLocalFile) return@LaunchedEffect
        fileFacts = withContext(Dispatchers.IO) { readLocalFileFacts(context, Uri.parse(videoId)) }
    }

    val facts = songFacts(
        videoId = videoId,
        isLocalFile = isLocalFile,
        song = song,
        album = album,
        format = format,
        info = info,
        file = fileFacts,
        downloaded = download?.state == Download.STATE_COMPLETED || song?.song?.isDownloaded == true,
        fallback = fallback,
    )

    val albumArtShape = RoundedCornerShape(24.dp)
    val artwork = song?.song?.thumbnailUrl
        ?: fallback?.thumbnailUrl
        ?: if (isLocalFile) null else "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
    // A local file's album-art address exists whether or not the file has a cover; when it
    // turns out to have none, the page shows no artwork rather than an empty panel.
    var artworkFailed by remember(artwork) { mutableStateOf(false) }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = windowInsets.asPaddingValues().calculateBottomPadding())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.song_info),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(onClick = { sheetState.dismiss() }) {
                    Text(stringResource(R.string.done))
                }
            }
        }

        if (artwork != null && !artworkFailed) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(albumArtShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = artwork,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { artworkFailed = true },
                    )
                }
            }
        }

        if (facts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ShimmerHost {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextPlaceholder()
                            TextPlaceholder()
                            TextPlaceholder()
                        }
                    }
                }
            }
        } else {
            items(facts.chunked(2)) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    pair.forEach { fact ->
                        InfoItem(label = fact.label, value = fact.value, modifier = Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // The video description is YouTube's; local files have none to show.
        if (!isLocalFile) {
            val description = info?.description?.takeIf { it.isNotBlank() }
            if (infoLoading || description != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.description),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (description == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator()
                            }
                        } else {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private data class Fact(val label: String, val value: String)

/** The facts the page shows, in reading order, each one only if its source has it. */
@Composable
private fun songFacts(
    videoId: String,
    isLocalFile: Boolean,
    song: Song?,
    album: Album?,
    format: FormatEntity?,
    info: MediaInfo?,
    file: LocalFileFacts?,
    downloaded: Boolean,
    fallback: MediaMetadata?,
): List<Fact> {
    val context = LocalContext.current
    val facts = mutableListOf<Fact>()
    fun add(label: String, value: String?) {
        value?.trim()?.takeIf { it.isNotEmpty() }?.let { facts += Fact(label, it) }
    }

    val entity = song?.song
    add(stringResource(R.string.song_info_title_label), entity?.title ?: fallback?.title ?: info?.title)
    add(
        stringResource(R.string.artist),
        song?.artists?.takeIf { it.isNotEmpty() }?.joinToString { it.name }
            ?: fallback?.artists?.takeIf { it.isNotEmpty() }?.joinToString { it.name }
            ?: info?.author,
    )
    add(stringResource(R.string.song_detail_album), entity?.albumName ?: album?.album?.title ?: fallback?.album?.title)
    // A local album's artists are Shiny's own grouping of its tracks, not a tag, so a file
    // only shows an album artist when it names one itself.
    add(
        stringResource(R.string.song_detail_album_artist),
        if (isLocalFile) file?.albumArtist
        else album?.artists?.takeIf { it.isNotEmpty() }?.joinToString { it.name },
    )

    val seconds = entity?.duration?.takeIf { it > 0 }
        ?: fallback?.duration?.takeIf { it > 0 }
        ?: file?.durationMs?.let { (it / 1000).toInt() }?.takeIf { it > 0 }
    add(stringResource(R.string.song_info_duration_label), seconds?.let { "%d:%02d".format(it / 60, it % 60) })

    val year = entity?.year?.takeIf { it > 0 } ?: album?.album?.year?.takeIf { it > 0 }
    add(stringResource(R.string.song_detail_year), year?.toString() ?: file?.year)
    add(stringResource(R.string.song_detail_genre), file?.genre)
    add(stringResource(R.string.song_detail_track), file?.track?.let { ofTotal(context, it) })
    add(stringResource(R.string.song_detail_disc), file?.disc?.let { ofTotal(context, it) })

    // The audio itself.
    val mime = file?.mimeType ?: format?.mimeType?.takeIf { it.isNotBlank() && it != "audio/*" }
    add(stringResource(R.string.song_detail_format), mime?.let(::containerName))
    add(
        stringResource(R.string.codecs),
        file?.codecMime?.let(::codecName) ?: format?.codecs?.takeIf { it.isNotBlank() }?.let(::codecName),
    )
    val bitrate = format?.bitrate?.takeIf { it > 0 } ?: file?.bitrate?.takeIf { it > 0 }
    add(stringResource(R.string.bitrate), bitrate?.let { "${it / 1000} kbps" })
    val sampleRate = format?.sampleRate?.takeIf { it > 0 } ?: file?.sampleRate?.takeIf { it > 0 }
    add(stringResource(R.string.sample_rate), sampleRate?.let(::sampleRateLabel))
    add(stringResource(R.string.song_detail_channels), file?.channels?.takeIf { it > 0 }?.let(::channelLabel))
    val size = file?.sizeBytes?.takeIf { it > 0 } ?: format?.contentLength?.takeIf { it > 0 }
    add(stringResource(R.string.song_detail_size), size?.let { Formatter.formatShortFileSize(context, it) })
    add(stringResource(R.string.loudness), format?.loudnessDb?.let { "%.1f dB".format(it) })
    add(stringResource(R.string.song_info_itag_label), format?.itag?.takeIf { it > 0 }?.toString())

    // Where it comes from.
    add(
        stringResource(R.string.song_detail_source),
        stringResource(
            when {
                isLocalFile -> R.string.song_detail_source_local
                downloaded -> R.string.song_detail_source_downloaded
                else -> R.string.song_detail_source_streamed
            }
        ),
    )
    add(stringResource(R.string.song_detail_location), file?.location)
    if (!isLocalFile) {
        add(stringResource(R.string.media_id), videoId)
        add(
            stringResource(R.string.views),
            info?.viewCount?.let { stringResource(R.string.song_info_views_count, shortNumberFormatter(it)) },
        )
        add(
            stringResource(R.string.likes),
            info?.like?.let { stringResource(R.string.song_info_likes_count, shortNumberFormatter(it)) },
        )
    }
    return facts
}

/** "3/12" as "3 of 12"; a lone number as itself; a zero or junk tag as nothing. */
private fun ofTotal(context: Context, raw: String): String? {
    val parts = raw.split('/').map { it.trim().toIntOrNull() }
    val number = parts.getOrNull(0)?.takeIf { it > 0 } ?: return null
    val total = parts.getOrNull(1)?.takeIf { it > 0 }
    return if (total != null) context.getString(R.string.song_detail_of, number.toString(), total.toString()) else number.toString()
}

private fun containerName(mime: String): String = when (mime.substringBefore(';').trim().lowercase(Locale.ROOT)) {
    "audio/mpeg", "audio/mp3", "audio/x-mpeg" -> "MP3"
    "audio/mp4", "audio/x-m4a", "audio/m4a", "audio/aac-adts", "audio/aac", "audio/x-aac" -> if (mime.contains("mp4") || mime.contains("m4a")) "MPEG-4" else "AAC"
    "audio/flac", "audio/x-flac" -> "FLAC"
    "audio/ogg", "application/ogg" -> "Ogg"
    "audio/opus" -> "Opus"
    "audio/webm", "video/webm" -> "WebM"
    "audio/wav", "audio/wave", "audio/x-wav" -> "WAV"
    "audio/x-matroska", "audio/x-mka" -> "Matroska"
    "audio/amr", "audio/amr-wb", "audio/3gpp", "video/3gpp" -> "3GPP"
    "video/mp4" -> "MPEG-4"
    else -> mime.substringBefore(';').trim()
}

/** A codec as people know it, from either a MIME type or an RFC 6381 codecs string. */
private fun codecName(raw: String): String {
    val value = raw.trim().lowercase(Locale.ROOT)
    return when {
        value == "audio/mp4a-latm" || value.startsWith("mp4a.40.2") -> "AAC"
        value.startsWith("mp4a.40.5") || value.startsWith("mp4a.40.29") -> "HE-AAC"
        value.startsWith("mp4a") -> "AAC"
        value == "audio/mpeg" || value == "mp3" || value == "mp4a.6b" || value == "mp4a.69" -> "MP3"
        value == "audio/opus" || value == "opus" -> "Opus"
        value == "audio/vorbis" || value == "vorbis" -> "Vorbis"
        value == "audio/flac" || value == "flac" -> "FLAC"
        value == "audio/alac" || value == "alac" -> "ALAC"
        value == "audio/raw" -> "PCM"
        value == "audio/3gpp" -> "AMR-NB"
        value == "audio/amr-wb" -> "AMR-WB"
        value == "audio/ac3" || value == "ac-3" -> "AC-3"
        value == "audio/eac3" || value == "ec-3" -> "E-AC-3"
        else -> raw.trim()
    }
}

private fun sampleRateLabel(hz: Int): String =
    if (hz % 1000 == 0) "${hz / 1000} kHz" else "%.1f kHz".format(hz / 1000f)

private fun channelLabel(count: Int): String = when (count) {
    1 -> "Mono"
    2 -> "Stereo"
    else -> "$count channels"
}

/** What a file on this device says about itself. Every field is null when absent. */
private data class LocalFileFacts(
    val location: String?,
    val sizeBytes: Long?,
    val mimeType: String?,
    val durationMs: Long?,
    val albumArtist: String?,
    val genre: String?,
    val year: String?,
    val track: String?,
    val disc: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val codecMime: String?,
    val channels: Int?,
)

@Suppress("DEPRECATION")
private fun readLocalFileFacts(context: Context, uri: Uri): LocalFileFacts {
    var location: String? = null
    var size: Long? = null
    var mime: String? = null
    runCatching {
        val projection = buildList {
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
            else add(MediaStore.MediaColumns.DATA)
        }.toTypedArray()
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0)
                size = c.getLong(1).takeIf { it > 0 }
                mime = c.getString(2)
                val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    c.getString(3)?.trimEnd('/')
                } else {
                    c.getString(3)?.substringBeforeLast('/')
                }
                location = listOfNotNull(folder?.takeIf { it.isNotBlank() }, name).joinToString("/")
            }
        }
    }

    var durationMs: Long? = null
    var albumArtist: String? = null
    var genre: String? = null
    var year: String? = null
    var track: String? = null
    var disc: String? = null
    var bitrate: Int? = null
    var sampleRate: Int? = null
    runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun key(k: Int) = retriever.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() }
            durationMs = key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            albumArtist = key(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            genre = key(MediaMetadataRetriever.METADATA_KEY_GENRE)
            // Many MP3 and FLAC files carry the year only as a recording date (ID3v2.4
            // TDRC, Vorbis DATE), which Android's YEAR key leaves empty; the date key has it.
            year = (key(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: key(MediaMetadataRetriever.METADATA_KEY_DATE)?.take(4))
                ?.takeIf { it.length == 4 && it.toIntOrNull()?.let { y -> y in 1000..9999 } == true }
            track = key(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            disc = key(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            bitrate = key(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sampleRate = key(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            }
        } finally {
            retriever.release()
        }
    }

    var codecMime: String? = null
    var channels: Int? = null
    runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val trackMime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
                if (!trackMime.startsWith("audio/")) continue
                codecMime = trackMime
                if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRate = sampleRate ?: trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    channels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                break
            }
        } finally {
            extractor.release()
        }
    }

    return LocalFileFacts(
        location = location,
        sizeBytes = size,
        mimeType = mime,
        durationMs = durationMs,
        albumArtist = albumArtist,
        genre = genre,
        year = year,
        track = track,
        disc = disc,
        bitrate = bitrate,
        sampleRate = sampleRate,
        codecMime = codecMime,
        channels = channels,
    )
}

@Composable
fun InfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .padding(end = 8.dp)
            .clickable {
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
            },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

fun shortNumberFormatter(count: Int): String {
    return when {
        count < 1000 -> count.toString()
        count < 1_000_000 -> String.format("%.1fk", count / 1000.0)
        else -> String.format("%.1fM", count / 1_000_000.0)
    }
}
