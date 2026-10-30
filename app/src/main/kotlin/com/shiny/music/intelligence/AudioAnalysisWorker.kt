package com.shiny.music.intelligence

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shiny.music.constants.AudioAnalysisEnabledKey
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.BeatInfoEntity
import com.shiny.music.di.DownloadCache
import com.shiny.music.di.PlayerCache
import com.shiny.music.downloads.DownloadFolderStore
import com.shiny.music.downloads.OfflineFileDataSource
import com.shiny.music.utils.dataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioAnalysisEntryPoint {
    fun database(): MusicDatabase

    fun folderStore(): DownloadFolderStore

    @DownloadCache
    fun downloadCache(): SimpleCache

    @PlayerCache
    fun playerCache(): SimpleCache
}

@androidx.annotation.OptIn(UnstableApi::class)
class AudioAnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        if (applicationContext.dataStore.data.first()[AudioAnalysisEnabledKey] == false) return@withContext Result.success()
        val deps = EntryPointAccessors.fromApplication(applicationContext, AudioAnalysisEntryPoint::class.java)
        val dao = deps.database().intelligenceDao
        val songs = dao.songsAwaitingAnalysis(CHECKED)
        var measured = 0
        for (song in songs) {
            if (!coroutineContext.isActive || measured >= BATCH) break
            val stored = if (song.isLocal) null else storedCopy(deps, song.songId) ?: continue
            val audio = runCatching {
                if (stored != null) AudioDecoder.decode(stored) else AudioDecoder.decode(applicationContext, Uri.parse(song.songId))
            }
                .onFailure { Timber.tag(IntelligenceRepository.TAG).d(it, "not decoded: %s", song.songId) }
                .getOrNull()
                ?.let { AudioFeatures.analyse(it, AudioDecoder.TARGET_RATE) }
            dao.upsertBeatInfo(
                BeatInfoEntity(
                    songId = song.songId,
                    bpm = audio?.bpm ?: 0f,
                    firstBeatOffsetMs = audio?.firstBeatMs ?: 0L,
                    confidence = audio?.tempoConfidence ?: 0f,
                    keyPitchClass = audio?.keyPitchClass,
                    keyIsMinor = audio?.keyIsMinor,
                    energy = audio?.energy,
                    brightness = audio?.brightness,
                    dynamics = audio?.dynamics,
                ),
            )
            if (audio != null) measured++
        }
        Timber.tag(IntelligenceRepository.TAG).d("audio analysis: %d measured, %d looked at", measured, songs.size)
        Result.success()
    }

    private fun storedCopy(deps: AudioAnalysisEntryPoint, id: String): MediaDataSource? {
        val store = deps.folderStore()
        if (store.readableDocument(id) != null) {
            val uri = DownloadFolderStore.playbackUri(id)
            val source = OfflineFileDataSource(applicationContext, store, PlaceholderDataSource.INSTANCE)
            val length = runCatching {
                try {
                    source.open(DataSpec.Builder().setUri(uri).setKey(id).build())
                } finally {
                    source.close()
                }
            }.getOrDefault(C.LENGTH_UNSET.toLong())
            if (length > 0) return DataSourceAudio(source, uri, id, length)
        }
        for (cache in listOf(deps.downloadCache(), deps.playerCache())) {
            val length = runCatching { ContentMetadata.getContentLength(cache.getContentMetadata(id)) }
                .getOrDefault(C.LENGTH_UNSET.toLong())
            if (length <= 0 || !runCatching { cache.isCached(id, 0, length) }.getOrDefault(false)) continue
            return DataSourceAudio(CacheDataSource(cache, null), "shiny-analysis://$id".toUri(), id, length)
        }
        return null
    }

    companion object {
        private const val BATCH = 25
        private const val CHECKED = 200
        private const val NAME = "audio_analysis"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AudioAnalysisWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiresDeviceIdle(true)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
class DataSourceAudio(
    private val source: DataSource,
    private val uri: Uri,
    private val key: String,
    private val length: Long,
) : MediaDataSource() {
    private var opened = false
    private var cursor = 0L

    override fun getSize(): Long = length

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= length) return -1
        if (!opened || position != cursor) {
            if (opened) source.close()
            source.open(DataSpec.Builder().setUri(uri).setKey(key).setPosition(position).build())
            opened = true
            cursor = position
        }
        val read = source.read(buffer, offset, size)
        if (read == C.RESULT_END_OF_INPUT) return -1
        cursor += read
        return read
    }

    override fun close() {
        if (opened) source.close()
        opened = false
    }
}

class PcmFrames(private val bytes: ByteBuffer, private val channels: Int, private val encoding: Int) {
    private val width = when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        else -> 2
    }
    private val start = bytes.position()

    val count = bytes.remaining() / (width * channels.coerceAtLeast(1))

    fun mono(frame: Int): Float {
        var sum = 0f
        for (c in 0 until channels) sum += sample(start + (frame * channels + c) * width)
        return sum / channels
    }

    private fun sample(at: Int): Float = when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> bytes.getFloat(at)
        AudioFormat.ENCODING_PCM_32BIT -> bytes.getInt(at) / 2_147_483_648f
        AudioFormat.ENCODING_PCM_24BIT_PACKED ->
            ((bytes.get(at).toInt() and 0xff) or ((bytes.get(at + 1).toInt() and 0xff) shl 8) or (bytes.get(at + 2).toInt() shl 16)) / 8_388_608f
        else -> bytes.getShort(at) / 32_768f
    }
}

object AudioDecoder {
    const val TARGET_RATE = 11_025
    private const val SECONDS = 45
    private const val TIMEOUT_US = 10_000L

    fun decode(context: Context, uri: Uri): FloatArray? = decode { it.setDataSource(context, uri, null) }

    fun decode(source: MediaDataSource): FloatArray? = decode { it.setDataSource(source) }

    private fun decode(open: (MediaExtractor) -> Unit): FloatArray? {
        val extractor = MediaExtractor()
        try {
            open(extractor)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            if (duration > (SECONDS + 30) * 1_000_000L) extractor.seekTo(duration / 3, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                return drain(extractor, codec, format)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun drain(extractor: MediaExtractor, codec: MediaCodec, input: MediaFormat): FloatArray? {
        var sourceRate = input.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = input.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        val output = FloatArray(TARGET_RATE * SECONDS)
        var written = 0
        var position = 0.0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        while (written < output.size) {
            if (!inputDone) {
                val slot = codec.dequeueInputBuffer(TIMEOUT_US)
                if (slot >= 0) {
                    val size = extractor.readSampleData(codec.getInputBuffer(slot)!!, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(slot, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(slot, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    sourceRate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (codec.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        encoding = codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                }
                out >= 0 -> {
                    val frames = PcmFrames(codec.getOutputBuffer(out)!!.order(ByteOrder.LITTLE_ENDIAN), channels, encoding)
                    val step = sourceRate.toDouble() / TARGET_RATE
                    while (position < frames.count && written < output.size) {
                        output[written++] = frames.mono(position.toInt())
                        position += step
                    }
                    position -= frames.count
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
        return if (written < TARGET_RATE * 10) null else output.copyOf(written)
    }
}
