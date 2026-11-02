package com.shiny.music.ui.liquid.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.Dp
import com.shiny.music.ui.liquid.prewarmOnTouch
import com.shiny.music.ui.liquid.ExplicitBadge
import com.shiny.music.ui.liquid.library.libraryPalette
import com.shiny.music.ui.liquid.library.libraryReveal
import com.shiny.music.ui.liquid.library.LoadingDots
import com.shiny.music.ui.liquid.library.LibraryEmptyLine
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.PlaylistItem
import com.shiny.music.ui.liquid.library.LibraryScaffold
import com.shiny.music.ui.liquid.library.LibraryAvatar
import com.shiny.music.ui.liquid.library.LibraryChips
import com.shiny.music.ui.liquid.library.LibraryRule
import com.shiny.music.viewmodels.coverKey
import com.music.innertube.utils.parseCookieString
import com.shiny.music.constants.InnerTubeCookieKey
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
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
import com.shiny.music.search.SearchKind
import com.shiny.music.search.SearchShelf
import com.shiny.music.search.searchKind
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
    accountImageUrl: String? = null,
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

    val listState = rememberLazyListState()
    val cookie by rememberPreference(InnerTubeCookieKey, "")
    val loggedIn = remember(cookie) { "SAPISID" in parseCookieString(cookie) }
    var active by rememberSaveable { mutableStateOf(false) }
    val engaged = active || typing
    val reveal = remember { Animatable(1f) }
    val seen = remember { booleanArrayOf(engaged) }
    LaunchedEffect(engaged) {
        if (seen[0] == engaged) return@LaunchedEffect
        seen[0] = engaged
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(460))
    }
    val progress = { reveal.value }

    // back steps out of typing before it leaves the page
    BackHandler(enabled = active && !typing) {
        active = false
        focusManager.clearFocus()
    }

    LibraryScaffold(
        title = stringResource(R.string.liquid_search),
        state = listState,
        query = query,
        onQueryChange = { query = it },
        placeholder = stringResource(R.string.liquid_search_placeholder),
        onSearch = submit,
        scrollToTopSignal = navController,
        collapsed = engaged,
        focusRequester = focusRequester,
        onFieldFocus = { if (it) active = true },
        avatar = {
            LibraryAvatar(
                imageUrl = if (loggedIn) accountImageUrl else null,
                onClick = { navController.navigate("settings") },
            )
        },
        fieldLeading = if (engaged) {
            {
                RowGlyph(Icons.AutoMirrored.Rounded.ArrowBack, size = 20.dp) {
                    query = ""
                    active = false
                    focusManager.clearFocus()
                    keyboard?.hide()
                }
            }
        } else {
            null
        },
        fieldTrailing = {
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
    ) {
        when {
            typing -> typeaheadContent(
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
                reveal = progress,
            )
            active -> historyContent(
                history = viewState.history,
                reveal = progress,
                onSubmit = submit,
                onFill = { query = it },
                onDelete = { h -> scope.launch(Dispatchers.IO) { database.query { delete(h) } } },
                onClear = { scope.launch(Dispatchers.IO) { database.query { clearSearchHistory() } } },
            )
            else -> restingContent(
                history = viewState.history,
                browseGroups = browseGroups,
                browseFailed = browseFailed,
                covers = browseViewModel.covers,
                onCovers = browseViewModel::requestCovers,
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

private fun LazyListScope.historyContent(
    history: List<SearchHistory>,
    reveal: () -> Float,
    onSubmit: (String) -> Unit,
    onFill: (String) -> Unit,
    onDelete: (SearchHistory) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) return
    item(key = "history_header") {
        SearchHeader(
            title = stringResource(R.string.liquid_recent_searches),
            modifier = Modifier.libraryReveal(reveal, 0),
            trailing = {
                Text(
                    stringResource(R.string.liquid_clear),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Liquid.colors.accent,
                    modifier = Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClear,
                    ),
                )
            },
        )
    }
    items(history.take(12), key = { "h_${it.id}_${it.query}" }) { h ->
        HistoryRow(
            text = h.query,
            onClick = { onSubmit(h.query) },
            onDelete = { onDelete(h) },
            onFill = { onFill(h.query) },
            modifier = Modifier.libraryReveal(reveal, 1),
        )
    }
}

/** Recent searches, then the catalogue to browse. Two sections, each earning its place. */
private fun LazyListScope.restingContent(
    history: List<SearchHistory>,
    browseGroups: List<MoodAndGenres>?,
    browseFailed: Boolean,
    covers: Map<String, List<String>>,
    onCovers: (String, String?) -> Unit,
    onSubmit: (String) -> Unit,
    onFill: (String) -> Unit,
    onDeleteHistory: (SearchHistory) -> Unit,
    onClearHistory: () -> Unit,
    onBrowse: (MoodAndGenres.Item) -> Unit,
    onRetryBrowse: () -> Unit,
) {
    val groups = browseGroups
    when {
        groups != null -> groups.forEach { group ->
            item(key = "browse_header_${group.title}") { MoodSectionTitle(group.title) }
            items(
                group.items.distinctBy { it.title }.chunked(2),
                key = { "bg_${group.title}_${it.first().title}" },
            ) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageMargin)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { item ->
                        val key = coverKey(item.endpoint.browseId, item.endpoint.params)
                        LaunchedEffect(key) { onCovers(item.endpoint.browseId, item.endpoint.params) }
                        MoodCard(
                            title = item.title,
                            color = Color(item.stripeColor),
                            covers = covers[key].orEmpty(),
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
    reveal: () -> Float,
) {
    item(key = "scopes") {
        LibraryChips(
            labels = SearchScope.entries.map { s ->
                when (s) {
                    SearchScope.All -> stringResource(R.string.liquid_search_scope_all)
                    SearchScope.Catalogue -> stringResource(R.string.app_name)
                    SearchScope.Library -> stringResource(R.string.liquid_library)
                }
            },
            selected = SearchScope.entries.indexOf(searchScope),
            onSelect = { onScope(SearchScope.entries[it]) },
            modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = 2.dp, bottom = 14.dp),
        )
    }

    val wantsLibrary = searchScope != SearchScope.Catalogue
    val libraryHits = if (!wantsLibrary) {
        emptyList()
    } else {
        listOf(LocalFilter.SONG, LocalFilter.ALBUM, LocalFilter.ARTIST, LocalFilter.PLAYLIST)
            .flatMap { local.map[it].orEmpty() }
    }
    val shownLibrary = if (searchScope == SearchScope.Library) libraryHits else libraryHits.take(4)
    val lyricHits = when {
        !wantsLibrary -> emptyList()
        searchScope == SearchScope.Library -> local.lyrics
        else -> local.lyrics.take(3)
    }
    val online = searchScope != SearchScope.Library
    val suggestions = if (online) viewState.suggestions.distinct().take(6) else emptyList()
    val topItems = if (online) viewState.items else emptyList()
    var first = true

    if (suggestions.isNotEmpty()) {
        item(key = "sug_header") {
            SearchHeader(stringResource(R.string.liquid_search_suggestions), Modifier.libraryReveal(reveal, 0), ruleAbove = false)
        }
        items(suggestions, key = { "sg_$it" }) { text ->
            SuggestionPill(text = text, onClick = { onSubmit(text) }, onFill = { onFill(text) }, modifier = Modifier.libraryReveal(reveal, 1))
        }
        first = false
    }

    if (topItems.isNotEmpty()) {
        val ruleAbove = !first
        item(key = "items_header") {
            Spacer(Modifier.height(10.dp))
            SearchHeader(stringResource(R.string.liquid_top_result), Modifier.libraryReveal(reveal, 1), ruleAbove = ruleAbove)
        }
        items(topItems, key = { "i_${it.id}" }) { item ->
            YouTubeResultRow(item, actions, isActive = item.id == activeId, artSize = 44.dp, rule = false, modifier = Modifier.libraryReveal(reveal, 2))
        }
        first = false
    }

    if (shownLibrary.isNotEmpty()) {
        val ruleAbove = !first
        item(key = "lib_header") {
            Spacer(Modifier.height(10.dp))
            SearchHeader(stringResource(R.string.liquid_search_in_library), Modifier.libraryReveal(reveal, 2), ruleAbove = ruleAbove)
        }
        items(shownLibrary, key = { "lib_${it.id}" }) { item ->
            LocalSearchRow(item, actions, isActive = item.id == activeId, modifier = Modifier.libraryReveal(reveal, 3))
        }
        first = false
    }

    if (lyricHits.isNotEmpty()) {
        val ruleAbove = !first
        item(key = "lyric_header") {
            Spacer(Modifier.height(10.dp))
            SearchHeader(stringResource(R.string.liquid_search_in_lyrics), ruleAbove = ruleAbove)
        }
        items(lyricHits, key = { "ly_${it.id}" }) { song ->
            LocalSearchRow(song, actions, isActive = song.id == activeId)
        }
        first = false
    }

    if (first) {
        item(key = "waiting") {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                if (searchScope == SearchScope.Library) {
                    Text(stringResource(R.string.local_songs_no_matches_title), color = Liquid.colors.secondaryLabel, fontSize = 13.sp)
                } else {
                    LoadingDots()
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(
    title: String,
    modifier: Modifier = Modifier,
    ruleAbove: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = PageMargin),
    ) {
        if (ruleAbove) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(libraryPalette().rule),
            )
            Spacer(Modifier.height(18.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = Liquid.colors.label,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun RowGlyph(icon: ImageVector, size: Dp = 16.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Liquid.colors.secondaryLabel, modifier = Modifier.size(size))
    }
}

// a suggestion sits on its own soft pill, the arrow drops it into the field to keep typing
@Composable
private fun SuggestionPill(text: String, onClick: () -> Unit, onFill: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = PageMargin, vertical = 4.dp)
            .pressScale(interaction, 0.98f)
            .clip(RoundedCornerShape(14.dp))
            .background(libraryPalette().tile)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Liquid.colors.label,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        RowGlyph(Icons.Rounded.NorthWest, onClick = onFill)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(text: String, onClick: () -> Unit, onDelete: () -> Unit, onFill: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRowHighlight(),
                onClick = onClick,
            )
            .padding(horizontal = PageMargin, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.History, null, tint = Liquid.colors.secondaryLabel, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            color = Liquid.colors.label,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RowGlyph(Icons.Rounded.Close, onClick = onDelete)
        Spacer(Modifier.width(4.dp))
        RowGlyph(Icons.Rounded.NorthWest, onClick = onFill)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String?,
    artwork: Any?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    active: Boolean = false,
    explicit: Boolean = false,
    artSize: Dp = 46.dp,
    rule: Boolean = true,
    songId: String? = null,
) {
    val colors = Liquid.colors
    val haptics = LocalHapticFeedback.current
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .prewarmOnTouch(songId)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRowHighlight(),
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                )
                .padding(horizontal = PageMargin, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                model = artwork,
                shape = if (round) CircleShape else RoundedCornerShape(8.dp),
                hairline = false,
                modifier = Modifier.size(artSize),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (active) colors.accent else colors.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank() || explicit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (explicit) {
                            ExplicitBadge(color = colors.secondaryLabel)
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            text = subtitle.orEmpty(),
                            color = colors.secondaryLabel,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (rule) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin)
                    .height(1.dp)
                    .background(libraryPalette().rule),
            )
        }
    }
}

private fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun ytSubtitle(item: YTItem): String {
    val dot = " · "
    return when (item) {
        is SongItem -> listOfNotNull(item.artists.joinToString { it.name }.takeIf { it.isNotBlank() }, item.duration?.let(::clock)).joinToString(dot)
        is AlbumItem -> listOfNotNull(stringResource(R.string.liquid_album), item.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() }).joinToString(dot)
        is ArtistItem -> stringResource(R.string.liquid_artist)
        is PlaylistItem -> listOfNotNull(stringResource(R.string.liquid_playlist), item.author?.name).joinToString(dot)
    }
}

@Composable
private fun YouTubeResultRow(
    item: YTItem,
    actions: LiquidActions,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    artSize: Dp = 46.dp,
    rule: Boolean = true,
) {
    SearchResultRow(
        title = item.title,
        subtitle = ytSubtitle(item),
        artwork = item.thumbnail?.resize(240, 240),
        round = item is ArtistItem,
        active = isActive,
        explicit = item.explicit,
        artSize = artSize,
        rule = rule,
        songId = (item as? SongItem)?.id,
        onClick = { actions.open(item) },
        onLongClick = { actions.menu(item) },
        modifier = modifier,
    )
}

@Composable
private fun LocalSearchRow(item: LocalItem, actions: LiquidActions, isActive: Boolean, modifier: Modifier = Modifier, rule: Boolean = false) {
    SearchResultRow(
        title = item.title,
        subtitle = when (item) {
            is Song -> item.artists.joinToString { it.name }
            is Album -> listOf(stringResource(R.string.liquid_album), item.artists.joinToString { it.name }).filter { it.isNotBlank() }.joinToString(" · ")
            is Playlist -> pluralStringResource(R.plurals.liquid_songs_count, item.songCount, item.songCount)
            is Artist -> stringResource(R.string.liquid_artist)
        },
        artwork = when (item) {
            is Playlist -> item.thumbnails.firstOrNull()
            else -> item.thumbnailUrl
        },
        round = item is Artist,
        active = isActive,
        artSize = 44.dp,
        rule = rule,
        songId = (item as? Song)?.id,
        onClick = { actions.open(item) },
        onLongClick = { actions.menu(item) },
        modifier = modifier,
    )
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
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
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
    }
}

/** A YouTube result: the right artwork shape and subtitle for what it is. */
@Composable
internal fun ResultRow(item: YTItem, actions: LiquidActions, isActive: Boolean, isPlaying: Boolean) {
    val kind = when (item.searchKind()) {
        SearchKind.Song -> stringResource(R.string.liquid_song)
        SearchKind.MusicVideo -> stringResource(R.string.liquid_music_video)
        SearchKind.Video, SearchKind.Unsupported -> stringResource(R.string.liquid_video)
        SearchKind.Album -> stringResource(R.string.liquid_album)
        SearchKind.Artist -> stringResource(R.string.liquid_artist)
        SearchKind.Playlist -> stringResource(R.string.liquid_playlist)
    }
    SongRow(
        songId = (item as? SongItem)?.id,
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
            is Playlist -> pluralStringResource(R.plurals.liquid_songs_count, item.songCount, item.songCount)
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

private data class Scope(val label: String, val filter: YouTube.SearchFilter?)

@Composable
private fun shelfTitle(shelf: SearchShelf) = when (shelf) {
    SearchShelf.Songs -> stringResource(R.string.songs)
    SearchShelf.Videos -> stringResource(R.string.liquid_videos)
    SearchShelf.Albums -> stringResource(R.string.albums)
    SearchShelf.Artists -> stringResource(R.string.artists)
    SearchShelf.Playlists -> stringResource(R.string.playlists)
}

@Composable
fun LiquidSearchResultScreen(
    navController: NavController,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
    localViewModel: LocalSearchViewModel = hiltViewModel(),
    accountImageUrl: String? = null,
) {
    val actions = rememberLiquidActions(navController) ?: return
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val local by localViewModel.result.collectAsState()
    val listState = rememberLazyListState()
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val pauseHistory by rememberPreference(PauseSearchHistoryKey, false)
    val cookie by rememberPreference(InnerTubeCookieKey, "")
    val loggedIn = remember(cookie) { "SAPISID" in parseCookieString(cookie) }
    var field by rememberSaveable { mutableStateOf(viewModel.query) }

    LaunchedEffect(viewModel.query) { localViewModel.query.value = viewModel.query }

    val scopes = listOf(
        Scope(stringResource(R.string.liquid_search_scope_all), null),
        Scope(stringResource(R.string.songs), YouTube.SearchFilter.FILTER_SONG),
        Scope(stringResource(R.string.artists), YouTube.SearchFilter.FILTER_ARTIST),
        Scope(stringResource(R.string.liquid_videos), YouTube.SearchFilter.FILTER_VIDEO),
        Scope(stringResource(R.string.albums), YouTube.SearchFilter.FILTER_ALBUM),
        Scope(stringResource(R.string.playlists), YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST),
        Scope(stringResource(R.string.library_search_featured), YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST),
    )

    LaunchedEffect(listState, filter) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (filter != null && last != null && total > 0 && last >= total - 5) viewModel.loadMore()
            }
    }

    // a new search from this page replaces it instead of stacking another results page on top
    val resubmit: (String) -> Unit = { text ->
        val q = text.trim()
        if (q.isNotEmpty() && q != viewModel.query) {
            if (!pauseHistory) scope.launch(Dispatchers.IO) { database.query { insert(SearchHistory(query = q)) } }
            navController.popBackStack()
            navController.navigate("search/${URLEncoder.encode(q, "UTF-8")}")
        }
    }

    LibraryScaffold(
        title = stringResource(R.string.liquid_search),
        state = listState,
        query = field,
        onQueryChange = { field = it },
        placeholder = stringResource(R.string.liquid_search_placeholder),
        onSearch = resubmit,
        clearOnBack = false,
        avatar = {
            LibraryAvatar(
                imageUrl = if (loggedIn) accountImageUrl else null,
                onClick = { navController.navigate("settings") },
            )
        },
        fieldLeading = {
            RowGlyph(Icons.AutoMirrored.Rounded.ArrowBack, size = 20.dp) { navController.navigateUp() }
        },
        below = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                LibraryChips(
                    labels = scopes.map { it.label },
                    selected = scopes.indexOfFirst { it.filter?.value == filter?.value }.coerceAtLeast(0),
                    onSelect = { viewModel.filter.value = scopes[it].filter },
                    modifier = Modifier.padding(horizontal = PageMargin),
                )
            }
        },
    ) {
        if (filter == null) {
            val libraryHits = listOf(LocalFilter.SONG, LocalFilter.ALBUM, LocalFilter.ARTIST, LocalFilter.PLAYLIST)
                .flatMap { local.map[it].orEmpty() }
                .take(4)
            if (libraryHits.isNotEmpty()) {
                item(key = "lib_header") { SearchHeader(stringResource(R.string.liquid_search_in_library), ruleAbove = false) }
                items(libraryHits, key = { "lib_${it.id}" }) { item ->
                    LocalSearchRow(item, actions, isActive = item.id == mediaMetadata?.id, rule = true)
                }
                item(key = "lib_gap") { Spacer(Modifier.height(18.dp)) }
            }

            val summary = viewModel.summary
            val top = summary?.top
            when {
                summary == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { LoadingDots() }
                }

                top == null -> if (libraryHits.isEmpty()) {
                    item(key = "empty") { LibraryEmptyLine(stringResource(R.string.liquid_no_results)) }
                }

                else -> {
                    item(key = "top_header") {
                        SearchHeader(stringResource(R.string.liquid_top_results), ruleAbove = libraryHits.isNotEmpty())
                    }
                    item(key = "top_card") {
                        TopResultCard(top, actions, active = top.id == mediaMetadata?.id, playing = isPlaying)
                        Spacer(Modifier.height(22.dp))
                    }
                    summary.sections.forEach { section ->
                        val rows = section.items.filter { it.id != top.id }
                        if (rows.isNotEmpty()) {
                            item(key = "sum_header_${section.shelf}") { SearchHeader(shelfTitle(section.shelf)) }
                            items(rows, key = { "sum_${section.shelf}_${it.id.ifBlank { "t:${it.title}" }}" }) { item ->
                                YouTubeResultRow(item, actions, isActive = item.id == mediaMetadata?.id)
                            }
                            item(key = "sum_gap_${section.shelf}") { Spacer(Modifier.height(18.dp)) }
                        }
                    }
                }
            }
        } else {
            val page = viewModel.viewStateMap[filter?.value]
            when {
                page == null -> item(key = "loading_f") {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { LoadingDots() }
                }

                page.items.isEmpty() -> item(key = "empty_f") { LibraryEmptyLine(stringResource(R.string.liquid_no_results)) }

                else -> items(page.items, key = { "f_${it.id}" }) { item ->
                    YouTubeResultRow(item, actions, isActive = item.id == mediaMetadata?.id)
                }
            }
        }
    }
}

// the likeliest match gets a card of its own, its cover blurred behind it
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopResultCard(item: YTItem, actions: LiquidActions, active: Boolean, playing: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val blur = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.graphics.RenderEffect.createBlurEffect(70f, 70f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else {
            null
        }
    }
    val kind = when (item.searchKind()) {
        SearchKind.Song -> stringResource(R.string.liquid_song)
        SearchKind.MusicVideo -> stringResource(R.string.liquid_music_video)
        SearchKind.Video, SearchKind.Unsupported -> stringResource(R.string.liquid_video)
        SearchKind.Album -> stringResource(R.string.liquid_album)
        SearchKind.Artist -> stringResource(R.string.liquid_artist)
        SearchKind.Playlist -> stringResource(R.string.liquid_playlist)
    }
    val detail = listOfNotNull(
        kind,
        item.subtitleText()?.takeIf { it.isNotBlank() },
        (item as? SongItem)?.duration?.let(::clock),
    ).joinToString(" · ")
    Box(
        Modifier
            .padding(horizontal = PageMargin)
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF151517))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { actions.open(item) },
                onLongClick = { actions.menu(item) },
            ),
    ) {
        if (item.thumbnail != null) {
            AsyncImage(
                model = item.thumbnail?.resize(256, 256),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = 0.55f
                        renderEffect = blur
                    },
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.55f to Color.Black.copy(alpha = 0.62f),
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
            )
        }
        Column(Modifier.padding(16.dp)) {
            Artwork(
                model = item.thumbnail?.resize(400, 400),
                shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(10.dp),
                hairline = false,
                modifier = Modifier.size(108.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = detail,
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item !is ArtistItem) {
                    val play = remember { MutableInteractionSource() }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .size(46.dp)
                            .pressScale(play, 0.9f)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(interactionSource = play, indication = null) {
                                if (active) actions.togglePlayPause() else actions.open(item)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (active && playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodSectionTitle(title: String) {
    Column(Modifier.padding(top = 18.dp, bottom = 16.dp)) {
        Text(
            text = title,
            style = LiquidTypography.title1,
            color = Liquid.colors.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = PageMargin),
        )
        Spacer(Modifier.height(12.dp))
        LibraryRule()
    }
}

// each mood wears its own colour, and two of its playlists lean out of the right edge
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoodCard(
    title: String,
    color: Color,
    covers: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val top = lerp(colors.secondaryBackground, color, if (colors.isDark) 0.52f else 0.26f)
    val bottom = lerp(colors.secondaryBackground, color, if (colors.isDark) 0.22f else 0.12f)
    Box(
        modifier = modifier
            .height(86.dp)
            .pressScale(interaction, 0.96f)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(top, bottom)))
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Text(
            text = title,
            color = colors.label,
            fontSize = 19.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp, end = 56.dp),
        )
        MoodCovers(
            covers = covers,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 8.dp),
        )
    }
}

@Composable
private fun MoodCovers(covers: List<String>, modifier: Modifier = Modifier) {
    if (covers.isEmpty()) return
    val tile = 58.dp
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.size(width = tile * 1.6f, height = tile + 8.dp)) {
        covers.getOrNull(1)?.let { back ->
            Artwork(
                model = back.resize(180, 180),
                shape = shape,
                hairline = false,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(tile)
                    .graphicsLayer { rotationZ = -12f },
            )
        }
        Artwork(
            model = covers[0].resize(180, 180),
            shape = shape,
            hairline = false,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(tile)
                .graphicsLayer { rotationZ = 6f },
        )
    }
}
