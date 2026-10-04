package com.shiny.music.ui.liquid.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.pages.MoodAndGenres
import com.music.innertube.utils.YouTubeUrlParser
import com.shiny.music.LocalDatabase
import com.shiny.music.R
import com.shiny.music.constants.PauseSearchHistoryKey
import com.shiny.music.db.entities.Album
import com.shiny.music.db.entities.Artist
import com.shiny.music.db.entities.LocalItem
import com.shiny.music.db.entities.Playlist
import com.shiny.music.db.entities.SearchHistory
import com.shiny.music.db.entities.Song
import com.shiny.music.playback.queues.YouTubeQueue
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.Hairline
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.LiquidSearchField
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.ScopeCapsule
import com.shiny.music.ui.liquid.SectionHeader
import com.shiny.music.ui.liquid.ShelfGap
import com.shiny.music.ui.liquid.SongRow
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.rememberRowHighlight
import com.shiny.music.ui.liquid.subtitleText
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize
import com.shiny.music.utils.rememberPreference
import com.shiny.music.viewmodels.LocalFilter
import com.shiny.music.viewmodels.LocalSearchResult
import com.shiny.music.viewmodels.LocalSearchViewModel
import com.shiny.music.viewmodels.MoodAndGenresViewModel
import com.shiny.music.viewmodels.OnlineSearchSuggestionViewModel
import com.shiny.music.viewmodels.OnlineSearchViewModel
import com.shiny.music.viewmodels.SearchSuggestionViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

/**
 * Where a search looks.
 *
 * Deliberately *not* a stored preference. Shiny used to keep this choice in DataStore, so a
 * listener who narrowed one search to their library narrowed every search they made
 * afterwards, with nothing on screen to say so. Every search starts at [All].
 */
private enum class SearchScope { All, Catalogue, Library }

/**
 * Search: find something fast, or find something to look for.
 *
 * At rest it shows what was searched before and YouTube's browse catalogue, grouped as
 * YouTube itself groups it. While typing, the listener's own music answers first — it is on
 * the device, so it is instant and certain — then completions, then the catalogue.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidSearchScreen(
    navController: NavController,
    suggestionViewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
    localViewModel: LocalSearchViewModel = hiltViewModel(),
    browseViewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var searchScope by rememberSaveable { mutableStateOf(SearchScope.All) }
    val pauseHistory by rememberPreference(PauseSearchHistoryKey, false)
    val viewState by suggestionViewModel.viewState.collectAsState()
    val local by localViewModel.result.collectAsState()
    val browseGroups by browseViewModel.moodAndGenres.collectAsState()
    val browseFailed by browseViewModel.failed.collectAsState()

    LaunchedEffect(query) {
        suggestionViewModel.query.value = query
        localViewModel.query.value = query
    }
    LaunchedEffect(searchScope) {
        localViewModel.wide.value = searchScope == SearchScope.Library
    }

    val submit: (String) -> Unit = { text ->
        val q = text.trim()
        if (q.isNotEmpty()) {
            focusManager.clearFocus()
            keyboard?.hide()
            when (val parsed = YouTubeUrlParser.parse(q)) {
                is YouTubeUrlParser.ParsedUrl.Video ->
                    actions.playerConnection.playQueue(YouTubeQueue(WatchEndpoint(videoId = parsed.id)))

                is YouTubeUrlParser.ParsedUrl.Artist -> navController.navigate("artist/${parsed.id}")
                null -> navController.navigate("search/${URLEncoder.encode(q, "UTF-8")}")
            }
            if (!pauseHistory) {
                scope.launch(Dispatchers.IO) { database.query { insert(SearchHistory(query = q)) } }
            }
        }
    }

    val typing = query.isNotEmpty()

    LargeTitlePage(
        title = stringResource(R.string.liquid_search),
        scrollToTopSignal = navController,
    ) {
        item(key = "field") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin)
                    .padding(top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.liquid_search_placeholder),
                    onSearch = submit,
                    focusRequester = focusRequester,
                    onFocusChange = { focused = it },
                    modifier = Modifier.weight(1f),
                    trailing = {
                        Box(
                            Modifier
                                .size(34.dp)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { navController.navigate("recognition") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Mic, null, tint = Liquid.colors.secondaryLabel, modifier = Modifier.size(20.dp))
                        }
                    },
                )
                AnimatedVisibility(
                    visible = focused || typing,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut(),
                ) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                        style = LiquidTypography.body,
                        color = Liquid.colors.accent,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                query = ""
                                focusManager.clearFocus()
                                keyboard?.hide()
                            },
                    )
                }
            }
        }

        if (typing) {
            typeaheadContent(
                query = query,
                searchScope = searchScope,
                onScope = { searchScope = it },
                viewState = viewState,
                local = local,
                actions = actions,
                activeId = mediaMetadata?.id,
                isPlaying = isPlaying,
                onSubmit = submit,
                onFill = { query = it },
            )
        } else {
            restingContent(
                history = viewState.history,
                browseGroups = browseGroups,
                browseFailed = browseFailed,
                onSubmit = submit,
                onFill = { query = it },
                onDeleteHistory = { h -> scope.launch(Dispatchers.IO) { database.query { delete(h) } } },
                onClearHistory = { scope.launch(Dispatchers.IO) { database.query { clearSearchHistory() } } },
                onBrowse = { navController.navigate("youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}") },
                onRetryBrowse = browseViewModel::load,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// At rest
// ---------------------------------------------------------------------------------------

/** Recent searches, then the catalogue to browse. Two sections, each earning its place. */
private fun LazyListScope.restingContent(
    history: List<SearchHistory>,
    browseGroups: List<MoodAndGenres>?,
    browseFailed: Boolean,
    onSubmit: (String) -> Unit,
    onFill: (String) -> Unit,
    onDeleteHistory: (SearchHistory) -> Unit,
    onClearHistory: () -> Unit,
    onBrowse: (MoodAndGenres.Item) -> Unit,
    onRetryBrowse: () -> Unit,
) {
    val recent = history.take(6)
    if (recent.isNotEmpty()) {
        item(key = "recent_header") {
            SectionHeader(
                title = stringResource(R.string.liquid_recent_searches),
                trailing = {
                    Text(
                        stringResource(R.string.liquid_clear),
                        style = LiquidTypography.body,
                        color = Liquid.colors.accent,
                        modifier = Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClearHistory,
                        ),
                    )
                },
            )
        }
        items(recent, key = { "h_${it.id}_${it.query}" }) { h ->
            SuggestionRow(
                text = h.query,
                icon = Icons.Rounded.History,
                onClick = { onSubmit(h.query) },
                onFill = { onFill(h.query) },
                onLongClick = { onDeleteHistory(h) },
            )
        }
    }

    val groups = browseGroups
    when {
        groups != null -> groups.forEach { group ->
            item(key = "browse_header_${group.title}") { SectionHeader(title = group.title) }
            items(
                group.items.distinctBy { it.title }.chunked(2),
                key = { "bg_${group.title}_${it.first().title}" },
            ) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageMargin)
                        .padding(bottom = ShelfGap),
                    horizontalArrangement = Arrangement.spacedBy(ShelfGap),
                ) {
                    row.forEach { item ->
                        CategoryRow(
                            title = item.title,
                            color = Color(item.stripeColor),
                            onClick = { onBrowse(item) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // Offline, or YouTube could not be reached: say so once instead of spinning forever.
        browseFailed -> item(key = "browse_failed") {
            EmptyState(
                icon = Icons.Rounded.Search,
                title = stringResource(R.string.liquid_search_start_title),
                message = stringResource(R.string.liquid_browse_offline) + " " +
                    stringResource(R.string.liquid_search_offline_body),
                actionLabel = stringResource(R.string.retry),
                onAction = onRetryBrowse,
            )
        }

        else -> item(key = "browse_loading") {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { ActivityIndicator() }
        }
    }
}

/**
 * One browse category: its own colour as a bar and a wash, the name carrying the row.
 *
 * Half the height of the colour tile it replaces, so a catalogue of forty genres stays a
 * list a thumb can run down rather than a wall of identical rectangles.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    title: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    // YouTube's stripe colours are deep, so they are washed into a card surface rather than
    // flooded over the page; the leading bar carries the colour at full strength. Tinting the
    // *card* colour rather than the page is what keeps the grey categories (Focus, Gaming,
    // Sad) from disappearing into an AMOLED background, where their wash is nearly black.
    val wash = lerp(colors.secondaryBackground, color, if (colors.isDark) 0.26f else 0.14f)
    Row(
        modifier = modifier
            .height(56.dp)
            .pressScale(interaction, 0.97f)
            .clip(RoundedCornerShape(14.dp))
            .background(wash)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(color)
        )
        Text(
            text = title,
            style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = colors.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------
// While typing
// ---------------------------------------------------------------------------------------

private fun LazyListScope.typeaheadContent(
    query: String,
    searchScope: SearchScope,
    onScope: (SearchScope) -> Unit,
    viewState: SearchSuggestionViewState,
    local: LocalSearchResult,
    actions: LiquidActions,
    activeId: String?,
    isPlaying: Boolean,
    onSubmit: (String) -> Unit,
    onFill: (String) -> Unit,
) {
    item(key = "scopes") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageMargin),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        ) {
            items(SearchScope.entries, key = { it.name }) { s ->
                ScopeCapsule(
                    text = when (s) {
                        SearchScope.All -> stringResource(R.string.liquid_search_scope_all)
                        SearchScope.Catalogue -> stringResource(R.string.app_name)
                        SearchScope.Library -> stringResource(R.string.liquid_library)
                    },
                    selected = s == searchScope,
                    onClick = { onScope(s) },
                )
            }
        }
    }

    // The listener's own music is on the device, so it answers first and it answers for
    // certain — but only the first few of it, unless they asked for the library scope.
    val wantsLibrary = searchScope != SearchScope.Catalogue
    val libraryHits = if (!wantsLibrary) {
        emptyList()
    } else {
        listOf(LocalFilter.SONG, LocalFilter.ALBUM, LocalFilter.ARTIST, LocalFilter.PLAYLIST)
            .flatMap { local.map[it].orEmpty() }
    }
    val shownLibrary = if (searchScope == SearchScope.Library) libraryHits else libraryHits.take(5)
    val lyricHits = when {
        !wantsLibrary -> emptyList()
        searchScope == SearchScope.Library -> local.lyrics
        else -> local.lyrics.take(3)
    }

    if (shownLibrary.isNotEmpty()) {
        item(key = "lib_header") {
            SectionHeader(
                title = stringResource(R.string.liquid_search_in_library),
                onClick = if (searchScope == SearchScope.All && libraryHits.size > shownLibrary.size) {
                    { onScope(SearchScope.Library) }
                } else {
                    null
                },
            )
        }
        items(shownLibrary, key = { "lib_${it.id}" }) { item ->
            LocalResultRow(item, actions, isActive = item.id == activeId, isPlaying = isPlaying)
        }
    }

    if (lyricHits.isNotEmpty()) {
        item(key = "lyric_header") { SectionHeader(title = stringResource(R.string.liquid_search_in_lyrics)) }
        items(lyricHits, key = { "ly_${it.id}" }) { song ->
            LocalResultRow(song, actions, isActive = song.id == activeId, isPlaying = isPlaying)
        }
    }

    if (searchScope != SearchScope.Library) {
        // History first: a query already run is the likeliest one meant again.
        val completions = (viewState.history.map { it.query } + viewState.suggestions).distinct().take(6)
        if (completions.isNotEmpty()) {
            item(key = "sug_header") { SectionHeader(title = stringResource(R.string.liquid_search_suggestions)) }
            items(completions, key = { "sg_$it" }) { text ->
                SuggestionRow(
                    text = text,
                    icon = if (viewState.history.any { it.query == text }) Icons.Rounded.History else Icons.Rounded.Search,
                    onClick = { onSubmit(text) },
                    onFill = { onFill(text) },
                )
            }
        }

        if (viewState.items.isNotEmpty()) {
            item(key = "items_header") { SectionHeader(title = stringResource(R.string.liquid_top_results)) }
            items(viewState.items, key = { "i_${it.id}" }) { item ->
                ResultRow(item, actions, isActive = item.id == activeId, isPlaying = isPlaying)
            }
        }
    }

    val catalogueSilent = viewState.items.isEmpty() && viewState.suggestions.isEmpty() && viewState.history.isEmpty()
    val nothing = shownLibrary.isEmpty() && lyricHits.isEmpty() &&
        (searchScope == SearchScope.Library || catalogueSilent)
    if (nothing) {
        item(key = "empty") {
            EmptyState(
                icon = if (searchScope == SearchScope.Library) Icons.Rounded.LibraryMusic else Icons.Rounded.Search,
                title = "“$query”",
                message = if (searchScope == SearchScope.Library) {
                    stringResource(R.string.liquid_search_offline_body)
                } else {
                    stringResource(R.string.liquid_no_results_body)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestionRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onFill: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = rememberRowHighlight(),
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = PageMargin, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = colors.secondaryLabel, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                style = LiquidTypography.body,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            )
            Box(
                Modifier
                    .size(44.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onFill,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.NorthWest, null, tint = colors.tertiaryLabel, modifier = Modifier.size(18.dp))
            }
        }
        Hairline(Modifier.align(Alignment.BottomStart), startIndent = PageMargin + 34.dp)
    }
}

/** A YouTube result: the right artwork shape and subtitle for what it is. */
@Composable
internal fun ResultRow(item: YTItem, actions: LiquidActions, isActive: Boolean, isPlaying: Boolean) {
    val kind = when (item) {
        is SongItem -> stringResource(R.string.songs).trimEnd('s')
        is AlbumItem -> stringResource(R.string.liquid_album)
        is ArtistItem -> stringResource(R.string.artists).trimEnd('s')
        is PlaylistItem -> stringResource(R.string.liquid_playlist)
    }
    SongRow(
        songId = (item as? com.music.innertube.models.SongItem)?.id,
        title = item.title,
        subtitle = listOfNotNull(kind, item.subtitleText()?.takeIf { it.isNotBlank() }).joinToString(" · "),
        artwork = item.thumbnail?.resize(240, 240),
        circularArtwork = item is ArtistItem,
        isActive = isActive,
        isPlaying = isPlaying,
        explicit = item.explicit,
        onClick = { actions.open(item) },
        onLongClick = { actions.menu(item) },
        onMore = if (item is ArtistItem) null else ({ actions.menu(item) }),
        trailing = if (item is ArtistItem) {
            { Icon(Icons.Rounded.ChevronRight, null, tint = Liquid.colors.tertiaryLabel, modifier = Modifier.size(24.dp)) }
        } else {
            null
        },
    )
}

@Composable
private fun LocalResultRow(item: LocalItem, actions: LiquidActions, isActive: Boolean, isPlaying: Boolean) {
    SongRow(
        songId = (item as? com.shiny.music.db.entities.Song)?.id,
        title = item.title,
        subtitle = when (item) {
            is Song -> item.artists.joinToString { it.name }
            is Album -> item.artists.joinToString { it.name }
            is Playlist -> stringResource(R.string.liquid_songs_count, item.songCount)
            is Artist -> null
        },
        artwork = when (item) {
            is Playlist -> item.thumbnails.firstOrNull()
            else -> item.thumbnailUrl
        },
        circularArtwork = item is Artist,
        isActive = isActive,
        isPlaying = isPlaying,
        onClick = { actions.open(item) },
        onLongClick = { actions.menu(item) },
        onMore = { actions.menu(item) },
    )
}

// ---------------------------------------------------------------------------------------
// Results
// ---------------------------------------------------------------------------------------

private data class Scope(val label: String, val filter: YouTube.SearchFilter?)

/**
 * The results page.
 *
 * The catalogue answers in YouTube's own order, but the listener's own music is put above it
 * whenever any of it matches: a search that started online is still a search, and hiding a
 * song already on the phone behind a scope switch is the wrong answer.
 */
@Composable
fun LiquidSearchResultScreen(
    navController: NavController,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
    localViewModel: LocalSearchViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val local by localViewModel.result.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel.query) { localViewModel.query.value = viewModel.query }

    val scopes = listOf(
        Scope(stringResource(R.string.liquid_top_results), null),
        Scope(stringResource(R.string.songs), YouTube.SearchFilter.FILTER_SONG),
        Scope(stringResource(R.string.albums), YouTube.SearchFilter.FILTER_ALBUM),
        Scope(stringResource(R.string.artists), YouTube.SearchFilter.FILTER_ARTIST),
        Scope(stringResource(R.string.playlists), YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST),
        Scope("Videos", YouTube.SearchFilter.FILTER_VIDEO),
    )

    LaunchedEffect(listState, filter) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (filter != null && last != null && total > 0 && last >= total - 5) viewModel.loadMore()
            }
    }

    LargeTitlePage(
        title = viewModel.query,
        state = listState,
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        item(key = "scopes") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = PageMargin),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            ) {
                items(scopes, key = { it.label }) { s ->
                    ScopeCapsule(
                        text = s.label,
                        selected = filter?.value == s.filter?.value,
                        onClick = { viewModel.filter.value = s.filter },
                    )
                }
            }
        }

        if (filter == null) {
            val libraryHits = listOf(LocalFilter.SONG, LocalFilter.ALBUM, LocalFilter.ARTIST, LocalFilter.PLAYLIST)
                .flatMap { local.map[it].orEmpty() }
                .take(4)
            if (libraryHits.isNotEmpty()) {
                item(key = "lib_header") { SectionHeader(title = stringResource(R.string.liquid_search_in_library)) }
                items(libraryHits, key = { "lib_${it.id}" }) { item ->
                    LocalResultRow(item, actions, isActive = item.id == mediaMetadata?.id, isPlaying = isPlaying)
                }
            }

            val summary = viewModel.summaryPage
            when {
                summary == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { ActivityIndicator() }
                }

                summary.summaries.all { it.items.isEmpty() } -> if (libraryHits.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Rounded.Search,
                            title = stringResource(R.string.liquid_no_results),
                            message = stringResource(R.string.liquid_no_results_body),
                        )
                    }
                }

                else -> {
                    // YouTube's first section is its single best guess, so it gets the card.
                    // Songs follow it — a search for a name is usually a search for a song —
                    // and everything after that keeps YouTube's own order.
                    val sections = summary.summaries.filter { it.items.isNotEmpty() }
                    val head = sections.firstOrNull()
                    val rest = sections.drop(1).sortedBy { if (it.items.firstOrNull() is SongItem) 0 else 1 }

                    if (head != null) {
                        item(key = "top_card") {
                            SectionHeader(title = head.title)
                            TopResultCard(head.items.first(), actions)
                        }
                        items(head.items.drop(1), key = { "tr_${it.id}" }) { item ->
                            ResultRow(item, actions, isActive = item.id == mediaMetadata?.id, isPlaying = isPlaying)
                        }
                    }
                    rest.forEachIndexed { index, section ->
                        item(key = "sum_header_$index") { SectionHeader(title = section.title) }
                        items(section.items, key = { "sum_${index}_${it.id}" }) { item ->
                            ResultRow(item, actions, isActive = item.id == mediaMetadata?.id, isPlaying = isPlaying)
                        }
                    }
                }
            }
        } else {
            val page = viewModel.viewStateMap[filter?.value]
            when {
                page == null -> item(key = "loading_f") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { ActivityIndicator() }
                }

                page.items.isEmpty() -> item(key = "empty_f") {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = stringResource(R.string.liquid_no_results),
                        message = stringResource(R.string.liquid_no_results_body),
                    )
                }

                else -> items(page.items, key = { "f_${it.id}" }) { item ->
                    ResultRow(item, actions, isActive = item.id == mediaMetadata?.id, isPlaying = isPlaying)
                }
            }
        }
    }
}

/** The single likeliest match, given a card of its own as the Music app does. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopResultCard(item: YTItem, actions: LiquidActions) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .padding(horizontal = PageMargin)
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = rememberRowHighlight(),
                onClick = { actions.open(item) },
                onLongClick = { actions.menu(item) },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            model = item.thumbnail?.resize(400, 400),
            shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(10.dp),
            modifier = Modifier.size(92.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(item.title, style = LiquidTypography.title3, color = colors.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.subtitleText()?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = LiquidTypography.subheadline, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item !is ArtistItem) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { actions.open(item) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = colors.accent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}
