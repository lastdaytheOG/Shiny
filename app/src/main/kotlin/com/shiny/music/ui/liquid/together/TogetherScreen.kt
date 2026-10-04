package com.shiny.music.ui.liquid.together

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.shiny.music.ui.component.LocalMenuState
import com.shiny.music.R
import com.shiny.music.together.MODE_PHONE
import com.shiny.music.together.MODE_REMOTE
import com.shiny.music.together.TogetherMember
import com.shiny.music.together.TogetherPlayback
import com.shiny.music.together.TogetherPreview
import com.shiny.music.together.TogetherQueueItem
import com.shiny.music.together.TogetherSession
import com.shiny.music.together.TogetherSyncPolicy
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.ArtworkTones
import com.shiny.music.ui.liquid.ButtonTone
import com.shiny.music.ui.liquid.Hairline
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidAlert
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.LiquidButton
import com.shiny.music.ui.liquid.LiquidPullDownMenu
import com.shiny.music.ui.liquid.LiquidSegmentedControl
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.MenuAction
import com.shiny.music.ui.liquid.MenuDivider
import com.shiny.music.ui.liquid.NavigationRow
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.rememberArtworkTones
import com.shiny.music.ui.liquid.rememberRowHighlight
import com.shiny.music.ui.liquid.settings.SettingsToggleRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Listen Together. Out of a session it explains itself in one screen and offers two
 * doors — start one, or join with a code (with a look at the session before you knock).
 * In a session it is the room: what's playing and where, controls or a skip vote,
 * reactions, the shared Up Next with who added what, the people, messages, and — for the
 * host — the house rules.
 */
@Composable
fun TogetherScreen(navController: NavController, showBack: Boolean) {
    val session = LocalTogether.current ?: return
    val state by session.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(session) {
        session.notices.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    when {
        state.isLive -> LiveSession(session, state, navController, showBack)
        state.active -> Arriving(session, state, navController, showBack)
        else -> Lobby(session, state, navController, showBack)
    }
}

// ---------------------------------------------------------------------------------------
// Lobby
// ---------------------------------------------------------------------------------------

@Composable
private fun Lobby(
    session: TogetherSession,
    state: TogetherSession.State,
    navController: NavController,
    showBack: Boolean,
) {
    val colors = Liquid.colors
    val context = LocalContext.current
    var joining by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<TogetherPreview?>(null) }
    var looking by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }
    val configured = session.serverConfigured
    var alertShownFor by rememberSaveable { mutableLongStateOf(0L) }

    // A start or join that couldn't reach the server says so out loud, once.
    if (state.ending == TogetherSession.Ending.Unreachable && state.endedAt > alertShownFor) {
        val host = remember { com.shiny.music.together.TogetherServer.base(context)?.substringAfter("://").orEmpty() }
        LiquidAlert(
            title = stringResource(R.string.together_unreachable_title),
            message = stringResource(R.string.together_unreachable_message, host),
            confirmLabel = stringResource(R.string.login_retry),
            dismissLabel = stringResource(android.R.string.ok),
            onConfirm = session::start,
            onDismiss = { alertShownFor = state.endedAt },
        )
    }

    LaunchedEffect(code) {
        preview = null
        notFound = false
        if (code.length != 6) return@LaunchedEffect
        looking = true
        val found = session.preview(code)
        looking = false
        preview = found
        notFound = found == null
    }

    LargeTitlePage(
        title = stringResource(R.string.listen_together),
        navigationButton = if (showBack) ({ LiquidBackButton(onClick = { navController.navigateUp() }) }) else null,
        scrollToTopSignal = if (showBack) null else navController,
        actions = {
            com.shiny.music.ui.liquid.GlassIconButton(
                icon = Icons.Rounded.Settings,
                onClick = { navController.navigate("settings/together") },
                contentDescription = stringResource(R.string.settings),
            )
        },
    ) {
        item(key = "hero") { LobbyHero() }

        state.ending?.let { ending ->
            item(key = "ending") {
                endingText(ending)?.let { text ->
                    Text(
                        text = text,
                        style = LiquidTypography.subheadline,
                        color = colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PageMargin + 12.dp)
                            .padding(bottom = 14.dp),
                    )
                }
            }
        }

        item(key = "doors") {
            Column(Modifier.padding(horizontal = PageMargin)) {
                if (!configured) {
                    Text(
                        text = stringResource(R.string.together_no_server),
                        style = LiquidTypography.subheadline,
                        color = colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    )
                    LiquidButton(
                        text = stringResource(R.string.together_set_up_server),
                        tone = ButtonTone.Tinted,
                        height = 52.dp,
                        onClick = { navController.navigate("settings/together") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    return@Column
                }
                LiquidButton(
                    text = stringResource(R.string.together_start),
                    icon = Icons.Rounded.Headphones,
                    tone = ButtonTone.Filled,
                    height = 52.dp,
                    onClick = session::start,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                LiquidButton(
                    text = stringResource(R.string.together_join),
                    tone = ButtonTone.Tinted,
                    height = 52.dp,
                    onClick = { joining = !joining },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "join") {
            AnimatedVisibility(
                visible = joining && configured,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.padding(horizontal = PageMargin).padding(top = 22.dp)) {
                    CodeCells(code = code, onCode = { code = it }, error = notFound, modifier = Modifier.fillMaxWidth())
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when {
                                notFound -> stringResource(R.string.together_code_not_found)
                                looking -> stringResource(R.string.together_code_looking)
                                else -> stringResource(R.string.together_code_hint)
                            },
                            style = LiquidTypography.footnote,
                            color = if (notFound) colors.destructive else colors.secondaryLabel,
                            modifier = Modifier.weight(1f),
                        )
                        LiquidButton(
                            text = stringResource(R.string.together_paste),
                            icon = Icons.Rounded.ContentPaste,
                            height = 36.dp,
                            onClick = {
                                val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
                                TogetherSession.normalizeCode(clip)?.let { code = it }
                            },
                        )
                    }
                    preview?.let { found ->
                        Spacer(Modifier.height(16.dp))
                        PreviewCard(found, onJoin = { session.join(found.code) })
                    }
                }
            }
        }

        item(key = "how_title") {
            Text(
                text = stringResource(R.string.together_how_title),
                style = LiquidTypography.title2,
                color = colors.label,
                modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = 36.dp, bottom = 6.dp),
            )
        }
        item(key = "how") {
            Column {
                HowRow(Icons.AutoMirrored.Rounded.QueueMusic, R.string.together_how_queue_title, R.string.together_how_queue_text)
                HowRow(Icons.Rounded.HowToVote, R.string.together_how_vote_title, R.string.together_how_vote_text)
                HowRow(Icons.Rounded.PhoneAndroid, R.string.together_how_listen_title, R.string.together_how_listen_text)
                HowRow(Icons.Rounded.ChatBubble, R.string.together_how_react_title, R.string.together_how_react_text, last = true)
            }
        }
        item(key = "who") {
            Text(
                text = stringResource(R.string.together_you_appear_as, session.displayName),
                style = LiquidTypography.footnote,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin, vertical = 22.dp)
                    .combinedClickableNoRipple { navController.navigate("settings/together") },
            )
        }
    }
}

/** Two friends and you, with the sound between you: the idea of the feature, drawn once. */
@Composable
private fun LobbyHero() {
    val colors = Liquid.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 260.dp, height = 150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                listOf(0.16f, 0.10f, 0.06f).forEachIndexed { i, alpha ->
                    drawCircle(
                        color = colors.accent.copy(alpha = alpha),
                        radius = 52.dp.toPx() + i * 22.dp.toPx(),
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            Monogram("Ana", size = 50.dp, modifier = Modifier.offset(x = (-84).dp, y = 10.dp), ring = colors.background)
            Monogram("Jo", size = 50.dp, modifier = Modifier.offset(x = 84.dp, y = 10.dp), ring = colors.background)
            Box(
                Modifier
                    .size(78.dp)
                    .shadow(18.dp, CircleShape, ambientColor = colors.accent, spotColor = colors.accent)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(colors.accent, lerp(colors.accent, Color(0xFF5E0B23), 0.55f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(38.dp))
            }
            Text("🔥", fontSize = 22.sp, modifier = Modifier.offset(x = (-44).dp, y = (-50).dp))
            Text("❤️", fontSize = 20.sp, modifier = Modifier.offset(x = 52.dp, y = (-54).dp))
        }
        Text(
            text = stringResource(R.string.together_headline),
            style = LiquidTypography.title2,
            color = colors.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = PageMargin, vertical = 6.dp),
        )
        Text(
            text = stringResource(R.string.together_pitch),
            style = LiquidTypography.subheadline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = PageMargin + 8.dp),
        )
    }
}

@Composable
private fun HowRow(icon: ImageVector, title: Int, text: Int, last: Boolean = false) {
    val colors = Liquid.colors
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PageMargin, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.padding(top = 1.dp).size(24.dp))
            Column(Modifier.padding(start = 16.dp)) {
                Text(stringResource(title), style = LiquidTypography.headline, color = colors.label)
                Text(
                    stringResource(text),
                    style = LiquidTypography.subheadline,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (!last) Hairline(Modifier.align(Alignment.BottomStart), startIndent = PageMargin + 40.dp)
    }
}

@Composable
private fun PreviewCard(preview: TogetherPreview, onJoin: () -> Unit) {
    val colors = Liquid.colors
    val tones = rememberArtworkTones(preview.playing?.thumbnail, ArtworkTones(Color(0xFF2C2C30), Color(0xFF48484E)))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(tones.deep, lerp(tones.deep, Color.Black, 0.45f))))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                model = preview.playing?.thumbnail,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(64.dp),
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = preview.host?.let { stringResource(R.string.together_session_of, it) } ?: stringResource(R.string.listen_together),
                    style = LiquidTypography.headline,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = preview.playing?.let { "${it.title} · ${it.artist}" } ?: stringResource(R.string.together_nothing_playing),
                    style = LiquidTypography.subheadline,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.together_listening_count, preview.listeners),
                        if (preview.approval == "ask") stringResource(R.string.together_host_lets_you_in) else null,
                    ).joinToString(" · "),
                    style = LiquidTypography.footnote,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        LiquidButton(
            text = stringResource(R.string.together_join_now),
            tone = ButtonTone.Filled,
            height = 48.dp,
            containerColor = Color.White,
            contentColor = Color.Black,
            onClick = onJoin,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------------------
// On the way in
// ---------------------------------------------------------------------------------------

@Composable
private fun Arriving(
    session: TogetherSession,
    state: TogetherSession.State,
    navController: NavController,
    showBack: Boolean,
) {
    val colors = Liquid.colors
    LargeTitlePage(
        title = stringResource(R.string.listen_together),
        navigationButton = if (showBack) ({ LiquidBackButton(onClick = { navController.navigateUp() }) }) else null,
    ) {
        item(key = "arriving") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin, vertical = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ActivityIndicator(size = 34.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = when (state.phase) {
                        TogetherSession.Phase.Starting -> stringResource(R.string.together_starting)
                        TogetherSession.Phase.Waiting -> state.waitingFor?.let { stringResource(R.string.together_waiting_for, it) }
                            ?: stringResource(R.string.together_waiting)
                        else -> stringResource(R.string.together_joining, spacedCode(state.code.orEmpty()))
                    },
                    style = LiquidTypography.title3,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                )
                if (state.phase == TogetherSession.Phase.Waiting) {
                    Text(
                        text = stringResource(R.string.together_waiting_hint),
                        style = LiquidTypography.subheadline,
                        color = colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(28.dp))
                LiquidButton(text = stringResource(android.R.string.cancel), onClick = session::cancelJoin)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// In a session
// ---------------------------------------------------------------------------------------

@Composable
private fun LiveSession(
    session: TogetherSession,
    state: TogetherSession.State,
    navController: NavController,
    showBack: Boolean,
) {
    val colors = Liquid.colors
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val room = state.room ?: return
    var showInvite by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var personMenu by remember { mutableStateOf<String?>(null) }
    var queueMenu by remember { mutableStateOf<String?>(null) }
    var showWholeQueue by rememberSaveable { mutableStateOf(false) }
    val people = room.members.sortedWith(compareByDescending<TogetherMember> { it.id == room.hostId }.thenBy { it.joinedAt })

    LargeTitlePage(
        title = stringResource(R.string.listen_together),
        navigationButton = if (showBack) ({ LiquidBackButton(onClick = { navController.navigateUp() }) }) else null,
        scrollToTopSignal = if (showBack) null else navController,
        actions = {
            com.shiny.music.ui.liquid.GlassIconButton(
                icon = Icons.Rounded.IosShare,
                onClick = { showInvite = true },
                contentDescription = stringResource(R.string.together_invite),
            )
        },
    ) {
        if (state.reconnecting) {
            item(key = "reconnecting") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = PageMargin, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActivityIndicator(size = 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.together_reconnecting), style = LiquidTypography.footnote, color = colors.secondaryLabel)
                }
            }
        }

        item(key = "stage") {
            Box {
                SessionStage(session, state, onInvite = { showInvite = true })
                TogetherReactionsLayer(
                    session = session,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        if (state.isHost && room.requests.isNotEmpty()) {
            room.requests.forEach { request ->
                item(key = "req_${request.id}") {
                    RequestRow(
                        name = request.name,
                        onLetIn = { session.approve(request.id) },
                        onDecline = { session.deny(request.id) },
                    )
                }
            }
        }

        item(key = "reactions") {
            ReactionBar(
                onReact = session::react,
                modifier = Modifier.padding(horizontal = PageMargin).padding(top = 14.dp),
            )
        }

        // Finding the next song is the main thing people do in a session, so the search is
        // on the page rather than behind the Up Next button. Whoever drives plays from it.
        if (state.canAdd) {
            item(key = "search") {
                SearchEntry(
                    placeholder = stringResource(if (state.canControl) R.string.together_search_play else R.string.together_search_add),
                    onClick = {
                        menuState.show {
                            TogetherAddSongs(session = session, onDismiss = menuState::dismiss, focusSearch = true)
                        }
                    },
                    modifier = Modifier.padding(horizontal = PageMargin).padding(top = 16.dp),
                )
            }
        }

        if (state.isGuest) {
            item(key = "mode") {
                Column(Modifier.padding(horizontal = PageMargin).padding(top = 26.dp)) {
                    LiquidSegmentedControl(
                        items = listOf(stringResource(R.string.together_mode_phone), stringResource(R.string.together_mode_remote)),
                        selectedIndex = if (state.mode == MODE_REMOTE) 1 else 0,
                        onSelect = { session.setMode(if (it == 1) MODE_REMOTE else MODE_PHONE) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SyncLine(session, state)
                }
            }
        }

        // ---- Up Next --------------------------------------------------------------------
        item(key = "queue_header") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = PageMargin, end = PageMargin - 4.dp, top = 30.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.together_up_next), style = LiquidTypography.title2, color = colors.label)
                    if (room.settings.votesReorder && room.queue.isNotEmpty()) {
                        Text(stringResource(R.string.together_votes_hint), style = LiquidTypography.footnote, color = colors.secondaryLabel)
                    }
                }
                if (state.canAdd) {
                    LiquidButton(
                        text = stringResource(R.string.together_add_songs),
                        icon = Icons.Rounded.Add,
                        height = 36.dp,
                        onClick = {
                            menuState.show {
                                TogetherAddSongs(session = session, onDismiss = menuState::dismiss)
                            }
                        },
                    )
                }
            }
        }
        if (room.queue.isEmpty()) {
            item(key = "queue_empty") {
                Text(
                    text = stringResource(if (state.canAdd) R.string.together_queue_empty else R.string.together_queue_empty_locked),
                    style = LiquidTypography.subheadline,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = PageMargin, vertical = 10.dp),
                )
            }
        }
        val shownQueue = if (showWholeQueue) room.queue.take(100) else room.queue.take(QueuePreview)
        shownQueue.forEachIndexed { index, item ->
            item(key = "q_${item.uid}") {
                val voters = room.votes[item.track.id].orEmpty()
                val canRemove = state.isHost || item.addedBy?.id == state.meId
                Box {
                    QueueRow(
                        item = item,
                        votes = voters.size,
                        voted = state.meId in voters,
                        onVote = { session.vote(item.track.id, on = state.meId !in voters) },
                        onMore = if (canRemove) ({ queueMenu = item.uid }) else null,
                        last = index == shownQueue.lastIndex,
                    )
                    Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)) {
                        LiquidPullDownMenu(
                            expanded = queueMenu == item.uid,
                            onDismiss = { queueMenu = null },
                            entries = listOf(
                                MenuAction(stringResource(R.string.together_remove_song), icon = Icons.Rounded.Delete, destructive = true) {
                                    session.remove(item)
                                },
                            ),
                        )
                    }
                }
            }
        }

        if (room.queue.size > QueuePreview) {
            item(key = "queue_more") {
                LiquidButton(
                    text = if (showWholeQueue) stringResource(R.string.together_show_less)
                           else stringResource(R.string.together_show_all, room.queue.size),
                    height = 38.dp,
                    onClick = { showWholeQueue = !showWholeQueue },
                    modifier = Modifier.padding(start = PageMargin, top = 8.dp),
                )
            }
        }

        // ---- People -----------------------------------------------------------------------
        item(key = "people_header") {
            Text(
                text = stringResource(R.string.together_people, state.listeners),
                style = LiquidTypography.title2,
                color = colors.label,
                modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = 32.dp, bottom = 4.dp),
            )
        }
        people.forEachIndexed { index, person ->
            item(key = "p_${person.id}") {
                val manageable = state.isHost && person.id != state.meId
                Box {
                    PersonRow(
                        person = person,
                        isMe = person.id == state.meId,
                        isHost = person.id == room.hostId,
                        hostName = state.host?.name.orEmpty(),
                        onMore = if (manageable) ({ personMenu = person.id }) else null,
                        last = index == people.lastIndex,
                    )
                    Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)) {
                        LiquidPullDownMenu(
                            expanded = personMenu == person.id,
                            onDismiss = { personMenu = null },
                            entries = listOf(
                                MenuAction(stringResource(R.string.together_make_host), icon = Icons.Rounded.Star) {
                                    session.makeHost(person.id)
                                },
                                MenuDivider,
                                MenuAction(stringResource(R.string.together_remove_person), icon = Icons.Rounded.PersonRemove, destructive = true) {
                                    session.removeMember(person.id, block = false)
                                },
                                MenuAction(stringResource(R.string.together_block_person), icon = Icons.Rounded.Block, destructive = true) {
                                    session.removeMember(person.id, block = true)
                                },
                            ),
                        )
                    }
                }
            }
        }

        item(key = "messages") {
            Spacer(Modifier.height(20.dp))
            NavigationRow(
                icon = Icons.Rounded.ChatBubble,
                title = stringResource(R.string.together_messages),
                detail = if (state.unreadChat > 0) state.unreadChat.toString() else null,
                onClick = { navController.navigate("together/chat") },
                showSeparator = false,
            )
        }

        if (state.isHost) {
            item(key = "rules") {
                Text(
                    text = stringResource(R.string.together_rules).uppercase(),
                    style = LiquidTypography.caption1.copy(fontWeight = FontWeight.SemiBold),
                    letterSpacing = 0.9.sp,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = 30.dp, bottom = 6.dp),
                )
                SettingsToggleRow(
                    title = stringResource(R.string.together_rule_approval),
                    subtitle = stringResource(R.string.together_rule_approval_desc),
                    checked = room.settings.approval == "ask",
                    onCheckedChange = { session.updateSettings(approval = if (it) "ask" else "open") },
                )
                SettingsToggleRow(
                    title = stringResource(R.string.together_rule_add),
                    subtitle = stringResource(R.string.together_rule_add_desc),
                    checked = room.settings.guestsCanAdd,
                    onCheckedChange = { session.updateSettings(guestsCanAdd = it) },
                )
                SettingsToggleRow(
                    title = stringResource(R.string.together_rule_control),
                    subtitle = stringResource(R.string.together_rule_control_desc),
                    checked = room.settings.guestsCanControl,
                    onCheckedChange = { session.updateSettings(guestsCanControl = it) },
                )
                SettingsToggleRow(
                    title = stringResource(R.string.together_rule_votes),
                    subtitle = stringResource(R.string.together_rule_votes_desc),
                    checked = room.settings.votesReorder,
                    onCheckedChange = { session.updateSettings(votesReorder = it) },
                    divider = false,
                )
            }
        }

        item(key = "leave") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin)
                    .padding(top = 32.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LiquidButton(
                    text = stringResource(R.string.together_leave),
                    contentColor = colors.destructive,
                    height = 50.dp,
                    onClick = { confirmLeave = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.isHost) {
                    LiquidButton(
                        text = stringResource(R.string.together_end),
                        tone = ButtonTone.Filled,
                        containerColor = colors.destructive,
                        height = 50.dp,
                        onClick = { confirmEnd = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showInvite) {
        InviteDialog(
            code = room.code,
            link = state.inviteUrl ?: session.inviteUrl(room.code),
            onDismiss = { showInvite = false },
        )
    }
    if (confirmLeave) {
        LiquidAlert(
            title = stringResource(R.string.together_leave_title),
            message = if (state.isHost) stringResource(R.string.together_leave_host_message) else null,
            confirmLabel = stringResource(R.string.together_leave),
            destructive = true,
            onConfirm = { session.leave() },
            onDismiss = { confirmLeave = false },
        )
    }
    if (confirmEnd) {
        LiquidAlert(
            title = stringResource(R.string.together_end_title),
            message = stringResource(R.string.together_end_message),
            confirmLabel = stringResource(R.string.together_end_confirm),
            destructive = true,
            onConfirm = { session.end() },
            onDismiss = { confirmEnd = false },
        )
    }
}

/**
 * The room's now playing, on a field of the song's own colour: the cover, who added it,
 * where the room is, and the controls — or, for a guest the host hasn't handed control
 * to, a vote to skip and a pause that only pauses this phone.
 */
@Composable
private fun SessionStage(session: TogetherSession, state: TogetherSession.State, onInvite: () -> Unit) {
    val room = state.room ?: return
    val playback = room.playback
    val track = playback?.track
    val tones = rememberArtworkTones(track?.thumbnail, ArtworkTones(Color(0xFF2A2A2E), Color(0xFF4A4A52)))
    val addedBy = room.queue.firstOrNull { it.track.id == track?.id }?.addedBy?.name
    val onStage = Color.White
    Column(
        Modifier
            .padding(horizontal = PageMargin)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(lerp(tones.deep, tones.vivid, 0.18f), lerp(tones.deep, Color.Black, 0.5f))
                )
            )
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The session line: live dot, code, the people.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .combinedClickableNoRipple(onInvite)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF453A)))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = spacedCode(room.code),
                        style = LiquidTypography.subheadline.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                        color = onStage,
                        letterSpacing = 1.sp,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            AvatarStack(members = room.members.filter { it.connected }, ring = tones.deep, size = 28.dp)
        }

        Spacer(Modifier.height(22.dp))
        Artwork(
            model = track?.thumbnail,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .size(188.dp)
                .shadow(24.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color.Black),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = track?.title ?: stringResource(R.string.together_nothing_playing),
            style = LiquidTypography.title3,
            color = onStage,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = listOfNotNull(
                track?.artist?.takeIf { it.isNotBlank() },
                addedBy?.let { stringResource(R.string.together_added_by, it) },
            ).joinToString(" · ").ifEmpty {
                if (state.isHost) stringResource(R.string.together_play_something) else stringResource(R.string.together_host_will_play, state.host?.name.orEmpty())
            },
            style = LiquidTypography.subheadline,
            color = onStage.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        if (playback != null && track != null) {
            Spacer(Modifier.height(16.dp))
            SessionProgress(session, playback)
        }

        Spacer(Modifier.height(14.dp))
        if (state.canControl) {
            val playing = playback?.playing == true
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                StageKey(Icons.Rounded.SkipPrevious, size = 30.dp) { session.control("prev") }
                Box(
                    Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .combinedClickableNoRipple { session.control(if (playing) "pause" else "play") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint = Color.Black,
                        modifier = Modifier.size(34.dp),
                    )
                }
                StageKey(Icons.Rounded.SkipNext, size = 30.dp) { session.control("next") }
            }
        } else if (track != null) {
            val voted = state.meId in room.skip.voters && room.skip.trackId == track.id
            val votes = if (room.skip.trackId == track.id) room.skip.voters.size else 0
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StagePill(
                    text = if (votes > 0) stringResource(R.string.together_skip_votes, votes, room.skip.needed)
                           else stringResource(R.string.together_vote_skip),
                    icon = Icons.Rounded.SkipNext,
                    selected = voted,
                    onClick = { session.voteSkip(!voted) },
                )
                if (state.following) {
                    StagePill(
                        text = stringResource(if (state.locallyPaused) R.string.together_catch_up else R.string.together_pause_for_me),
                        icon = if (state.locallyPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        selected = state.locallyPaused,
                        onClick = {
                            if (state.locallyPaused) session.catchUp()
                            else session.pauseForMe()
                        },
                    )
                }
            }
        }
    }
}

/** Where the room is in the song, advanced from the host's last report by the server clock. */
@Composable
private fun SessionProgress(session: TogetherSession, playback: TogetherPlayback) {
    val position = remember { mutableLongStateOf(0L) }
    LaunchedEffect(playback) {
        while (isActive) {
            position.longValue = TogetherSyncPolicy.expectedPosition(playback, session.clock.serverNow())
            if (!playback.playing) break
            delay(250)
        }
    }
    val duration = playback.track?.durationMs?.takeIf { it > 0 } ?: 1L
    val clamped = remember(duration) { { position.longValue.coerceAtMost(duration) } }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .drawBehind {
                    val fraction = (clamped().toFloat() / duration).coerceIn(0f, 1f)
                    drawRoundRect(Color.White.copy(alpha = 0.22f), cornerRadius = CornerRadius(size.height / 2))
                    drawRoundRect(
                        Color.White,
                        size = Size(size.width * fraction, size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                },
        )
        Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
            TimeText(position, duration)
            Spacer(Modifier.weight(1f))
            Text(formatMs(duration), style = LiquidTypography.caption2, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun TimeText(position: androidx.compose.runtime.MutableLongState, duration: Long) {
    Text(formatMs(position.longValue.coerceAtMost(duration)), style = LiquidTypography.caption2, color = Color.White.copy(alpha = 0.6f))
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** Looks like the search field it opens, so it reads as one; the typing happens in the sheet. */
@Composable
private fun SearchEntry(placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Liquid.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(CircleShape)
            .background(colors.tertiaryFill)
            .combinedClickableNoRipple(onClick)
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = colors.secondaryLabel, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(8.dp))
        Text(placeholder, style = LiquidTypography.body, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StageKey(icon: ImageVector, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size)) }
}

@Composable
private fun StagePill(text: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.16f))
            .combinedClickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Color.Black else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold), color = if (selected) Color.Black else Color.White)
    }
}

/** How this phone is doing against the room, in words. */
@Composable
private fun SyncLine(session: TogetherSession, state: TogetherSession.State) {
    val colors = Liquid.colors
    val host = state.host?.name.orEmpty()
    val (dot, text) = when {
        state.mode == MODE_REMOTE -> colors.gray to stringResource(R.string.together_sync_remote, host)
        state.locallyPaused -> colors.gray to stringResource(R.string.together_sync_paused)
        state.room?.playback?.track?.local == true -> colors.gray to stringResource(R.string.together_notice_local_song)
        state.driftMs == null -> colors.gray to stringResource(R.string.together_sync_loading)
        kotlin.math.abs(state.driftMs) <= 80 -> colors.green to stringResource(R.string.together_sync_in, host)
        else -> Color(0xFFFF9F0A) to stringResource(R.string.together_sync_catching)
    }
    Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, style = LiquidTypography.footnote, color = colors.secondaryLabel)
    }
}

@Composable
private fun QueueRow(
    item: TogetherQueueItem,
    votes: Int,
    voted: Boolean,
    onVote: () -> Unit,
    onMore: (() -> Unit)?,
    last: Boolean,
) {
    val colors = Liquid.colors
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = PageMargin, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(model = item.track.thumbnail, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.track.title, style = LiquidTypography.body, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    item.addedBy?.let { person ->
                        Monogram(person.name, size = 16.dp)
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = when {
                            item.pending -> stringResource(R.string.together_adding)
                            item.addedBy != null -> "${item.addedBy.name} · ${item.track.artist}"
                            else -> item.track.artist
                        },
                        style = LiquidTypography.subheadline,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            VotePill(votes = votes, voted = voted, onClick = onVote)
            if (onMore != null) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).combinedClickableNoRipple(onMore),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.MoreHoriz, null, tint = colors.secondaryLabel, modifier = Modifier.size(20.dp)) }
            }
        }
        if (!last) Hairline(Modifier.align(Alignment.BottomStart), startIndent = PageMargin + 60.dp)
    }
}

@Composable
private fun VotePill(votes: Int, voted: Boolean, onClick: () -> Unit) {
    val colors = Liquid.colors
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (voted) colors.accent else colors.quaternaryFill)
            .combinedClickableNoRipple(onClick)
            .padding(start = 8.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = if (voted) colors.onAccent else colors.label, modifier = Modifier.size(18.dp))
        Text(
            text = votes.toString(),
            style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = if (voted) colors.onAccent else colors.label,
        )
    }
}

@Composable
private fun PersonRow(
    person: TogetherMember,
    isMe: Boolean,
    isHost: Boolean,
    hostName: String,
    onMore: (() -> Unit)?,
    last: Boolean,
) {
    val colors = Liquid.colors
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(start = PageMargin, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(person.name, size = 40.dp, away = !person.connected)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isMe) stringResource(R.string.together_you_named, person.name) else person.name,
                        style = LiquidTypography.body,
                        color = colors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isHost) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFCC00), modifier = Modifier.size(16.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (person.mode == MODE_REMOTE && !isHost) Icons.Rounded.Speaker else Icons.Rounded.PhoneAndroid,
                        null,
                        tint = colors.tertiaryLabel,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when {
                            !person.connected -> stringResource(R.string.together_person_away)
                            isHost -> stringResource(R.string.together_person_host)
                            person.mode == MODE_REMOTE -> stringResource(R.string.together_person_remote, hostName)
                            else -> stringResource(R.string.together_person_phone)
                        },
                        style = LiquidTypography.footnote,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                    )
                }
            }
            if (onMore != null) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).combinedClickableNoRipple(onMore),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.MoreHoriz, null, tint = colors.secondaryLabel, modifier = Modifier.size(20.dp)) }
            }
        }
        if (!last) Hairline(Modifier.align(Alignment.BottomStart), startIndent = PageMargin + 52.dp)
    }
}

@Composable
private fun RequestRow(name: String, onLetIn: () -> Unit, onDecline: () -> Unit) {
    val colors = Liquid.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PageMargin, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.accent.copy(alpha = 0.10f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(name, size = 38.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(name, style = LiquidTypography.headline, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.together_wants_to_join), style = LiquidTypography.footnote, color = colors.secondaryLabel)
        }
        LiquidButton(text = stringResource(R.string.together_decline), height = 34.dp, onClick = onDecline)
        Spacer(Modifier.width(6.dp))
        LiquidButton(text = stringResource(R.string.together_let_in), tone = ButtonTone.Filled, height = 34.dp, onClick = onLetIn)
    }
}

/** The invite: a QR code any camera opens, the code to read out, and the link to send. */
@Composable
private fun InviteDialog(code: String, link: String, onDismiss: () -> Unit) {
    val colors = Liquid.colors
    val context = LocalContext.current
    val surface = if (colors.isDark) Color(0xFF1C1C1E) else Color.White
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(32.dp))
                .background(surface)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.together_invite_title), style = LiquidTypography.title3, color = colors.label)
            Text(
                stringResource(R.string.together_invite_text),
                style = LiquidTypography.subheadline,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
            )
            if (link.isNotEmpty()) {
                InviteQrCode(text = link, modifier = Modifier.fillMaxWidth(0.78f))
            }
            Text(
                text = spacedCode(code),
                style = LiquidTypography.title1.copy(fontFamily = FontFamily.Monospace),
                color = colors.label,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(top = 18.dp),
            )
            Spacer(Modifier.height(18.dp))
            LiquidButton(
                text = stringResource(R.string.together_send_invite),
                icon = Icons.Rounded.IosShare,
                tone = ButtonTone.Filled,
                height = 50.dp,
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.together_invite_message, spacedCode(code), link))
                    }
                    context.startActivity(Intent.createChooser(send, null))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LiquidButton(
                text = stringResource(R.string.together_copy_code),
                icon = Icons.Rounded.ContentCopy,
                height = 50.dp,
                onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Listen Together", code))
                    Toast.makeText(context, R.string.together_code_copied, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** How much of Up Next the room page shows before "Show All". */
private const val QueuePreview = 8

@Composable
private fun endingText(ending: TogetherSession.Ending): String? = when (ending) {
    TogetherSession.Ending.Left -> null
    TogetherSession.Ending.HostEnded -> stringResource(R.string.together_ended_host)
    TogetherSession.Ending.Removed -> stringResource(R.string.together_ended_removed)
    TogetherSession.Ending.Blocked -> stringResource(R.string.together_ended_blocked)
    TogetherSession.Ending.Declined -> stringResource(R.string.together_ended_declined)
    TogetherSession.Ending.Full -> stringResource(R.string.together_ended_full)
    TogetherSession.Ending.NoSession -> stringResource(R.string.together_code_not_found)
    TogetherSession.Ending.Unreachable -> stringResource(R.string.together_ended_unreachable)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.combinedClickableNoRipple(onClick: () -> Unit): Modifier =
    combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = rememberRowHighlight(),
        onClick = onClick,
    )
