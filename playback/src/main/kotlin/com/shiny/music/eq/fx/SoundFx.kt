package com.shiny.music.eq.fx

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

/** A reverb space: how long it rings, how dark the tail is, and how far the first reflection is. */
enum class ReverbPreset(
    val label: String,
    val decay: Float,
    /** Freeverb damping, 0 (bright) to 0.4 (dark). */
    val damping: Float,
    val preDelayMs: Float,
    /** Where the wet signal is rolled off, so a long tail never turns to hiss. */
    val toneHz: Float,
) {
    SmallRoom("Small Room", 0.30f, 0.30f, 4f, 7_500f),
    Room("Room", 0.50f, 0.28f, 10f, 7_000f),
    Studio("Studio", 0.42f, 0.18f, 12f, 10_000f),
    Hall("Hall", 0.70f, 0.25f, 24f, 7_000f),
    LargeHall("Large Hall", 0.80f, 0.25f, 38f, 6_500f),
    Cathedral("Cathedral", 0.90f, 0.20f, 55f, 6_000f),
    Dreamy("Dreamy", 0.86f, 0.36f, 32f, 4_500f),
    Huge("Huge", 0.95f, 0.24f, 80f, 6_000f),
}

/**
 * Every sound effect setting. Speed and pitch are applied by the player (its Sonic
 * time-stretcher); everything else by [SoundFxAudioProcessor]. All of it can be combined.
 */
data class SoundFxSettings(
    val preset: EffectPreset = EffectPreset.Normal,

    // Speed and pitch.
    val speed: Float = 1f,
    /** On: speed changes keep the pitch, and [pitchSemitones] sets it. Off: pitch follows speed, like a record. */
    val preservePitch: Boolean = false,
    val pitchSemitones: Float = 0f,

    // 8D.
    val eightD: Boolean = false,
    /** Turns per second around the head. */
    val rotationHz: Float = 0.12f,
    val eightDIntensity: Float = 0.75f,
    /** 1 = as recorded; below narrows, above widens. */
    val stereoWidth: Float = 1f,
    /** How much further away the sound seems as it passes behind you. */
    val depth: Float = 0.4f,

    // Reverb.
    val reverb: Boolean = false,
    val reverbPreset: ReverbPreset = ReverbPreset.Hall,
    /** How much of the song is sent into the room. */
    val reverbAmount: Float = 0.4f,
    val reverbDecay: Float = ReverbPreset.Hall.decay,
    val reverbPreDelayMs: Float = ReverbPreset.Hall.preDelayMs,
    /** Wet against dry, equal-power. 0.5 is an even blend. */
    val reverbMix: Float = 0.35f,

    // Tone, after everything else.
    val bassDb: Float = 0f,
    /** A high shelf: the brightness Nightcore gets, or a darker Dreamy. */
    val clarityDb: Float = 0f,
) {
    /** The pitch the player should use for this speed. */
    val playerPitch: Float
        get() = if (preservePitch) 2f.pow(pitchSemitones / 12f) else speed

    /** Whether the processor has any work to do (speed alone is the player's). */
    val needsDsp: Boolean
        get() = eightD || (reverb && reverbAmount > 0f) || stereoWidth != 1f || bassDb != 0f || clarityDb != 0f

    companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 2.0f
    }
}

/**
 * One-tap starting points. Each sets every effect (so presets never leave a stray setting
 * behind); after that every value can be changed, which makes the preset [Custom].
 */
enum class EffectPreset(val label: String) {
    Normal("Normal"),
    EightD("8D"),
    Slowed("Slowed"),
    UltraSlowed("Ultra Slowed"),
    SlowedReverb("Slowed + Reverb"),
    UltraSlowedReverb("Ultra Slowed + Reverb"),
    Nightcore("Nightcore"),
    EightDReverb("8D + Reverb"),
    EightDSlowed("8D + Slowed"),
    EightDSlowedReverb("8D + Slowed + Reverb"),
    Dreamy("Dreamy"),
    DeepBass("Deep Bass"),
    Custom("Custom"),
    ;

    /** This preset's settings, keeping the listener's Preserve Pitch choice. */
    fun settings(current: SoundFxSettings): SoundFxSettings {
        val base = SoundFxSettings(preset = this, preservePitch = current.preservePitch)
        fun SoundFxSettings.eightD() = copy(eightD = true, rotationHz = 0.12f, eightDIntensity = 0.75f, stereoWidth = 1.1f, depth = 0.4f)
        fun SoundFxSettings.room(preset: ReverbPreset, amount: Float, mix: Float, decay: Float = preset.decay) = copy(
            reverb = true,
            reverbPreset = preset,
            reverbAmount = amount,
            reverbMix = mix,
            reverbDecay = decay,
            reverbPreDelayMs = preset.preDelayMs,
        )
        return when (this) {
            Normal, Custom -> base
            EightD -> base.eightD()
            Slowed -> base.copy(speed = 0.85f)
            UltraSlowed -> base.copy(speed = 0.60f)
            SlowedReverb -> base.copy(speed = 0.80f, stereoWidth = 1.2f).room(ReverbPreset.Hall, amount = 0.45f, mix = 0.40f, decay = 0.74f)
            UltraSlowedReverb -> base.copy(speed = 0.55f, stereoWidth = 1.3f).room(ReverbPreset.LargeHall, amount = 0.55f, mix = 0.48f, decay = 0.84f)
            // Pitch follows speed: 1.2× is +3.2 semitones, the classic Nightcore lift.
            Nightcore -> base.copy(speed = 1.20f, preservePitch = false, clarityDb = 2.5f)
            EightDReverb -> base.eightD().room(ReverbPreset.Hall, amount = 0.35f, mix = 0.30f)
            EightDSlowed -> base.eightD().copy(speed = 0.85f)
            EightDSlowedReverb -> base.eightD().copy(speed = 0.80f).room(ReverbPreset.Hall, amount = 0.42f, mix = 0.38f, decay = 0.74f)
            Dreamy -> base.copy(speed = 0.90f, stereoWidth = 1.35f, clarityDb = -2f)
                .room(ReverbPreset.Dreamy, amount = 0.50f, mix = 0.45f)
            DeepBass -> base.copy(bassDb = 6f)
        }
    }

    companion object {
        /** Speeds offered as chips for each kind of speed change. */
        val SLOWED_SPEEDS = listOf(0.95f, 0.90f, 0.85f, 0.80f, 0.75f, 0.70f)
        val ULTRA_SLOWED_SPEEDS = listOf(0.65f, 0.60f, 0.55f, 0.50f, 0.45f)
        val NIGHTCORE_SPEEDS = listOf(1.10f, 1.15f, 1.20f, 1.25f, 1.30f)
    }
}

/**
 * The one place the sound effects live: the Equaliser's master switch, the settings every
 * [SoundFxAudioProcessor] reads (there is one per player, and crossfades create a second
 * player) and the A/B switch.
 */
object SoundFxEngine {
    private val _enabled = MutableStateFlow(false)

    /**
     * The Equaliser's master switch: effects, speed and the EQ bands all follow it. Never
     * saved, so Shiny always starts with it off, and [onAppOpened] turns it off again when
     * Shiny is opened later with nothing playing. The chosen values are kept for next time.
     */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _settings = MutableStateFlow(SoundFxSettings())
    val settings: StateFlow<SoundFxSettings> = _settings.asStateFlow()

    /** When audio last went through a processor, so "is anything playing" needs no player. */
    @Volatile
    var lastAudioNanos = 0L

    private val _original = MutableStateFlow(false)

    /** A/B: true plays the untouched song (A), false the processed one (B). Not saved. */
    val original: StateFlow<Boolean> = _original.asStateFlow()

    /**
     * How far the equaliser lowered its output so its boosts can't clip (dB). Half of it
     * is given back after the effects, under the limiter.
     */
    @Volatile
    var eqHeadroomDb: Float = 0f

    private var prefs: SharedPreferences? = null

    /** Loads the saved settings once; later calls do nothing. */
    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _settings.value = load(p)
    }

    fun update(transform: (SoundFxSettings) -> SoundFxSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        // Changing an effect is asking to hear it: the Equaliser being off, or A (the
        // untouched song), would hide the change.
        _enabled.value = true
        _original.value = false
        prefs?.let { save(it, next) }
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        if (!on) _original.value = false
    }

    /**
     * Shiny opened: if nothing has played for [quietMs], the Equaliser goes back to off. A
     * fresh launch uses a few seconds (anything not playing right now); coming back to a
     * screen that stayed in memory uses minutes, so a quick switch to another app and back
     * while paused doesn't lose it.
     */
    fun onAppOpened(quietMs: Long = 5_000L) {
        if (System.nanoTime() - lastAudioNanos > quietMs * 1_000_000L) setEnabled(false)
    }

    fun setOriginal(original: Boolean) {
        _original.value = original
    }

    private const val PREFS = "shiny_sound_fx"

    private fun load(p: SharedPreferences): SoundFxSettings {
        val d = SoundFxSettings()
        return SoundFxSettings(
            preset = runCatching { EffectPreset.valueOf(p.getString("preset", null) ?: "") }.getOrDefault(d.preset),
            speed = p.getFloat("speed", d.speed).coerceIn(SoundFxSettings.MIN_SPEED, SoundFxSettings.MAX_SPEED),
            preservePitch = p.getBoolean("preservePitch", d.preservePitch),
            pitchSemitones = p.getFloat("pitch", d.pitchSemitones),
            eightD = p.getBoolean("eightD", d.eightD),
            rotationHz = p.getFloat("rotationHz", d.rotationHz),
            eightDIntensity = p.getFloat("eightDIntensity", d.eightDIntensity),
            stereoWidth = p.getFloat("stereoWidth", d.stereoWidth),
            depth = p.getFloat("depth", d.depth),
            reverb = p.getBoolean("reverb", d.reverb),
            reverbPreset = runCatching { ReverbPreset.valueOf(p.getString("reverbPreset", null) ?: "") }.getOrDefault(d.reverbPreset),
            reverbAmount = p.getFloat("reverbAmount", d.reverbAmount),
            reverbDecay = p.getFloat("reverbDecay", d.reverbDecay),
            reverbPreDelayMs = p.getFloat("reverbPreDelay", d.reverbPreDelayMs),
            reverbMix = p.getFloat("reverbMix", d.reverbMix),
            bassDb = p.getFloat("bassDb", d.bassDb),
            clarityDb = p.getFloat("clarityDb", d.clarityDb),
        )
    }

    private fun save(p: SharedPreferences, s: SoundFxSettings) {
        p.edit()
            .putString("preset", s.preset.name)
            .putFloat("speed", s.speed)
            .putBoolean("preservePitch", s.preservePitch)
            .putFloat("pitch", s.pitchSemitones)
            .putBoolean("eightD", s.eightD)
            .putFloat("rotationHz", s.rotationHz)
            .putFloat("eightDIntensity", s.eightDIntensity)
            .putFloat("stereoWidth", s.stereoWidth)
            .putFloat("depth", s.depth)
            .putBoolean("reverb", s.reverb)
            .putString("reverbPreset", s.reverbPreset.name)
            .putFloat("reverbAmount", s.reverbAmount)
            .putFloat("reverbDecay", s.reverbDecay)
            .putFloat("reverbPreDelay", s.reverbPreDelayMs)
            .putFloat("reverbMix", s.reverbMix)
            .putFloat("bassDb", s.bassDb)
            .putFloat("clarityDb", s.clarityDb)
            .apply()
    }
}
