

package com.shiny.music.ui.component

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.shiny.music.LocalDatabase
import com.shiny.music.LocalDownloadUtil
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.R
import com.shiny.music.constants.CropAlbumArtKey
import com.shiny.music.constants.GridItemSize
import com.shiny.music.constants.GridItemsSizeKey
import com.shiny.music.constants.GridThumbnailHeight
import com.shiny.music.constants.ListItemHeight
import com.shiny.music.constants.ListThumbnailSize
import com.shiny.music.constants.SmallGridThumbnailHeight
import com.shiny.music.constants.SwipeToSongKey
import com.shiny.music.db.entities.Album
import com.shiny.music.db.entities.Artist
import com.shiny.music.db.entities.Playlist
import com.shiny.music.db.entities.Song
import com.shiny.music.extensions.toMediaItem
import com.shiny.music.models.MediaMetadata
import com.shiny.music.playback.queues.LocalAlbumRadio
import com.shiny.music.ui.theme.ArtworkEdgeAlpha
import com.shiny.music.ui.theme.ArtworkShape
import com.shiny.music.ui.utils.resize
import com.shiny.music.utils.isLocalMediaId
import com.shiny.music.utils.joinByBullet
import com.shiny.music.utils.makeTimeString
import com.shiny.music.utils.rememberEnumPreference
import com.shiny.music.utils.rememberPreference
import com.shiny.music.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

const val ActiveBoxAlpha = 0.6f

@Composable
fun currentGridThumbnailHeight(): Dp {
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    return if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight
}


/**
 * How a [ListItem] relates to the page it sits on.
 *
 * [Card] is the original treatment and stays the default: every row paints a
 * `surfaceContainer` fill, callers group consecutive rows into one panel with
 * `listItemShape`, and the artwork is inset 12dp inside that panel. It is what every
 * screen outside Library still uses.
 *
 * [Open] is the treatment Library was rebuilt on. A collection of music is not a
 * collection of cards: the covers are the subject, so the fill comes off entirely and the
 * rows are separated by space instead of by edges. Three things follow from removing the
 * fill, and all three are the point rather than side effects:
 *
 * - **The artwork moves to the page keyline.** The 12dp inset existed to hold the cover
 *   off the edge of its panel; with no panel it just pushed every cover 28dp in, out of
 *   line with the 16dp keyline every heading and shelf in the app sits on.
 * - **The active row loses its slab.** A filled `secondaryContainer` row was the only way
 *   to mark "playing" against other filled rows. Against open rows the accent can go on
 *   the title, which is quieter and says the same thing.
 * - **Rows breathe.** 2dp of separation reads as a seam between two panels; 4dp reads as
 *   space between two songs.
 */
enum class ListItemStyle { Card, Open }

@Composable
inline fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    noinline subtitle: (@Composable RowScope.() -> Unit)? = null,
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    isAvailable: Boolean = true,
    shape: Shape = RectangleShape,
    drawHighlight: Boolean = true,
    horizontalPadding: Dp = 16.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    style: ListItemStyle = ListItemStyle.Card,
) {
    val open = style == ListItemStyle.Open
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = if (open) 4.dp else 2.dp)
            .padding(horizontal = horizontalPadding)
            .clip(if (open) RectangleShape else shape)
            .background(
                color = when {
                    // An open row marks "playing" on the title, not with a slab behind it.
                    open -> if (isSelected == true && drawHighlight) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    } else {
                        Color.Transparent
                    }
                    isActive -> MaterialTheme.colorScheme.secondaryContainer
                    isSelected == true && drawHighlight -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else -> color
                }
            )
            .then(modifier)
            .height(ListItemHeight)
    ) {
        Box(
            modifier = Modifier.padding(
                start = if (open) 0.dp else 12.dp,
                top = 6.dp,
                end = if (open) 12.dp else 6.dp,
                bottom = 6.dp,
            ),
            contentAlignment = Alignment.Center
        ) {
            thumbnailContent()
            if (!isAvailable) {
                Box(
                    modifier = Modifier
                        .size(ListThumbnailSize)
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.25f), ArtworkShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.offline),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(ListThumbnailSize / 2)
                            .align(Alignment.Center)
                            .graphicsLayer { alpha = 1f }
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                // The whole of the playing state on an open row, together with the
                // indicator already drawn over the cover. Accent on the one row you would
                // tap to resume is the quietest signal that still reads at a glance.
                color = if (open && isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Unspecified
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    subtitle()
                }
            }
        }

        trailingContent()
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: AnnotatedString?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    shape: Shape = RectangleShape,
    drawHighlight: Boolean = true,
    horizontalPadding: Dp = 16.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    style: ListItemStyle = ListItemStyle.Card,
) = ListItem(
    title = title,
    subtitle = {
        badges()
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent,
    modifier = modifier,
    isSelected = isSelected,
    isActive = isActive,
    shape = shape,
    drawHighlight = drawHighlight,
    horizontalPadding = horizontalPadding,
    color = color,
    style = style,
)


@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    shape: Shape = RectangleShape,
    drawHighlight: Boolean = true,
    horizontalPadding: Dp = 16.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    style: ListItemStyle = ListItemStyle.Card,
) = ListItem(
    title = title,
    subtitle = {
        badges()

        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent,
    modifier = modifier,
    isSelected = isSelected,
    isActive = isActive,
    shape = shape,
    drawHighlight = drawHighlight,
    horizontalPadding = horizontalPadding,
    color = color,
    style = style,
)

/**
 * A tile in a horizontal shelf owns half of the 12dp gap to its neighbour, so the gap
 * comes out even whichever lazy container lays the tiles out.
 */
internal val ShelfTileHalfGutter = 6.dp
internal val ShelfTileVerticalPadding = 8.dp

/** The 16dp page keyline, less the half-gutter the first tile already brings. */
private val ShelfEdgePadding = 16.dp - ShelfTileHalfGutter

internal val TileArtworkToText = 8.dp

/**
 * Horizontal content padding for a `LazyRow` or `LazyHorizontalGrid` of [GridItem]s.
 *
 * With the tile's own half-gutter this lands the first cover on the same 16dp keyline as
 * the section title above it, so a shelf starts exactly under its heading.
 */
@Composable
fun shelfContentPadding(): PaddingValues =
    WindowInsets.systemBars
        .only(WindowInsetsSides.Horizontal)
        .add(WindowInsets(left = ShelfEdgePadding, right = ShelfEdgePadding))
        .asPaddingValues()

/**
 * The full height of one shelf tile, for containers that have to size a
 * `LazyHorizontalGrid` up front. Exact because the tile's text block is always exactly
 * one title line and one subtitle line.
 */
@Composable
fun shelfTileHeight(): Dp {
    val typography = MaterialTheme.typography
    val textBlock = with(LocalDensity.current) {
        typography.titleSmall.lineHeight.toDp() + typography.bodyMedium.lineHeight.toDp()
    }
    return currentGridThumbnailHeight() + TileArtworkToText + textBlock + ShelfTileVerticalPadding * 2
}

/**
 * An artwork tile — the unit every shelf and grid in the app is built from.
 *
 * The cover is the subject, so everything else is held still and quiet: one title line
 * and one subtitle line, both ellipsized rather than marqueed, and no controls stamped on
 * the artwork. The subtitle line is reserved even when there is nothing to put in it, so
 * every tile in a shelf is the same height and the covers and titles form two clean
 * lines across the row.
 *
 * A fixed-width tile belongs to a horizontal shelf and is padded for
 * [shelfContentPadding]. A [fillMaxWidth] tile fills a vertical-grid cell and keeps the
 * 12dp margin those grids were laid out around.
 */
@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
) {
    val gridHeight = currentGridThumbnailHeight()
    val subtitleLineHeight = with(LocalDensity.current) {
        MaterialTheme.typography.bodyMedium.lineHeight.toDp()
    }
    Column(
        modifier = if (fillMaxWidth) {
            modifier
                .padding(12.dp)
                .fillMaxWidth()
        } else {
            modifier
                .padding(horizontal = ShelfTileHalfGutter, vertical = ShelfTileVerticalPadding)
                .width(gridHeight * thumbnailRatio)
        }
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.height(gridHeight)
            }
                .aspectRatio(thumbnailRatio)
        ) {
            thumbnailContent()
        }

        Spacer(modifier = Modifier.height(TileArtworkToText))

        title()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = subtitleLineHeight)
        ) {
            badges()

            subtitle()
        }
    }
}

@Composable
private fun TileTitle(
    text: String,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TileSubtitle(text: String?) {
    if (text.isNullOrEmpty()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
) = GridItem(
    modifier = modifier,
    title = { TileTitle(title) },
    subtitle = { TileSubtitle(subtitle) },
    thumbnailContent = thumbnailContent,
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth
)

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    showSize: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {        if (showLikedIcon && song.song.liked) {
            Icon.Favorite()
        }
        if (song.song.explicit) {
            Icon.Explicit()
        }
        if (showInLibraryIcon && song.song.inLibrary != null) {
            Icon.Library()
        }
        if (showDownloadIcon) {
            val download by LocalDownloadUtil.current.getDownload(song.id)
                .collectAsState(initial = null)
            Icon.Download(download?.state)
        }
    },
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    onSelectionChange: (Boolean) -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
    drawHighlight: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    horizontalPadding: Dp = 16.dp,
    style: ListItemStyle = ListItemStyle.Card,
) {
    val swipeEnabled by rememberPreference(SwipeToSongKey, defaultValue = true)

    val content: @Composable () -> Unit = {
        ListItem(
            title = song.song.title,
            subtitle = joinByBullet(
                song.artists.joinToString { it.name },
                makeTimeString(song.song.duration * 1000L),
                if (showSize && song.format?.contentLength != null) {
                    android.text.format.Formatter.formatFileSize(LocalContext.current, song.format!!.contentLength)
                } else null
            ),
            badges = badges,
            thumbnailContent = {
                ItemThumbnail(
                    thumbnailUrl = song.thumbnailUrl,
                    albumIndex = albumIndex,
                    isSelected = isSelected,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = ArtworkShape,
                    modifier = Modifier.size(ListThumbnailSize)
                )
            },
            trailingContent = trailingContent,
            modifier = modifier,
            isSelected = isSelected,
            isActive = isActive,
            shape = shape,
            color = color,
            drawHighlight = drawHighlight,
            horizontalPadding = horizontalPadding,
            style = style,
        )
    }

    if (isSwipeable && swipeEnabled) {
        SwipeToSongBox(
            mediaItem = song.toMediaItem(),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun SongGridItem(
    song: Song,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && song.song.liked) {
            Icon.Favorite()
        }
        if (showInLibraryIcon && song.song.inLibrary != null) {
            Icon.Library()
        }
        if (showDownloadIcon) {
            val download by LocalDownloadUtil.current.getDownload(song.id).collectAsState(initial = null)
            Icon.Download(download?.state)
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = { TileTitle(song.song.title) },
    // Artists only: a duration under a cover is list-row metadata, not something anyone
    // picks a tile by.
    subtitle = { TileSubtitle(song.artists.joinToString { it.name }) },
    badges = badges,
    thumbnailContent = {
        val gridHeight = currentGridThumbnailHeight()
        ItemThumbnail(
            thumbnailUrl = song.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = ArtworkShape,
            modifier = Modifier.size(gridHeight)
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun ArtistListItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: Shape = RectangleShape,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp),
            )
        }
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
    style: ListItemStyle = ListItemStyle.Card,
) = ListItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artist.artist.thumbnailUrl?.resize(544, 544))
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            // Crop, as [ArtistGridItem] already does. Without it a press shot wider than
            // it is tall fits inside the square and the circle clips it to a lens — the
            // pill-shaped artist badge this row was known for. A portrait should read as
            // a photograph of a person, so it fills the circle and is cropped to it.
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(CircleShape)
                .artworkEdge(CircleShape, artworkEdgeColor()),
        )
    },
    trailingContent = trailingContent,
    shape = shape,
    color = color,
    modifier = modifier,
    style = style,
)

@Composable
fun ArtistGridItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon.Favorite()
        }
    },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artist.artist.thumbnailUrl?.resize(544, 544))
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .artworkEdge(CircleShape, artworkEdgeColor())
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun AlbumListItem(
    album: Album,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: Shape = RectangleShape,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val downloadUtil = LocalDownloadUtil.current
        val database = LocalDatabase.current

        val songs by produceState<List<Song>>(initialValue = emptyList(), album.id) {
            withContext(Dispatchers.IO) {
                value = database.albumSongs(album.id).first()
            }
        }

        val allDownloads by downloadUtil.downloads.collectAsState()

        val downloadState by remember(songs, allDownloads) {
            androidx.compose.runtime.mutableIntStateOf(
                if (songs.isEmpty()) {
                    Download.STATE_STOPPED
                } else {
                    when {
                        songs.all { allDownloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED
                        songs.any { allDownloads[it.id]?.state in listOf(STATE_QUEUED, STATE_DOWNLOADING) } -> STATE_DOWNLOADING
                        else -> Download.STATE_STOPPED
                    }
                }
            )
        }

        if (showLikedIcon && album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }
        if (album.album.explicit) {
            Icon.Explicit()
        }
        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
    style: ListItemStyle = ListItemStyle.Card,
) = ListItem(
    title = album.album.title,
    subtitle = joinByBullet(
        album.artists.joinToString { it.name },
        pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount),
        album.album.year?.toString()
    ),
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = ArtworkShape,
            modifier = Modifier.size(ListThumbnailSize)
        )
    },
    trailingContent = trailingContent,
    shape = shape,
    color = color,
    modifier = modifier,
    style = style,
)

@Composable
fun AlbumGridItem(
    album: Album,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val downloadUtil = LocalDownloadUtil.current
        val database = LocalDatabase.current

        val songs by produceState<List<Song>>(initialValue = emptyList(), album.id) {
            withContext(Dispatchers.IO) {
                value = database.albumSongs(album.id).first()
            }
        }

        val allDownloads by downloadUtil.downloads.collectAsState()

        val downloadState by remember(songs, allDownloads) {
            androidx.compose.runtime.mutableIntStateOf(
                if (songs.isEmpty()) {
                    Download.STATE_STOPPED
                } else {
                    when {
                        songs.all { allDownloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED
                        songs.any { allDownloads[it.id]?.state in listOf(STATE_QUEUED, STATE_DOWNLOADING) } -> STATE_DOWNLOADING
                        else -> Download.STATE_STOPPED
                    }
                }
            )
        }

        if (showLikedIcon && album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }
        if (album.album.explicit) {
            Icon.Explicit()
        }
        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    // Off in Library, where the grid *is* the collection: a disc stamped on every one of
    // forty covers stops reading as a control and starts reading as a watermark. Home
    // shows a handful of albums among other things, so there it still earns its place.
    showPlayButton: Boolean = true,
) = GridItem(
    title = { TileTitle(album.album.title) },
    subtitle = { TileSubtitle(album.artists.joinToString { it.name }) },
    badges = badges,
    thumbnailContent = {
        val database = LocalDatabase.current
        val playerConnection = LocalPlayerConnection.current ?: return@GridItem
        val scope = rememberCoroutineScope()

        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = ArtworkShape,
        )

        AlbumPlayButton(
            visible = showPlayButton && !isActive,
            onClick = {
                scope.launch {
                    val albumWithSongs = withContext(Dispatchers.IO) {
                        database.albumWithSongs(album.id).firstOrNull()
                    }
                    albumWithSongs?.let {
                        playerConnection.playQueue(LocalAlbumRadio(it))
                    }
                }
            }
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    autoPlaylist: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {
        val downloadUtil = LocalDownloadUtil.current
        val database = LocalDatabase.current

        val songs by produceState<List<Song>>(initialValue = emptyList(), playlist.id) {
            withContext(Dispatchers.IO) {
                value = database.playlistSongs(playlist.id).first().map { it.song }
            }
        }

        val allDownloads by downloadUtil.downloads.collectAsState()

        val downloadState by remember(songs, allDownloads) {
            androidx.compose.runtime.mutableIntStateOf(
                if (songs.isEmpty()) {
                    Download.STATE_STOPPED
                } else {
                    when {
                        songs.all { allDownloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED
                        songs.any { allDownloads[it.id]?.state in listOf(STATE_QUEUED, STATE_DOWNLOADING) } -> STATE_DOWNLOADING
                        else -> Download.STATE_STOPPED
                    }
                }
            )
        }

        if (playlist.playlist.isPinned) {
            Icon(
                painter = painterResource(R.drawable.ic_push_pin),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp).padding(end = 2.dp)
            )
        }
        Icon.Download(downloadState)
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
    shape: Shape = androidx.compose.ui.graphics.RectangleShape,
    style: ListItemStyle = ListItemStyle.Card,
) = ListItem(
    title = playlist.playlist.name,
    subtitle = if (autoPlaylist) {
        ""
    } else {
        val remoteCount = playlist.playlist.remoteSongCount
        if (playlist.songCount == 0 && remoteCount != null) {
            pluralStringResource(
                R.plurals.n_song,
                remoteCount,
                remoteCount
            )
        } else {
            pluralStringResource(
                R.plurals.n_song,
                playlist.songCount,
                playlist.songCount
            )
        }
    },
    badges = badges,
    thumbnailContent = {
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = ListThumbnailSize,
            placeHolder = {
                val painter = when (playlist.playlist.name) {
                    stringResource(R.string.liked) -> R.drawable.favorite_border
                    stringResource(R.string.offline) -> R.drawable.offline
                    stringResource(R.string.cached_playlist) -> R.drawable.cached
                    
                    stringResource(R.string.uploaded_playlist) -> R.drawable.backup
                    else -> if (autoPlaylist) R.drawable.trending_up else R.drawable.ic_launcher_nobg
                }
                Icon(
                    painter = painterResource(painter),
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = 0.8f),
                    modifier = Modifier.size(ListThumbnailSize / 2)
                )
            },
            shape = ArtworkShape
        )
    },
    trailingContent = trailingContent,
    modifier = modifier,
    shape = shape,
    color = color,
    style = style,
)

@Composable
fun PlaylistGridItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {
        val downloadUtil = LocalDownloadUtil.current
        val database = LocalDatabase.current

        val songs by produceState<List<Song>>(initialValue = emptyList(), playlist.id) {
            withContext(Dispatchers.IO) {
                value = database.playlistSongs(playlist.id).first().map { it.song }
            }
        }

        val allDownloads by downloadUtil.downloads.collectAsState()

        val downloadState by remember(songs, allDownloads) {
            mutableIntStateOf(
                if (songs.isEmpty()) {
                    Download.STATE_STOPPED
                } else {
                    when {
                        songs.all { allDownloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED
                        songs.any { allDownloads[it.id]?.state in listOf(STATE_QUEUED, STATE_DOWNLOADING) } -> STATE_DOWNLOADING
                        else -> Download.STATE_STOPPED
                    }
                }
            )
        }

        if (playlist.playlist.isPinned) {
            Icon(
                painter = painterResource(R.drawable.ic_push_pin),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp).padding(end = 2.dp)
            )
        }
        Icon.Download(downloadState)
    },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = { TileTitle(playlist.playlist.name) },
    subtitle = {
        val subtitle = if (autoPlaylist) {
            ""
        } else {
            val remoteCount = playlist.playlist.remoteSongCount
            if (playlist.songCount == 0 && remoteCount != null) {
                pluralStringResource(
                    R.plurals.n_song,
                    remoteCount,
                    remoteCount
                )
            } else {
                pluralStringResource(
                    R.plurals.n_song,
                    playlist.songCount,
                    playlist.songCount
                )
            }
        }
        TileSubtitle(subtitle)
    },
    badges = badges,
    thumbnailContent = {
        val width = maxWidth
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = width,
            placeHolder = {
                val painter = when (playlist.playlist.name) {
                    stringResource(R.string.liked) -> R.drawable.favorite_border
                    stringResource(R.string.offline) -> R.drawable.offline
                    stringResource(R.string.cached_playlist) -> R.drawable.cached
                    
                    stringResource(R.string.uploaded_playlist) -> R.drawable.backup
                    else -> if (autoPlaylist) R.drawable.trending_up else R.drawable.ic_launcher_nobg
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        painter = painterResource(painter),
                        contentDescription = null,
                        tint = LocalContentColor.current.copy(alpha = 0.8f),
                        modifier = Modifier.size(width / 2)
                    )
                }
            },
            shape = ArtworkShape
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun MediaMetadataListItem(
    mediaMetadata: MediaMetadata,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    ListItem(
        title = mediaMetadata.title,
        subtitle = if (mediaMetadata.suggestedBy != null) {
            buildAnnotatedString {
                append(mediaMetadata.artists.joinToString { it.name })
                append(" • ")
                append(makeTimeString(mediaMetadata.duration * 1000L))
                append(" • ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(mediaMetadata.suggestedBy)
                }
            }
        } else {
            AnnotatedString(
                joinByBullet(
                    mediaMetadata.artists.joinToString { it.name },
                    makeTimeString(mediaMetadata.duration * 1000L)
                )
            )
        },
        badges = {
            if (mediaMetadata.explicit) Icon.Explicit()
        },
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = mediaMetadata.thumbnailUrl,
                albumIndex = null,
                isSelected = isSelected,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = ArtworkShape,
                modifier = Modifier.size(ListThumbnailSize)
            )
        },
        trailingContent = trailingContent,
        modifier = modifier,
        isActive = isActive,
        shape = shape,
        color = color
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {},
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by produceState<Song?>(initialValue = null, item.id) {
            if (item is SongItem) value = database.song(item.id).firstOrNull()
        }
        val album by produceState<Album?>(initialValue = null, item.id) {
            if (item is AlbumItem) value = database.album(item.id).firstOrNull()
        }

        if ((item is SongItem && song?.song?.liked == true) ||
            (item is AlbumItem && album?.album?.bookmarkedAt != null)
        ) {
            Icon.Favorite()
        }
        if (item.explicit) Icon.Explicit()
        
        if (item is SongItem) {
            val download by LocalDownloadUtil.current.getDownload(item.id).collectAsState(null)
            Icon.Download(download?.state)
        }
    },
    shape: Shape = RectangleShape,
    drawHighlight: Boolean = true,
) {
    val swipeEnabled by rememberPreference(SwipeToSongKey, defaultValue = true)

    val content: @Composable () -> Unit = {
        ListItem(
            title = item.title,
            subtitle = when (item) {
                is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
                is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
                is ArtistItem -> null
                is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
            },
            badges = badges,
            thumbnailContent = {
                ItemThumbnail(
                    thumbnailUrl = item.thumbnail,
                    albumIndex = albumIndex,
                    isSelected = isSelected,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = if (item is ArtistItem) CircleShape else ArtworkShape,
                    modifier = Modifier.size(ListThumbnailSize)
                )
            },
            trailingContent = trailingContent,
            modifier = modifier,
            isSelected = isSelected,
            isActive = isActive,
            shape = shape,
            drawHighlight = drawHighlight
        )
    }

    if (item is SongItem && isSwipeable && swipeEnabled) {
        SwipeToSongBox(
            mediaItem = item.copy(thumbnail = item.thumbnail.resize(544,544)).toMediaItem(),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun YouTubeGridItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope? = null,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by produceState<Song?>(initialValue = null, item.id) {
            if (item is SongItem) value = database.song(item.id).firstOrNull()
        }
        val album by produceState<Album?>(initialValue = null, item.id) {
            if (item is AlbumItem) value = database.album(item.id).firstOrNull()
        }

        if (item is SongItem && song?.song?.liked == true ||
            item is AlbumItem && album?.album?.bookmarkedAt != null
        ) {
            Icon.Favorite()
        }
        if (item.explicit) Icon.Explicit()
        
        if (item is SongItem) {
            val download by LocalDownloadUtil.current.getDownload(item.id).collectAsState(null)
            Icon.Download(download?.state)
        }
    },
    thumbnailRatio: Float = if (item is SongItem) 16f / 9 else 1f,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        TileTitle(
            text = item.title,
            textAlign = if (item is ArtistItem) TextAlign.Center else TextAlign.Start,
        )
    },
    subtitle = {
        TileSubtitle(
            when (item) {
                is SongItem -> item.artists.joinToString { it.name }
                is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
                is ArtistItem -> null
                is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
            }
        )
    },
    badges = badges,
    thumbnailContent = {
        val database = LocalDatabase.current
        val playerConnection = LocalPlayerConnection.current ?: return@GridItem
        val scope = rememberCoroutineScope()

        ItemThumbnail(
            thumbnailUrl = item.thumbnail,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = if (item is ArtistItem) CircleShape else ArtworkShape,
            fillFrame = item is ArtistItem,
        )

        AlbumPlayButton(
            visible = item is AlbumItem && !isActive,
            onClick = {
                scope.launch(Dispatchers.IO) {
                    var albumWithSongs = database.albumWithSongs(item.id).first()
                    if (albumWithSongs?.songs.isNullOrEmpty()) {
                        YouTube.album(item.id).onSuccess { albumPage ->
                            database.transaction { insert(albumPage) }
                            albumWithSongs = database.albumWithSongs(item.id).first()
                        }.onFailure { reportException(it) }
                    }
                    albumWithSongs?.let {
                        withContext(Dispatchers.Main) {
                            playerConnection.playQueue(LocalAlbumRadio(it))
                        }
                    }
                }
            }
        )
    },
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun LocalSongsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = ArtworkShape,
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = false,
            playButtonVisible = false
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun LocalArtistsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = false,
            isPlaying = false,
            shape = CircleShape,
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = false,
            playButtonVisible = false,
            fillFrame = true
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun LocalAlbumsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = ArtworkShape,
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = false,
            playButtonVisible = false
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

/**
 * @param fillFrame always crop the image to the frame, whatever the Crop album art
 * preference says. For portraits: that preference is about album art, and a letterboxed
 * photo inside a circle reads as a broken image rather than an uncropped one.
 */
@Composable
fun ItemThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    thumbnailRatio: Float = 1f,
    fillFrame: Boolean = false,
) {
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    val crop = cropAlbumArt || fillFrame

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .aspectRatio(thumbnailRatio)
            .clip(shape)
    ) {
        if (albumIndex == null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailUrl?.resize(544, 544))
                    .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
                // The edge goes on the image, not the frame: a fitted 16:9 thumbnail is
                // only as tall as itself, and outlining the empty square around it would
                // draw a frame around nothing.
                modifier = Modifier
                    .then(if (crop) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .clip(shape)
                    .artworkEdge(shape, artworkEdgeColor())
            )
        }

        if (albumIndex != null) {
            AnimatedVisibility(
                visible = !isActive,
                enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut()
            ) {
                Text(
                    text = albumIndex.toString(),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .clip(shape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    painter = painterResource(R.drawable.done),
                    contentDescription = null
                )
            }
        }

        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            color = if (albumIndex != null) MaterialTheme.colorScheme.onBackground else Color.White,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (albumIndex != null)
                        Color.Transparent
                    else
                        Color.Black.copy(alpha = ActiveBoxAlpha),
                    shape = shape
                )
        )
    }
}

/** See [ArtworkEdgeAlpha]. */
@Composable
private fun artworkEdgeColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = ArtworkEdgeAlpha)

private fun Modifier.artworkEdge(shape: Shape, color: Color): Modifier =
    border(Dp.Hairline, color, shape)

/** @param fillFrame as on [ItemThumbnail]. */
@Composable
fun LocalThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    showCenterPlay: Boolean = false,
    playButtonVisible: Boolean = false,
    thumbnailRatio: Float = 1f,
    fillFrame: Boolean = false,
) {
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    val crop = cropAlbumArt || fillFrame

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(thumbnailRatio)
            .clip(shape)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(thumbnailUrl)
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
            // Edge on the image, not the frame — see ItemThumbnail.
            modifier = Modifier
                .then(if (crop) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .clip(shape)
                .artworkEdge(shape, artworkEdgeColor())
        )

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(500))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f), shape)
            ) {
                if (isPlaying) {
                    PlayingIndicator(
                        color = Color.White,
                        modifier = Modifier.height(24.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }

        if (showCenterPlay) {
            AnimatedVisibility(
                visible = !(isActive && isPlaying),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }

        if (playButtonVisible) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    placeHolder: @Composable () -> Unit,
    shape: Shape,
    cacheKey: String? = null
) {
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    val edgeColor = artworkEdgeColor()

    when (thumbnails.size) {
        0 -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .artworkEdge(shape, edgeColor)
        ) {
            placeHolder()
        }
        1 -> AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(thumbnails[0].resize(544, 544))
                .apply {  }
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
            placeholder = painterResource(R.drawable.shiny_artwork),
            error = painterResource(R.drawable.shiny_artwork),
            modifier = Modifier
                .size(size)
                .clip(shape)
                .artworkEdge(shape, edgeColor)
        )
        else -> Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .artworkEdge(shape, edgeColor)
        ) {
            listOf(
                Alignment.TopStart,
                Alignment.TopEnd,
                Alignment.BottomStart,
                Alignment.BottomEnd
            ).fastForEachIndexed { index, alignment ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnails.getOrNull(index)?.resize(544, 544))
                        .apply {  }
                        .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                        .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                    placeholder = painterResource(R.drawable.shiny_artwork),
                    error = painterResource(R.drawable.shiny_artwork),
                    modifier = Modifier
                        .align(alignment)
                        .size(size / 2)
                )
            }
        }
    }
}

@Composable
fun BoxScope.OverlayPlayButton(
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.Center)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha))
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun BoxScope.OverlayEditButton(
    visible: Boolean,
    onClick: () -> Unit,
    alignment: Alignment = Alignment.Center,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(alignment)
            .then(if (alignment == Alignment.BottomEnd) Modifier.padding(8.dp) else Modifier)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                .padding(0.dp)
                .clickable(onClick = onClick)
        ) {
            Icon(
                painter = painterResource(R.drawable.edit),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Plays an album without opening it. The one control allowed on a cover, so it is kept
 * small and tucked into the corner: the tile is the album, and the button only has to be
 * findable, not seen first.
 */
@Composable
fun BoxScope.AlbumPlayButton(
    visible: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onClick)
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SwipeToSongBox(
    modifier: Modifier = Modifier,
    mediaItem: MediaItem,
    content: @Composable BoxScope.() -> Unit
) {
    val ctx = LocalContext.current
    val player = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val offset = remember { mutableFloatStateOf(0f) }
    val threshold = 300f

    val dragState = rememberDraggableState { delta ->
        offset.floatValue = (offset.floatValue + delta).coerceIn(-threshold, threshold)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .draggable(
                orientation = Orientation.Horizontal,
                state = dragState,
                onDragStopped = {
                    when {
                        offset.floatValue >= threshold -> {
                            player?.playNext(listOf(mediaItem))
                            Toast.makeText(ctx, R.string.play_next, Toast.LENGTH_SHORT).show()
                            reset(offset, scope)
                        }

                        offset.floatValue <= -threshold -> {
                            player?.addToQueue(listOf(mediaItem))
                            Toast.makeText(ctx, R.string.add_to_queue, Toast.LENGTH_SHORT).show()
                            reset(offset, scope)
                        }

                        else -> reset(offset, scope)
                    }
                }
            )
    ) {
        if (offset.floatValue != 0f) {
            val (iconRes, bg, tint, align) = if (offset.floatValue > 0)
                Quadruple(
                    R.drawable.playlist_play,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.onSecondary,
                    Alignment.CenterStart
                ) else
                Quadruple(
                    R.drawable.ic_launcher_nobg,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimary,
                    Alignment.CenterEnd
                )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.Center)
                    .background(bg),
                contentAlignment = align
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .size(30.dp)
                        .alpha(0.9f),
                    tint = tint
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.floatValue.roundToInt(), 0) }
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            content = content
        )
    }
}


private fun reset(offset: MutableState<Float>, scope: CoroutineScope) {
    scope.launch {
        animate(
            initialValue = offset.value,
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300)
        ) { value, _ -> offset.value = value }
    }
}


data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

object Icon {
    /**
     * Badges are metadata, so they are tinted like the subtitle they sit in rather than
     * like something that can be tapped.
     *
     * The liked badge in particular used to be `error` red — the loudest pixel in a row,
     * on a saturated hue the palette reserves for things going wrong, spent on "you like
     * this". Three of these in a row (liked, explicit, in-library) turned every song's
     * metadata line into a warning strip before the artist's name.
     */
    @Composable
    fun Favorite() {
        Icon(
            painter = painterResource(R.drawable.favorite),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 2.dp)
        )
    }

    @Composable
    fun Library() {
        Icon(
            painter = painterResource(R.drawable.library_add_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 2.dp)
        )
    }

    @Composable
    fun Download(state: Int?) {
        when (state) {
            STATE_COMPLETED -> Icon(
                painter = painterResource(R.drawable.offline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
            STATE_QUEUED, STATE_DOWNLOADING -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 2.dp)
            )
            else -> {  }
        }
    }

    @Composable
    fun Explicit() {
        Icon(
            painter = painterResource(R.drawable.explicit),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 2.dp)
        )
    }

}



