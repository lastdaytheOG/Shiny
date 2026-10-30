package com.shiny.music.ui.menu

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shiny.music.LocalDatabase
import com.shiny.music.LocalIntelligence
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.R
import com.shiny.music.db.entities.PlaylistEntity
import com.shiny.music.db.entities.RecSurface
import com.shiny.music.intelligence.FeedbackKind
import com.shiny.music.models.MediaMetadata
import com.shiny.music.playback.queues.PlaybackOrigin
import com.shiny.music.playback.queues.YouTubeQueue
import com.shiny.music.ui.component.Material3MenuGroup
import com.shiny.music.ui.component.Material3MenuItemData
import com.shiny.music.ui.component.MenuGlyphs
import java.time.LocalDateTime

const val SAVED_FOR_LATER_PLAYLIST_ID = "LP_SAVED_FOR_LATER"

data class FeedbackTarget(
    val songId: String,
    val title: String,
    val artistId: String?,
    val artistName: String?,
    val metadata: MediaMetadata?,
)

fun recommendationsRow(open: () -> Unit) = Material3MenuItemData(
    title = { Text(stringResource(R.string.feedback_title)) },
    description = { Text(stringResource(R.string.feedback_row_body)) },
    icon = { Icon(Icons.Rounded.AutoAwesome, null) },
    chevron = true,
    onClick = open,
)

@Composable
fun RecommendationFeedbackSheet(target: FeedbackTarget, onDone: () -> Unit) {
    val context = LocalContext.current
    val intelligence = LocalIntelligence.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val artistId = target.artistId
    val artistName = target.artistName

    fun record(kind: FeedbackKind, id: String, label: String?) {
        intelligence.setFeedback(kind, id, label)
        Toast.makeText(context, R.string.feedback_saved, Toast.LENGTH_SHORT).show()
        onDone()
    }

    fun row(title: @Composable () -> String, icon: ImageVector, onClick: () -> Unit) = Material3MenuItemData(
        title = { Text(title()) },
        icon = { Icon(icon, null) },
        onClick = onClick,
    )

    val savedPlaylistName = stringResource(R.string.saved_for_later_playlist)

    Column {
        Material3MenuGroup(
            glyphs = MenuGlyphs.Leading,
            items = buildList {
                add(
                    row({ stringResource(R.string.feedback_explore_more) }, Icons.Rounded.AutoAwesome) {
                        intelligence.setFeedback(FeedbackKind.ExploreMore, target.songId, target.title)
                        target.metadata?.let { metadata ->
                            playerConnection?.playQueue(YouTubeQueue.radio(metadata, PlaybackOrigin(RecSurface.ExploreMore)))
                        }
                        onDone()
                    },
                )
                add(
                    row({ stringResource(R.string.feedback_save_later) }, Icons.Rounded.BookmarkAdd) {
                        intelligence.setFeedback(FeedbackKind.SaveForLater, target.songId, target.title)
                        database.query {
                            val existing = getPlaylistByIdBlocking(SAVED_FOR_LATER_PLAYLIST_ID)
                            if (existing == null) {
                                insert(
                                    PlaylistEntity(
                                        id = SAVED_FOR_LATER_PLAYLIST_ID,
                                        name = savedPlaylistName,
                                        bookmarkedAt = LocalDateTime.now(),
                                    ),
                                )
                            }
                            getPlaylistByIdBlocking(SAVED_FOR_LATER_PLAYLIST_ID)?.let { playlist ->
                                if (playlistDuplicates(SAVED_FOR_LATER_PLAYLIST_ID, listOf(target.songId)).isEmpty()) {
                                    addSongToPlaylist(playlist, listOf(target.songId))
                                }
                            }
                        }
                        Toast.makeText(context, R.string.feedback_saved, Toast.LENGTH_SHORT).show()
                        onDone()
                    },
                )
                if (artistId != null && artistName != null) {
                    add(
                        row({ stringResource(R.string.feedback_more_from_artist, artistName) }, Icons.Rounded.Add) {
                            record(FeedbackKind.MoreFromArtist, artistId, artistName)
                        },
                    )
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Material3MenuGroup(
            glyphs = MenuGlyphs.Leading,
            items = buildList {
                add(
                    row({ stringResource(R.string.feedback_not_interested) }, Icons.Rounded.VisibilityOff) {
                        record(FeedbackKind.NotInterested, target.songId, target.title)
                    },
                )
                if (artistId != null && artistName != null) {
                    add(
                        row({ stringResource(R.string.feedback_less_like_this) }, Icons.Rounded.ThumbDown) {
                            record(FeedbackKind.LessLikeThis, artistId, artistName)
                        },
                    )
                    add(
                        row({ stringResource(R.string.feedback_hide_artist, artistName) }, Icons.Rounded.PersonOff) {
                            record(FeedbackKind.HideArtist, artistId, artistName)
                        },
                    )
                }
                add(
                    row({ stringResource(R.string.feedback_block_song) }, Icons.Rounded.Block) {
                        record(FeedbackKind.BlockSong, target.songId, target.title)
                    },
                )
            },
        )
    }
}
