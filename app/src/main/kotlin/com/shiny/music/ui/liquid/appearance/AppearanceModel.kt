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

    /**
     * An album's portrait artwork, edge to edge at the head of Now Playing, in its own shape:
     * never cropped, never stretched, never enlarged. Its foot goes out of focus into a field
     * of its own colours, which carries on down the screen under the title and the controls.
     * An album without portrait artwork keeps the [Card]: a square sleeve is not hung this way.
     */
    Poster,
}

/**
 * Where a poster cover dissolves, measured down the player in whatever unit the cover's
 * side was given in. It is sharp to [start] and gone by [end]; what shows instead is its
 * own blurred copy lying exactly beneath it, so the picture reads as going out of focus
 * rather than as a card ending.
 */
data class PosterMelt(val start: Float, val end: Float)

/** How much of the cover's height the dissolve takes when the screen has room for all of it. */
const val PosterMeltFraction = 0.26f

/**
 * The dissolve for a cover [coverSide] square, hung from the top, on a player whose title
 * begins at [titleTop].
 *
 * On a tall screen the cover ends above the title and the dissolve finishes on the cover's
 * own bottom edge. On a short one the title sits over the cover, so the dissolve has to be
 * over [gap] before the type begins. It never takes more than the cover's lower half, and
 * what is left above it is never less than a third of the picture.
 */
fun posterMelt(coverSide: Float, titleTop: Float, gap: Float): PosterMelt {
    // [coverSide] is the picture's height: the portrait artwork's, about a third more than its width.
    val end = minOf(coverSide, titleTop - gap).coerceAtLeast(coverSide * 0.5f)
    val start = (end - coverSide * PosterMeltFraction).coerceAtLeast(coverSide * 0.34f)
    return PosterMelt(start, end)
}

/**
 * How tall a poster cover hangs on a player [width] wide and [height] tall: its own shape
 * ([aspect], width over height; 1 for a sleeve, about 3:4 for portrait motion artwork), but
 * never past the foot of the player.
 */
fun posterCoverHeight(width: Float, height: Float, aspect: Float): Float =
    (width / aspect.coerceIn(0.5f, 1f)).coerceAtMost(height)

/** How much of the cover has dissolved at [y]: nothing above the dissolve, all of it from its end. */
fun posterMeltAt(y: Float, melt: PosterMelt): Float {
    if (melt.end <= melt.start) return if (y >= melt.end) 1f else 0f
    return smoothStep((y - melt.start) / (melt.end - melt.start))
}

/**
 * How much the poster's ground is darkened where the title begins, for a ground whose bright
 * end has relative luminance [brightness] there.
 *
 * Solved, not fixed. White type needs the ground under it at or below a luminance; a dark
 * sleeve is already there and gets only the floor, while a white one gets as much as it
 * takes. A scrim multiplies the encoded colour, and luminance follows it to the power of the
 * display's gamma, which is why the root is taken. Atmosphere moves both the target and
 * the floor: Immersive lets the most colour through, Off the least.
 */
fun posterTitleScrim(atmosphere: Atmosphere, brightness: Float): Float {
    val target = when (atmosphere) {
        Atmosphere.Immersive -> 0.18f
        Atmosphere.Balanced -> 0.155f
        Atmosphere.Soft -> 0.115f
        Atmosphere.Off -> 0.08f
    }
    val floor = when (atmosphere) {
        Atmosphere.Immersive -> 0.14f
        Atmosphere.Balanced -> 0.22f
        Atmosphere.Soft -> 0.34f
        Atmosphere.Off -> 0.44f
    }
    val needed = if (brightness <= target) 0f else 1f - Math.pow((target / brightness).toDouble(), 1.0 / 2.2).toFloat()
    return maxOf(floor, needed).coerceAtMost(0.9f)
}

/**
 * How vivid the poster's ground is. A blur averages colour towards grey, so the ground is
 * given back what the blur took and a little more: the field under the controls should read
 * as the cover's colours poured out and lit, not as a dim copy of them.
 */
fun posterSaturation(atmosphere: Atmosphere): Float = when (atmosphere) {
    Atmosphere.Off -> 1f
    Atmosphere.Soft -> 0.94f
    Atmosphere.Balanced -> 1.24f
    Atmosphere.Immersive -> 1.44f
}

/**
 * How much more colour the poster's ground is given where it is shaded, per unit of shade.
 * A colour that is only darkened turns to mud; this is what keeps the foot of the stage a
 * rich dark instead of a grey one. Pale colours take most of it, strong ones almost none.
 */
fun posterDepthRichness(atmosphere: Atmosphere): Float = when (atmosphere) {
    Atmosphere.Off -> 0f
    Atmosphere.Soft -> 0.5f
    Atmosphere.Balanced -> 1.1f
    Atmosphere.Immersive -> 1.5f
}

/** The same ground at the very foot of the player, under the last row of controls. */
fun posterFootScrim(titleScrim: Float): Float = (titleScrim + 0.18f).coerceAtMost(0.92f)

/**
 * The darkening at [y] down a poster's ground: none over the sharp cover, rising from
 * [meltStart] to [titleScrim] where the type begins, and on to [footScrim] at [bottom].
 * Each rise eases at both ends, so no line shows where one hands over to the next.
 */
fun posterScrimAt(
    y: Float,
    meltStart: Float,
    titleTop: Float,
    bottom: Float,
    titleScrim: Float,
    footScrim: Float,
): Float = when {
    y <= meltStart -> 0f
    y < titleTop -> titleScrim * smoothStep((y - meltStart) / (titleTop - meltStart))
    bottom <= titleTop -> footScrim
    else -> titleScrim + (footScrim - titleScrim) * smoothStep((y - titleTop) / (bottom - titleTop))
}

private fun smoothStep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The scrim at the head of a poster, under the grabber and the status bar. Light: it only
 * has to hold two small white marks, not a block of type.
 */
fun posterTopScrim(atmosphere: Atmosphere): Float = when (atmosphere) {
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

    /** The backdrop turns slowly and the cover settles when paused. The cover itself is still. The default. */
    Subtle,

    /**
     * The cover itself drifts, ripples and catches the light while music plays. Motion Shiny
     * adds to a still picture, so only for whoever chooses it, and never over an album's own
     * motion artwork.
     */
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
        // Balanced is the defaults: an untouched Shiny reads back as it, cover left as it is.
        Experience.Balanced -> ExperienceMix(Atmosphere.Balanced, ArtworkGlow.Off, ArtworkMotion.Subtle, PageTransitions.Slide)
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
