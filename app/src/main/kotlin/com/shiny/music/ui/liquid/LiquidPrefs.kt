package com.shiny.music.ui.liquid

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Preferences that belong to the Liquid design (Settings → Appearance). */
object LiquidPrefs {
    /** The Now Playing backdrop turns slowly while music plays. */
    val PlayerMotion = booleanPreferencesKey("liquidPlayerMotion")

    /** The Now Playing artwork settles back when paused and swells when playing. */
    val ArtworkBreathe = booleanPreferencesKey("liquidArtworkBreathe")

    /** The system volume slider under the transport. */
    val ShowVolume = booleanPreferencesKey("liquidShowVolume")

    /** Lines fill word by word when the provider has word timings. */
    val WordByWord = booleanPreferencesKey("liquidWordByWord")

    /** The still cover drifts, ripples and catches the light while music plays. */
    val LivingArtwork = booleanPreferencesKey("liquidLivingArtwork")

    /** Songs with motion artwork (Apple Music animated covers, canvases) play it in place of the cover. */
    val MotionArtwork = booleanPreferencesKey("liquidMotionArtwork")

    /**
     * How the cover is presented on Now Playing — `ArtworkPresentation`. Absent means Card,
     * the square stage Shiny has always drawn, so nobody's player changes until they say so.
     */
    val ArtworkPresentation = stringPreferencesKey("appearanceArtworkPresentation")

    /** How strongly the artwork colours Now Playing — `Atmosphere`. Absent means Balanced, today's look. */
    val Atmosphere = stringPreferencesKey("appearanceAtmosphere")

    /** The light the artwork throws around itself on the stage — `ArtworkGlow`. Absent means Off. */
    val ArtworkGlow = stringPreferencesKey("appearanceArtworkGlow")

    /**
     * The finish glass is drawn with — `GlassFinish`. Only read while glass is on; "Solid"
     * is the existing Liquid Glass switch turned off, not a value of this key.
     */
    val GlassFinish = stringPreferencesKey("appearanceGlassFinish")

    /** How pages replace each other — `PageTransitions`. Absent means Slide. */
    val PageTransitions = stringPreferencesKey("appearancePageTransitions")
}
