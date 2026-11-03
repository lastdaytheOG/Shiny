// StereoResampler.kt

package com.shiny.music.video

import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.tan

// down to the printer's rate, low-passed first so nothing folds back
internal class StereoResampler(
    val inputRate: Int,
    outputRate: Int,
    cutoffHz: Double = 6_500.0,
) {
    internal val step = inputRate.toDouble() / outputRate
    internal val filtering = inputRate > outputRate
    // a 4th-order Butterworth low-pass: two biquads per channel
    internal val left = arrayOf(Biquad.lowPass(inputRate, cutoffHz, 0.5412), Biquad.lowPass(inputRate, cutoffHz, 1.3066))
    internal val right = arrayOf(Biquad.lowPass(inputRate, cutoffHz, 0.5412), Biquad.lowPass(inputRate, cutoffHz, 1.3066))
    internal var position = 0.0
    internal var previousLeft = 0f
    internal var previousRight = 0f

    inline fun push(pcm: ShortBuffer, channels: Int, emit: (mid: Float, side: Float) -> Unit) {
        val frames = pcm.remaining() / channels
        for (frame in 0 until frames) {
            var l = pcm.get() / 32_768f
            var r = if (channels >= 2) pcm.get() / 32_768f else l
            // a surround stream's other channels aren't part of the stereo picture
            for (c in 2 until channels) pcm.get()
            if (filtering) {
                l = left[1].process(left[0].process(l))
                r = right[1].process(right[0].process(r))
            }
            while (position <= 1.0) {
                val t = position.toFloat()
                val sampleL = previousLeft + (l - previousLeft) * t
                val sampleR = previousRight + (r - previousRight) * t
                emit((sampleL + sampleR) * 0.5f, (sampleL - sampleR) * 0.5f)
                position += step
            }
            position -= 1.0
            previousLeft = l
            previousRight = r
        }
    }

    internal class Biquad(
        private val b0: Float, private val b1: Float, private val b2: Float,
        private val a1: Float, private val a2: Float,
    ) {
        private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }

        companion object {
            fun lowPass(rate: Int, cutoffHz: Double, q: Double): Biquad {
                val k = tan(PI * cutoffHz / rate)
                val norm = 1.0 / (1.0 + k / q + k * k)
                val b0 = k * k * norm
                return Biquad(
                    b0 = b0.toFloat(),
                    b1 = (2.0 * b0).toFloat(),
                    b2 = b0.toFloat(),
                    a1 = (2.0 * (k * k - 1.0) * norm).toFloat(),
                    a2 = ((1.0 - k / q + k * k) * norm).toFloat(),
                )
            }
        }
    }

}
