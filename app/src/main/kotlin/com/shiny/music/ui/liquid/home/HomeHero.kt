package com.shiny.music.ui.liquid.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.R
import com.shiny.music.home.Daypart
import com.shiny.music.home.HeroMixSection
import com.shiny.music.home.OfflineNoticeSection
import com.shiny.music.home.StartHereSection
import com.shiny.music.models.MediaMetadata
import com.shiny.music.ui.liquid.ArtworkTones
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.NeutralTones
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.rememberArtworkTones
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize

// ---------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------

/** A small tracked all-caps line that says why something is here. */
@Composable
internal fun Eyebrow(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = LiquidTypography.caption2.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
internal fun daypartEyebrow(daypart: Daypart): String = stringResource(
    when (daypart) {
        Daypart.Morning -> R.string.home_eyebrow_morning
        Daypart.Afternoon -> R.string.home_eyebrow_afternoon
        Daypart.Evening -> R.string.home_eyebrow_evening
        Daypart.Night -> R.string.home_eyebrow_night
    }
)

@Composable
internal fun mixTitle(section: HeroMixSection): String = stringResource(
    if (section.offlineOnly) R.string.home_mix_offline else when (section.daypart) {
        Daypart.Morning -> R.string.home_mix_morning
        Daypart.Afternoon -> R.string.home_mix_afternoon
        Daypart.Evening -> R.string.home_mix_evening
        Daypart.Night -> R.string.home_mix_night
    }
)

/**
 * The opening of Home: a band of the lead artwork's own colour that rises out of the page
 * ground and sinks back into it.
 *
 * It is deliberately **not a card**. A card would be the sixth rounded rectangle on the
 * screen; this is a field — full width, no rim, no shadow, no corner — so the first thing
 * Home does is change the colour of the room rather than put another box in it. The fade
 * at each edge is measured in pixels from the real height, so the colour never touches a
 * hard line whatever the content inside turns out to be, and the content itself always
 * sits in the solid middle where white type is legible in either theme.
 */
@Composable
private fun AtmosphericField(
    tones: ArtworkTones,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val background = Liquid.colors.background
    val fade = with(LocalDensity.current) { FieldFade.toPx() }
    // The artwork chooses the hue; the field chooses the depth.
    //
    // `rememberArtworkTones` already darkens its deep swatch, but it does so by a fixed
    // number of steps towards black, and a saturated green or yellow — a grass pitch, a
    // neon sleeve — has too much luminance to reach the same depth as a navy in that many.
    // A card can live with that; a field the width of the screen cannot, so Home takes it
    // down again from its own end. The vivid swatch is then worth only a suggestion of
    // itself, enough to keep the hue alive without lifting the whole band.
    val base = lerp(tones.deep, Color.Black, 0.30f)
    val top = lerp(base, tones.vivid, 0.10f)
    val bottom = lerp(tones.deep, Color.Black, 0.55f)
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val h = size.height
                if (h <= 0f) return@drawBehind
                val edge = (fade / h).coerceIn(0.02f, 0.30f)
                drawRect(
                    Brush.verticalGradient(
                        0f to background,
                        edge to top,
                        (1f - edge) to bottom,
                        1f to background,
                    )
                )
                // One soft off-centre bloom, so the field has a light source instead of
                // reading as a flat printed panel.
                drawRect(
                    Brush.radialGradient(
                        listOf(tones.vivid.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.82f, h * 0.22f),
                        radius = size.width * 0.72f,
                    )
                )
            },
    ) {
        Spacer(Modifier.height(FieldFade))
        content()
        Spacer(Modifier.height(FieldFade))
    }
}

/** How far the field's colour takes to arrive and to leave. */
private val FieldFade = 30.dp

/** A capsule button for coloured grounds: white with dark text, or a translucent white. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OnColorButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .pressScale(interaction, 0.95f)
            .clip(CircleShape)
            .background(if (primary) Color.White else Color.White.copy(alpha = 0.18f))
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val content = if (primary) Color.Black else Color.White
        Icon(icon, null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = LiquidTypography.headline, color = content, maxLines = 1)
    }
}

/**
 * Three covers fanned like records pulled from a shelf. They open from a stack the first
 * time the card is drawn — the one piece of motion on the hero — unless motion is reduced.
 *
 * Every cover is drawn square at its natural aspect and only ever rotated, never scaled on
 * one axis: artwork leans, it does not stretch.
 */
@Composable
internal fun CoverFan(covers: List<String?>, size: Dp, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val spread = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { spread.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 180f)) }
    val step = with(LocalDensity.current) { (size * 0.26f).toPx() }
    val shown = covers.take(3)
    // The lead cover sits in the middle, in front; the others lean out behind it.
    val offsets = when (shown.size) {
        1 -> listOf(0f)
        2 -> listOf(0.5f, -0.5f)
        else -> listOf(0f, -1f, 1f)
    }
    val drawOrder = shown.indices.sortedWith(compareByDescending<Int> { kotlin.math.abs(offsets[it]) }.thenByDescending { it })
    Box(modifier.size(width = size * 1.52f, height = size * 1.1f), contentAlignment = Alignment.Center) {
        drawOrder.forEach { i ->
            val offset = offsets[i]
            val lead = i == 0
            Artwork(
                model = shown[i],
                shape = RoundedCornerShape(10.dp),
                hairline = false,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        val t = spread.value
                        translationX = offset * step * t
                        rotationZ = offset * 7f * t
                        val s = if (lead) 1f else 0.9f
                        scaleX = s
                        scaleY = s
                    }
                    .shadow(if (lead) 18.dp else 8.dp, RoundedCornerShape(10.dp), clip = false),
            )
        }
    }
}

/**
 * The editorial head of the opening: why this is here, what it is, and what it is made of.
 * One shape for every state Home can open in, so the first screenful always has the same
 * rhythm — eyebrow, headline, one true sentence, two ways to start.
 */
@Composable
private fun OpeningHead(
    eyebrow: String,
    title: String,
    reason: String,
    covers: List<String?>,
    reduceMotion: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    shuffleLabel: String = stringResource(R.string.liquid_shuffle),
) {
    Column(Modifier.padding(horizontal = PageMargin)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow(eyebrow, Color.White.copy(alpha = 0.74f))
                Spacer(Modifier.height(7.dp))
                Text(
                    text = title,
                    style = LiquidTypography.largeTitle.copy(fontSize = 33.sp, lineHeight = 37.sp),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = reason,
                    style = LiquidTypography.subheadline,
                    color = Color.White.copy(alpha = 0.80f),
                    // A chart's own name can be long ("Top 100 Music Videos United
                    // States") and it is quoted verbatim, so the sentence gets the room.
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (covers.isNotEmpty()) {
                CoverFan(covers, size = 84.dp, reduceMotion = reduceMotion, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OnColorButton(stringResource(R.string.liquid_play), Icons.Rounded.PlayArrow, primary = true, onClick = onPlay)
            OnColorButton(shuffleLabel, Icons.Rounded.Shuffle, primary = false, onClick = onShuffle)
        }
    }
}

// ---------------------------------------------------------------------------------------
// The day's mix
// ---------------------------------------------------------------------------------------

/**
 * The first thing Home says: a mix for this part of the day, made of songs the listener
 * keeps returning to with a measured share of new ones, over a field of its lead cover's
 * colour.
 *
 * The reason line always states the real split, so the card never has to pretend. A mix
 * that is one familiar song and sixteen new ones says exactly that, and its eyebrow says
 * it is a place to start rather than claiming to know the listener's evenings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HeroMixCard(
    section: HeroMixSection,
    reduceMotion: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    compact: Boolean,
) {
    val covers = remember(section.songs) {
        section.songs.mapNotNull { it.thumbnailUrl }.distinct().take(3).map { it.resize(360, 360) }
    }
    val tones = rememberArtworkTones(covers.firstOrNull(), NeutralTones)
    val title = mixTitle(section)
    val eyebrow = when {
        section.offlineOnly -> stringResource(R.string.home_eyebrow_offline)
        section.tentative -> stringResource(R.string.home_eyebrow_start)
        else -> daypartEyebrow(section.daypart)
    }
    val reason = if (section.fresh > 0) {
        stringResource(R.string.home_mix_reason_both, section.familiar, section.fresh)
    } else {
        pluralStringResource(R.plurals.home_mix_reason_familiar, section.familiar, section.familiar)
    }

    if (compact) {
        // A paused session already owns the top of the page; the mix steps aside into one
        // quiet row rather than competing with it.
        CompactOpening(
            eyebrow = eyebrow,
            title = title,
            reason = reason,
            covers = covers,
            tones = tones,
            reduceMotion = reduceMotion,
            onPlay = onPlay,
        )
        return
    }

    AtmosphericField(
        tones = tones,
        modifier = Modifier.semantics { contentDescription = "$eyebrow. $title. $reason" },
    ) {
        OpeningHead(
            eyebrow = eyebrow,
            title = title,
            reason = reason,
            covers = covers,
            reduceMotion = reduceMotion,
            onPlay = onPlay,
            onShuffle = onShuffle,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactOpening(
    eyebrow: String,
    title: String,
    reason: String,
    covers: List<String?>,
    tones: ArtworkTones,
    reduceMotion: Boolean,
    onPlay: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .padding(horizontal = PageMargin)
            .padding(top = 16.dp)
            .fillMaxWidth()
            .pressScale(interaction, 0.985f)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(lerp(tones.vivid, tones.deep, 0.62f), tones.deep)))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = stringResource(R.string.liquid_play),
                onClick = onPlay,
            )
            .semantics { contentDescription = "$eyebrow. $title. $reason" }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverFan(covers, size = 52.dp, reduceMotion = reduceMotion)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Eyebrow(eyebrow, Color.White.copy(alpha = 0.72f))
            Text(title, style = LiquidTypography.title3.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(reason, style = LiquidTypography.footnote, color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PlayDisc(onClick = onPlay, background = Color.White, tint = Color.Black)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlayDisc(onClick: () -> Unit, background: Color, tint: Color, size: Dp = 44.dp) {
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(R.string.liquid_play)
    Box(
        modifier = Modifier
            .size(size.coerceAtLeast(44.dp))
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .background(background)
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.PlayArrow, null, tint = tint, modifier = Modifier.size(26.dp))
    }
}

// ---------------------------------------------------------------------------------------
// Pick up where you left off
// ---------------------------------------------------------------------------------------

/**
 * Shown while a session is loaded but paused — the restored queue after a relaunch, or a
 * pause earlier today. One tap resumes it; the mini player alone does not say where the
 * song came from or how far in it was.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ResumeCard(
    metadata: MediaMetadata,
    queueTitle: String?,
    positionMs: Long,
    onResume: () -> Unit,
) {
    val tones = rememberArtworkTones(metadata.thumbnailUrl?.resize(360, 360), NeutralTones)
    val interaction = remember { MutableInteractionSource() }
    val durationMs = metadata.duration * 1000L
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val artists = metadata.artists.joinToString { it.name }
    val resume = stringResource(R.string.home_resume)
    Row(
        modifier = Modifier
            .padding(horizontal = PageMargin)
            .padding(top = 8.dp)
            .fillMaxWidth()
            .pressScale(interaction, 0.985f)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(tones.deep, lerp(tones.deep, Color.Black, 0.35f))))
            .combinedClickable(interactionSource = interaction, indication = null, onClickLabel = resume, onClick = onResume)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            model = metadata.thumbnailUrl?.resize(240, 240),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(76.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Eyebrow(stringResource(R.string.home_resume_eyebrow), Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(3.dp))
            Text(metadata.title, style = LiquidTypography.headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(artists.takeIf { it.isNotBlank() }, queueTitle?.let { stringResource(R.string.home_from_queue, it) })
                    .joinToString(" · "),
                style = LiquidTypography.footnote,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress > 0f) {
                Spacer(Modifier.height(9.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(Color.White),
                    )
                }
            }
        }
        PlayDisc(onClick = onResume, background = Color.White, tint = Color.Black)
    }
}

// ---------------------------------------------------------------------------------------
// A Home with no listening behind it
// ---------------------------------------------------------------------------------------

internal enum class HomePath { Charts, NewReleases, Moods, Search, Device, Identify }

/**
 * The opening for someone Shiny has never heard play anything.
 *
 * It is the *same* shape as the day's mix — the same field, the same eyebrow, headline,
 * sentence and pair of buttons — because a first-run Home should look like a finished
 * product, not a placeholder with a progress meter in it. The only difference is where the
 * music comes from: the chart for wherever they are, or what is already on the phone.
 *
 * There is no plays-until-personal bar and nothing here says "come back later". Home is
 * already learning; it simply has nothing to report yet, and says so in one line.
 */
@Composable
internal fun StartHereBlock(
    section: StartHereSection,
    reduceMotion: Boolean,
    onPlayChart: (List<com.music.innertube.models.SongItem>, Boolean) -> Unit,
    onPlayDevice: (Boolean) -> Unit,
) {
    val chart = section.chart
    val device = section.deviceSongs
    when {
        chart != null -> {
            val covers = remember(chart.playlistId, chart.songs) {
                chart.songs.mapNotNull { it.thumbnail }.distinct().take(3).map { it.resize(360, 360) }
            }
            val tones = rememberArtworkTones(covers.firstOrNull(), NeutralTones)
            val eyebrow = stringResource(R.string.home_start_eyebrow)
            val title = stringResource(R.string.home_start_title)
            val reason = stringResource(R.string.home_start_chart_reason, chart.title)
            AtmosphericField(
                tones = tones,
                modifier = Modifier.semantics { contentDescription = "$eyebrow. $title. $reason" },
            ) {
                OpeningHead(
                    eyebrow = eyebrow,
                    title = title,
                    reason = reason,
                    covers = covers,
                    reduceMotion = reduceMotion,
                    onPlay = { onPlayChart(chart.songs, false) },
                    onShuffle = { onPlayChart(chart.songs, true) },
                )
            }
        }

        device.isNotEmpty() -> {
            val covers = remember(device) {
                device.mapNotNull { it.thumbnailUrl }.distinct().take(3).map { it.resize(360, 360) }
            }
            val tones = rememberArtworkTones(covers.firstOrNull(), NeutralTones)
            val total = section.localSongs + section.downloadedSongs
            val eyebrow = stringResource(R.string.home_start_device_eyebrow)
            val title = stringResource(R.string.home_start_device_title)
            val reason = pluralStringResource(R.plurals.home_start_device_reason, total, total)
            AtmosphericField(
                tones = tones,
                modifier = Modifier.semantics { contentDescription = "$eyebrow. $title. $reason" },
            ) {
                OpeningHead(
                    eyebrow = eyebrow,
                    title = title,
                    reason = reason,
                    covers = covers,
                    reduceMotion = reduceMotion,
                    onPlay = { onPlayDevice(false) },
                    onShuffle = { onPlayDevice(true) },
                )
            }
        }

        else -> QuietStart(section.online)
    }
}

/**
 * Nothing on the device and nothing on the network. The one state Home cannot fill with
 * music, so it fills it with a sentence instead of a wall of buttons.
 */
@Composable
private fun QuietStart(online: Boolean) {
    val colors = Liquid.colors
    Column(
        Modifier
            .padding(horizontal = PageMargin)
            .padding(top = 10.dp, bottom = 6.dp)
            .fillMaxWidth(),
    ) {
        Text(
            text = stringResource(if (online) R.string.home_quiet_title else R.string.home_quiet_offline_title),
            style = LiquidTypography.title1,
            color = colors.label,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(if (online) R.string.home_quiet_body else R.string.home_quiet_offline_body),
            style = LiquidTypography.callout,
            color = colors.secondaryLabel,
        )
    }
}

// ---------------------------------------------------------------------------------------
// Offline
// ---------------------------------------------------------------------------------------

@Composable
internal fun OfflineNotice(section: OfflineNoticeSection) {
    val colors = Liquid.colors
    Row(
        Modifier
            .padding(horizontal = PageMargin)
            .padding(top = 6.dp, bottom = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.quaternaryFill)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CloudOff, null, tint = colors.secondaryLabel, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_offline_title), style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.label)
            Text(
                text = if (section.playable > 0) {
                    pluralStringResource(R.plurals.home_offline_body, section.playable, section.playable)
                } else {
                    stringResource(R.string.home_offline_empty)
                },
                style = LiquidTypography.footnote,
                color = colors.secondaryLabel,
            )
        }
    }
}
