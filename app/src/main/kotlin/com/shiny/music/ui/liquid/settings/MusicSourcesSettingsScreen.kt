package com.shiny.music.ui.liquid.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.shiny.music.R
import com.shiny.music.constants.LocalSongsExcludedFoldersKey
import com.shiny.music.constants.LocalSongsIncludedFoldersKey
import com.shiny.music.constants.LocalSongsSkippedSourcesKey
import com.shiny.music.localmedia.DefaultSkippedSourceKeys
import com.shiny.music.localmedia.LocalSongScanConfig
import com.shiny.music.localmedia.SkippedSource
import com.shiny.music.localmedia.rememberLocalScanConfig
import com.shiny.music.localmedia.toLocalFolderEntry
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.utils.rememberPreference
import com.shiny.music.viewmodels.LocalSongsViewModel

/**
 * Where Shiny looks for music on this device.
 *
 * Every control here changes what a scan keeps: the three kinds of non-music audio Shiny
 * leaves out, folders to always include (which win over those), and folders to never
 * include (which win over everything). Changes apply on the next scan — this page's own
 * Scan now, or the automatic check when On This Device opens.
 */
@Composable
fun MusicSourcesSettingsScreen(
    navController: NavController,
    viewModel: LocalSongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var skippedKeys by rememberPreference(LocalSongsSkippedSourcesKey, DefaultSkippedSourceKeys)
    var included by rememberPreference(LocalSongsIncludedFoldersKey, emptySet<String>())
    var excluded by rememberPreference(LocalSongsExcludedFoldersKey, emptySet<String>())
    val scanConfig = rememberLocalScanConfig()
    val scanState by viewModel.scanState.collectAsState()

    val permission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) viewModel.scanDevice(scanConfig)
    }

    // Which list the next folder pick goes to.
    var pickingForIncluded by rememberSaveable { mutableStateOf(true) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val entry = uri?.toLocalFolderEntry() ?: return@rememberLauncherForActivityResult
        if (pickingForIncluded) {
            included = LocalSongScanConfig.deduplicateFolderEntries(included + entry)
        } else {
            excluded = LocalSongScanConfig.deduplicateFolderEntries(excluded + entry)
        }
    }

    fun setSkipped(source: SkippedSource, skip: Boolean) {
        skippedKeys = if (skip) skippedKeys + source.key else skippedKeys - source.key
    }

    SettingsPage(title = stringResource(R.string.local_sources_title), navController = navController) {
        item(key = "skipped") {
            SettingsSection(
                title = stringResource(R.string.local_sources_section_skipped),
                footer = stringResource(R.string.local_sources_section_skipped_footer),
            ) {
                SettingsToggleRow(
                    title = stringResource(R.string.local_sources_messaging),
                    subtitle = stringResource(R.string.local_sources_messaging_desc),
                    checked = SkippedSource.Messaging.key in skippedKeys,
                    onCheckedChange = { setSkipped(SkippedSource.Messaging, it) },
                )
                SettingsToggleRow(
                    title = stringResource(R.string.local_sources_recordings),
                    subtitle = stringResource(R.string.local_sources_recordings_desc),
                    checked = SkippedSource.Recordings.key in skippedKeys,
                    onCheckedChange = { setSkipped(SkippedSource.Recordings, it) },
                )
                SettingsToggleRow(
                    title = stringResource(R.string.local_sources_system),
                    subtitle = stringResource(R.string.local_sources_system_desc),
                    checked = SkippedSource.SystemSounds.key in skippedKeys,
                    onCheckedChange = { setSkipped(SkippedSource.SystemSounds, it) },
                    divider = false,
                )
            }
        }

        item(key = "included") {
            SettingsSection(
                title = stringResource(R.string.local_sources_section_included),
                footer = stringResource(R.string.local_sources_section_included_footer),
            ) {
                FolderRows(folders = included, onRemove = { included = included - it })
                SettingsActionRow(
                    title = stringResource(R.string.local_sources_add_folder),
                    onClick = {
                        pickingForIncluded = true
                        folderPicker.launch(null)
                    },
                    divider = false,
                )
            }
        }

        item(key = "excluded") {
            SettingsSection(
                title = stringResource(R.string.local_sources_section_excluded),
                footer = stringResource(R.string.local_sources_section_excluded_footer),
            ) {
                FolderRows(folders = excluded, onRemove = { excluded = excluded - it })
                SettingsActionRow(
                    title = stringResource(R.string.local_sources_add_folder),
                    onClick = {
                        pickingForIncluded = false
                        folderPicker.launch(null)
                    },
                    divider = false,
                )
            }
        }

        item(key = "scan") {
            val summary = scanState.lastSummary
            SettingsSection(
                footer = when {
                    summary != null && summary.skippedFiles > 0 -> stringResource(
                        R.string.local_songs_scan_summary,
                        summary.scannedSongs,
                        summary.removedSongs,
                    ) + "\n" + pluralStringResource(R.plurals.local_sources_skipped_files, summary.skippedFiles, summary.skippedFiles)
                    summary != null -> stringResource(R.string.local_songs_scan_summary, summary.scannedSongs, summary.removedSongs)
                    !hasPermission -> stringResource(R.string.local_sources_scan_needs_access)
                    else -> null
                },
            ) {
                SettingsActionRow(
                    title = stringResource(
                        if (scanState.isScanning) R.string.local_sources_scanning else R.string.local_sources_scan
                    ),
                    enabled = !scanState.isScanning,
                    onClick = {
                        if (hasPermission) viewModel.scanDevice(scanConfig) else permissionLauncher.launch(permission)
                    },
                    divider = false,
                )
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }
}

/** One row per folder, each with a remove button. */
@Composable
private fun FolderRows(folders: Set<String>, onRemove: (String) -> Unit) {
    val sorted = remember(folders) {
        LocalSongScanConfig.deduplicateFolderEntries(folders).sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    sorted.forEach { folder ->
        SettingsRow(
            title = folder,
            trailing = {
                IconButton(onClick = { onRemove(folder) }) {
                    Icon(
                        imageVector = Icons.Rounded.RemoveCircle,
                        contentDescription = stringResource(R.string.local_sources_remove_folder, folder),
                        tint = Liquid.colors.destructive,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )
    }
}
