package com.shiny.music.ui.liquid.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.shiny.music.R
import com.shiny.music.constants.LocalSongsMinDurationSecondsKey
import com.shiny.music.constants.LocalSongsSortDescendingKey
import com.shiny.music.constants.LocalSongsSortTypeKey
import com.shiny.music.localmedia.LocalSongScanConfig
import com.shiny.music.localmedia.musicSourcesSummary
import com.shiny.music.localmedia.rememberLocalScanConfig
import com.shiny.music.localmedia.SupportedLocalAudio
import com.shiny.music.ui.component.LocalMenuState
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.ButtonTone
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.GlassIconButton
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.LiquidButton
import com.shiny.music.ui.liquid.LiquidPullDownMenu
import com.shiny.music.ui.liquid.LiquidSearchField
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.MenuAction
import com.shiny.music.ui.liquid.MenuDivider
import com.shiny.music.ui.liquid.MenuEntry
import com.shiny.music.ui.liquid.MenuHeader
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.SongRow
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.rememberRowHighlight
import com.shiny.music.ui.menu.SelectionSongMenu
import com.shiny.music.utils.rememberPreference
import com.shiny.music.viewmodels.LocalSongsScanState
import com.shiny.music.viewmodels.LocalSongsViewModel
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale

private enum class LocalSort(val label: Int) {
    MODIFIED(R.string.sort_by_last_updated),
    NAME(R.string.sort_by_name),
    ARTIST(R.string.sort_by_artist),
    ALBUM(R.string.sort_by_album),
}

/** Skip-shorter-than choices, in seconds. */
private val DurationChoices = listOf(0, 15, 30, 45, 60, 90, 120, 180)

/**
 * On This Device — the phone's own audio files, as a Library category page: large title,
 * search, Play / Shuffle, the songs, and an iOS "Select" mode. Sorting lives in the ⋯
 * pull-down; scanning and its filters in a form sheet.
 */
@Composable
fun LiquidLocalSongsScreen(
    navController: NavController,
    viewModel: LocalSongsViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val playerConnection = actions.playerConnection
    val context = LocalContext.current
    val colors = Liquid.colors
    val menuState = LocalMenuState.current
    val songs by viewModel.songs.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showScanSheet by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    val selection = remember { mutableStateListOf<String>() }
    val (sortDescending, onSortDescendingChange) = rememberPreference(LocalSongsSortDescendingKey, true)
    val (sortName, onSortNameChange) = rememberPreference(LocalSongsSortTypeKey, LocalSort.MODIFIED.name)
    val sort = remember(sortName) { runCatching { LocalSort.valueOf(sortName) }.getOrDefault(LocalSort.MODIFIED) }
    val (minimumSeconds, onMinimumSecondsChange) = rememberPreference(LocalSongsMinDurationSecondsKey, 0)
    val scanConfig = rememberLocalScanConfig()

    val storagePermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasPermission by remember(storagePermission) {
        mutableStateOf(ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) viewModel.scanDevice(scanConfig)
    }
    val scan = {
        if (hasPermission) viewModel.scanDevice(scanConfig)
        else permissionLauncher.launch(storagePermission)
    }
    // Keeps the library current without the user asking: a light check of the audio index
    // and the source settings, and a scan only if either changed since the last one. This
    // is also what applies a change of the music-only rules to an existing library.
    LaunchedEffect(hasPermission, scanConfig) {
        if (hasPermission) viewModel.refreshIfStale(scanConfig)
    }
    // The library can list the phone's songs without access to them (it was restored, or the
    // permission was taken back). None of them would play, and the page only asked for the
    // permission when it was empty; so it asks once, as soon as it has songs to show.
    var askedForAccess by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(hasPermission, songs.isNotEmpty()) {
        if (!hasPermission && songs.isNotEmpty() && !askedForAccess) {
            askedForAccess = true
            permissionLauncher.launch(storagePermission)
        }
    }

    val collator = remember { Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY } }
    val visible by remember(songs, query, sort, sortDescending) {
        derivedStateOf {
            val q = query.trim()
            val supported = songs.filter { SupportedLocalAudio.isSupportedMimeType(it.format?.mimeType) }
            val filtered = if (q.isBlank()) supported else supported.filter { song ->
                song.song.title.contains(q, true) ||
                    song.song.albumName.orEmpty().contains(q, true) ||
                    song.artists.any { it.name.contains(q, true) }
            }
            val sorted = when (sort) {
                LocalSort.MODIFIED -> filtered.sortedBy { it.song.dateModified ?: LocalDateTime.MIN }
                LocalSort.NAME -> filtered.sortedWith(compareBy(collator) { it.song.title })
                LocalSort.ARTIST -> filtered.sortedWith(compareBy(collator) { s -> s.artists.joinToString("") { it.name } })
                LocalSort.ALBUM -> filtered.sortedWith(compareBy(collator) { it.song.albumName.orEmpty() })
            }
            if (sortDescending) sorted.asReversed() else sorted
        }
    }
    val title = stringResource(R.string.liquid_local_files)
    val exitSelection = {
        selecting = false
        selection.clear()
    }

    val menuEntries: List<MenuEntry> = buildList {
        add(MenuAction(stringResource(R.string.liquid_select), icon = Icons.Rounded.CheckCircle) { selecting = true })
        add(MenuAction(stringResource(R.string.scan_device), icon = Icons.Rounded.Sync) { showScanSheet = true })
        add(MenuDivider)
        add(MenuHeader(stringResource(R.string.liquid_sort_by)))
        LocalSort.entries.forEach { option ->
            add(MenuAction(stringResource(option.label), checked = option == sort) { onSortNameChange(option.name) })
        }
        add(MenuDivider)
        add(MenuAction(stringResource(R.string.liquid_ascending), checked = !sortDescending) { onSortDescendingChange(false) })
        add(MenuAction(stringResource(R.string.liquid_descending), checked = sortDescending) { onSortDescendingChange(true) })
    }

    LargeTitlePage(
        title = if (selecting) pluralStringResource(R.plurals.n_selected, selection.size, selection.size) else title,
        navigationButton = { LiquidBackButton(onClick = { if (selecting) exitSelection() else navController.navigateUp() }) },
        actions = {
            if (selecting) {
                GlassIconButton(
                    icon = Icons.Rounded.MoreHoriz,
                    enabled = selection.isNotEmpty(),
                    onClick = {
                        menuState.show {
                            SelectionSongMenu(
                                songSelection = selection.mapNotNull { id -> songs.find { it.id == id } },
                                onDismiss = menuState::dismiss,
                                clearAction = exitSelection,
                            )
                        }
                    },
                )
                LiquidButton(
                    text = stringResource(R.string.liquid_done),
                    onClick = exitSelection,
                    tone = ButtonTone.Filled,
                    height = 44.dp,
                )
            } else {
                Box {
                    GlassIconButton(icon = Icons.Rounded.MoreHoriz, onClick = { menuOpen = true })
                    LiquidPullDownMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, entries = menuEntries)
                }
            }
        },
    ) {
        item(key = "search") {
            LiquidSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.liquid_search_device),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin, vertical = 6.dp),
            )
        }

        if (scanState.isScanning) {
            item(key = "scanning") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageMargin, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    ActivityIndicator(size = 20.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.scanning_device), style = LiquidTypography.subheadline, color = colors.secondaryLabel)
                }
            }
        }

        if (visible.isNotEmpty() && !selecting) {
            item(key = "play_row") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageMargin, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LiquidButton(
                        text = stringResource(R.string.liquid_play),
                        icon = Icons.Rounded.PlayArrow,
                        onClick = { actions.playSongs(title, visible) },
                        modifier = Modifier.weight(1f),
                    )
                    LiquidButton(
                        text = stringResource(R.string.liquid_shuffle),
                        icon = Icons.Rounded.Shuffle,
                        onClick = { actions.playSongs(title, visible, shuffle = true) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            item(key = "empty") {
                when {
                    query.isNotBlank() -> EmptyState(
                        icon = Icons.Rounded.Search,
                        title = stringResource(R.string.local_songs_no_matches_title),
                    )
                    !hasPermission -> EmptyState(
                        icon = Icons.Rounded.PhoneAndroid,
                        title = stringResource(R.string.permission_storage_title),
                        message = stringResource(R.string.local_songs_permission_body),
                        actionLabel = stringResource(R.string.allow),
                        onAction = { permissionLauncher.launch(storagePermission) },
                    )
                    else -> EmptyState(
                        icon = Icons.Rounded.PhoneAndroid,
                        title = stringResource(R.string.local_songs_empty_title),
                        message = stringResource(R.string.liquid_local_empty_message),
                        actionLabel = if (scanState.isScanning) null else stringResource(R.string.scan_device),
                        onAction = scan,
                    )
                }
            }
        }

        itemsIndexed(visible, key = { _, s -> "ls_${s.id}" }) { index, song ->
            val selected = song.id in selection
            val toggle = { if (selected) selection.remove(song.id) else selection.add(song.id) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = selecting,
                    enter = expandHorizontally(spring(stiffness = 520f)) + fadeIn(),
                    exit = shrinkHorizontally(spring(stiffness = 520f)) + fadeOut(),
                ) {
                    SelectionCircle(selected = selected, modifier = Modifier.padding(start = PageMargin))
                }
                SongRow(
                    title = song.title,
                    subtitle = listOfNotNull(
                        song.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
                        song.song.albumName?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    artwork = song.thumbnailUrl,
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    startPadding = if (selecting) 12.dp else PageMargin,
                    onClick = {
                        when {
                            selecting -> toggle()
                            song.id == mediaMetadata?.id -> playerConnection.togglePlayPause()
                            else -> actions.playSongs(title, visible, startIndex = index)
                        }
                    },
                    onLongClick = {
                        if (selecting) toggle() else actions.menu(song)
                    },
                    onMore = if (selecting) null else ({ actions.menu(song) }),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (visible.isNotEmpty()) {
            item(key = "footer") {
                val minutes = visible.sumOf { it.song.duration.coerceAtLeast(0) } / 60
                Text(
                    text = stringResource(R.string.liquid_songs_minutes, visible.size, minutes),
                    style = LiquidTypography.footnote,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = PageMargin, vertical = 14.dp),
                )
            }
        }
    }

    if (showScanSheet) {
        ScanSheet(
            hasPermission = hasPermission,
            scanState = scanState,
            minimumSeconds = minimumSeconds,
            onMinimumSecondsChange = onMinimumSecondsChange,
            scanConfig = scanConfig,
            onOpenSources = {
                showScanSheet = false
                navController.navigate("settings/library/sources")
            },
            onScan = scan,
            onDismiss = { showScanSheet = false },
        )
    }
}

@Composable
private fun SelectionCircle(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = Liquid.colors
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(colors.accent)
                else Modifier.border(1.5.dp, colors.tertiaryLabel, CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/**
 * The scan form sheet: where things stand, the two filters as grouped rows, and one
 * button that scans (or asks for access first).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanSheet(
    hasPermission: Boolean,
    scanState: LocalSongsScanState,
    minimumSeconds: Int,
    onMinimumSecondsChange: (Int) -> Unit,
    scanConfig: LocalSongScanConfig,
    onOpenSources: () -> Unit,
    onScan: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Liquid.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var durationMenu by remember { mutableStateOf(false) }
    val sourcesSummary = musicSourcesSummary(scanConfig)
    val durationLabel: @Composable (Int) -> String = { seconds ->
        if (seconds <= 0) stringResource(R.string.liquid_off)
        else pluralStringResource(R.plurals.seconds, seconds, seconds)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (colors.isDark) Color(0xFF1C1C1E) else colors.groupedBackground,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(colors.tertiaryLabel)
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = PageMargin)
                .padding(bottom = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(60.dp))
                Text(
                    stringResource(R.string.scan_device),
                    style = LiquidTypography.headline,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.liquid_done),
                    style = LiquidTypography.headline,
                    color = colors.accent,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(60.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                )
            }

            // Status
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (scanState.isScanning) ActivityIndicator(color = Color.White, size = 30.dp)
                    else Icon(Icons.Rounded.PhoneAndroid, null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(12.dp))
                val summary = scanState.lastSummary
                Text(
                    text = when {
                        scanState.isScanning -> stringResource(R.string.scanning_device)
                        scanState.errorMessage != null -> stringResource(R.string.local_songs_scan_failed)
                        !hasPermission -> stringResource(R.string.local_songs_permission_body)
                        summary != null && summary.skippedFiles > 0 -> stringResource(R.string.local_songs_scan_summary, summary.scannedSongs, summary.removedSongs) +
                            "\n" + pluralStringResource(R.plurals.local_sources_skipped_files, summary.skippedFiles, summary.skippedFiles)
                        summary != null -> stringResource(R.string.local_songs_scan_summary, summary.scannedSongs, summary.removedSongs)
                        else -> stringResource(R.string.liquid_local_empty_message)
                    },
                    style = LiquidTypography.subheadline,
                    color = colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
            }

            // Filters
            GroupCaption(stringResource(R.string.liquid_filters))
            GroupBox {
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRowHighlight(),
                                enabled = !scanState.isScanning,
                                onClick = { durationMenu = true },
                            )
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.liquid_skip_shorter), style = LiquidTypography.body, color = colors.label, modifier = Modifier.weight(1f))
                        Text(durationLabel(minimumSeconds), style = LiquidTypography.body, color = colors.secondaryLabel)
                        Icon(Icons.Rounded.UnfoldMore, null, tint = colors.secondaryLabel, modifier = Modifier.padding(start = 4.dp).size(18.dp))
                    }
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        LiquidPullDownMenu(
                            expanded = durationMenu,
                            onDismiss = { durationMenu = false },
                            width = 200.dp,
                            entries = DurationChoices.map { seconds ->
                                MenuAction(durationLabel(seconds), checked = seconds == minimumSeconds) { onMinimumSecondsChange(seconds) }
                            },
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.local_songs_scan_duration_desc),
                style = LiquidTypography.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )

            GroupCaption(stringResource(R.string.local_sources_title))
            GroupBox {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRowHighlight(),
                            enabled = !scanState.isScanning,
                            onClick = onOpenSources,
                        )
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.local_sources_title), style = LiquidTypography.body, color = colors.label, modifier = Modifier.weight(1f))
                    Text(sourcesSummary, style = LiquidTypography.body, color = colors.secondaryLabel, maxLines = 1)
                    Icon(Icons.Rounded.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.padding(start = 4.dp).size(20.dp))
                }
            }
            Text(
                stringResource(R.string.local_sources_sheet_footer),
                style = LiquidTypography.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )

            Spacer(Modifier.height(26.dp))
            LiquidButton(
                text = if (hasPermission) stringResource(R.string.liquid_scan_now) else stringResource(R.string.allow),
                icon = if (hasPermission) Icons.Rounded.Sync else null,
                tone = ButtonTone.Filled,
                enabled = !scanState.isScanning,
                height = 52.dp,
                onClick = onScan,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GroupCaption(text: String) {
    Text(
        text.uppercase(),
        style = LiquidTypography.footnote,
        color = Liquid.colors.secondaryLabel,
        modifier = Modifier.padding(start = 16.dp, top = 26.dp, bottom = 7.dp),
    )
}

@Composable
private fun GroupBox(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (Liquid.colors.isDark) Color(0xFF2C2C2E) else Liquid.colors.secondaryGroupedBackground),
    ) { content() }
}


