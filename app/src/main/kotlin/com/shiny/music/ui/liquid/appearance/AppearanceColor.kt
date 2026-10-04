package com.shiny.music.ui.liquid.appearance

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Colour arithmetic for the appearance system, on plain ARGB ints.
 *
 * Kept free of Compose and Android types on purpose: everything here decides whether a
 * colour taken from someone's album cover is fit to put on screen, and that is worth
 * being able to test on the JVM.
 */
object AppearanceColor {

    /** The stored tint that means "Shiny Rose, resolved per theme" (`DefaultThemeColor`). */
    val ShinyTint: Int = 0xFFED5564.toInt()

    /**
     * The stored tint that means "Monochrome, resolved per theme".
     *
     * A sentinel, like [ShinyTint]: no swatch uses it, and [MonochromeDark] /
     * [MonochromeLight] are what actually reaches the screen.
     */
    val MonochromeTint: Int = 0xFF8A8A8E.toInt()

    /**
     * Graphite for dark grounds. Light enough to read as text on black (6:1), dark enough
     * that the white glyphs Shiny draws on accent fills still clear 3:1.
     */
    val MonochromeDark: Int = 0xFF8E8E93.toInt()

    /** Graphite for light grounds: 6:1 against white either way round. */
    val MonochromeLight: Int = 0xFF636366.toInt()

    /**
     * The luminance band an accent taken from artwork is pulled into.
     *
     * The accent is used two ways: as text and ticks on the page ground, and as a fill
     * under white glyphs. Above [AccentMaxLuminance] white stops reading on it and it
     * vanishes into a white page; below [AccentMinLuminance] it vanishes into a black one.
     */
    const val AccentMinLuminance = 0.12
    const val AccentMaxLuminance = 0.30

    /** Saturation ceiling for an artwork accent, so a fluorescent cover does not turn the UI neon. */
    private const val AccentMaxSaturation = 0.90

    /** Below this saturation a swatch is treated as grey. */
    private const val GreyThreshold = 0.12

    /** WCAG relative luminance of [argb]. */
    fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** WCAG contrast ratio between two opaque colours, 1..21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /**
     * [argb] made fit to be Shiny's accent: saturation capped, then lightness moved — hue
     * untouched — only as far as it takes to land inside the luminance band. A colour
     * already inside the band comes back with only its saturation capped.
     */
    fun sanitizeAccent(argb: Int): Int {
        val (h, s0, l0) = toHsl(argb)
        val s = min(s0, AccentMaxSaturation)
        val capped = fromHsl(h, s, l0)
        val lum = luminance(capped)
        return when {
            lum > AccentMaxLuminance -> fromHsl(h, s, searchLightness(h, s, 0.0, l0, AccentMaxLuminance, brighter = false))
            lum < AccentMinLuminance -> fromHsl(h, s, searchLightness(h, s, l0, 1.0, AccentMinLuminance, brighter = true))
            else -> capped
        }
    }

    /**
     * The colour of the light an artwork throws around itself: its vivid swatch held to a
     * mid lightness so the halo reads as light rather than paint, and grey covers answered
     * with a soft neutral instead of a muddy one.
     */
    fun glowTone(argb: Int): Int {
        val (h, s, l) = toHsl(argb)
        if (s < GreyThreshold) return fromHsl(0.0, 0.0, 0.82)
        return fromHsl(h, min(s, 0.80), l.coerceIn(0.50, 0.66))
    }

    /**
     * Lightness in [lo]..[hi] at which the colour crosses [target] luminance. Luminance
     * rises monotonically with HSL lightness, so bisection is exact to the precision asked.
     * [brighter] picks the side of the crossing that is inside the band.
     */
    private fun searchLightness(h: Double, s: Double, lo0: Double, hi0: Double, target: Double, brighter: Boolean): Double {
        var lo = lo0
        var hi = hi0
        repeat(24) {
            val mid = (lo + hi) / 2
            if (luminance(fromHsl(h, s, mid)) > target) hi = mid else lo = mid
        }
        return if (brighter) hi else lo
    }

    /** Hue in degrees, saturation and lightness in 0..1. */
    fun toHsl(argb: Int): Triple<Double, Double, Double> {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2
        val d = mx - mn
        if (d == 0.0) return Triple(0.0, 0.0, l)
        val s = d / (1 - abs(2 * l - 1))
        val h = when (mx) {
            r -> 60 * (((g - b) / d).mod(6.0))
            g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }
        return Triple(h, s.coerceIn(0.0, 1.0), l)
    }

    /** An opaque colour from hue (degrees), saturation and lightness. */
    fun fromHsl(h: Double, s: Double, l: Double): Int {
        val c = (1 - abs(2 * l - 1)) * s
        val hp = (h.mod(360.0)) / 60
        val x = c * (1 - abs(hp.mod(2.0) - 1))
        val (r1, g1, b1) = when {
            hp < 1 -> Triple(c, x, 0.0)
            hp < 2 -> Triple(x, c, 0.0)
            hp < 3 -> Triple(0.0, c, x)
            hp < 4 -> Triple(0.0, x, c)
            hp < 5 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        val m = l - c / 2
        fun byte(v: Double) = ((v + m) * 255.0 + 0.5).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (byte(r1) shl 16) or (byte(g1) shl 8) or byte(b1)
    }
}
