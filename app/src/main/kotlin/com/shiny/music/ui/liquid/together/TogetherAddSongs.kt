package com.shiny.music.ui.liquid.together

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.shiny.music.LocalDatabase
import com.shiny.music.R
import com.shiny.music.models.MediaMetadata
import com.shiny.music.models.toMediaMetadata
import com.shiny.music.together.TogetherSession
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.Hairline
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidSearchField
import com.shiny.music.ui.liquid.LiquidSegmentedControl
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.PageMargin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Where a picked song goes: on now for everyone, straight after this one, or at the end. */
private enum class PickMode { Now, Next, Last }

/**
 * Picking songs without leaving the session: your own favourites first — the songs you'd
 * put on anyway — then a search. Whoever may drive the room (the host, or a guest the host
 * lets) can play a pick for everyone right away; anyone can put it next or last in the
 * shared queue, with their name on it. [focusSearch] opens it ready to type.
 */
@Composable
fun ColumnScope.TogetherAddSongs(session: TogetherSession, onDismiss: () -> Unit, focusSearch: Boolean = false) {
    val colors = Liquid.colors
    val database = LocalDatabase.current
    val state by session.state.collectAsState()
    val canPlay = state.canControl
    val modes = if (canPlay) listOf(PickMode.Now, PickMode.Next, PickMode.Last) else listOf(PickMode.Last, PickMode.Next)
    var query by rememberSaveable { mutableStateOf("") }
    var chosenMode by rememberSaveable { mutableStateOf(if (canPlay) PickMode.Now else PickMode.Last) }
    // The host can take driving away while the sheet is open.
    val mode = chosenMode.takeIf { it in modes } ?: PickMode.Last
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<MediaMetadata>>(emptyList()) }
    val added = remember { mutableStateListOf<String>() }
    val focusRequester = remember { FocusRequester() }
    if (focusSearch) {
        LaunchedEffect(Unit) {
            // The sheet is still sliding up on the first frame; ask once it has somewhere to be.
            delay(250)
            runCatching { focusRequester.requestFocus() }
        }
    }

    // Only songs everyone can stream: a file on this phone has nothing for the others to play.
    val picks by remember(database) {
        database.quickPicks().map { songs ->
            songs.filter { TogetherSession.isShareable(it.id) }.take(20).map { it.toMediaMetadata() }
        }
    }.collectAsState(initial = emptyList())
    val liked by remember(database) {
        database.likedSongsByCreateDateAsc().map { songs ->
            songs.filter { TogetherSession.isShareable(it.id) }.takeLast(20).reversed().map { it.toMediaMetadata() }
        }
    }.collectAsState(initial = emptyList())

    LaunchedEffect(query) {
        val text = query.trim()
        if (text.isEmpty()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(350)
        searching = true
        results = withContext(Dispatchers.IO) {
            YouTube.search(text, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                ?.items?.filterIsInstance<SongItem>()?.map { it.toMediaMetadata() }.orEmpty()
        }
        searching = false
    }

    fun pick(song: MediaMetadata) {
        when (mode) {
            PickMode.Now -> {
                session.playNow(song)
                onDismiss()
                return
            }
            PickMode.Next -> session.add(listOf(song), next = true)
            PickMode.Last -> session.add(listOf(song), next = false)
        }
        added += song.id
    }

    Column(Modifier.padding(horizontal = PageMargin).padding(top = 6.dp, bottom = 10.dp)) {
        Text(stringResource(R.string.together_add_title), style = LiquidTypography.title3, color = colors.label)
        Text(
            stringResource(R.string.together_add_subtitle),
            style = LiquidTypography.footnote,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        LiquidSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.together_add_search),
            focusRequester = focusRequester,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        LiquidSegmentedControl(
            items = modes.map {
                when (it) {
                    PickMode.Now -> stringResource(R.string.together_add_now)
                    PickMode.Next -> stringResource(R.string.together_add_next)
                    PickMode.Last -> stringResource(R.string.together_add_end)
                }
            },
            selectedIndex = modes.indexOf(mode),
            onSelect = { chosenMode = modes[it] },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
        if (query.isBlank()) {
            if (picks.isNotEmpty()) {
                item(key = "picks_h") { Caption(stringResource(R.string.together_add_picks)) }
                items(picks, key = { "p_${it.id}" }) { song -> AddRow(song, song.id in added, playing = mode == PickMode.Now) { pick(song) } }
            }
            if (liked.isNotEmpty()) {
                item(key = "liked_h") { Caption(stringResource(R.string.together_add_liked)) }
                items(liked, key = { "l_${it.id}" }) { song -> AddRow(song, song.id in added, playing = mode == PickMode.Now) { pick(song) } }
            }
            if (picks.isEmpty() && liked.isEmpty()) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.together_add_empty),
                        style = LiquidTypography.subheadline,
                        color = colors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = PageMargin, vertical = 18.dp),
                    )
                }
            }
        } else {
            if (searching && results.isEmpty()) {
                item(key = "searching") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { ActivityIndicator() }
                }
            }
            items(results, key = { "r_${it.id}" }) { song -> AddRow(song, song.id in added, playing = mode == PickMode.Now) { pick(song) } }
        }
        item(key = "tail") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = LiquidTypography.headline,
        color = Liquid.colors.label,
        modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun AddRow(song: MediaMetadata, added: Boolean, playing: Boolean, onAdd: () -> Unit) {
    val colors = Liquid.colors
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickableNoRipple { if (!added) onAdd() }
                .padding(start = PageMargin, end = PageMargin - 4.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(model = song.thumbnailUrl, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(song.title, style = LiquidTypography.body, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artists.joinToString { it.name },
                    style = LiquidTypography.subheadline,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (added) colors.green else colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        added -> Icons.Rounded.Check
                        playing -> Icons.Rounded.PlayArrow
                        else -> Icons.Rounded.Add
                    },
                    null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Hairline(Modifier.align(Alignment.BottomStart), startIndent = PageMargin + 56.dp)
    }
}
