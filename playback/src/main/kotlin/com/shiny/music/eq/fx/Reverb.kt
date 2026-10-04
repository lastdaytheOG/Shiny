package com.shiny.music.eq.fx

/**
 * A stereo algorithmic reverb in the Freeverb design (Jezar at Dreampoint): eight damped
 * comb filters in parallel into four all-passes in series, per channel, the right channel
 * detuned from the left so the tail is wide rather than a copy. The delay lengths are the
 * published 44.1 kHz tunings, scaled to the real sample rate.
 *
 * [feedback] sets the decay (the room size), [damping] how quickly the highs die in the
 * tail. Both may change every block; the comb state carries over, so a change is heard
 * as the tail evolving, never as a click.
 */
internal class Reverb(sampleRate: Int) {
    private val scale = sampleRate / 44_100f
    private val combsL = COMB_TUNINGS.map { Comb((it * scale).toInt()) }.toTypedArray()
    private val combsR = COMB_TUNINGS.map { Comb(((it + STEREO_SPREAD) * scale).toInt()) }.toTypedArray()
    private val allpassesL = ALLPASS_TUNINGS.map { Allpass((it * scale).toInt()) }.toTypedArray()
    private val allpassesR = ALLPASS_TUNINGS.map { Allpass(((it + STEREO_SPREAD) * scale).toInt()) }.toTypedArray()

    /** 0.7 (a small room) to 0.98 (a very long tail). */
    var feedback = 0.84f

    /** 0 (bright) to 0.4 (dark). */
    var damping = 0.2f

    var outL = 0f
        private set
    var outR = 0f
        private set

    fun clear() {
        combsL.forEach { it.clear() }
        combsR.forEach { it.clear() }
        allpassesL.forEach { it.clear() }
        allpassesR.forEach { it.clear() }
        outL = 0f
        outR = 0f
    }

    /** Feeds one mono sample; the stereo result is in [outL] and [outR]. */
    fun process(input: Float) {
        val x = input * FIXED_GAIN
        var l = 0f
        var r = 0f
        for (i in combsL.indices) {
            l += combsL[i].process(x, feedback, damping)
            r += combsR[i].process(x, feedback, damping)
        }
        for (i in allpassesL.indices) {
            l = allpassesL[i].process(l)
            r = allpassesR[i].process(r)
        }
        outL = l * WET_SCALE
        outR = r * WET_SCALE
    }

    private class Comb(size: Int) {
        private val buffer = FloatArray(size.coerceAtLeast(1))
        private var index = 0
        private var store = 0f

        fun clear() {
            buffer.fill(0f)
            store = 0f
            index = 0
        }

        fun process(input: Float, feedback: Float, damping: Float): Float {
            val output = buffer[index]
            store = output * (1f - damping) + store * damping + DENORMAL_GUARD
            buffer[index] = input + store * feedback
            if (++index == buffer.size) index = 0
            return output
        }
    }

    private class Allpass(size: Int) {
        private val buffer = FloatArray(size.coerceAtLeast(1))
        private var index = 0

        fun clear() {
            buffer.fill(0f)
            index = 0
        }

        fun process(input: Float): Float {
            val buffered = buffer[index]
            buffer[index] = input + buffered * 0.5f + DENORMAL_GUARD
            if (++index == buffer.size) index = 0
            return buffered - input
        }
    }

    private companion object {
        val COMB_TUNINGS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALLPASS_TUNINGS = intArrayOf(556, 441, 341, 225)
        const val STEREO_SPREAD = 23
        const val FIXED_GAIN = 0.015f
        const val WET_SCALE = 3f
    }
}
