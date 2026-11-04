package com.shiny.music.ui.liquid.customize

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shiny.music.R
import com.shiny.music.utils.PreferencesSnapshot
import com.shiny.music.extensions.toEnum

/** Everything on Settings › Customise is stored under these keys. */
object CustomizePrefs {
    val CoverRounding = floatPreferencesKey("customizeCoverRounding")
    val Animations = booleanPreferencesKey("customizeAnimations")
    val AnimationSpeed = floatPreferencesKey("customizeAnimationSpeed")
    val SwipeRight = stringPreferencesKey("customizeSwipeRight")
    val SwipeLeft = stringPreferencesKey("customizeSwipeLeft")
    val SwipeDownAnywhere = booleanPreferencesKey("customizeSwipeDownAnywhere")
    val PlayerStatusBar = booleanPreferencesKey("customizePlayerStatusBar")
    val PlayerNavBar = booleanPreferencesKey("customizePlayerNavBar")
    val PlaylistStyle = stringPreferencesKey("customizePlaylistStyle")
    val HomeStyle = stringPreferencesKey("customizeHomeStyle")
    val LyricsStyle = stringPreferencesKey("customizeLyricsStyle")
    val ProgressiveBlur = booleanPreferencesKey("customizeProgressiveBlur")
    val BlurQuality = stringPreferencesKey("customizeBlurQuality")
    val BlurStrength = floatPreferencesKey("customizeBlurStrength")
    val ElementBlur = booleanPreferencesKey("customizeElementBlur")

    // 25 draws every cover exactly as Shiny always has
    const val CoverRoundingDefault = 25f
    const val CoverRoundingMax = 45f

    // 25 px is the glass exactly as Shiny draws it
    const val BlurStrengthDefault = 25f
    const val BlurStrengthMin = 10f
    const val BlurStrengthMax = 50f
}

/** How album and playlist pages lay out their top. The first is the page Shiny has always had. */
enum class PlaylistStyle(val title: String, val blurb: String) {
    Classic("Classic", "The cover centred over its own colour"),
    Flow("Flow", "The cover edge to edge, the title over its foot"),
    Minimal("Minimal", "No cover, just the title over a wash of its colour"),
}

/** What Home opens with. The first is Home as it is. */
enum class HomeStyle(val title: String, val blurb: String) {
    Hero("Hero", "Big artwork cards to swipe through"),
    ListenNow("Listen now", "A short list of songs to start with"),
    HopIn("Hop in", "Your playlists as tiles, one tap from playing"),
}

/** How a synced lyric line is sung. The first is Shiny's own. */
enum class LyricsStyle(val title: String, val blurb: String, val glow: Boolean, val rise: Boolean, val words: Boolean, val cascade: Boolean) {
    Shiny("Shiny", "Words light up as they're sung and glow, lines follow in a wave", glow = true, rise = false, words = true, cascade = true),
    Rise("Rise", "Words lift into place as they're sung, with the glow", glow = true, rise = true, words = true, cascade = true),
    Calm("Calm", "Words light up as they're sung, without the glow", glow = false, rise = false, words = true, cascade = true),
    Plain("Plain", "Whole lines light at once and the page moves as one", glow = false, rise = false, words = false, cascade = false),
}

data class GlassTuning(val strength: Float, val low: Boolean)

/** How finely glass is drawn: Low reads the page behind it at a lower resolution, which costs less. */
enum class BlurQuality(val title: String) {
    High("High"),
    Low("Low"),
}

/** The stops the animation speed moves between, slowest to fastest. */
val AnimationSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

fun animationSpeedLabel(speed: Float): String {
    val text = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')
    return "${text}x"
}

/** What sliding a song row does, picked per side. */
enum class SongSwipeAction(val title: String, val pill: String, @DrawableRes val icon: Int, val color: Color) {
    Library("Add or remove from library", "Library", R.drawable.library_add, Color(0xFF9B72F2)),
    Queue("Add to queue", "Add to queue", R.drawable.queue_music, Color(0xFF34C759)),
    Download("Download", "Download", R.drawable.download, Color(0xFF0A84FF)),
    PlayNext("Play next", "Play next", R.drawable.playlist_play, Color(0xFFFF9F0A)),
    AddToPlaylist("Add to playlist", "Add to playlist", R.drawable.playlist_add, Color(0xFFFF375F)),
    Details("View details", "Details", R.drawable.info, Color(0xFF5AC8FA)),
}

val DefaultSwipeRight = SongSwipeAction.PlayNext
val DefaultSwipeLeft = SongSwipeAction.Queue

/**
 * The customise choices that drawing code reads outside composition. Every getter reads the
 * app-wide preference snapshot, so a change shows on the next frame.
 */
object CustomizeLook {
    private var systemScale by mutableFloatStateOf(1f)

    val animations: Boolean get() = PreferencesSnapshot.read(CustomizePrefs.Animations, true)
    val speed: Float get() = PreferencesSnapshot.read(CustomizePrefs.AnimationSpeed, 1f)

    /** How much rounder or squarer covers are than Shiny's own: 1 is unchanged, 0 is square. */
    val coverScale: Float
        get() = PreferencesSnapshot.read(CustomizePrefs.CoverRounding, CustomizePrefs.CoverRoundingDefault)
            .coerceIn(0f, CustomizePrefs.CoverRoundingMax) / CustomizePrefs.CoverRoundingDefault

    val swipeRight: SongSwipeAction get() = PreferencesSnapshot.readString(CustomizePrefs.SwipeRight).toEnum(DefaultSwipeRight)
    val swipeLeft: SongSwipeAction get() = PreferencesSnapshot.readString(CustomizePrefs.SwipeLeft).toEnum(DefaultSwipeLeft)

    val playlistStyle: PlaylistStyle get() = PreferencesSnapshot.readString(CustomizePrefs.PlaylistStyle).toEnum(PlaylistStyle.Classic)
    val homeStyle: HomeStyle get() = PreferencesSnapshot.readString(CustomizePrefs.HomeStyle).toEnum(HomeStyle.Hero)
    val lyricsStyle: LyricsStyle get() = PreferencesSnapshot.readString(CustomizePrefs.LyricsStyle).toEnum(LyricsStyle.Shiny)

    /** The scroll edge under the top bar blurs progressively; off, it is a plain fade. */
    val progressiveBlur: Boolean get() = PreferencesSnapshot.read(CustomizePrefs.ProgressiveBlur, true)
    val blurQuality: BlurQuality get() = PreferencesSnapshot.readString(CustomizePrefs.BlurQuality).toEnum(BlurQuality.High)

    /** How strong glass blur is against Shiny's own: 1 is unchanged. */
    val blurScale: Float
        get() = PreferencesSnapshot.read(CustomizePrefs.BlurStrength, CustomizePrefs.BlurStrengthDefault)
            .coerceIn(CustomizePrefs.BlurStrengthMin, CustomizePrefs.BlurStrengthMax) / CustomizePrefs.BlurStrengthDefault

    /**
     * Strength and quality as one value that only changes when one of them does, so glass
     * reading it in composition is not woken by every other preference written.
     */
    val glassTuning: androidx.compose.runtime.State<GlassTuning> = androidx.compose.runtime.derivedStateOf {
        GlassTuning(blurScale, blurQuality == BlurQuality.Low)
    }

    /** Things themselves blurring as they move: the Home hero scrolling away, Library titles. */
    val elementBlur: Boolean get() = PreferencesSnapshot.read(CustomizePrefs.ElementBlur, true)

    /** Given to the window's recomposer, so every animation in the app reads it each frame. */
    val durationScale: MotionDurationScale = object : MotionDurationScale {
        override val scaleFactor: Float
            get() = if (!animations) 0f else systemScale / speed.coerceAtLeast(0.1f)
    }

    private var observer: ContentObserver? = null

    // the system's own animator scale still applies underneath, as it did before; watched once per process
    fun followSystemScale(context: Context) {
        val resolver = context.applicationContext.contentResolver
        fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        systemScale = read()
        if (observer != null) return
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                systemScale = read()
            }
        }.also {
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, it)
        }
    }
}

/**
 * A cover's own rounded shape with its corners scaled by the cover rounding setting. Any other
 * shape (a circle for an artist) is left alone.
 */
fun Shape.withCoverRounding(): Shape {
    if (this !is CornerBasedShape) return this
    val scale = CustomizeLook.coverScale
    if (scale == 1f) return this
    return ScaledCorners(this, scale)
}

private class ScaledCorners(private val base: CornerBasedShape, private val scale: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val limit = size.minDimension / 2f
        fun scaled(corner: CornerSize) = CornerSize((corner.toPx(size, density) * scale).coerceIn(0f, limit))
        return RoundedCornerShape(
            topStart = scaled(base.topStart),
            topEnd = scaled(base.topEnd),
            bottomEnd = scaled(base.bottomEnd),
            bottomStart = scaled(base.bottomStart),
        ).createOutline(size, layoutDirection, density)
    }

    override fun equals(other: Any?): Boolean = other is ScaledCorners && other.base == base && other.scale == scale
    override fun hashCode(): Int = base.hashCode() * 31 + scale.hashCode()
}
