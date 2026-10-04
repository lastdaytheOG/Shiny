package com.shiny.music.ui.liquid.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.shiny.music.LocalDatabase
import com.shiny.music.R
import com.shiny.music.canvas.AppleMusicArtistBackgroundProvider
import com.shiny.music.constants.DataSaverEnabledKey
import com.shiny.music.constants.ShowArtistBackgroundVideoKey
import com.shiny.music.db.entities.ArtistEntity
import com.shiny.music.playback.queues.YouTubeQueue
import com.shiny.music.ui.component.backdrop.backdrops.layerBackdrop
import com.shiny.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.NavPeek
import com.shiny.music.ui.liquid.ArtworkTones
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.DetailTopBar
import com.shiny.music.ui.liquid.GlassIconButton
import com.shiny.music.ui.liquid.GlassKind
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.LocalLiquidBackdrop
import com.shiny.music.ui.liquid.MediaTile
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.SectionHeader
import com.shiny.music.ui.liquid.Shelf
import com.shiny.music.ui.liquid.SongGrid
import com.shiny.music.ui.liquid.SongRow
import com.shiny.music.ui.liquid.glassPressScale
import com.shiny.music.ui.liquid.liquidBottomPadding
import com.shiny.music.ui.liquid.liquidGlass
import com.shiny.music.ui.liquid.rememberArtworkTones
import com.shiny.music.ui.liquid.rememberGlassPress
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.subtitleText
import com.shiny.music.ui.menu.YouTubeArtistMenu
import com.shiny.music.ui.component.LocalMenuState
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize
import com.shiny.music.utils.rememberPreference
import com.shiny.music.viewmodels.ArtistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val OnColor = Color.White
private val OnColorSecondary = Color.White.copy(alpha = 0.68f)
private val OnColorSeparator = Color.White.copy(alpha = 0.16f)

/**
 * The artist page in the iOS 27 idiom: the artist's picture runs edge to edge and melts
 * into a full-screen field of its own colour; the name sits on the image with info, play
 * and follow gathered right beneath it; the newest release gets a box of its own; then
 * Top Songs, discography and the rest in white type on that colour.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidArtistScreen(
    navController: NavController,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val playerConnection = actions.playerConnection
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val page = viewModel.artistPage
    val libraryArtist by viewModel.libraryArtist.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()
    val libraryAlbums by viewModel.libraryAlbums.collectAsState()

    // A first visit: the tile tapped to get here already showed the name and picture.
    val peek = remember(viewModel.artistId) { NavPeek.get(viewModel.artistId) }
    val name = page?.artist?.title ?: libraryArtist?.artist?.name ?: peek?.title.orEmpty()
    val thumbnail = page?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl ?: peek?.artwork
    val followed = libraryArtist?.artist?.bookmarkedAt != null

    val dataSaver by rememberPreference(DataSaverEnabledKey, false)
    val videoPref by rememberPreference(ShowArtistBackgroundVideoKey, true)
    var videoUrl by remember(name) { mutableStateOf<String?>(null) }
    LaunchedEffect(name, videoPref, dataSaver) {
        if (name.isNotBlank() && videoPref && !dataSaver) {
            videoUrl = withContext(Dispatchers.IO) {
                runCatching { AppleMusicArtistBackgroundProvider.getByArtistName(name) }.getOrNull()
            }
        }
    }

    val tones = rememberArtworkTones(thumbnail?.resize(544, 544), ArtworkTones(Color(0xFF1E1E22), Color(0xFF3A3A40)))
    val pageColor = tones.deep

    // Light status bar icons on the coloured page, restored on the way out.
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    val listState = rememberLazyListState()
    val pageBackdrop = rememberLayerBackdrop {
        drawRect(pageColor)
        drawContent()
    }
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }

    val topSongsSection = page?.sections?.firstOrNull { it.items.firstOrNull() is SongItem }
    val featured: AlbumItem? = remember(page) {
        page?.sections
            ?.firstOrNull { s -> s.items.firstOrNull() is AlbumItem && s.title.contains("single", ignoreCase = true) }
            ?.items?.firstOrNull() as? AlbumItem
            ?: page?.sections?.firstOrNull { it.items.firstOrNull() is AlbumItem }?.items?.firstOrNull() as? AlbumItem
    }

    val onPlay: () -> Unit = {
        val artist = page?.artist
        val endpoint = artist?.shuffleEndpoint ?: artist?.radioEndpoint
        when {
            endpoint != null -> playerConnection.playQueue(YouTubeQueue(endpoint))
            librarySongs.isNotEmpty() -> actions.playSongs(name, librarySongs, shuffle = true)
            topSongsSection != null -> actions.playSongItems(name, topSongsSection.items.filterIsInstance<SongItem>(), shuffle = true)
        }
    }
    val onFollow: () -> Unit = {
        database.transaction {
            val existing = libraryArtist?.artist
            if (existing != null) {
                update(existing.toggleLike())
            } else {
                page?.artist?.let {
                    insert(
                        ArtistEntity(
                            id = it.id,
                            name = it.title,
                            channelId = it.channelId,
                            thumbnailUrl = it.thumbnail,
                        ).toggleLike()
                    )
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(pageColor)
    ) {
        CompositionLocalProvider(LocalLiquidBackdrop provides null) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(pageBackdrop),
                contentPadding = PaddingValues(bottom = liquidBottomPadding()),
            ) {
                item(key = "hero") {
                    ArtistHero(
                        name = name,
                        imageUrl = thumbnail?.resize(1200, 1200),
                        videoUrl = videoUrl,
                        pageColor = pageColor,
                        followed = followed,
                        listeners = page?.monthlyListenerCount ?: page?.subscriberCountText,
                        onInfo = {
                            descriptionExpanded = true
                            scope.launch { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) }
                        },
                        playEnabled = true,
                        onPlay = onPlay,
                        onFollow = onFollow,
                    )
                }

                if (page == null && libraryArtist?.artist?.isLocal != true) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            ActivityIndicator(color = OnColorSecondary)
                        }
                    }
                }

                featured?.let { album ->
                    item(key = "featured") {
                        FeaturedRelease(
                            album = album,
                            onClick = { actions.open(album) },
                            onLongClick = { actions.menu(album) },
                        )
                    }
                }

                topSongsSection?.let { section ->
                    val songs = section.items.filterIsInstance<SongItem>().distinctBy { it.id }
                    item(key = "top_songs_header") {
                        SectionHeader(
                            title = stringResource(R.string.liquid_top_songs),
                            color = OnColor,
                            secondaryColor = OnColorSecondary,
                            onClick = section.moreEndpoint?.let { e ->
                                { navController.navigate("artist/${viewModel.artistId}/items?browseId=${e.browseId}?params=${e.params}") }
                            },
                        )
                    }
                    item(key = "top_songs") {
                        SongGrid(items = songs, key = { "ts_${it.id}" }) { song, index, modifier, last ->
                            SongRow(
                                songId = song.id,
                                title = song.title,
                                subtitle = song.album?.name ?: song.artists.joinToString { it.name },
                                artwork = song.thumbnail,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                explicit = song.explicit,
                                onClick = {
                                    if (song.id == mediaMetadata?.id) actions.togglePlayPause()
                                    else actions.playSongItems(name, songs, startIndex = index)
                                },
                                onLongClick = { actions.menu(song) },
                                onMore = { actions.menu(song) },
                                showSeparator = !last,
                                startPadding = 0.dp,
                                artworkSize = 48.dp,
                                titleColor = OnColor,
                                subtitleColor = OnColorSecondary,
                                separatorColor = OnColorSeparator,
                                accent = OnColor,
                                modifier = modifier,
                            )
                        }
                    }
                }

                page?.sections?.filter { it !== topSongsSection && it.items.isNotEmpty() }?.forEachIndexed { index, section ->
                    item(key = "section_header_${index}_${section.title}") {
                        SectionHeader(
                            title = section.title,
                            color = OnColor,
                            secondaryColor = OnColorSecondary,
                            onClick = section.moreEndpoint?.let { e ->
                                { navController.navigate("artist/${viewModel.artistId}/items?browseId=${e.browseId}?params=${e.params}") }
                            },
                        )
                    }
                    item(key = "section_${index}_${section.title}") {
                        Shelf(items = section.items.distinctBy { it.id }, key = { "s${index}_${it.id}" }) { item ->
                            MediaTile(
                                songId = (item as? com.music.innertube.models.SongItem)?.id,
                                title = item.title,
                                subtitle = item.subtitleText()?.takeIf { item !is SongItem } ?: (item as? SongItem)?.album?.name,
                                artwork = item.thumbnail?.resize(544, 544),
                                circular = item is ArtistItem,
                                width = if (item is ArtistItem) 140.dp else 164.dp,
                                onClick = { actions.open(item) },
                                onLongClick = { actions.menu(item) },
                                titleColor = OnColor,
                                subtitleColor = OnColorSecondary,
                            )
                        }
                    }
                }

                if (librarySongs.isNotEmpty()) {
                    item(key = "library_header") {
                        SectionHeader(
                            title = stringResource(R.string.liquid_library),
                            color = OnColor,
                            secondaryColor = OnColorSecondary,
                            onClick = { navController.navigate("artist/${viewModel.artistId}/songs") },
                        )
                    }
                    items(librarySongs.take(6), key = { "lib_${it.id}" }) { song ->
                        SongRow(
                            songId = song.id,
                            title = song.title,
                            subtitle = song.album?.title ?: song.artists.joinToString { it.name },
                            artwork = song.thumbnailUrl,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            explicit = song.song.explicit,
                            onClick = { actions.playSongs(name, librarySongs, startIndex = librarySongs.indexOf(song)) },
                            onLongClick = { actions.menu(song) },
                            onMore = { actions.menu(song) },
                            titleColor = OnColor,
                            subtitleColor = OnColorSecondary,
                            separatorColor = OnColorSeparator,
                            accent = OnColor,
                        )
                    }
                }

                if (libraryAlbums.isNotEmpty()) {
                    item(key = "library_albums_header") {
                        SectionHeader(
                            title = stringResource(R.string.albums),
                            color = OnColor,
                            secondaryColor = OnColorSecondary,
                            onClick = { navController.navigate("artist/${viewModel.artistId}/albums") },
                        )
                    }
                    item(key = "library_albums") {
                        Shelf(items = libraryAlbums, key = { "la_${it.id}" }) { album ->
                            MediaTile(
                                title = album.title,
                                subtitle = album.album.year?.toString(),
                                artwork = album.thumbnailUrl,
                                onClick = { navController.navigate("album/${album.id}") },
                                onLongClick = { actions.menu(album) },
                                titleColor = OnColor,
                                subtitleColor = OnColorSecondary,
                            )
                        }
                    }
                }

                val about = page?.description
                if (!about.isNullOrBlank()) {
                    item(key = "about") {
                        Column(Modifier.padding(horizontal = PageMargin)) {
                            SectionHeader(
                                title = stringResource(R.string.liquid_about),
                                color = OnColor,
                                secondaryColor = OnColorSecondary,
                                modifier = Modifier.padding(horizontal = 0.dp),
                            )
                            val interaction = remember { MutableInteractionSource() }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .combinedClickable(interactionSource = interaction, indication = null) {
                                        descriptionExpanded = !descriptionExpanded
                                    }
                                    .padding(16.dp),
                            ) {
                                Text(
                                    text = about,
                                    style = LiquidTypography.subheadline,
                                    color = OnColor.copy(alpha = 0.9f),
                                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                page.subscriberCountText?.let {
                                    Spacer(Modifier.height(10.dp))
                                    Text("$it · ${stringResource(R.string.subscribers)}", style = LiquidTypography.footnote, color = OnColorSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // The top bar floats over the page; its buttons refract the page beneath them.
        val heroPx = with(density) { 360.dp.toPx() }
        DetailTopBar(
            backdrop = pageBackdrop,
            edgeBackdrop = pageBackdrop,
            edgeBackground = pageColor,
            title = name,
            onBack = { navController.navigateUp() },
            contentColor = OnColor,
            titleAlpha = {
                if (listState.firstVisibleItemIndex > 0) 1f
                else ((listState.firstVisibleItemScrollOffset - heroPx * 0.72f) / (heroPx * 0.18f)).coerceIn(0f, 1f)
            },
            actions = {
                GlassIconButton(
                    icon = Icons.Rounded.IosShare,
                    onClick = {
                        page?.artist?.shareLink?.let { link ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Artist Link", link))
                            Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                        }
                    },
                    tint = OnColor,
                    iconSize = 19.dp,
                    kind = GlassKind.Clear,
                )
                GlassIconButton(
                    icon = Icons.Rounded.MoreHoriz,
                    onClick = {
                        page?.artist?.let { artist ->
                            menuState.show { YouTubeArtistMenu(artist = artist, onDismiss = menuState::dismiss) }
                        }
                    },
                    tint = OnColor,
                    kind = GlassKind.Clear,
                )
            },
        )
    }
}

@Composable
private fun ArtistHero(
    name: String,
    imageUrl: String?,
    videoUrl: String?,
    pageColor: Color,
    followed: Boolean,
    listeners: String?,
    playEnabled: Boolean,
    onInfo: () -> Unit,
    onPlay: () -> Unit,
    onFollow: () -> Unit,
) {
    val heroBackdrop = rememberLayerBackdrop()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val imageHeight = (maxWidth * 1.08f).coerceAtMost(520.dp)
        Box(Modifier.fillMaxWidth()) {
            // The picture, recorded so the buttons laid over its foot can refract it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .layerBackdrop(heroBackdrop),
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (videoUrl != null) {
                    com.shiny.music.artistvideo.ArtistVideo(
                        videoUrl = videoUrl,
                        modifier = Modifier.fillMaxSize(),
                        onClick = {},
                    )
                }
                // Melt into the page colour: nothing at the top, fully the page by the foot.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.18f),
                                0.22f to Color.Transparent,
                                0.52f to pageColor.copy(alpha = 0.0f),
                                0.8f to pageColor.copy(alpha = 0.78f),
                                1f to pageColor,
                            )
                        )
                )
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin)
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = name,
                    style = LiquidTypography.largeTitle.copy(fontSize = androidx.compose.ui.unit.TextUnit(38f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(44f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    color = OnColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!listeners.isNullOrBlank()) {
                    Text(
                        text = listeners,
                        style = LiquidTypography.footnote.copy(fontWeight = FontWeight.Medium),
                        color = OnColorSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                CompositionLocalProvider(LocalLiquidBackdrop provides heroBackdrop) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassIconButton(
                            icon = Icons.Rounded.Info,
                            onClick = onInfo,
                            size = 48.dp,
                            iconSize = 22.dp,
                            kind = GlassKind.Clear,
                            tint = OnColor,
                        )
                        HeroPlayButton(enabled = playEnabled, onClick = onPlay)
                        GlassIconButton(
                            icon = if (followed) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            onClick = onFollow,
                            size = 48.dp,
                            iconSize = 22.dp,
                            kind = GlassKind.Clear,
                            tint = OnColor,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroPlayButton(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    Row(
        modifier = Modifier
            .height(48.dp)
            .glassPressScale(press, 0.05f)
            .clip(CircleShape)
            .background(Color.White)
            .combinedClickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.liquid_play), style = LiquidTypography.headline, color = Color.Black)
    }
}

/** iOS 27 gives the artist's featured music a box of its own under the header. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedRelease(
    album: AlbumItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .padding(horizontal = PageMargin)
            .padding(top = 18.dp)
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            model = album.thumbnail.resize(544, 544),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(96.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.liquid_latest_release).uppercase(),
                style = LiquidTypography.caption2.copy(fontWeight = FontWeight.Bold, letterSpacing = androidx.compose.ui.unit.TextUnit(0.6f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = OnColorSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = album.title,
                style = LiquidTypography.headline,
                color = OnColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            album.year?.let {
                Text(it.toString(), style = LiquidTypography.footnote, color = OnColorSecondary)
            }
        }
    }
}

