package com.shiny.music.ui.liquid.customize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.shiny.music.utils.dataStore
import kotlinx.coroutines.launch

private val Ink = Color.White.copy(alpha = 0.92f)
private val Bone = Color.White.copy(alpha = 0.22f)
private val BoneDim = Color.White.copy(alpha = 0.11f)
private val Art = Brush.verticalGradient(listOf(Color(0xFF6A6A6E), Color(0xFF3E3E42)))
private val ArtLight = Color(0xFF8A8A8E)
private val Ground = Brush.verticalGradient(listOf(Color(0xFF4A4A50), Color(0xFF1C1C1E)))

/** Every player style as a tile; picking one writes it at once. */
@Composable
internal fun PlayerStyleTiles(selected: PlayerStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    StyleTiles(
        styles = PlayerStyle.entries,
        selected = selected,
        name = { it.title },
        onSelect = { style -> scope.launch { context.dataStore.edit { it.choosePlayerStyle(style) } } },
        modifier = modifier,
    ) { PlayerStylePreview(it) }
}

@Composable
internal fun PlayerStylePreview(style: PlayerStyle) {
    when (style) {
        PlayerStyle.Shiny -> Column(Modifier.fillMaxSize().background(Ground).padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth(0.74f).aspectRatio(1f).background(ArtLight, RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(10.dp))
            NowPlayingBars()
        }
        PlayerStyle.Soft -> Column(Modifier.fillMaxSize().background(Art)) {
            Box(Modifier.fillMaxWidth().weight(1f))
            NowPlayingBars(Modifier.padding(bottom = 12.dp))
        }
        PlayerStyle.Turntable -> Column(Modifier.fillMaxSize().background(Ground).padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth(0.82f).aspectRatio(1f)) {
                Box(Modifier.fillMaxSize().background(Color(0xFF111113), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize(0.8f).background(Color.Transparent, CircleShape))
                    Box(Modifier.fillMaxSize(0.36f).background(ArtLight, CircleShape))
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-4).dp)
                        .rotate(28f)
                        .size(3.dp, 40.dp)
                        .background(Ink, RoundedCornerShape(2.dp)),
                )
            }
            Spacer(Modifier.height(10.dp))
            NowPlayingBars()
        }
        PlayerStyle.Poster -> Box(Modifier.fillMaxSize().background(Ground)) {
            Box(
                Modifier
                    .offset(x = (-14).dp, y = (-18).dp)
                    .size(86.dp)
                    .background(ArtLight, CircleShape),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Bar(54.dp, 8.dp)
                Bar(42.dp, 8.dp)
                Bar(48.dp, 8.dp, Bone)
                Spacer(Modifier.height(4.dp))
                Bar(26.dp, 3.dp, Bone)
            }
        }
        PlayerStyle.Timeline -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(0.45f).background(Color(0xFF55555A))) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(ArtLight, RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(4.dp))
                    Bar(26.dp, 4.dp)
                }
                Bar(36.dp, 12.dp, Ink, Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 3.dp))
            }
            Box(Modifier.fillMaxWidth().weight(0.55f).background(Color(0xFF1C1C1E))) {
                Bar(36.dp, 12.dp, Bone, Modifier.padding(start = 8.dp, top = 3.dp))
                TransportDots(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
            }
        }
        PlayerStyle.Lyrics -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(0.62f).background(Color(0xFF55555A))) {
                Column(Modifier.padding(start = 8.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Bar(58.dp, 10.dp)
                    Bar(44.dp, 10.dp)
                    Bar(30.dp, 10.dp, Bone)
                }
            }
            Box(Modifier.fillMaxWidth().weight(0.38f).background(Color(0xFF1C1C1E))) {
                TransportDots(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
            }
        }
        PlayerStyle.Video -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.42f).align(Alignment.Center).background(Art))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Bar(30.dp, 4.dp, Bone)
                Bar(56.dp, 7.dp)
            }
        }
        PlayerStyle.BedsideTiles -> Column(Modifier.fillMaxSize().background(Color.Black).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Art, RoundedCornerShape(8.dp))) {
                Column(Modifier.align(Alignment.BottomStart).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Bar(34.dp, 5.dp)
                    Bar(52.dp, 2.dp, Bone)
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF151517), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Bar(30.dp, 14.dp, Bone)
                    Bar(30.dp, 14.dp, Bone)
                }
            }
        }
        PlayerStyle.Bedside -> Column(
            Modifier.fillMaxSize().background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
        ) {
            Bar(34.dp, 4.dp, BoneDim)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Bar(26.dp, 20.dp, Bone)
                Bar(4.dp, 4.dp, Bone)
                Bar(26.dp, 20.dp, Bone)
            }
        }
    }
}

// title and artist, the bar and the three buttons under a cover
@Composable
private fun NowPlayingBars(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 9.dp)) {
        Bar(38.dp, 5.dp)
        Spacer(Modifier.height(3.dp))
        Bar(24.dp, 4.dp, Bone)
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Bone, RoundedCornerShape(1.dp)))
        Spacer(Modifier.height(7.dp))
        TransportDots(Modifier.fillMaxWidth())
    }
}

@Composable
private fun TransportDots(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(Bone, CircleShape))
        Box(Modifier.size(12.dp).background(Ink, CircleShape))
        Box(Modifier.size(7.dp).background(Bone, CircleShape))
    }
}
