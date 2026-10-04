

package com.shiny.music.ui.menu

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shiny.music.LocalDatabase
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.R
import com.shiny.music.constants.ArtistSongSortType
import com.shiny.music.db.entities.SpeedDialItem
import com.shiny.music.db.entities.Artist
import com.shiny.music.extensions.toMediaItem
import com.shiny.music.playback.queues.ListQueue
import com.shiny.music.ui.component.ArtistListItem
import com.shiny.music.ui.component.Material3MenuGroup
import com.shiny.music.ui.component.Material3MenuItemData
import com.shiny.music.ui.component.NewAction
import com.shiny.music.ui.component.NewActionGrid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ArtistMenu(
    originalArtist: Artist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val artistState = database.artist(originalArtist.id).collectAsState(initial = originalArtist)
    val artist = artistState.value ?: originalArtist
    val isPinned by database.speedDialDao.isPinned(artist.id).collectAsState(initial = false)

    ArtistListItem(
        artist = artist,
        shape = MaterialTheme.shapes.large,
        color = androidx.compose.ui.graphics.Color.Transparent,
        badges = {},
        trailingContent = {},
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = buildList {
                    if (artist.songCount > 0) {
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = stringResource(R.string.play),
                                onClick = {
                                    coroutineScope.launch {
                                        val songs = withContext(Dispatchers.IO) {
                                            database
                                                .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                                                .first()
                                                .map { it.toMediaItem() }
                                        }
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = artist.artist.name,
                                                items = songs,
                                            ),
                                        )
                                    }
                                    onDismiss()
                                }
                            )
                        )

                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = stringResource(R.string.shuffle),
                                onClick = {
                                    coroutineScope.launch {
                                        val songs = withContext(Dispatchers.IO) {
                                            database
                                                .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                                                .first()
                                                .map { it.toMediaItem() }
                                                .shuffled()
                                        }
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = artist.artist.name,
                                                items = songs,
                                            ),
                                        )
                                    }
                                    onDismiss()
                                }
                            )
                        )
                    }
                

                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(if (isPinned) R.drawable.remove else R.drawable.add),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = if (isPinned) "Unpin" else "Pin",
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    if (isPinned) {
                                        database.speedDialDao.delete(artist.id)
                                    } else {
                                        database.speedDialDao.insert(
                                            SpeedDialItem(
                                                id = artist.id,
                                                title = artist.artist.name,
                                                subtitle = null,
                                                thumbnailUrl = artist.artist.thumbnailUrl,
                                                type = "ARTIST"
                                            )
                                        )
                                    }
                                }
                                onDismiss()
                            }
                        )
                    )

                    if (artist.artist.isYouTubeArtist) {
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.share),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = stringResource(R.string.share),
                                onClick = {
                                    onDismiss()
                                val intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "${com.shiny.music.social.ShinyLinks.WEB_BASE}/channel/${artist.id}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(intent, null))
                                }
                            )
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                columns = 3
            )
        }

        item {
            Material3MenuGroup(
                items = listOf(
                    Material3MenuItemData(
                        title = {
                            Text(text = if (artist.artist.bookmarkedAt != null) stringResource(R.string.subscribed) else stringResource(R.string.subscribe))
                        },
                        icon = {
                            Icon(
                                painter = painterResource(if (artist.artist.bookmarkedAt != null) R.drawable.subscribed else R.drawable.subscribe),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            database.transaction {
                                update(artist.artist.toggleLike())
                            }
                        }
                    )
                )
            )
        }
    }
}
