package com.shiny.music.eq.fx

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shiny's sound effects, on the real audio: stereo width, 8D, reverb, tone, then a limiter,
 * with an A/B blend back to the untouched signal at the very end.
 *
 * It sits after the player's Sonic stage, so it hears the song at the speed and pitch the
 * listener chose: an 8D turn takes the same time whatever the speed, and the reverb is a
 * room around the slowed song rather than a slowed room.
 *
 * With nothing switched on it is an exact copy — no limiter, no dither — so "Normal"
 * sounds exactly like the song. Switching anything on or off glides: every parameter is
 * smoothed, a reverb tail rings out before the reverb stops, and the processor only drops
 * back to the exact copy once everything has come to rest.
 *
 * Signal flow per frame:
 *   in → mid/side (width) → 8D (the mid rotated around the head: level, time and
 *   head-shadow cues, further and darker behind) → reverb send (pre-delay, low cut) →
 *   Freeverb → wet tone → equal-power wet/dry → bass and clarity shelves → EQ makeup →
 *   limiter and soft knee → A/B blend → out.
 */
@UnstableApi
class SoundFxAudioProcessor : BaseAudioProcessor() {
    private var sampleRate = 0
    private var inChannels = 0

    // DSP state, built for the sample rate in onConfigure.
    private var reverb: Reverb? = null
    private var preDelay = DelayLine(4)
    private var itd = DelayLine(4)
    private val sendLowCut = StereoBiquad()
    private val wetToneL = OnePole()
    private val wetToneR = OnePole()
    private val shadowL = OnePole()
    private val shadowR = OnePole()
    private val behindL = OnePole()
    private val behindR = OnePole()
    private val bassShelf = StereoBiquad()
    private val clarityShelf = StereoBiquad()
    private val limiter = Limiter()

    // Smoothed parameters: every one glides, so nothing clicks.
    private val intensity = Smoothed(0f)
    private val width = Smoothed(1f)
    private val depth = Smoothed(0f)
    private val send = Smoothed(0f)
    private val dryGain = Smoothed(1f)
    private val wetGain = Smoothed(0f)
    private val preDelaySamples = Smoothed(0f)
    private val feedback = Smoothed(0.84f)
    private val damping = Smoothed(0.2f)
    private val toneHz = Smoothed(7_000f)
    private val rotationHz = Smoothed(0.12f)
    private val bassDb = Smoothed(0f)
    private val clarityDb = Smoothed(0f)
    private val makeup = Smoothed(1f)
    private val abMix = Smoothed(0f)

    // The rotation, as a unit vector turned a little every sample.
    private var rotCos = 1.0
    private var rotSin = 0.0
    private var stepCos = 1.0
    private var stepSin = 0.0

    private var idle = true
    private var reverbRunning = false
    /** The wet level while the reverb is on, kept after it is switched off so the tail rings out. */
    private var ringingWet = 0f
    private var quietWetSamples = 0
    private var blockCounter = 0
    private var appliedBassDb = Float.NaN
    private var appliedClarityDb = Float.NaN
    private var toneCoef = 0f

    // Per-block 8D values.
    private var itdMaxSamples = 0f
    private var shadowMaxCoef = 0f
    private var behindMaxCoef = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            // Nothing Shiny plays gets here in another format; if it ever does, stay out of the way.
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        inChannels = inputAudioFormat.channelCount
        reverb = Reverb(sampleRate)
        preDelay = DelayLine((0.2 * sampleRate).toInt() + 4)
        itd = DelayLine((0.002 * sampleRate).toInt() + 4)
        sendLowCut.setHighPass(sampleRate, 180.0)
        limiter.configure(sampleRate)
        itdMaxSamples = (ITD_MAX_SECONDS * sampleRate).toFloat()
        shadowMaxCoef = OnePole.coefFor(2_800f, sampleRate)
        behindMaxCoef = OnePole.coefFor(5_500f, sampleRate)
        for (s in listOf(intensity, width, depth, send, dryGain, wetGain, bassDb, clarityDb, makeup)) s.setTime(0.045f, sampleRate)
        rotationHz.setTime(0.35f, sampleRate)
        preDelaySamples.setTime(0.3f, sampleRate)
        feedback.setTime(0.25f, sampleRate)
        damping.setTime(0.25f, sampleRate)
        toneHz.setTime(0.25f, sampleRate)
        abMix.setTime(0.02f, sampleRate)
        goIdle()
        // Mono is widened to stereo: 8D and reverb need two ears.
        return AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / (2 * inChannels)
        if (frames == 0) return
        val out = replaceOutputBuffer(frames * 4)
        SoundFxEngine.lastAudioNanos = System.nanoTime()
        // With the Equaliser off, every target is the neutral one and this glides to a copy.
        val on = SoundFxEngine.enabled.value
        val settings = if (on) SoundFxEngine.settings.value else NEUTRAL
        val original = on && SoundFxEngine.original.value
        setTargets(settings, original)

        if (idle && restingAtNeutral()) {
            copyThrough(inputBuffer, out, frames)
        } else {
            idle = false
            process(inputBuffer, out, frames)
            if (restingAtNeutral() && !reverbRunning && limiter.reductionDb > -0.01f) goIdle()
        }
        out.flip()
    }

    private fun copyThrough(input: ByteBuffer, out: ByteBuffer, frames: Int) {
        if (inChannels == 2) {
            out.put(input)
            return
        }
        repeat(frames) {
            val v = input.getShort()
            out.putShort(v)
            out.putShort(v)
        }
    }

    // Samples move in and out in bulk: a ByteBuffer getShort/putShort per sample, with its
    // bounds and byte-order checks, cost more than the effects themselves on the phone.
    private var inSamples = ShortArray(0)
    private var outSamples = ShortArray(0)

    private fun process(input: ByteBuffer, out: ByteBuffer, frames: Int) {
        val reverb = reverb ?: return copyThrough(input, out, frames)
        val inCount = frames * inChannels
        if (inSamples.size < inCount) inSamples = ShortArray(inCount)
        if (outSamples.size < frames * 2) outSamples = ShortArray(frames * 2)
        input.asShortBuffer().get(inSamples, 0, inCount)
        input.position(input.position() + inCount * 2)
        var readAt = 0
        var writeAt = 0
        for (frame in 0 until frames) {
            if (blockCounter-- <= 0) updateBlock()

            val inL = inSamples[readAt++] * FROM_PCM
            val inR = if (inChannels == 2) inSamples[readAt++] * FROM_PCM else inL

            // Mid/side: the width works on the side, 8D moves the mid.
            val mid = (inL + inR) * 0.5f
            val side = (inL - inR) * 0.5f * width.next()
            val i = intensity.next()
            val d = depth.next()

            // Advance the rotation (a unit vector: sin is left/right, cos front/back).
            val c = rotCos * stepCos - rotSin * stepSin
            val s = rotSin * stepCos + rotCos * stepSin
            rotCos = c
            rotSin = s

            // Constant-power pan with a floor: neither ear ever goes silent.
            val pan = (s * i * MAX_PAN).toFloat()
            val gainL = sqrt(1f - pan)
            val gainR = sqrt(1f + pan)
            // The far ear hears the mid a little later and a little darker.
            itd.push(mid)
            val farL = max(0f, pan)
            val farR = max(0f, -pan)
            var earL = shadowL.process(itd.read(farL * itdMaxSamples), farL * shadowMaxCoef) * gainL
            var earR = shadowR.process(itd.read(farR * itdMaxSamples), farR * shadowMaxCoef) * gainR
            // Behind the head: further away, darker.
            val behind = (max(0.0, -c) * i * d).toFloat()
            if (behind > 0f || i > 0f) {
                val distance = 1f - 0.28f * behind
                earL = behindL.process(earL, behind * behindMaxCoef) * distance
                earR = behindR.process(earR, behind * behindMaxCoef) * distance
            }
            var l = earL + side
            var r = earR - side

            // Reverb: a mono send, pre-delayed and cleared of low end, into the room.
            val sendLevel = send.next() * (1f + 0.35f * behind)
            val dry = dryGain.next()
            val wet = wetGain.next()
            if (reverbRunning) {
                preDelay.push((l + r) * 0.5f * sendLevel)
                val sent = sendLowCut.left(preDelay.read(preDelaySamples.next()))
                reverb.feedback = feedback.next()
                reverb.damping = damping.next()
                reverb.process(sent)
                val wetL = wetToneL.process(reverb.outL, toneCoef) * wet
                val wetR = wetToneR.process(reverb.outR, toneCoef) * wet
                l = l * dry + wetL
                r = r * dry + wetR
                if (abs(wetL) + abs(wetR) < 1e-5f) quietWetSamples++ else quietWetSamples = 0
            } else {
                l *= dry
                r *= dry
            }

            // Tone.
            if (appliedBassDb != 0f) {
                l = bassShelf.left(l)
                r = bassShelf.right(r)
            }
            if (appliedClarityDb != 0f) {
                l = clarityShelf.left(l)
                r = clarityShelf.right(r)
            }

            // Gain back what the equaliser gave up for headroom, then keep it all under 0 dBFS.
            val g = makeup.next()
            l *= g
            r *= g
            val limit = limiter.gainFor(max(abs(l), abs(r)))
            l = Limiter.softClip(l * limit)
            r = Limiter.softClip(r * limit)

            // A/B: blend to the untouched input.
            val a = abMix.next()
            if (a > 0f) {
                l += (inL - l) * a
                r += (inR - r) * a
            }

            outSamples[writeAt++] = toPcm(l)
            outSamples[writeAt++] = toPcm(r)
        }
        out.asShortBuffer().put(outSamples, 0, writeAt)
        out.position(out.position() + writeAt * 2)
    }

    /** Work done every [BLOCK] frames: rotation step, reverb tone, shelves, reverb lifecycle. */
    private fun updateBlock() {
        blockCounter = BLOCK
        val step = 2.0 * PI * rotationHz.value / sampleRate
        repeat(BLOCK) { rotationHz.next() }
        stepCos = cos(step)
        stepSin = sin(step)
        // Keep the rotation vector exactly unit length.
        val norm = sqrt(rotCos * rotCos + rotSin * rotSin)
        rotCos /= norm
        rotSin /= norm

        toneCoef = OnePole.coefFor(toneHz.value, sampleRate)
        repeat(BLOCK) { toneHz.next() }

        val bass = bassDb.value
        if (abs(bass - appliedBassDb) > 0.01f || appliedBassDb.isNaN()) {
            appliedBassDb = if (abs(bass) < 0.01f && bassDb.target == 0f) 0f else bass
            if (appliedBassDb != 0f) bassShelf.setLowShelf(sampleRate, 100.0, appliedBassDb.toDouble())
        }
        repeat(BLOCK) { bassDb.next() }
        val clarity = clarityDb.value
        if (abs(clarity - appliedClarityDb) > 0.01f || appliedClarityDb.isNaN()) {
            appliedClarityDb = if (abs(clarity) < 0.01f && clarityDb.target == 0f) 0f else clarity
            if (appliedClarityDb != 0f) clarityShelf.setHighShelf(sampleRate, 6_500.0, appliedClarityDb.toDouble())
        }
        repeat(BLOCK) { clarityDb.next() }

        // The reverb starts when it is sent anything, and stops once its tail has died away.
        if (send.target > 0f) {
            if (!reverbRunning) {
                reverbRunning = true
                quietWetSamples = 0
            }
        } else if (reverbRunning && send.settled() && quietWetSamples > sampleRate / 2) {
            reverbRunning = false
            ringingWet = 0f
            wetGain.reset(0f)
            reverb?.clear()
            preDelay.clear()
            sendLowCut.clear()
            wetToneL.clear()
            wetToneR.clear()
        }
    }

    private fun setTargets(s: SoundFxSettings, original: Boolean) {
        intensity.target = if (s.eightD) s.eightDIntensity.coerceIn(0f, 1f) else 0f
        rotationHz.target = s.rotationHz.coerceIn(0.02f, 1f)
        depth.target = if (s.eightD) s.depth.coerceIn(0f, 1f) else 0f
        width.target = s.stereoWidth.coerceIn(0f, 2f)
        val reverbOn = s.reverb && s.reverbAmount > 0f
        send.target = if (reverbOn) s.reverbAmount.coerceIn(0f, 1f) else 0f
        if (reverbOn) {
            // Equal-power: the blend never gets louder or quieter as it moves.
            val mix = s.reverbMix.coerceIn(0f, 1f)
            dryGain.target = cos(mix * PI / 2).toFloat()
            wetGain.target = sin(mix * PI / 2).toFloat()
            ringingWet = wetGain.target
        } else {
            // Nothing new goes in, but what is in the room rings out at the level it had.
            dryGain.target = 1f
            wetGain.target = ringingWet
        }
        preDelaySamples.target = s.reverbPreDelayMs.coerceIn(0f, 150f) / 1000f * sampleRate
        feedback.target = 0.7f + 0.28f * s.reverbDecay.coerceIn(0f, 1f)
        damping.target = s.reverbPreset.damping
        toneHz.target = s.reverbPreset.toneHz
        bassDb.target = s.bassDb.coerceIn(-12f, 12f)
        clarityDb.target = s.clarityDb.coerceIn(-12f, 12f)
        makeup.target = 10f.pow(SoundFxEngine.eqHeadroomDb.coerceIn(0f, 24f) * 0.5f / 20f)
        abMix.target = if (original) 1f else 0f
    }

    /** True when every parameter is at (and has glided to) the value that changes nothing. */
    private fun restingAtNeutral(): Boolean {
        val abResting = abMix.target == 0f && abMix.settled() || abMix.target == 1f && abMix.settled()
        return intensity.target == 0f && intensity.settled() &&
            width.target == 1f && width.settled() &&
            send.target == 0f && send.settled() &&
            dryGain.target == 1f && dryGain.settled() &&
            bassDb.target == 0f && bassDb.settled(0.01f) &&
            clarityDb.target == 0f && clarityDb.settled(0.01f) &&
            makeup.target == 1f && makeup.settled() &&
            abResting
    }

    /** Back to the exact copy: states cleared, every parameter at its neutral value. */
    private fun goIdle() {
        idle = true
        reverbRunning = false
        ringingWet = 0f
        quietWetSamples = 0
        reverb?.clear()
        preDelay.clear()
        itd.clear()
        sendLowCut.clear()
        wetToneL.clear(); wetToneR.clear()
        shadowL.clear(); shadowR.clear()
        behindL.clear(); behindR.clear()
        bassShelf.clear(); clarityShelf.clear()
        limiter.clear()
        intensity.reset(0f)
        width.reset(1f)
        depth.reset(0f)
        send.reset(0f)
        dryGain.reset(1f)
        wetGain.reset(0f)
        bassDb.reset(0f)
        clarityDb.reset(0f)
        makeup.reset(1f)
        appliedBassDb = 0f
        appliedClarityDb = 0f
        // The A/B blend keeps its value: idle only when it rests at one end anyway.
        blockCounter = 0
    }

    override fun onFlush() {
        // A seek or a new stream: the old tail and delays belong to where playback was.
        if (!idle) {
            reverb?.clear()
            preDelay.clear()
            itd.clear()
            sendLowCut.clear()
            wetToneL.clear(); wetToneR.clear()
            shadowL.clear(); shadowR.clear()
            behindL.clear(); behindR.clear()
            bassShelf.clear(); clarityShelf.clear()
            limiter.clear()
            quietWetSamples = 0
        }
    }

    override fun onReset() {
        sampleRate = 0
        inChannels = 0
        reverb = null
        preDelay = DelayLine(4)
        itd = DelayLine(4)
        idle = true
    }

    /** The exact inverse of reading (÷ 32768), so a neutral pass changes no sample. */
    private fun toPcm(x: Float): Short {
        val v = x * 32_768f
        val i = if (v >= 0f) (v + 0.5f).toInt() else (v - 0.5f).toInt()
        return (if (i > 32_767) 32_767 else if (i < -32_768) -32_768 else i).toShort()
    }

    private companion object {
        val NEUTRAL = SoundFxSettings()
        const val FROM_PCM = 1f / 32_768f
        const val BLOCK = 64
        /** How far the mid swings towards one ear at full intensity (1 would silence the other). */
        const val MAX_PAN = 0.82
        /** The largest interaural delay, as for a head about 18 cm across. */
        const val ITD_MAX_SECONDS = 0.00062
    }
}
