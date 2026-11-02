package com.shiny.music.ui.liquid.screens

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.shiny.music.R
import com.shiny.music.home.ArtistVideos
import com.shiny.music.home.SimilarPicks
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.cachedArtworkTones
import com.shiny.music.ui.liquid.home.HomeCard
import com.shiny.music.ui.liquid.home.HomeGap
import com.shiny.music.ui.liquid.home.HomeInset
import com.shiny.music.ui.liquid.home.HomeLabelStyle
import com.shiny.music.ui.liquid.home.HomeRowHeight
import com.shiny.music.ui.liquid.home.HomeTrackRow
import com.shiny.music.ui.liquid.home.cardSubtitle
import com.shiny.music.ui.liquid.liquidBottomPadding
import com.shiny.music.ui.liquid.loadArtworkTones
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.statusBarHeight
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private class SimilarGroups(
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val songs: List<SongItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
)

private val SmallLine = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 28.sp, letterSpacing = (-0.06).em)
private val BigLine = TextStyle(fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.07).em)
private val ReceiptStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.42.em)

// "similar to <artist>": the artists around them, the playlists they're on, those artists' songs
// and their newest records. opened from a box in home's artist list, it leads with that box's picks
@Composable
fun LiquidArtistSimilarScreen(navController: NavController, artistId: String) {
    val actions = rememberLiquidActions(navController) ?: return
    val context = LocalContext.current
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()
    val media by actions.playerConnection.mediaMetadata.collectAsState()
    val picks = remember(artistId) { SimilarPicks.get(artistId) }
    var name by remember(artistId) { mutableStateOf(picks?.artistName.orEmpty()) }
    var photo by remember(artistId) { mutableStateOf(picks?.artistPhoto) }
    var groups by remember(artistId) {
        mutableStateOf(
            picks?.let { p ->
                SimilarGroups(
                    artists = p.items.filterIsInstance<ArtistItem>(),
                    playlists = p.items.filterIsInstance<PlaylistItem>(),
                    songs = p.items.filterIsInstance<SongItem>(),
                    albums = p.items.filterIsInstance<AlbumItem>(),
                )
            },
        )
    }
    var deepLoading by remember(artistId) { mutableStateOf(true) }

    LaunchedEffect(artistId) {
        val picked = picks?.items.orEmpty()
        val pickedArtists = picked.filterIsInstance<ArtistItem>()
        val pickedPlaylists = picked.filterIsInstance<PlaylistItem>()
        val pickedSongs = picked.filterIsInstance<SongItem>()
        val pickedAlbums = picked.filterIsInstance<AlbumItem>()
        val page = withContext(Dispatchers.IO) { YouTube.artist(artistId).getOrNull() }
        if (page == null) {
            if (groups == null) groups = SimilarGroups()
            deepLoading = false
            return@LaunchedEffect
        }
        name = page.artist.title
        photo = page.artist.thumbnail ?: photo
        val artists = (pickedArtists + page.sections.filter { it.items.firstOrNull() is ArtistItem }.flatMap { it.items.filterIsInstance<ArtistItem>() }).distinctBy { it.id }
        val playlists = (pickedPlaylists + page.sections.filter { it.items.firstOrNull() is PlaylistItem }.flatMap { it.items.filterIsInstance<PlaylistItem>() }).distinctBy { it.id }
        groups = SimilarGroups(artists, playlists, pickedSongs, pickedAlbums)
        // a handful of the similar artists' pages at once; slow or failing ones are left out
        val pages = withContext(Dispatchers.IO) {
            coroutineScope { artists.take(5).map { a -> async { withTimeoutOrNull(8_000) { YouTube.artist(a.id).getOrNull() } } }.awaitAll() }
        }.filterNotNull()
        // the box's own songs first, then three top songs from each, one artist at a time
        val songs = (pickedSongs + ArtistVideos.interleave(
            pages.map { p -> p.sections.firstOrNull { (it.items.firstOrNull() as? SongItem)?.album != null }?.items?.filterIsInstance<SongItem>()?.take(3).orEmpty() },
            15,
        )).distinctBy { it.id }
        val albums = (pickedAlbums + pages.mapNotNull { p ->
            p.sections.filter { it.items.firstOrNull() is AlbumItem }.flatMap { it.items.filterIsInstance<AlbumItem>() }.maxByOrNull { it.year ?: 0 }
        }).distinctBy { it.id }
        groups = SimilarGroups(artists, playlists, songs, albums)
        deepLoading = false
    }

    val band = photo?.resize(544, 544)
    var theme by remember(band) { mutableStateOf<Color?>(band?.let { cachedArtworkTones(it.resize(120, 120))?.deep }) }
    LaunchedEffect(band) {
        if (band != null) theme = loadArtworkTones(context, band.resize(120, 120))?.deep
    }
    val ground = Liquid.colors.background
    val background by animateColorAsState(theme?.let { lerp(it, Color.Black, 0.35f) } ?: ground, tween(600), label = "similarGround")
    val bandHeight = LocalConfiguration.current.screenHeightDp.dp * 0.42f

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(background) },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenWidth = maxWidth
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = liquidBottomPadding())) {
                item(key = "band") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(bandHeight),
                    ) {
                        if (photo != null) {
                            // a small copy softened, so it reads as colour and light rather than a photo
                            AsyncImage(
                                model = photo?.resize(96, 96),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(10.dp) else Modifier),
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val h = size.height
                                    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.32f), 1f to Color.Transparent, endY = h * 0.35f))
                                    drawRect(
                                        Brush.verticalGradient(0f to Color.Transparent, 0.6f to background.copy(alpha = 0.72f), 1f to background, startY = h * 0.38f, endY = h),
                                        topLeft = Offset(0f, h * 0.38f),
                                    )
                                },
                        )
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = HomeInset - 3.dp, end = HomeInset, bottom = 14.dp),
                        ) {
                            Text(stringResource(R.string.similar_small_line), style = SmallLine, color = Color.White, maxLines = 1)
                            Text(name, style = BigLine, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                item(key = "start") { StartOfTheLine() }
                val g = groups
                if (g == null) return@LazyColumn
                if (g.artists.isNotEmpty()) {
                    item(key = "artists_title") { SimilarTitle(stringResource(R.string.similar_artists), top = 22.dp) }
                    item(key = "artists") {
                        LazyRow(contentPadding = PaddingValues(horizontal = HomeInset), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                            items(g.artists, key = { it.id }) { a -> SimilarArtist(a) { actions.open(a) } }
                        }
                    }
                }
                if (g.playlists.isNotEmpty()) {
                    item(key = "playlists_title") { SimilarTitle(stringResource(R.string.similar_playlists)) }
                    item(key = "playlists") {
                        LazyRow(contentPadding = PaddingValues(horizontal = HomeInset), horizontalArrangement = Arrangement.spacedBy(HomeGap)) {
                            items(g.playlists, key = { it.id }) { p -> TallPlaylistCard(p, onClick = { actions.open(p) }, onLongClick = { actions.menu(p) }) }
                        }
                    }
                }
                if (g.songs.isNotEmpty()) {
                    item(key = "songs_title") { SimilarTitle(stringResource(R.string.similar_songs)) }
                    item(key = "songs") {
                        val rows = minOf(3, g.songs.size).coerceAtLeast(1)
                        val state = rememberLazyGridState()
                        val column = if (screenWidth * 0.475f >= 320.dp) screenWidth * 0.475f else screenWidth * 0.76f
                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(rows),
                            state = state,
                            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
                            contentPadding = PaddingValues(horizontal = HomeInset),
                            horizontalArrangement = Arrangement.spacedBy(39.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(HomeRowHeight * rows + 6.dp * (rows - 1)),
                        ) {
                            items(g.songs.size, key = { g.songs[it].id }) { i ->
                                val s = g.songs[i]
                                HomeTrackRow(
                                    songId = s.id,
                                    title = s.title,
                                    subtitle = s.artists.joinToString { it.name },
                                    artwork = s.thumbnail.resize(240, 240),
                                    isActive = s.id == media?.id,
                                    onClick = { if (s.id == media?.id) actions.togglePlayPause() else actions.open(s) },
                                    onLongClick = { actions.menu(s) },
                                    modifier = Modifier.width(column),
                                )
                            }
                        }
                    }
                }
                if (g.albums.isNotEmpty()) {
                    item(key = "albums_title") { SimilarTitle(stringResource(R.string.similar_albums)) }
                    item(key = "albums") {
                        LazyRow(contentPadding = PaddingValues(horizontal = HomeInset), horizontalArrangement = Arrangement.spacedBy(HomeGap)) {
                            items(g.albums, key = { it.id }) { a ->
                                HomeCard(
                                    title = a.title,
                                    subtitle = a.cardSubtitle(),
                                    artwork = a.thumbnail.resize(544, 544),
                                    onClick = { actions.open(a) },
                                    onLongClick = { actions.menu(a) },
                                )
                            }
                        }
                    }
                }
                if (!deepLoading && g.artists.isEmpty() && g.playlists.isEmpty() && g.songs.isEmpty() && g.albums.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.similar_empty),
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = HomeInset, vertical = 28.dp),
                        )
                    }
                }
                item(key = "bottom") { Spacer(Modifier.height(50.dp)) }
            }
        }
        LiquidBackButton(
            onClick = { navController.navigateUp() },
            modifier = Modifier.padding(start = 16.dp, top = statusBarHeight() + 8.dp),
        )
    }
}

@Composable
private fun StartOfTheLine() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = HomeInset, end = HomeInset, top = 6.dp),
    ) {
        ReceiptRule()
        Text(
            stringResource(R.string.similar_start_line),
            style = ReceiptStyle,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )
        ReceiptRule()
    }
}

@Composable
private fun ReceiptRule() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawLine(Color.White.copy(alpha = 0.24f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 0.75.dp.toPx())
            },
    )
}

@Composable
private fun SimilarTitle(text: String, top: androidx.compose.ui.unit.Dp = 30.dp) {
    Text(
        text,
        style = HomeLabelStyle,
        color = Color.White.copy(alpha = 0.69f),
        maxLines = 1,
        modifier = Modifier.padding(start = HomeInset, end = HomeInset, top = top, bottom = 9.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SimilarArtist(artist: ArtistItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .width(95.dp)
            .pressScale(interaction, 0.95f)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Artwork(model = artist.thumbnail?.resize(300, 300), shape = RoundedCornerShape(12.dp), hairline = false, modifier = Modifier.size(95.dp))
        Spacer(Modifier.height(8.dp))
        Text(artist.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// the cover fills a tall card and the name sits in a dark fade at the foot with a chevron
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TallPlaylistCard(playlist: PlaylistItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(width = 200.dp, height = 245.dp)
            .pressScale(interaction, 0.96f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(model = playlist.thumbnail?.resize(544, 544), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 0.78f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.82f))),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(playlist.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(playlist.author?.name, playlist.songCountText).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = Color.White.copy(alpha = 0.66f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        }
    }
}
