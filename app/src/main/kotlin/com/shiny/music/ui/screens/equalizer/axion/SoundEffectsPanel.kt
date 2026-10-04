package com.shiny.music.ui.screens.equalizer.axion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shiny.music.eq.audio.BiquadFilter
import com.shiny.music.eq.data.FilterType
import com.shiny.music.eq.fx.EffectPreset
import com.shiny.music.eq.fx.ReverbPreset
import com.shiny.music.eq.fx.SoundFxEngine
import com.shiny.music.eq.fx.SoundFxSettings
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidSegmentedControl
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.settings.SettingsGutter
import com.shiny.music.ui.liquid.settings.SettingsSection
import com.shiny.music.ui.liquid.settings.SettingsSliderRow
import com.shiny.music.ui.liquid.settings.SettingsToggleRow
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** Any hand edit turns the effect preset into Custom. */
private fun edit(transform: (SoundFxSettings) -> SoundFxSettings) =
    SoundFxEngine.update { transform(it).copy(preset = EffectPreset.Custom) }

// ---- Header: A/B ---------------------------------------------------------------------------

/** A for the untouched song, B for everything this screen does to it. Always B again on leaving. */
@Composable
fun AbSwitch(modifier: Modifier = Modifier) {
    val original by SoundFxEngine.original.collectAsState()
    DisposableEffect(Unit) { onDispose { SoundFxEngine.setOriginal(false) } }
    Column(modifier) {
        LiquidSegmentedControl(
            items = listOf("A · Original", "B · Processed"),
            selectedIndex = if (original) 0 else 1,
            onSelect = { SoundFxEngine.setOriginal(it == 0) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (original) {
            // Otherwise A looks like every effect has stopped working.
            Text(
                text = "A plays the song untouched: no effects, EQ or speed change. Tap B, or change any effect, to hear them again.",
                style = LiquidTypography.footnote,
                color = Liquid.colors.accent,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

// ---- Effect sections ----------------------------------------------------------------------

@Composable
fun SoundEffectsSections(inListenTogether: Boolean) {
    val fx by SoundFxEngine.settings.collectAsState()

    SettingsSection(
        title = "Effects",
        footer = "A starting point. Every effect below can be changed and combined; editing one makes it Custom.",
    ) {
        ChipRow(
            items = EffectPreset.entries.filter { it != EffectPreset.Custom || fx.preset == EffectPreset.Custom },
            label = { it.label },
            selected = { it == fx.preset },
            onClick = { preset -> if (preset != EffectPreset.Custom) SoundFxEngine.update { preset.settings(it) } },
        )
    }

    SpeedSection(fx, inListenTogether)
    EightDSection(fx)
    ReverbSection(fx)
    ToneSection(fx)
}

private enum class SpeedMode(val label: String, val speeds: List<Float>, val default: Float) {
    Normal("Normal", emptyList(), 1f),
    Slowed("Slowed", EffectPreset.SLOWED_SPEEDS, 0.85f),
    UltraSlowed("Ultra Slowed", EffectPreset.ULTRA_SLOWED_SPEEDS, 0.60f),
    Nightcore("Nightcore", EffectPreset.NIGHTCORE_SPEEDS, 1.20f);

    companion object {
        fun of(speed: Float) = when {
            abs(speed - 1f) < 0.005f -> Normal
            speed < 0.675f -> UltraSlowed
            speed < 1f -> Slowed
            else -> Nightcore
        }
    }
}

@Composable
private fun SpeedSection(fx: SoundFxSettings, inListenTogether: Boolean) {
    val mode = SpeedMode.of(fx.speed)
    SettingsSection(
        title = "Speed & Pitch",
        footer = if (inListenTogether) {
            "In a Listen Together session everyone plays at the host's speed."
        } else if (fx.preservePitch) {
            "Preserve Pitch keeps voices at their own pitch while the tempo changes; Pitch then moves it on its own."
        } else {
            "Pitch follows speed, like a record: slower is deeper, faster is higher (the Nightcore sound)."
        },
    ) {
        if (!inListenTogether) {
            ChipRow(
                items = SpeedMode.entries,
                label = { it.label },
                selected = { it == mode },
                onClick = { m -> edit { it.copy(speed = m.default) } },
            )
            if (mode.speeds.isNotEmpty()) {
                ChipRow(
                    items = mode.speeds,
                    label = { "%.2f×".format(it) },
                    selected = { abs(it - fx.speed) < 0.004f },
                    onClick = { v -> edit { it.copy(speed = v) } },
                    small = true,
                )
            }
            SettingsSliderRow(
                title = "Speed",
                value = fx.speed.coerceIn(0.45f, 1.5f),
                onValueChange = { v -> edit { it.copy(speed = (v * 100).roundToInt() / 100f) } },
                valueRange = 0.45f..1.5f,
                valueLabel = "%.2f×".format(fx.speed),
            )
        }
        SettingsToggleRow(
            title = "Preserve Pitch",
            subtitle = "Change the tempo without changing the key",
            checked = fx.preservePitch,
            onCheckedChange = { on -> edit { it.copy(preservePitch = on) } },
            enabled = !inListenTogether,
        )
        if (fx.preservePitch) {
            SettingsSliderRow(
                title = "Pitch",
                value = fx.pitchSemitones,
                onValueChange = { v -> edit { it.copy(pitchSemitones = v.roundToInt().toFloat()) } },
                valueRange = -12f..12f,
                steps = 23,
                valueLabel = semitones(fx.pitchSemitones),
                divider = false,
            )
        } else if (fx.speed != 1f) {
            SettingsSliderRow(
                title = "Pitch",
                value = 12f * log2(fx.speed),
                onValueChange = {},
                valueRange = -12f..12f,
                valueLabel = semitones(12f * log2(fx.speed)) + " with speed",
                divider = false,
            )
        }
    }
}

private fun semitones(st: Float): String {
    val rounded = (st * 10).roundToInt() / 10f
    return when {
        abs(rounded) < 0.05f -> "0 st"
        rounded > 0 -> "+%s st".format(trim(rounded))
        else -> "−%s st".format(trim(-rounded))
    }
}

private fun trim(v: Float) = if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else "%.1f".format(v)

@Composable
private fun EightDSection(fx: SoundFxSettings) {
    SettingsSection(
        title = "8D Audio",
        footer = "Moves the song in a slow circle around your head, with the cues ears use for direction. " +
            "Made for headphones; on a speaker it only drifts left and right.",
    ) {
        SettingsToggleRow(
            title = if (fx.eightD) "8D On" else "8D Off",
            subtitle = "Best with headphones",
            checked = fx.eightD,
            onCheckedChange = { on -> edit { it.copy(eightD = on) } },
        )
        if (fx.eightD) {
            SettingsSliderRow(
                title = "Rotation Speed",
                value = fx.rotationHz,
                onValueChange = { v -> edit { it.copy(rotationHz = v) } },
                valueRange = 0.03f..0.5f,
                valueLabel = "one turn / %.0f s".format(1f / fx.rotationHz),
            )
            SettingsSliderRow(
                title = "8D Intensity",
                value = fx.eightDIntensity,
                onValueChange = { v -> edit { it.copy(eightDIntensity = v) } },
                valueRange = 0f..1f,
                valueLabel = percent(fx.eightDIntensity),
            )
            SettingsSliderRow(
                title = "Depth",
                subtitle = "Further away as it passes behind you",
                value = fx.depth,
                onValueChange = { v -> edit { it.copy(depth = v) } },
                valueRange = 0f..1f,
                valueLabel = percent(fx.depth),
            )
        }
        SettingsSliderRow(
            title = "Stereo Width",
            value = fx.stereoWidth,
            onValueChange = { v -> edit { it.copy(stereoWidth = snapTo(v, 1f, 0.03f)) } },
            valueRange = 0f..2f,
            valueLabel = if (fx.stereoWidth == 1f) "As recorded" else percent(fx.stereoWidth),
            divider = false,
        )
    }
}

@Composable
private fun ReverbSection(fx: SoundFxSettings) {
    SettingsSection(
        title = "Reverb",
        footer = "The room is fed without its deep bass, so the tail stays clear instead of muddy.",
    ) {
        SettingsToggleRow(
            title = "Reverb",
            checked = fx.reverb,
            onCheckedChange = { on -> edit { it.copy(reverb = on) } },
        )
        if (fx.reverb) {
            ChipRow(
                items = ReverbPreset.entries,
                label = { it.label },
                selected = { it == fx.reverbPreset },
                onClick = { room ->
                    edit { it.copy(reverbPreset = room, reverbDecay = room.decay, reverbPreDelayMs = room.preDelayMs) }
                },
                small = true,
            )
            SettingsSliderRow(
                title = "Reverb Amount",
                value = fx.reverbAmount,
                onValueChange = { v -> edit { it.copy(reverbAmount = v) } },
                valueRange = 0f..1f,
                valueLabel = percent(fx.reverbAmount),
            )
            SettingsSliderRow(
                title = "Decay",
                value = fx.reverbDecay,
                onValueChange = { v -> edit { it.copy(reverbDecay = v) } },
                valueRange = 0f..1f,
                valueLabel = percent(fx.reverbDecay),
            )
            SettingsSliderRow(
                title = "Pre-delay",
                value = fx.reverbPreDelayMs,
                onValueChange = { v -> edit { it.copy(reverbPreDelayMs = v.roundToInt().toFloat()) } },
                valueRange = 0f..150f,
                valueLabel = "${fx.reverbPreDelayMs.roundToInt()} ms",
            )
            SettingsSliderRow(
                title = "Wet/Dry Mix",
                value = fx.reverbMix,
                onValueChange = { v -> edit { it.copy(reverbMix = v) } },
                valueRange = 0f..1f,
                valueLabel = "${percent(fx.reverbMix)} wet",
                divider = false,
            )
        }
    }
}

@Composable
private fun ToneSection(fx: SoundFxSettings) {
    SettingsSection(
        title = "Tone",
        footer = "After the effects, and before the limiter that keeps every combination from clipping.",
    ) {
        SettingsSliderRow(
            title = "Bass",
            value = fx.bassDb,
            onValueChange = { v -> edit { it.copy(bassDb = snapTo((v * 2).roundToInt() / 2f, 0f, 0.4f)) } },
            valueRange = -6f..12f,
            valueLabel = db(fx.bassDb),
        )
        SettingsSliderRow(
            title = "Clarity",
            value = fx.clarityDb,
            onValueChange = { v -> edit { it.copy(clarityDb = snapTo((v * 2).roundToInt() / 2f, 0f, 0.4f)) } },
            valueRange = -6f..6f,
            valueLabel = db(fx.clarityDb),
            divider = false,
        )
    }
}

private fun percent(v: Float) = "${(v * 100).roundToInt()}%"

private fun db(v: Float) = when {
    abs(v) < 0.05f -> "0 dB"
    v > 0 -> "+%.1f dB".format(v)
    else -> "−%.1f dB".format(-v)
}

/** Lets a slider land exactly on its neutral value. */
private fun snapTo(v: Float, neutral: Float, within: Float) = if (abs(v - neutral) < within) neutral else v

@Composable
private fun <T> ChipRow(
    items: List<T>,
    label: (T) -> String,
    selected: (T) -> Boolean,
    onClick: (T) -> Unit,
    small: Boolean = false,
) {
    val colors = Liquid.colors
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = SettingsGutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val on = selected(item)
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (on) colors.accent else colors.secondaryFill)
                    .clickable { onClick(item) }
                    .padding(PaddingValues(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 7.dp else 9.dp)),
            ) {
                Text(
                    text = label(item),
                    style = (if (small) LiquidTypography.footnote else LiquidTypography.subheadline)
                        .copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) Color.White else colors.label,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---- Equaliser curve ---------------------------------------------------------------------

private val EQ_FREQUENCIES = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

/**
 * The equaliser's frequency response — the same biquads the audio goes through, measured
 * at 120 points from 20 Hz to 20 kHz — with the preamp included.
 */
@Composable
fun EqResponseCurve(bandGains: FloatArray, preampDb: Float, enabled: Boolean, modifier: Modifier = Modifier) {
    val colors = Liquid.colors
    val response = remember(bandGains.toList(), preampDb) {
        val filters = bandGains.mapIndexed { i, g ->
            BiquadFilter(sampleRate = 48_000, frequency = EQ_FREQUENCIES[i], gain = g / 50.0, q = 1.41, filterType = FilterType.PK)
        }
        FloatArray(CURVE_POINTS) { p ->
            val f = 20.0 * 1000.0.pow(p / (CURVE_POINTS - 1.0))
            (filters.sumOf { it.magnitudeDb(f) } + preampDb).toFloat()
        }
    }
    val line = if (enabled) colors.accent else colors.tertiaryLabel
    val grid = colors.separator
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.secondaryGroupedBackground),
        ) {
            val rangeDb = 15f
            fun yFor(db: Float) = size.height / 2 - db.coerceIn(-rangeDb, rangeDb) / rangeDb * (size.height / 2 - 8.dp.toPx())
            for (db in listOf(-12f, -6f, 0f, 6f, 12f)) {
                drawLine(grid, Offset(0f, yFor(db)), Offset(size.width, yFor(db)), strokeWidth = if (db == 0f) 1.5f else 1f)
            }
            val path = Path()
            val fill = Path()
            response.forEachIndexed { i, db ->
                val x = i * size.width / (CURVE_POINTS - 1)
                val y = yFor(db)
                if (i == 0) {
                    path.moveTo(x, y); fill.moveTo(x, size.height / 2); fill.lineTo(x, y)
                } else {
                    path.lineTo(x, y); fill.lineTo(x, y)
                }
            }
            fill.lineTo(size.width, size.height / 2)
            fill.close()
            drawPath(fill, line.copy(alpha = 0.16f))
            drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (label in listOf("20", "100", "1k", "10k", "20k")) {
                Text(label, style = LiquidTypography.caption2, color = colors.tertiaryLabel, textAlign = TextAlign.Center)
            }
        }
    }
}

private const val CURVE_POINTS = 120
