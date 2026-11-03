// Fft.kt

package com.shiny.music.video

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// an in-place radix-2 fft for one fixed power-of-two size, its twiddles worked out once
internal class Fft(private val size: Int) {
    private val cosTable = FloatArray(size / 2) { cos(2.0 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2.0 * PI * it / size).toFloat() }

    fun transform(re: FloatArray, im: FloatArray) {
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val r = re[i]; re[i] = re[j]; re[j] = r
                val m = im[i]; im[i] = im[j]; im[j] = m
            }
        }
        var length = 2
        while (length <= size) {
            val half = length / 2
            for (start in 0 until size step length) {
                for (k in 0 until half) {
                    val twiddle = k * (size / length)
                    val wr = cosTable[twiddle]; val wi = -sinTable[twiddle]
                    val even = start + k; val odd = even + half
                    val oddReal = re[odd] * wr - im[odd] * wi
                    val oddImaginary = re[odd] * wi + im[odd] * wr
                    re[odd] = re[even] - oddReal; im[odd] = im[even] - oddImaginary
                    re[even] += oddReal; im[even] += oddImaginary
                }
            }
            length = length shl 1
        }
    }
}
