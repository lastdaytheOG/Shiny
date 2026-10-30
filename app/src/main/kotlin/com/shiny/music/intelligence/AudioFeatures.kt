package com.shiny.music.intelligence

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class MeasuredAudio(
    val bpm: Float,
    val firstBeatMs: Long,
    val tempoConfidence: Float,
    val energy: Float,
    val brightness: Float,
    val dynamics: Float,
    val keyPitchClass: Int?,
    val keyIsMinor: Boolean?,
)

object AudioFeatures {
    private const val FRAME = 256
    private const val MIN_BPM = 60.0
    private const val MAX_BPM = 190.0

    private val MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    fun analyse(samples: FloatArray, sampleRate: Int): MeasuredAudio? {
        if (samples.size < sampleRate * 10) return null
        val frames = samples.size / FRAME
        val envelope = DoubleArray(frames)
        var crossings = 0L
        var energySum = 0.0
        var peak = 0.0
        for (frame in 0 until frames) {
            var sum = 0.0
            val start = frame * FRAME
            for (i in start until start + FRAME) {
                val s = samples[i].toDouble()
                sum += s * s
                peak = max(peak, abs(s))
                if (i > start && (samples[i] >= 0f) != (samples[i - 1] >= 0f)) crossings++
            }
            envelope[frame] = sqrt(sum / FRAME)
            energySum += sum
        }
        val rms = sqrt(energySum / (frames * FRAME))
        if (rms < 1e-4) return null

        val flux = DoubleArray(frames)
        for (i in 1 until frames) flux[i] = max(0.0, envelope[i] - envelope[i - 1])
        val frameRate = sampleRate.toDouble() / FRAME
        val tempo = tempo(flux, frameRate)

        val zcrPerSecond = crossings.toDouble() / (samples.size.toDouble() / sampleRate)
        val brightness = (zcrPerSecond / sampleRate).coerceIn(0.0, 0.5) * 2.0
        val db = 20.0 * log10(rms.coerceAtLeast(1e-6))
        val energy = ((db + 40.0) / 40.0).coerceIn(0.0, 1.0)
        val crest = 20.0 * log10((peak / rms).coerceAtLeast(1.0))
        val dynamics = (crest / 20.0).coerceIn(0.0, 1.0)
        val key = key(samples, sampleRate)

        return MeasuredAudio(
            bpm = tempo.first.toFloat(),
            firstBeatMs = firstBeatMs(flux, frameRate),
            tempoConfidence = tempo.second.toFloat(),
            energy = energy.toFloat(),
            brightness = brightness.toFloat(),
            dynamics = dynamics.toFloat(),
            keyPitchClass = key?.first,
            keyIsMinor = key?.second,
        )
    }

    fun tempo(flux: DoubleArray, frameRate: Double): Pair<Double, Double> {
        val mean = flux.average()
        val centred = DoubleArray(flux.size) { flux[it] - mean }
        val minLag = (frameRate * 60.0 / MAX_BPM).toInt().coerceAtLeast(1)
        val maxLag = min((frameRate * 60.0 / MIN_BPM).toInt() + 1, centred.size / 2)
        if (maxLag <= minLag) return 0.0 to 0.0
        var zero = 0.0
        for (v in centred) zero += v * v
        if (zero <= 0.0) return 0.0 to 0.0

        val scores = DoubleArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var sum = 0.0
            for (i in 0 until centred.size - lag) sum += centred[i] * centred[i + lag]
            scores[lag] = sum / zero
        }
        var bestLag = minLag
        var best = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            val bpm = 60.0 * frameRate / lag
            val preference = 1.0 - 0.15 * abs(bpm - 110.0) / 110.0
            val value = scores[lag] * preference
            if (value > best) {
                best = value
                bestLag = lag
            }
        }
        val refined = if (bestLag in (minLag + 1) until maxLag) {
            val a = scores[bestLag - 1]
            val b = scores[bestLag]
            val c = scores[bestLag + 1]
            val shift = if (a - 2 * b + c != 0.0) 0.5 * (a - c) / (a - 2 * b + c) else 0.0
            bestLag + shift.coerceIn(-0.5, 0.5)
        } else {
            bestLag.toDouble()
        }
        return (60.0 * frameRate / refined) to best.coerceIn(0.0, 1.0)
    }

    private fun firstBeatMs(flux: DoubleArray, frameRate: Double): Long {
        val threshold = (flux.maxOrNull() ?: 0.0) * 0.5
        val index = flux.indexOfFirst { it >= threshold }.coerceAtLeast(0)
        return (index / frameRate * 1000.0).toLong()
    }

    private fun goertzel(samples: FloatArray, sampleRate: Int, frequency: Double, step: Int): Double {
        val omega = 2.0 * PI * frequency / sampleRate
        val coefficient = 2.0 * cos(omega)
        var previous = 0.0
        var beforePrevious = 0.0
        var i = 0
        while (i < samples.size) {
            val value = samples[i] + coefficient * previous - beforePrevious
            beforePrevious = previous
            previous = value
            i += step
        }
        return beforePrevious * beforePrevious + previous * previous - coefficient * previous * beforePrevious
    }

    fun key(samples: FloatArray, sampleRate: Int): Pair<Int, Boolean>? {
        val chroma = DoubleArray(12)
        val window = min(samples.size, sampleRate * 20)
        val slice = samples.copyOfRange(0, window)
        for (octave in 3..5) {
            for (pitch in 0 until 12) {
                val frequency = 16.3516 * 2.0.pow(octave) * 2.0.pow(pitch / 12.0)
                if (frequency >= sampleRate / 2.0) continue
                chroma[pitch] += goertzel(slice, sampleRate, frequency, 1)
            }
        }
        val max = chroma.maxOrNull() ?: return null
        if (max <= 0.0) return null
        for (i in chroma.indices) chroma[i] /= max

        var bestPitch = 0
        var bestMinor = false
        var best = Double.NEGATIVE_INFINITY
        var second = Double.NEGATIVE_INFINITY
        for (pitch in 0 until 12) {
            for (minor in listOf(false, true)) {
                val profile = if (minor) MINOR else MAJOR
                val corr = correlation(chroma, profile, pitch)
                if (corr > best) {
                    second = best
                    best = corr
                    bestPitch = pitch
                    bestMinor = minor
                } else if (corr > second) {
                    second = corr
                }
            }
        }
        return if (best < 0.4 || best - second < 0.02) null else bestPitch to bestMinor
    }

    private fun correlation(chroma: DoubleArray, profile: DoubleArray, rotation: Int): Double {
        val meanChroma = chroma.average()
        val meanProfile = profile.average()
        var top = 0.0
        var a = 0.0
        var b = 0.0
        for (i in 0 until 12) {
            val x = chroma[(i + rotation) % 12] - meanChroma
            val y = profile[i] - meanProfile
            top += x * y
            a += x * x
            b += y * y
        }
        return if (a <= 0.0 || b <= 0.0) 0.0 else top / sqrt(a * b)
    }
}
