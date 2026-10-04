package com.shiny.music.eq.fx

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Small building blocks for [SoundFxAudioProcessor]. Everything runs on the audio thread,
 * per sample, so nothing here allocates after construction.
 */

/** Moves a value toward its target with a time constant, so no parameter ever jumps. */
internal class Smoothed(initial: Float) {
    var value = initial
        private set
    var target = initial
    private var coef = 0f

    fun setTime(seconds: Float, sampleRate: Int) {
        coef = exp(-1.0 / (seconds * sampleRate)).toFloat()
    }

    /** Snaps to [v] (a fresh start, where there is nothing to glide from). */
    fun reset(v: Float) {
        value = v
        target = v
    }

    fun next(): Float {
        val next = target + (value - target) * coef
        // Snap when a step no longer moves the value (or it is as good as there). Near 1.0
        // each step soon becomes smaller than a float can represent, and the value stalled
        // about 0.0001 short for ever, so the processor never went back to its exact copy.
        value = if (next == value || abs(next - target) < SNAP) target else next
        return value
    }

    fun settled(epsilon: Float = 1e-4f) = abs(value - target) < epsilon

    private companion object {
        const val SNAP = 1e-5f
    }
}

/** A float biquad (RBJ cookbook) with independent left and right state. */
internal class StereoBiquad {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var x1L = 0f
    private var x2L = 0f
    private var y1L = 0f
    private var y2L = 0f
    private var x1R = 0f
    private var x2R = 0f
    private var y1R = 0f
    private var y2R = 0f

    fun clear() {
        x1L = 0f; x2L = 0f; y1L = 0f; y2L = 0f
        x1R = 0f; x2R = 0f; y1R = 0f; y2R = 0f
    }

    fun setHighPass(sampleRate: Int, frequency: Double, q: Double = 0.7071) {
        val w = 2.0 * PI * frequency / sampleRate
        val alpha = sin(w) / (2.0 * q)
        val c = cos(w)
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    fun setLowShelf(sampleRate: Int, frequency: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w = 2.0 * PI * frequency / sampleRate
        val c = cos(w)
        val alpha = sin(w) / 2.0 * sqrt(2.0)
        val s = 2 * sqrt(a) * alpha
        set(
            a * ((a + 1) - (a - 1) * c + s), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - s),
            (a + 1) + (a - 1) * c + s, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - s,
        )
    }

    fun setHighShelf(sampleRate: Int, frequency: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w = 2.0 * PI * frequency / sampleRate
        val c = cos(w)
        val alpha = sin(w) / 2.0 * sqrt(2.0)
        val s = 2 * sqrt(a) * alpha
        set(
            a * ((a + 1) + (a - 1) * c + s), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - s),
            (a + 1) - (a - 1) * c + s, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - s,
        )
    }

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = (nb0 / na0).toFloat()
        b1 = (nb1 / na0).toFloat()
        b2 = (nb2 / na0).toFloat()
        a1 = (na1 / na0).toFloat()
        a2 = (na2 / na0).toFloat()
    }

    fun left(x: Float): Float {
        val y = b0 * x + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L + DENORMAL_GUARD
        x2L = x1L; x1L = x; y2L = y1L; y1L = y
        return y
    }

    fun right(x: Float): Float {
        val y = b0 * x + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R + DENORMAL_GUARD
        x2R = x1R; x1R = x; y2R = y1R; y1R = y
        return y
    }
}

/** A one-pole low-pass whose cutoff can move every sample without zipper noise. */
internal class OnePole {
    private var z = 0f
    fun clear() {
        z = 0f
    }

    /** [coef] from [coefFor]: 0 is fully open, towards 1 is darker. */
    fun process(x: Float, coef: Float): Float {
        z = x + (z - x) * coef + DENORMAL_GUARD
        return z
    }

    companion object {
        fun coefFor(cutoffHz: Float, sampleRate: Int): Float =
            exp(-2.0 * PI * cutoffHz / sampleRate).toFloat()
    }
}

/** A circular delay line read at a fractional delay (linear interpolation). */
internal class DelayLine(capacity: Int) {
    private val buffer = FloatArray(capacity.coerceAtLeast(4))
    private val longest = (buffer.size - 2).toFloat()
    private var write = 0

    fun clear() {
        buffer.fill(0f)
        write = 0
    }

    fun push(x: Float) {
        buffer[write] = x
        write = if (write + 1 == buffer.size) 0 else write + 1
    }

    /** The sample pushed [delay] samples ago (0 = the last one pushed). */
    fun read(delay: Float): Float {
        val d = if (delay < 0f) 0f else if (delay > longest) longest else delay
        val whole = d.toInt()
        val frac = d - whole
        var i0 = write - 1 - whole
        if (i0 < 0) i0 += buffer.size
        var i1 = i0 - 1
        if (i1 < 0) i1 += buffer.size
        return buffer[i0] + (buffer[i1] - buffer[i0]) * frac
    }
}

/**
 * The last stage before the output: a stereo-linked peak limiter (fast attack, smooth
 * release) under a ceiling of −1 dBFS, then a soft knee that rounds off anything the
 * attack was too slow for. Quiet material passes untouched; nothing leaves above 0 dBFS.
 */
internal class Limiter {
    private var envelope = 1f
    private var attack = 0f
    private var release = 0f

    fun configure(sampleRate: Int) {
        attack = exp(-1.0 / (0.0008 * sampleRate)).toFloat()
        release = exp(-1.0 / (0.180 * sampleRate)).toFloat()
        envelope = 1f
    }

    fun clear() {
        envelope = 1f
    }

    /** Gain reduction currently applied, in dB (0 when idle). */
    val reductionDb: Float get() = if (envelope >= 1f) 0f else (20.0 * kotlin.math.log10(envelope.toDouble())).toFloat()

    /** Returns the gain to apply to this frame, whose peak is [peak]. */
    fun gainFor(peak: Float): Float {
        val target = if (peak > CEILING) CEILING / peak else 1f
        envelope = if (target < envelope) {
            target + (envelope - target) * attack
        } else {
            target + (envelope - target) * release
        }
        return envelope
    }

    companion object {
        const val CEILING = 0.891f // −1 dBFS

        private const val KNEE = 0.93f

        /** Leaves |x| ≤ [KNEE] alone and bends the rest smoothly towards 1.0. */
        fun softClip(x: Float): Float {
            val a = abs(x)
            if (a <= KNEE) return x
            val over = (a - KNEE) / (1f - KNEE)
            val bent = KNEE + (1f - KNEE) * tanh(over.toDouble()).toFloat()
            return if (x < 0) -bent else bent
        }
    }
}

/** Added in feedback paths so decaying tails never turn into slow denormal arithmetic. */
internal const val DENORMAL_GUARD = 1e-20f
