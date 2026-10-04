package com.shiny.music.ui.liquid.appearance

/*
 * Shiny's appearance, as values.
 *
 * Every enum below is one setting a person can see, and every value of it changes
 * something on screen. The first-listed default of each is today's Shiny, so a user who
 * never opens Appearance — or whose stored value no longer decodes — gets exactly the
 * look they had before any of this existed.
 *
 * Nothing in this file touches Android or Compose, so the mappings between these
 * settings and the preferences behind them can be tested on the JVM.
 */

/** How strongly the playing artwork colours the Now Playing environment. */
enum class Atmosphere {
    /** A plain dark stage — what Shiny shows for a song without artwork. True black on AMOLED. */
    Off,

    /** The artwork's colour, quieted: deeper scrim, softer saturation. */
    Soft,

    /** Shiny's Now Playing as it has always been. The default. */
    Balanced,

    /** The artwork's colour, brought forward: lighter scrim, richer saturation. */
    Immersive,
}

/**
 * How the cover is presented on Now Playing.
 *
 * Only the stage changes. The controls, the scrubber, the gestures, the lyrics and the queue
 * are the same objects in the same places in both, and playback is not in this file's reach
 * at all.
 */
enum class ArtworkPresentation {
    /** The cover as a square on a field of its own colour. Shiny as it ships. */
    Card,

    /** The cover fills Now Playing, cropped to the screen, with the controls set over it. */
    FullScreen,
}

/**
 * How deep the scrim at the foot of a full-screen cover has to be.
 *
 * [balanced] is the scrim Shiny already derives from this artwork's own brightness — 0.26 for
 * a dark cover, 0.42 for a bright one. A full-bleed cover puts the title and the transport
 * directly on the image instead of on a blurred field, so the foot needs more than the field
 * ever did, and a bright sleeve needs more than a dark one at every setting.
 *
 * Atmosphere still moves it: Immersive lets the most of the cover through, Off the least,
 * which is what those words mean everywhere else in Shiny.
 */
fun fullScreenScrim(atmosphere: Atmosphere, balanced: Float): Float {
    val floor = when (atmosphere) {
        Atmosphere.Immersive -> 0.62f
        Atmosphere.Balanced -> 0.72f
        Atmosphere.Soft -> 0.80f
        Atmosphere.Off -> 0.88f
    }
    return (floor + (balanced - 0.26f).coerceAtLeast(0f)).coerceIn(floor, 0.92f)
}

/**
 * The scrim at the head of a full-screen cover, under the grabber and the status bar. Light
 * — it only has to hold two small white marks, not a block of type.
 */
fun fullScreenTopScrim(atmosphere: Atmosphere): Float = when (atmosphere) {
    Atmosphere.Immersive -> 0.22f
    Atmosphere.Balanced -> 0.30f
    Atmosphere.Soft -> 0.36f
    Atmosphere.Off -> 0.42f
}

/** The light the artwork throws on the stage around itself. */
enum class ArtworkGlow {
    Off,
    Subtle,
    Medium,
}

/** The finish of glass surfaces while glass is on. */
enum class GlassFinish {
    /** Clear glass over artwork, frosted over pages: the recipe picked per surface. The default. */
    Adaptive,

    /** Less blur and less tint everywhere: more of what is behind shows through. */
    Clear,

    /** More blur, calmer refraction: what is behind is quieted. */
    Frosted,
}

/** The Material row: a glass finish, or no glass at all. */
enum class SurfaceMaterial {
    Adaptive,
    Clear,
    Frosted,
    Solid,
}

/** How one page replaces another. */
enum class PageTransitions {
    /** The page pushes in from the edge over the last one. The default. */
    Slide,

    /** Pages dissolve into each other. */
    Fade,

    /** No page transition at all. */
    Instant,
}

/**
 * How much the Now Playing artwork moves. One scale over three stored switches — the
 * backdrop drift, the breathe on pause, and the living cover — which only made sense
 * together.
 */
enum class ArtworkMotion {
    /** Nothing moves. The cover is a still frame on a still ground. */
    Off,

    /** The backdrop turns slowly and the cover settles when paused. */
    Subtle,

    /** The cover itself drifts, ripples and catches the light while music plays. The default. */
    Living,
}

/** Where the accent comes from. */
enum class AccentMode {
    Shiny,
    Artwork,
    Mono,
    Custom,
}

/**
 * A named combination of the environment settings. Never stored: it is read back from
 * the settings themselves, so it can never disagree with them, and anything that is not
 * one of the three named mixes is [Custom].
 */
enum class Experience {
    Minimal,
    Balanced,
    Immersive,
    Custom,
}

/** The four settings an [Experience] sets together. */
data class ExperienceMix(
    val atmosphere: Atmosphere,
    val glow: ArtworkGlow,
    val motion: ArtworkMotion,
    val transitions: PageTransitions,
)

/** The mix [this] stands for, or null for [Experience.Custom]. */
val Experience.mix: ExperienceMix?
    get() = when (this) {
        Experience.Minimal -> ExperienceMix(Atmosphere.Soft, ArtworkGlow.Off, ArtworkMotion.Off, PageTransitions.Fade)
        // Balanced is the defaults, which are today's Shiny.
        Experience.Balanced -> ExperienceMix(Atmosphere.Balanced, ArtworkGlow.Off, ArtworkMotion.Living, PageTransitions.Slide)
        Experience.Immersive -> ExperienceMix(Atmosphere.Immersive, ArtworkGlow.Medium, ArtworkMotion.Living, PageTransitions.Slide)
        Experience.Custom -> null
    }

fun experienceOf(mix: ExperienceMix): Experience =
    Experience.entries.firstOrNull { it.mix == mix } ?: Experience.Custom

/** The scale position of the three stored motion switches. */
fun artworkMotionOf(backdropDrift: Boolean, breathe: Boolean, living: Boolean): ArtworkMotion = when {
    living -> ArtworkMotion.Living
    backdropDrift || breathe -> ArtworkMotion.Subtle
    else -> ArtworkMotion.Off
}

/** The three switches a scale position writes: backdrop drift, breathe, living. */
data class MotionSwitches(val backdropDrift: Boolean, val breathe: Boolean, val living: Boolean)

val ArtworkMotion.switches: MotionSwitches
    get() = when (this) {
        ArtworkMotion.Off -> MotionSwitches(backdropDrift = false, breathe = false, living = false)
        ArtworkMotion.Subtle -> MotionSwitches(backdropDrift = true, breathe = true, living = false)
        ArtworkMotion.Living -> MotionSwitches(backdropDrift = true, breathe = true, living = true)
    }

fun surfaceMaterialOf(glassOn: Boolean, finish: GlassFinish): SurfaceMaterial = when {
    !glassOn -> SurfaceMaterial.Solid
    finish == GlassFinish.Clear -> SurfaceMaterial.Clear
    finish == GlassFinish.Frosted -> SurfaceMaterial.Frosted
    else -> SurfaceMaterial.Adaptive
}

/**
 * The finish [this] stores, or null for [SurfaceMaterial.Solid], which only turns glass
 * off and leaves the finish alone for when it is turned back on.
 */
val SurfaceMaterial.finish: GlassFinish?
    get() = when (this) {
        SurfaceMaterial.Adaptive -> GlassFinish.Adaptive
        SurfaceMaterial.Clear -> GlassFinish.Clear
        SurfaceMaterial.Frosted -> GlassFinish.Frosted
        SurfaceMaterial.Solid -> null
    }

fun accentModeOf(followArtwork: Boolean, tint: Int): AccentMode = when {
    followArtwork -> AccentMode.Artwork
    tint == AppearanceColor.ShinyTint -> AccentMode.Shiny
    tint == AppearanceColor.MonochromeTint -> AccentMode.Mono
    else -> AccentMode.Custom
}

/** How much light the glow throws at full strength. */
val ArtworkGlow.strength: Float
    get() = when (this) {
        ArtworkGlow.Off -> 0f
        ArtworkGlow.Subtle -> 0.34f
        ArtworkGlow.Medium -> 0.58f
    }

/**
 * The glow's brightness while the cover settles back on pause. [restScale] runs from
 * [pausedScale] (paused) to 1 (playing); the glow dims to 60% with it, so a paused stage
 * is calmer without the glow ever blinking off.
 */
fun glowPauseFactor(restScale: Float, pausedScale: Float): Float {
    val t = ((restScale - pausedScale) / (1f - pausedScale)).coerceIn(0f, 1f)
    return 0.6f + 0.4f * t
}

/**
 * The Now Playing scrim for [atmosphere], from the scrim [balanced] Shiny has always used
 * for this artwork. Soft deepens it; Immersive thins it, but never below the point where
 * white type stops reading.
 */
fun atmosphereScrim(atmosphere: Atmosphere, balanced: Float): Float = when (atmosphere) {
    Atmosphere.Soft -> balanced + 0.16f
    Atmosphere.Immersive -> (balanced * 0.78f).coerceAtLeast(0.2f)
    Atmosphere.Off, Atmosphere.Balanced -> balanced
}

/** Colour saturation applied to the artwork field, or null to draw it untouched. */
fun atmosphereSaturation(atmosphere: Atmosphere): Float? = when (atmosphere) {
    Atmosphere.Soft -> 0.72f
    Atmosphere.Immersive -> 1.28f
    Atmosphere.Off, Atmosphere.Balanced -> null
}

/**
 * Narrowest interface Shiny allows an enlarged size to produce, in dp. Below this the
 * tab bar and the transport stop fitting side by side.
 */
const val MinScaledWidthDp = 320

/**
 * Shortest long edge Shiny allows an enlarged size to produce, in dp. Now Playing stacks
 * the cover, the title and a ~270dp controls block; below this height they overlap.
 */
const val MinScaledLongSideDp = 720

/**
 * The density factor actually applied for a requested [scale] on a screen whose
 * smallest width is [smallestWidthDp] and whose long edge is [longSideDp], both at the
 * system density. Shrinking is never limited; enlarging stops where the screen would
 * become narrower than [MinScaledWidthDp] or shorter than [MinScaledLongSideDp], and a
 * screen already below either is never enlarged.
 */
fun effectiveDensityScale(scale: Float, smallestWidthDp: Int, longSideDp: Int): Float {
    if (scale <= 1f || smallestWidthDp <= 0 || longSideDp <= 0) return scale
    val byWidth = smallestWidthDp.toFloat() / MinScaledWidthDp
    val byHeight = longSideDp.toFloat() / MinScaledLongSideDp
    return minOf(scale, minOf(byWidth, byHeight).coerceAtLeast(1f))
}
