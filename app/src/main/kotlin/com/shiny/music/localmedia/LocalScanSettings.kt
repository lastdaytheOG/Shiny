package com.shiny.music.localmedia

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shiny.music.R
import com.shiny.music.constants.LocalSongsExcludedFoldersKey
import com.shiny.music.constants.LocalSongsIncludedFoldersKey
import com.shiny.music.constants.LocalSongsMinDurationSecondsKey
import com.shiny.music.constants.LocalSongsSkippedSourcesKey
import com.shiny.music.utils.PreferencesSnapshot
import com.shiny.music.utils.rememberPreference

/** The stored keys of the sources skipped by default — every one of them. */
val DefaultSkippedSourceKeys: Set<String> = SkippedSource.Default.map(SkippedSource::key).toSet()

/** The scan settings as they are stored, for a composable that starts or checks a scan. */
@Composable
fun rememberLocalScanConfig(): LocalSongScanConfig {
    val minimumSeconds by rememberPreference(LocalSongsMinDurationSecondsKey, 0)
    val excluded by rememberPreference(LocalSongsExcludedFoldersKey, emptySet<String>())
    val included by rememberPreference(LocalSongsIncludedFoldersKey, emptySet<String>())
    val skippedKeys by rememberPreference(LocalSongsSkippedSourcesKey, DefaultSkippedSourceKeys)
    return remember(minimumSeconds, excluded, included, skippedKeys) {
        LocalSongScanConfig(
            minimumDurationSeconds = minimumSeconds,
            excludedFolders = excluded,
            skippedSources = SkippedSource.fromKeys(skippedKeys),
            includedFolders = included,
        )
    }
}

/** One line for what the sources are set to: "Music only", "Everything", or the excluded count. */
@Composable
fun musicSourcesSummary(config: LocalSongScanConfig): String {
    val excluded = config.sanitizedExcludedFolders.size
    return when {
        excluded > 0 -> pluralStringResource(R.plurals.local_sources_summary_folders, excluded, excluded)
        config.skippedSources.isEmpty() -> stringResource(R.string.local_sources_summary_all)
        else -> stringResource(R.string.local_sources_summary_music_only)
    }
}

/** The same, read once outside composition. */
fun currentLocalScanConfig(): LocalSongScanConfig = LocalSongScanConfig(
    minimumDurationSeconds = PreferencesSnapshot.read(LocalSongsMinDurationSecondsKey, 0),
    excludedFolders = PreferencesSnapshot.read(LocalSongsExcludedFoldersKey, emptySet()),
    skippedSources = SkippedSource.fromKeys(PreferencesSnapshot.read(LocalSongsSkippedSourcesKey, DefaultSkippedSourceKeys)),
    includedFolders = PreferencesSnapshot.read(LocalSongsIncludedFoldersKey, emptySet()),
)

/**
 * The folder a document-tree pick points at, relative to its storage volume
 * (`primary:Music/Albums` → `Music/Albums`).
 *
 * Only the path is kept. Shiny reads local music through Android's media index, which
 * already covers every shared folder the audio permission allows, so no persistent
 * document permission is taken — the pick is a way of naming a folder, not of granting
 * access to it.
 */
fun Uri.toLocalFolderEntry(): String? {
    if (!DocumentsContract.isTreeUri(this)) return null
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull().orEmpty()
    val relative = documentId.substringAfter(':', missingDelimiterValue = documentId)
    return LocalSongScanConfig.normalizeFolderEntry(relative).takeIf(String::isNotEmpty)
}
