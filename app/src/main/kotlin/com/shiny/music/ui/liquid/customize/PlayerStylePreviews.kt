package com.shiny.music.ui.liquid.customize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import com.shiny.music.utils.dataStore
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val Ink = Color.White.copy(alpha = 0.94f)
private val MutedInk = Color.White.copy(alpha = 0.64f)
private val TileGround = Color(0xFF111113)

@Composable
internal fun PlayerStyleTiles(selected: PlayerStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    StyleTiles(
        styles = PlayerStyle.entries.filter { it != PlayerStyle.PosterRing },
        selected = selected,
        name = { it.title },
        onSelect = { style -> scope.launch { context.dataStore.edit { it.choosePlayerStyle(style) } } },
        modifier = modifier,
        tileWidth = 148.dp,
        stacks = mapOf(PlayerStyle.Poster to listOf(PlayerStyle.Poster, PlayerStyle.PosterRing)),
    ) { PlayerStylePreview(it) }
}

@Composable
internal fun PlayerStylePreview(style: PlayerStyle) {
    val title = LocalPreviewTitle.current
    val artist = LocalPreviewArtist.current
    val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    when (style) {
        PlayerStyle.Shiny -> Column(
            Modifier.fillMaxSize().background(TileGround).padding(horizontal = 7.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PreviewArtwork(Modifier.fillMaxWidth(0.82f).aspectRatio(1f), RoundedCornerShape(5.dp))
            Spacer(Modifier.height(6.dp))
            PreviewTrack(title, artist, Modifier.fillMaxWidth())
            PreviewProgress(Modifier.fillMaxWidth().padding(top = 5.dp))
            PreviewTransport(Modifier.fillMaxWidth().padding(top = 5.dp))
        }
        PlayerStyle.Soft -> Box(Modifier.fillMaxSize().background(TileGround)) {
            PreviewArtwork(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PreviewTrack(title, artist, Modifier.fillMaxWidth(), align = TextAlign.Start)
                PreviewProgress(Modifier.fillMaxWidth().padding(top = 4.dp), tint = Color.White)
            }
        }
        PlayerStyle.Turntable -> Column(
            Modifier.fillMaxSize().background(TileGround).padding(vertical = 9.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(0.88f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF080809)).padding(7.dp).clip(CircleShape)) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        for (i in 1..5) drawCircle(Color.White.copy(alpha = 0.035f), size.minDimension * i / 12f, style = androidx.compose.ui.graphics.drawscope.Stroke(size.minDimension * 0.004f))
                    }
                    PreviewArtwork(Modifier.fillMaxSize(0.48f), CircleShape)
                }
                Box(Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 1.dp).rotate(24f).size(2.dp, 34.dp).background(Color.White.copy(alpha = 0.76f), RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.height(7.dp))
            PreviewTrack(title, artist, Modifier.fillMaxWidth(), align = TextAlign.Center)
        }
        PlayerStyle.Poster, PlayerStyle.PosterRing -> Box(Modifier.fillMaxSize().background(TileGround)) {
            PreviewArtwork(Modifier.fillMaxWidth(0.86f).aspectRatio(1f).align(Alignment.TopCenter).offset(y = (-14).dp), CircleShape)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.25f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.96f))))
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PreviewTrack(title, artist, Modifier.fillMaxWidth(), align = TextAlign.Start)
                if (style == PlayerStyle.PosterRing) PreviewProgress(Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
        PlayerStyle.Dial -> Column(
            Modifier.fillMaxSize().background(Color(0xFF161618)).padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = Ink, fontSize = 7.sp, lineHeight = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp).clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(2.dp).background(Color.White.copy(alpha = 0.3f), CircleShape))
                    Box(Modifier.fillMaxWidth(0.72f).height(2.5.dp).background(Ink, CircleShape))
                    Box(Modifier.fillMaxWidth(0.5f).height(2.dp).background(Color.White.copy(alpha = 0.3f), CircleShape))
                }
            }
            Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(Color.White.copy(alpha = 0.1f), CircleShape))
                Box(Modifier.size(15.dp).background(Color(0xFFC0462A), CircleShape))
                Box(Modifier.size(9.dp).background(Color.White.copy(alpha = 0.1f), CircleShape))
            }
            Box(Modifier.fillMaxWidth(0.9f).aspectRatio(1f)) {
                PreviewArtwork(Modifier.fillMaxSize(), CircleShape)
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                    drawCircle(Color.White.copy(alpha = 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    drawArc(Color.White, -90f, 140f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                }
            }
        }
        PlayerStyle.Timeline -> Column(Modifier.fillMaxSize().background(Color(0xFF101012))) {
            Box(Modifier.fillMaxWidth().weight(0.58f)) {
                PreviewArtwork(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.02f), Color.Black.copy(alpha = 0.68f)))))
                PreviewTrack(title, artist, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(7.dp))
            }
            Column(Modifier.fillMaxWidth().weight(0.42f).padding(7.dp), verticalArrangement = Arrangement.Center) {
                PreviewProgress(Modifier.fillMaxWidth())
                PreviewTransport(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        PlayerStyle.Lyrics -> Column(Modifier.fillMaxSize().background(TileGround)) {
            Box(Modifier.fillMaxWidth().weight(0.62f)) {
                PreviewArtwork(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
                PreviewTrack(title, artist, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(7.dp))
            }
            Box(Modifier.fillMaxWidth().weight(0.38f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                Text(title, color = Ink, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        PlayerStyle.Video -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            PreviewArtwork(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Black.copy(alpha = 0.04f), 1f to Color.Black.copy(alpha = 0.9f))))
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PreviewTrack(title, artist, Modifier.fillMaxWidth())
                PreviewProgress(Modifier.fillMaxWidth())
            }
        }
        PlayerStyle.BedsideTiles -> Column(Modifier.fillMaxSize().background(Color.Black).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp))) {
                PreviewArtwork(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))
                PreviewTrack(title, artist, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(5.dp))
            }
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).background(TileGround), contentAlignment = Alignment.Center) {
                Text(time, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.7).sp)
            }
        }
        PlayerStyle.Bedside -> Column(
            Modifier.fillMaxSize().background(Color.Black).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(time, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.8).sp)
            Spacer(Modifier.height(8.dp))
            PreviewArtwork(Modifier.size(26.dp), CircleShape)
            Spacer(Modifier.height(4.dp))
            PreviewTrack(title, artist, Modifier.fillMaxWidth(), align = TextAlign.Center)
        }
    }
}

@Composable
private fun PreviewTrack(title: String, artist: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp), horizontalAlignment = if (align == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(title, color = Ink, fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align)
        if (artist.isNotBlank()) Text(artist, color = MutedInk, fontSize = 7.sp, lineHeight = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align)
    }
}

@Composable
private fun PreviewProgress(modifier: Modifier = Modifier, tint: Color = Color(0xFFFF375F)) {
    Box(modifier.height(1.5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.26f))) {
        Box(Modifier.fillMaxWidth(0.42f).fillMaxSize().background(tint, CircleShape))
    }
}

@Composable
private fun PreviewTransport(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Text("‹", color = MutedInk, fontSize = 12.sp)
        Text("▶", color = Ink, fontSize = 9.sp)
        Text("›", color = MutedInk, fontSize = 12.sp)
    }
}
