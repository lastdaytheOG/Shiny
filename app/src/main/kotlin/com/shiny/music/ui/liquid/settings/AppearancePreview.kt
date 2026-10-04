package com.shiny.music.ui.liquid.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.models.MediaMetadata
import com.shiny.music.ui.component.backdrop.backdrops.layerBackdrop
import com.shiny.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.GlassIconButton
import com.shiny.music.ui.liquid.GlassKind
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.appearance.ArtworkPresentation
import com.shiny.music.ui.liquid.appearance.LocalShinyAppearance
import com.shiny.music.ui.liquid.appearance.fullScreenScrim
import com.shiny.music.ui.liquid.appearance.strength
import com.shiny.music.ui.liquid.player.NowPlayingBackground
import com.shiny.music.ui.liquid.player.NowPlayingInk
import com.shiny.music.ui.liquid.player.artworkGlow
import com.shiny.music.ui.liquid.player.balancedScrimFor
import com.shiny.music.ui.liquid.player.rememberArtworkGlowColor
import com.shiny.music.ui.liquid.player.rememberNowPlayingBackdrop
import com.shiny.music.ui.utils.resize

/**
 * The Appearance page's specimen: the playing song on a small Now Playing stage, drawn by
 * the player's own background, glow and glass code, so every setting below shows its
 * effect here the moment it changes — no description to trust, nothing to go and check.
 *
 * The stage is still (the drift only runs in the real player), and the button is the
 * real play/pause. With nothing playing it shows the stage a song without artwork gets.
 */
@Composable
internal fun AppearancePreview(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current
    val metadata: MediaMetadata? by (playerConnection?.mediaMetadata?.collectAsState()
        ?: remember { mutableStateOf(null) })
    val isPlaying by (playerConnection?.isPlaying?.collectAsState()
        ?: remember { mutableStateOf(false) })
    val appearance = LocalShinyAppearance.current

    val url = metadata?.thumbnailUrl
    val backdrop = rememberNowPlayingBackdrop(url)
    val glowColor = rememberArtworkGlowColor(backdrop, appearance.glow)
    val glowStrength = appearance.glow.strength
    val stage = rememberLayerBackdrop()
    val artShape = RoundedCornerShape(10.dp)
    // The specimen shows the presentation that is actually chosen, so the row below it is
    // never a promise the preview contradicts. Without a cover there is nothing to fill
    // the stage with, which is exactly what the real player does too.
    val fullScreen = appearance.artwork == ArtworkPresentation.FullScreen && url != null

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsGutter)
            .padding(top = 6.dp)
            .height(188.dp)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        NowPlayingBackground(
            backdrop = backdrop,
            playing = false,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(stage),
        )
        if (fullScreen) {
            Artwork(
                model = url?.resize(600, 600),
                shape = RectangleShape,
                hairline = false,
                modifier = Modifier.fillMaxSize(),
            )
            val base = backdrop.value.gradient.firstOrNull()
            val scrim = remember(appearance.atmosphere, base) {
                fullScreenScrim(appearance.atmosphere, base?.let(::balancedScrimFor) ?: 0.26f)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.2f to Color.Transparent,
                            0.7f to Color.Black.copy(alpha = scrim * 0.52f),
                            1f to Color.Black.copy(alpha = scrim),
                        )
                    )
            )
        }
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (fullScreen) 20.dp else 28.dp)
                .padding(bottom = if (fullScreen) 16.dp else 0.dp),
            verticalAlignment = if (fullScreen) Alignment.Bottom else Alignment.CenterVertically,
        ) {
            if (!fullScreen) {
                Box(
                    Modifier
                        .size(104.dp)
                        .artworkGlow(color = glowColor, strength = { glowStrength })
                        .clip(artShape),
                ) {
                    Artwork(
                        model = url?.resize(300, 300),
                        shape = artShape,
                        hairline = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = metadata?.title ?: "Nothing playing",
                    style = LiquidTypography.headline,
                    color = NowPlayingInk.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metadata?.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() }
                        ?: "Play a song to preview its stage",
                    style = LiquidTypography.subheadline,
                    color = NowPlayingInk.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                GlassIconButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    onClick = { playerConnection?.togglePlayPause() },
                    size = 42.dp,
                    iconSize = 22.dp,
                    kind = GlassKind.Regular,
                    tint = Liquid.colors.label,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    backdrop = stage,
                    enabled = metadata != null,
                )
            }
        }
    }
}
