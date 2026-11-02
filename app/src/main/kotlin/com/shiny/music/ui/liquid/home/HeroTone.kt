package com.shiny.music.ui.liquid.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

internal val NeutralBand = Color(0xFF242427)
private val NeutralField = Color(0xFF2C2C30)
private val PaleNeutral = Color(0xFFE9E9EE)

private const val BandLuminance = 0.08f
private const val BandMute = 0.2f
private const val FieldLuminance = 0.11f
private const val FieldMute = 0.3f

internal data class HeroTone(val band: Color, val field: Color, val paleField: Color)

internal val NeutralHeroTone = HeroTone(NeutralBand, NeutralField, PaleNeutral)

private fun saturation(color: Color): Float {
    val high = maxOf(color.red, color.green, color.blue)
    val low = minOf(color.red, color.green, color.blue)
    val range = 1f - kotlin.math.abs(high + low - 1f)
    return if (range <= 0f) 0f else (high - low) / range
}

private fun heldTo(color: Color, luminance: Float): Color {
    var tone = color
    var guard = 0
    while (tone.luminance() > luminance && guard < 20) {
        tone = lerp(tone, Color.Black, 0.1f)
        guard++
    }
    return tone
}

internal fun heroTone(deep: Color, vivid: Color): HeroTone {
    if (saturation(deep) < 0.2f && saturation(vivid) < 0.2f) return NeutralHeroTone
    val source = if (saturation(deep) >= 0.2f && deep.luminance() >= 0.01f) deep else vivid
    val band = heldTo(source, BandLuminance)
    if (band.luminance() < 0.012f) return NeutralHeroTone
    return HeroTone(
        band = lerp(band, NeutralBand, BandMute),
        field = lerp(heldTo(lerp(source, vivid, 0.4f), FieldLuminance), NeutralField, FieldMute),
        paleField = lerp(vivid, Color.White, 0.68f),
    )
}
