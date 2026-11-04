package com.shiny.music.ui.liquid.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shiny.music.db.entities.RecSurface
import com.shiny.music.db.entities.Song
import com.shiny.music.home.ShortcutsSection
import com.shiny.music.playback.queues.PlaybackOrigin
import com.shiny.music.ui.liquid.ArtworkTones
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.rememberArtworkTones
import com.shiny.music.ui.liquid.statusBarHeight
import com.shiny.music.ui.utils.resize

@Composable
internal fun ListenNowTop(
    title: String,
    mix: List<Song>,
    fallback: List<Song>,
    activeId: String?,
    actions: LiquidActions,
) {
    val fromMix = mix.isNotEmpty()
    val songs = remember(mix, fallback) { (mix.ifEmpty { fallback }).distinctBy { it.id }.take(12) }
    if (songs.isEmpty()) return
    val origin = remember(fromMix) { if (fromMix) PlaybackOrigin(RecSurface.HomeMix) else null }
    CompactTop(glowFrom = songs.firstOrNull()?.thumbnailUrl) {
        HomeSectionTitle(title)
        HomeTrackGrid(items = songs, key = { it.id }) { song, index, modifier ->
            HomeTrackRow(
                songId = song.id,
                title = song.title,
                subtitle = song.artists.joinToString { it.name },
                artwork = song.thumbnailUrl?.resize(240, 240),
                isActive = song.id == activeId,
                onClick = { if (song.id == activeId) actions.togglePlayPause() else actions.playSongMix(title, songs, index, origin) },
                onLongClick = { actions.menu(song) },
                modifier = modifier,
            )
        }
    }
}

@Composable
internal fun HopInTop(title: String, section: ShortcutsSection, glowFrom: String?, actions: LiquidActions) {
    CompactTop(glowFrom = glowFrom) {
        HomeSectionTitle(title)
        ShortcutsRow(section, actions)
    }
}

@Composable
private fun CompactTop(glowFrom: String?, content: @Composable () -> Unit) {
    val tones = rememberArtworkTones(glowFrom?.resize(120, 120), ArtworkTones(Color.Transparent, Color.Transparent))
    val vivid = tones.vivid
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to vivid.copy(alpha = vivid.alpha * 0.42f),
                            0.55f to vivid.copy(alpha = vivid.alpha * 0.16f),
                            1f to Color.Transparent,
                        )
                    )
                },
        )
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(statusBarHeight() + 40.dp))
            content()
            Spacer(Modifier.height(6.dp))
        }
    }
}
