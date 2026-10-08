

package com.shiny.music.ui.menu

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.shiny.music.LocalDatabase
import com.shiny.music.LocalDownloadUtil
import com.shiny.music.ui.liquid.together.LocalTogether
import com.shiny.music.ui.liquid.share.shareSong
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.R
import com.shiny.music.constants.ListItemHeight
import com.shiny.music.models.MediaMetadata
import com.shiny.music.playback.ExoDownloadService
import com.shiny.music.ui.component.BottomSheetState
import com.shiny.music.ui.component.ListDialog
import com.shiny.music.ui.component.Material3MenuGroup
import com.shiny.music.ui.component.Material3MenuItemData
import com.shiny.music.ui.component.MenuGlyphs
import com.shiny.music.ui.component.MenuGroupGap
import com.shiny.music.ui.component.MenuHeader
import com.shiny.music.ui.component.MenuHeaderArtwork
import com.shiny.music.ui.component.NewAction
import com.shiny.music.ui.component.NewActionGrid
import com.shiny.music.ui.component.LocalBottomSheetPageState
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidIcons
import com.shiny.music.ui.liquid.artworkRadius
import com.shiny.music.ui.utils.ShowMediaInfo
import com.shiny.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.log2
import kotlin.math.round

@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    isQueueTrigger: Boolean? = false,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val bottomSheetPageState = LocalBottomSheetPageState.current
    
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    var systemVolume by remember { androidx.compose.runtime.mutableFloatStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()) }
    var maxSystemVolume by remember { androidx.compose.runtime.mutableFloatStateOf(audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()) }

    androidx.compose.runtime.DisposableEffect(context) {
        val volumeChangeReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (streamType == android.media.AudioManager.STREAM_MUSIC) {
                        systemVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
                        maxSystemVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
                    }
                }
            }
        }
        context.registerReceiver(
            volumeChangeReceiver,
            android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        )
        onDispose {
            context.unregisterReceiver(volumeChangeReceiver)
        }
    }
    
    
    val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    val download by LocalDownloadUtil.current.getDownload(mediaMetadata.id)
        .collectAsState(initial = null)



    val artists =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.filter { it.id != null }
        }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    
    

    
    val together = LocalTogether.current
    val togetherState by (together?.state?.collectAsState() ?: remember { mutableStateOf(com.shiny.music.together.TogetherSession.State()) })
    // Same guard as the player: a Listen Together guest without control can't change playback.
    val controlsEnabled = !(togetherState.isGuest && !togetherState.canControl && !togetherState.following)
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val repeatOne = repeatMode == Player.REPEAT_MODE_ONE

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            database.transaction {
                insert(mediaMetadata)
            }
            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { YouTube.addToPlaylist(it, mediaMetadata.id) }
            }
            listOf(mediaMetadata.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        }
    )


    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(artists) { artist ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier =
                    Modifier
                        .fillParentMaxWidth()
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            playerBottomSheetState.collapseSoft()
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    var showPitchTempoDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showPitchTempoDialog) {
        TempoPitchDialog(
            onDismiss = { showPitchTempoDialog = false },
        )
    }



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
            MenuHeader(
                title = mediaMetadata.title,
                subtitle = mediaMetadata.artists.joinToString { it.name },
                detail = mediaMetadata.album?.title,
                explicit = mediaMetadata.explicit,
                atmosphere = mediaMetadata.thumbnailUrl,
            ) {
                Artwork(
                    model = mediaMetadata.thumbnailUrl,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(artworkRadius(MenuHeaderArtwork)),
                )
            }
        }

        item {
            NewActionGrid(
                actions = listOf(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        text = stringResource(R.string.add_to_an_playlist),
                        onClick = { showChoosePlaylistDialog = true }
                    ),
                    // Repeat this song: one tap toggles Repeat One on and off. The menu stays
                    // open so the tile's lit state confirms the change.
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.repeat_one),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        text = stringResource(R.string.repeat),
                        onClick = {
                            playerConnection.player.repeatMode =
                                if (repeatOne) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                        },
                        enabled = controlsEnabled,
                        // Lit, the tile is the accent seen through the same glass as its neighbours,
                        // brighter than them (a solid container read as a darker hole), and its
                        // glyph and label stay white: accent type on an accent tile was under 3:1.
                        backgroundColor = if (repeatOne) Liquid.colors.accent.copy(alpha = 0.32f) else Color.Unspecified,
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                imageVector = LiquidIcons.Share,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        text = stringResource(R.string.share),
                        onClick = {
                            context.shareSong(mediaMetadata)
                            onDismiss()
                        }
                    )
                ),
                columns = 3,
            )
        }

        item { Spacer(modifier = Modifier.height(MenuGroupGap)) }

        // Where the song comes from, and keeping it.
        item {
            Material3MenuGroup(
                glyphs = MenuGlyphs.Leading,
                items = buildList {
                    if (artists.isNotEmpty()) {
                        add(
                            Material3MenuItemData(
                                chevron = true,
                                title = { Text(text = stringResource(R.string.view_artist)) },
                                description = {
                                    Text(
                                        text = mediaMetadata.artists.joinToString { it.name },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.artist),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = {
                                    if (mediaMetadata.artists.size == 1) {
                                        navController.navigate("artist/${mediaMetadata.artists[0].id}")
                                        playerBottomSheetState.collapseSoft()
                                        onDismiss()
                                    } else {
                                        showSelectArtistDialog = true
                                    }
                                }
                            )
                        )
                    }
                    if (mediaMetadata.album != null) {
                        add(
                            Material3MenuItemData(
                                chevron = true,
                                title = { Text(text = stringResource(R.string.view_album)) },
                                description = {
                                    Text(
                                        text = mediaMetadata.album!!.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.album),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = {
                                    navController.navigate("album/${mediaMetadata.album!!.id}")
                                    playerBottomSheetState.collapseSoft()
                                    onDismiss()
                                }
                            )
                        )
                    }
                    
                    val isInLibrary = librarySong?.song?.inLibrary != null
                    add(
                        Material3MenuItemData(
                            title = { 
                                Text(
                                    text = stringResource(
                                        if (isInLibrary) R.string.remove_from_library
                                        else R.string.add_to_library
                                    )
                                )
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(
                                        if (isInLibrary) R.drawable.library_add_check
                                        else R.drawable.library_add
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                playerConnection.toggleLibrary()
                                onDismiss()
                            }
                        )
                    )
                }
            )
        }

        item { Spacer(modifier = Modifier.height(MenuGroupGap)) }

        // Other ways to hear it.
        item {
            Material3MenuGroup(
                glyphs = MenuGlyphs.Leading,
                items = buildList {
                    add(
                        Material3MenuItemData(
                            chevron = true,
                            title = { Text(text = "Ambient Mode") },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.fullscreen),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                navController.navigate("ambient_mode")
                                playerBottomSheetState.collapseSoft()
                                onDismiss()
                            }
                        )
                    )
                    if (together != null) {
                        add(
                            Material3MenuItemData(
                                chevron = true,
                                title = { Text(text = stringResource(R.string.listen_together)) },
                                description = {
                                    Text(
                                        text = when {
                                            togetherState.isLive -> stringResource(
                                                R.string.together_menu_live,
                                                togetherState.listeners,
                                                com.shiny.music.ui.liquid.together.spacedCode(togetherState.room?.code.orEmpty()),
                                            )
                                            togetherState.active -> stringResource(R.string.together_menu_connecting)
                                            else -> stringResource(R.string.together_menu_start)
                                        }
                                    )
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.group),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = {
                                    if (!togetherState.active && together.serverConfigured) together.start()
                                    onDismiss()
                                    playerBottomSheetState.collapseSoft()
                                    navController.navigate("together")
                                }
                            )
                        )
                        if (togetherState.isGuest && !togetherState.canControl) {
                            add(
                                Material3MenuItemData(
                                    title = { Text(text = stringResource(R.string.together_vote_skip)) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.skip_next),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    onClick = {
                                        together.voteSkip()
                                        onDismiss()
                                    }
                                )
                            )
                        }
                        if (togetherState.following && togetherState.locallyPaused) {
                            add(
                                Material3MenuItemData(
                                    title = { Text(text = stringResource(R.string.together_catch_up)) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.replay),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    onClick = {
                                        together.catchUp()
                                        onDismiss()
                                    }
                                )
                            )
                        }
                    }
                }
            )
        }

        item { Spacer(modifier = Modifier.height(MenuGroupGap)) }

        // Keeping a copy of it.
        item {
            // Every song exports: files on this device directly, streamed songs from their
            // download or cache, or fetched fresh (see AudioExporter).
            val exportable = com.shiny.music.export.AudioExporter.isEligible(mediaMetadata.id)
            Material3MenuGroup(
                glyphs = MenuGlyphs.Leading,
                items = listOf(
                    when (download?.state) {
                        Download.STATE_COMPLETED -> {
                            Material3MenuItemData(
                                title = {
                                    Text(
                                        text = stringResource(R.string.remove_download)
                                    )
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.offline),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = {
                                    DownloadService.sendRemoveDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        mediaMetadata.id,
                                        false,
                                    )
                                }
                            )
                        }

                        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.downloading)) },
                                icon = {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                },
                                onClick = {
                                    DownloadService.sendRemoveDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        mediaMetadata.id,
                                        false,
                                    )
                                }
                            )
                        }

                        else -> {
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.action_download)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.download),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = {
                                    database.transaction {
                                        insert(mediaMetadata)
                                    }
                                    val downloadRequest =
                                        DownloadRequest
                                            .Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                                            .setCustomCacheKey(mediaMetadata.id)
                                            .setData(mediaMetadata.title.toByteArray())
                                            .build()
                                    DownloadService.sendAddDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        downloadRequest,
                                        false,
                                    )
                                }
                            )
                        }
                    },
                    Material3MenuItemData(
                        title = { Text(text = "Export as MP3") },
                        description = { Text(text = if (exportable) "Save a copy and share it" else "Not available for this song") },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.file_export),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        onClick = {
                            onDismiss()
                            val started = com.shiny.music.export.AudioExporter.start(
                                context,
                                com.shiny.music.export.AudioExporter.Request(
                                    songId = mediaMetadata.id,
                                    title = mediaMetadata.title,
                                    artist = artists.joinToString(", ") { it.name },
                                    album = mediaMetadata.album?.title,
                                    thumbnailUrl = mediaMetadata.thumbnailUrl,
                                ),
                            )
                            if (!started) {
                                android.widget.Toast.makeText(context, com.shiny.music.export.AudioExporter.UNSUPPORTED_SOURCE, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                    ),
                )
            )
        }

        item { Spacer(modifier = Modifier.height(MenuGroupGap)) }

        item {
            Material3MenuGroup(
                glyphs = MenuGlyphs.Leading,
                items = listOf(
                    Material3MenuItemData(
                        chevron = true,
                        title = { Text(text = stringResource(R.string.details)) },
                        description = { Text(text = stringResource(R.string.details_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.info),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        onClick = {
                            // The id is read now, from the song this menu was opened
                            // for — not from whatever is playing when the page appears.
                            val song = mediaMetadata
                            onDismiss()
                            bottomSheetPageState.show {
                                ShowMediaInfo(song.id, fallback = song)
                            }
                        }
                    )
                )
            )
        }
    }
}


@Composable
fun TempoPitchDialog(onDismiss: () -> Unit) {
    LocalPlayerConnection.current ?: return
    // One setting with the Equaliser's speed and pitch: the service applies it to the player
    // (with a glide) and keeps it across songs and restarts.
    val fx = remember { com.shiny.music.eq.fx.SoundFxEngine.settings.value }
    var tempo by remember { mutableFloatStateOf(fx.speed) }
    var transposeValue by remember {
        mutableIntStateOf(round(12 * log2(fx.playerPitch)).toInt())
    }
    val updatePlaybackParameters = {
        com.shiny.music.eq.fx.SoundFxEngine.update {
            it.copy(
                speed = tempo,
                preservePitch = true,
                pitchSemitones = transposeValue.toFloat(),
                preset = com.shiny.music.eq.fx.EffectPreset.Custom,
            )
        }
    }
    // In a session everyone plays at the host's speed, so tempo isn't offered.
    val isInRoom = LocalTogether.current?.state?.collectAsState()?.value?.active == true

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.tempo_and_pitch))
        },
        dismissButton = {
            TextButton(
                onClick = {
                    tempo = 1f
                    transposeValue = 0
                    updatePlaybackParameters()
                },
            ) {
                Text(stringResource(R.string.reset))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        text = {
            Column {
                if (!isInRoom) {
                    ValueAdjuster(
                        icon = R.drawable.speed,
                        currentValue = tempo,
                        values = (0..35).map { round((0.25f + it * 0.05f) * 100) / 100 },
                        onValueUpdate = {
                            tempo = it
                            updatePlaybackParameters()
                        },
                        valueText = { "x$it" },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                ValueAdjuster(
                    icon = R.drawable.discover_tune,
                    currentValue = transposeValue,
                    values = (-12..12).toList(),
                    onValueUpdate = {
                        transposeValue = it
                        updatePlaybackParameters()
                    },
                    valueText = { "${if (it > 0) "+" else ""}$it" },
                )
            }
        },
    )
}

@Composable
fun <T> ValueAdjuster(
    @DrawableRes icon: Int,
    currentValue: T,
    values: List<T>,
    onValueUpdate: (T) -> Unit,
    valueText: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )

        IconButton(
            enabled = currentValue != values.first(),
            onClick = {
                onValueUpdate(values[values.indexOf(currentValue) - 1])
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.remove),
                contentDescription = null,
            )
        }

        Text(
            text = valueText(currentValue),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(80.dp),
        )

        IconButton(
            enabled = currentValue != values.last(),
            onClick = {
                onValueUpdate(values[values.indexOf(currentValue) + 1])
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.add),
                contentDescription = null,
            )
        }
    }
}
