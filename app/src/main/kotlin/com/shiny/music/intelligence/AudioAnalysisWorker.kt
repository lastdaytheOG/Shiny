package com.shiny.music.intelligence

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shiny.music.constants.AudioAnalysisEnabledKey
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.BeatInfoEntity
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
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioAnalysisEntryPoint {
    fun database(): MusicDatabase
}

class AudioAnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        if (applicationContext.dataStore.data.first()[AudioAnalysisEnabledKey] == false) return@withContext Result.success()
        val dao = EntryPointAccessors.fromApplication(applicationContext, AudioAnalysisEntryPoint::class.java).database().intelligenceDao
        val songs = dao.songsAwaitingAnalysis(BATCH)
        var measured = 0
        for (song in songs) {
            if (!coroutineContext.isActive) break
            val audio = runCatching { AudioDecoder.decode(applicationContext, Uri.parse(song.songId)) }
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
        Timber.tag(IntelligenceRepository.TAG).d("audio analysis: %d of %d measured", measured, songs.size)
        Result.success()
    }

    companion object {
        private const val BATCH = 25
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

object AudioDecoder {
    const val TARGET_RATE = 11_025
    private const val SECONDS = 45
    private const val TIMEOUT_US = 10_000L

    fun decode(context: Context, uri: Uri): FloatArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
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
                }
                out >= 0 -> {
                    val buffer = codec.getOutputBuffer(out)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val frames = buffer.remaining() / channels.coerceAtLeast(1)
                    val step = sourceRate.toDouble() / TARGET_RATE
                    while (position < frames && written < output.size) {
                        val frame = position.toInt()
                        var sum = 0f
                        for (c in 0 until channels) sum += buffer.get(frame * channels + c) / 32768f
                        output[written++] = sum / channels
                        position += step
                    }
                    position -= frames
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
        return if (written < TARGET_RATE * 10) null else output.copyOf(written)
    }
}
