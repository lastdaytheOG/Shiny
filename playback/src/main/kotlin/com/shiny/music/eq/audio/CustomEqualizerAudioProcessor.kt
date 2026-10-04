package com.shiny.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.shiny.music.eq.data.ParametricEQ
import com.shiny.music.eq.data.ParametricEQBand
import com.shiny.music.eq.fx.SoundFxEngine
import timber.log.Timber
import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * The equaliser: a cascade of biquads on the 16-bit stream.
 *
 * Three things keep it clean:
 * - **No clicks on change.** A new setting builds a new filter chain that takes over the
 *   old chain's history and is crossfaded in over [FADE_SECONDS]; switching the EQ off
 *   fades to the dry signal the same way. Moving a slider never resets anything.
 * - **No clipping.** Boosts would push a loud master past full scale, and 16-bit samples
 *   can only be clamped. So the chain is lowered by the peak of its own frequency response
 *   (headroom), and [SoundFxEngine] gives half of it back after the effects, under the
 *   limiter.
 * - **A/B.** With [SoundFxEngine.original] on it blends to the untouched input.
 */
@UnstableApi
class CustomEqualizerAudioProcessor : BaseAudioProcessor() {

    /** A set of filters and the gain after them; immutable once built. */
    private class Chain(val filters: Array<BiquadFilter>, val gain: Double, val headroomDb: Double) {
        val isFlat get() = filters.isEmpty() && gain == 1.0
    }

    private var sampleRate = 0
    private var channelCount = 0

    /** The profile the listener chose; the chain is rebuilt from it for every sample rate. */
    @Volatile
    private var profile: ParametricEQ? = null

    /** Handed from [applyProfile]/[disable] to the audio thread, which starts the crossfade. */
    @Volatile
    private var pending: Chain? = null

    private var current: Chain = FLAT
    private var previous: Chain? = null
    private var fadeLeft = 0
    private var fadeLength = 1

    private var abMix = 0.0
    private var abCoef = 0.0
    private var samples = ShortArray(0)

    @Synchronized
    fun applyProfile(parametricEQ: ParametricEQ) {
        profile = parametricEQ
        if (sampleRate == 0) {
            Timber.tag(TAG).d("Not configured yet; %d bands will apply at configure", parametricEQ.bands.size)
            return
        }
        pending = buildChain(parametricEQ, sampleRate)
    }

    @Synchronized
    fun disable() {
        profile = null
        SoundFxEngine.eqHeadroomDb = 0f
        if (sampleRate != 0) pending = FLAT
        Timber.tag(TAG).d("Equalizer disabled")
    }

    fun isEnabled(): Boolean = profile != null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        synchronized(this) {
            sampleRate = inputAudioFormat.sampleRate
            channelCount = inputAudioFormat.channelCount
            fadeLength = max(1, (FADE_SECONDS * sampleRate).toInt())
            abCoef = exp(-1.0 / (0.02 * sampleRate))
            // A new sample rate needs new coefficients; start on them directly.
            current = profile?.let { buildChain(it, sampleRate) } ?: FLAT
            previous = null
            pending = null
            fadeLeft = 0
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)

        pending?.let { next ->
            pending = null
            handOver(current, next)
            previous = current
            current = next
            fadeLeft = fadeLength
        }

        val abTarget = if (SoundFxEngine.original.value) 1.0 else 0.0
        val chain = current
        if (chain.isFlat && fadeLeft == 0) {
            // Flat and not fading: the input as it is. (A/B has nothing to blend to.)
            abMix = abTarget
            out.put(inputBuffer)
            out.flip()
            return
        }

        val frames = size / (2 * channelCount)
        val count = frames * channelCount
        if (samples.size < count) samples = ShortArray(count)
        // In bulk: a getShort/putShort per sample cost more than the filters on the phone.
        inputBuffer.asShortBuffer().get(samples, 0, count)
        inputBuffer.position(inputBuffer.position() + count * 2)
        var at = 0
        for (frame in 0 until frames) {
            abMix = abTarget + (abMix - abTarget) * abCoef
            if (channelCount == 2) {
                val inL = samples[at] / 32_768.0
                val inR = samples[at + 1] / 32_768.0
                var l = runLeft(chain, inL)
                var r = runRight(chain, inR)
                val old = previous
                if (fadeLeft > 0 && old != null) {
                    val t = fadeLeft.toDouble() / fadeLength
                    l += (runLeft(old, inL) - l) * t
                    r += (runRight(old, inR) - r) * t
                    if (--fadeLeft == 0) previous = null
                }
                l += (inL - l) * abMix
                r += (inR - r) * abMix
                samples[at++] = toPcm(l)
                samples[at++] = toPcm(r)
            } else {
                val x = samples[at] / 32_768.0
                var y = runLeft(chain, x)
                val old = previous
                if (fadeLeft > 0 && old != null) {
                    y += (runLeft(old, x) - y) * (fadeLeft.toDouble() / fadeLength)
                    if (--fadeLeft == 0) previous = null
                }
                y += (x - y) * abMix
                samples[at++] = toPcm(y)
            }
        }
        out.asShortBuffer().put(samples, 0, count)
        out.position(out.position() + count * 2)
        if (fadeLeft == 0) previous = null
        out.flip()
    }

    override fun onFlush() {
        current.filters.forEach { it.reset() }
        previous = null
        fadeLeft = 0
    }

    override fun onReset() {
        sampleRate = 0
        channelCount = 0
        current = FLAT
        previous = null
        pending = null
        fadeLeft = 0
    }

    private fun runLeft(chain: Chain, x: Double): Double {
        var y = x
        for (filter in chain.filters) y = filter.processLeft(y)
        return y * chain.gain
    }

    private fun runRight(chain: Chain, x: Double): Double {
        var y = x
        for (filter in chain.filters) y = filter.processRight(y)
        return y * chain.gain
    }

    /** The same band in the new chain continues the old one's waveform. */
    private fun handOver(from: Chain, to: Chain) {
        if (from.filters.size != to.filters.size) return
        for (i in to.filters.indices) to.filters[i].copyStateFrom(from.filters[i])
    }

    private fun buildChain(eq: ParametricEQ, rate: Int): Chain {
        val filters = eq.bands
            .filter { it.enabled && it.frequency < rate / 2.0 }
            .map { band: ParametricEQBand ->
                BiquadFilter(sampleRate = rate, frequency = band.frequency, gain = band.gain, q = band.q, filterType = band.filterType)
            }
            .toTypedArray()
        // The loudest point of the response, preamp included, is the headroom to leave.
        val peakDb = PROBE_FREQUENCIES.filter { it < rate / 2.0 }.maxOfOrNull { f ->
            filters.sumOf { it.magnitudeDb(f) }
        }?.plus(eq.preamp) ?: eq.preamp
        val headroomDb = max(0.0, peakDb)
        SoundFxEngine.eqHeadroomDb = headroomDb.toFloat()
        val gain = 10.0.pow((eq.preamp - headroomDb) / 20.0)
        Timber.tag(TAG).d("EQ: %d bands, preamp %.1f dB, headroom %.1f dB", filters.size, eq.preamp, headroomDb)
        return Chain(filters, gain, headroomDb)
    }

    private fun toPcm(x: Double): Short {
        val v = x * 32_768.0
        val i = if (v >= 0.0) (v + 0.5).toInt() else (v - 0.5).toInt()
        return (if (i > 32_767) 32_767 else if (i < -32_768) -32_768 else i).toShort()
    }

    private companion object {
        const val TAG = "CustomEqualizerAudioProcessor"
        const val FADE_SECONDS = 0.025
        val FLAT = Chain(emptyArray(), 1.0, 0.0)

        /** Where the response is probed for its peak: 96 points, evenly spaced in octaves. */
        val PROBE_FREQUENCIES = DoubleArray(96) { 20.0 * 1000.0.pow(it / 95.0) }
    }
}
