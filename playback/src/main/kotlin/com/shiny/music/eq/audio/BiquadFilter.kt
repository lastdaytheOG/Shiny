package com.shiny.music.eq.audio

import com.shiny.music.eq.data.FilterType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt


class BiquadFilter(
    private val sampleRate: Int,
    private val frequency: Double,
    private val gain: Double,
    private val q: Double = 1.41,
    private val filterType: FilterType = FilterType.PK
) {
    
    private var a0 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0

    
    private var x1L = 0.0
    private var x2L = 0.0
    private var y1L = 0.0
    private var y2L = 0.0

    private var x1R = 0.0
    private var x2R = 0.0
    private var y1R = 0.0
    private var y2R = 0.0

    init {
        calculateCoefficients()
    }

    
    private fun calculateCoefficients() {
        when (filterType) {
            FilterType.PK -> calculatePeakingCoefficients()
            FilterType.LSC -> calculateLowShelfCoefficients()
            FilterType.HSC -> calculateHighShelfCoefficients()
            else -> {
                
                calculatePeakingCoefficients()
            }
        }
    }

    
    private fun calculatePeakingCoefficients() {
        val A = 10.0.pow(gain / 40.0) 
        val omega = 2.0 * PI * frequency / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2.0 * q)

        
        b0 = 1.0 + alpha * A
        b1 = -2.0 * cosOmega
        b2 = 1.0 - alpha * A
        a0 = 1.0 + alpha / A
        a1 = -2.0 * cosOmega
        a2 = 1.0 - alpha / A

        
        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 /= a0
        a2 /= a0
        a0 = 1.0
    }

    
    private fun calculateLowShelfCoefficients() {
        val A = sqrt(10.0.pow(gain / 20.0)) 
        val omega = 2.0 * PI * frequency / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val S = 1.0 
        val alpha = sinOmega / 2.0 * sqrt((A + 1.0 / A) * (1.0 / S - 1.0) + 2.0)
        val sqrtA = sqrt(A)

        
        val aPlusOne = A + 1.0
        val aMinusOne = A - 1.0
        val twoSqrtAAlpha = 2.0 * sqrtA * alpha

        b0 = A * (aPlusOne - aMinusOne * cosOmega + twoSqrtAAlpha)
        b1 = 2.0 * A * (aMinusOne - aPlusOne * cosOmega)
        b2 = A * (aPlusOne - aMinusOne * cosOmega - twoSqrtAAlpha)
        a0 = aPlusOne + aMinusOne * cosOmega + twoSqrtAAlpha
        a1 = -2.0 * (aMinusOne + aPlusOne * cosOmega)
        a2 = aPlusOne + aMinusOne * cosOmega - twoSqrtAAlpha

        
        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 /= a0
        a2 /= a0
        a0 = 1.0
    }

    
    private fun calculateHighShelfCoefficients() {
        val A = sqrt(10.0.pow(gain / 20.0)) 
        val omega = 2.0 * PI * frequency / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val S = 1.0 
        val alpha = sinOmega / 2.0 * sqrt((A + 1.0 / A) * (1.0 / S - 1.0) + 2.0)
        val sqrtA = sqrt(A)

        
        val aPlusOne = A + 1.0
        val aMinusOne = A - 1.0
        val twoSqrtAAlpha = 2.0 * sqrtA * alpha

        b0 = A * (aPlusOne + aMinusOne * cosOmega + twoSqrtAAlpha)
        b1 = -2.0 * A * (aMinusOne + aPlusOne * cosOmega)
        b2 = A * (aPlusOne + aMinusOne * cosOmega - twoSqrtAAlpha)
        a0 = aPlusOne - aMinusOne * cosOmega + twoSqrtAAlpha
        a1 = 2.0 * (aMinusOne - aPlusOne * cosOmega)
        a2 = aPlusOne - aMinusOne * cosOmega - twoSqrtAAlpha

        
        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 /= a0
        a2 /= a0
        a0 = 1.0
    }

    
    fun processSample(input: Double): Double {
        val output = b0 * input + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L

        
        x2L = x1L
        x1L = input
        y2L = y1L
        y1L = output

        return output
    }

    
    fun processStereo(inputLeft: Double, inputRight: Double): Pair<Double, Double> {
        
        val outputLeft = b0 * inputLeft + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
        x2L = x1L
        x1L = inputLeft
        y2L = y1L
        y1L = outputLeft

        
        val outputRight = b0 * inputRight + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
        x2R = x1R
        x1R = inputRight
        y2R = y1R
        y1R = outputRight

        return Pair(outputLeft, outputRight)
    }

    /** One left-channel sample, without the allocation [processStereo]'s Pair costs per sample. */
    fun processLeft(input: Double): Double {
        val output = b0 * input + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
        x2L = x1L
        x1L = input
        y2L = y1L
        y1L = output
        return output
    }

    fun processRight(input: Double): Double {
        val output = b0 * input + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
        x2R = x1R
        x1R = input
        y2R = y1R
        y1R = output
        return output
    }

    /**
     * Takes over [other]'s history, so a filter replacing it (the same band, retuned)
     * continues the waveform instead of starting from silence.
     */
    fun copyStateFrom(other: BiquadFilter) {
        x1L = other.x1L; x2L = other.x2L; y1L = other.y1L; y2L = other.y2L
        x1R = other.x1R; x2R = other.x2R; y1R = other.y1R; y2R = other.y2R
    }

    /** This filter's gain at [frequencyHz], in dB. */
    fun magnitudeDb(frequencyHz: Double): Double {
        val w = 2.0 * PI * frequencyHz / sampleRate
        val cos1 = cos(w)
        val sin1 = sin(w)
        val cos2 = cos(2 * w)
        val sin2 = sin(2 * w)
        val numRe = b0 + b1 * cos1 + b2 * cos2
        val numIm = -(b1 * sin1 + b2 * sin2)
        val denRe = 1.0 + a1 * cos1 + a2 * cos2
        val denIm = -(a1 * sin1 + a2 * sin2)
        val num = numRe * numRe + numIm * numIm
        val den = denRe * denRe + denIm * denIm
        return 10.0 * kotlin.math.log10((num / den).coerceAtLeast(1e-12))
    }

    fun reset() {
        x1L = 0.0
        x2L = 0.0
        y1L = 0.0
        y2L = 0.0
        x1R = 0.0
        x2R = 0.0
        y1R = 0.0
        y2R = 0.0
    }
}