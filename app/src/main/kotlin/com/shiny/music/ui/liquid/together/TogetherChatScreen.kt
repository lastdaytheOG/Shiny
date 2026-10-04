package com.shiny.music.ui.liquid.together

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.shiny.music.R
import com.shiny.music.together.TogetherChatMessage
import com.shiny.music.ui.component.backdrop.backdrops.layerBackdrop
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.LocalLiquidBackdrop
import com.shiny.music.ui.liquid.ScrollEdgeEffect
import com.shiny.music.ui.liquid.liquidGlass
import com.shiny.music.ui.liquid.rememberLiquidBackdrop
import com.shiny.music.ui.liquid.statusBarHeight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The session's chat as Messages: your bubbles on the right in the accent, everyone else's
 * on the left with their monogram at the end of a run, a glass composer floating at the
 * bottom, and a long press to reply.
 */
@Composable
fun TogetherChatScreen(navController: NavController) {
    val session = LocalTogether.current ?: return
    val state by session.state.collectAsState()
    val colors = Liquid.colors
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val messages = state.room?.chat.orEmpty()
    var text by rememberSaveable { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<TogetherChatMessage?>(null) }
    var composerHeight by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val backdrop = rememberLiquidBackdrop(colors.background)
    val topInset = statusBarHeight()

    DisposableEffect(session) {
        session.chatOpen = true
        onDispose { session.chatOpen = false }
    }
    LaunchedEffect(state.isLive) {
        if (!state.isLive) navController.navigateUp()
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send() {
        if (text.isBlank()) return
        session.chat(text, replyingTo)
        text = ""
        replyingTo = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))
    ) {
        CompositionLocalProvider(LocalLiquidBackdrop provides null) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = topInset + 64.dp,
                    bottom = with(density) { composerHeight.toDp() } + 12.dp,
                ),
            ) {
                if (messages.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Rounded.ChatBubble,
                            title = stringResource(R.string.together_chat_empty),
                            message = stringResource(R.string.together_chat_empty_hint),
                        )
                    }
                }
                itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                    val previous = messages.getOrNull(index - 1)
                    val following = messages.getOrNull(index + 1)
                    Bubble(
                        message = message,
                        isMe = message.from.id == state.meId,
                        firstOfRun = previous?.from?.id != message.from.id,
                        lastOfRun = following?.from?.id != message.from.id,
                        onReply = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            replyingTo = message
                        },
                    )
                }
            }
        }

        ScrollEdgeEffect(
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + 70.dp),
        )

        CompositionLocalProvider(LocalLiquidBackdrop provides backdrop) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidBackButton(onClick = { navController.navigateUp() })
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.together_messages), style = LiquidTypography.headline, color = colors.label)
                    Text(
                        stringResource(R.string.together_listening_count, state.listeners),
                        style = LiquidTypography.caption1,
                        color = colors.secondaryLabel,
                    )
                }
                Spacer(Modifier.width(44.dp))
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { composerHeight = it.height }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                AnimatedVisibility(
                    visible = replyingTo != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    val reply = replyingTo
                    Row(
                        Modifier
                            .padding(bottom = 8.dp)
                            .fillMaxWidth()
                            .liquidGlass(RoundedCornerShape(18.dp))
                            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(colors.accent))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(reply?.from?.name.orEmpty(), style = LiquidTypography.caption1.copy(fontWeight = FontWeight.SemiBold), color = colors.accent)
                            Text(reply?.text.orEmpty(), style = LiquidTypography.footnote, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).clickable { replyingTo = null },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp)) }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it.take(500) },
                        maxLines = 5,
                        textStyle = LiquidTypography.body.copy(color = colors.label),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        decorationBox = { inner ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .liquidGlass(RoundedCornerShape(22.dp))
                                    .padding(horizontal = 16.dp, vertical = 11.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (text.isEmpty()) Text(stringResource(R.string.together_chat_placeholder), style = LiquidTypography.body, color = colors.tertiaryLabel)
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    AnimatedVisibility(
                        visible = text.isNotBlank(),
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                    ) {
                        Box(
                            Modifier
                                .padding(start = 8.dp, bottom = 4.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                                .clickable { send() },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.send), tint = Color.White, modifier = Modifier.size(22.dp)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    message: TogetherChatMessage,
    isMe: Boolean,
    firstOfRun: Boolean,
    lastOfRun: Boolean,
    onReply: () -> Unit,
) {
    val colors = Liquid.colors
    val bubbleColor = if (isMe) colors.accent else if (colors.isDark) Color(0xFF26262A) else Color(0xFFE9E9EB)
    val textColor = if (isMe) Color.White else colors.label
    val big = 20.dp
    val small = 6.dp
    val shape = if (isMe) {
        RoundedCornerShape(topStart = big, topEnd = if (firstOfRun) big else small, bottomEnd = if (lastOfRun) big else small, bottomStart = big)
    } else {
        RoundedCornerShape(topStart = if (firstOfRun) big else small, topEnd = big, bottomEnd = big, bottomStart = if (lastOfRun) big else small)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (firstOfRun) 10.dp else 2.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
    ) {
        if (!isMe && firstOfRun) {
            Text(
                message.from.name,
                style = LiquidTypography.caption1,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 50.dp, bottom = 3.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (!isMe) {
                Box(Modifier.width(38.dp)) { if (lastOfRun) Monogram(message.from.name, size = 30.dp) }
            }
            Column(
                Modifier
                    .widthIn(max = 290.dp)
                    .clip(shape)
                    .background(bubbleColor)
                    .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}, onLongClick = onReply)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                message.replyTo?.let { reply ->
                    Row(
                        Modifier
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isMe) Color.White.copy(alpha = 0.18f) else colors.fill)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text(reply.name, style = LiquidTypography.caption1.copy(fontWeight = FontWeight.SemiBold), color = textColor.copy(alpha = 0.9f), maxLines = 1)
                            Text(reply.text, style = LiquidTypography.footnote, color = textColor.copy(alpha = 0.75f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text(linkified(message.text, if (isMe) Color.White else colors.accent), style = LiquidTypography.body, color = textColor)
            }
        }
        if (lastOfRun) {
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.at)),
                style = LiquidTypography.caption2,
                color = colors.tertiaryLabel,
                modifier = Modifier.padding(start = if (isMe) 0.dp else 50.dp, end = 4.dp, top = 3.dp),
            )
        }
    }
}

/** Song links in a message open in Shiny. */
@Composable
private fun linkified(text: String, linkColor: Color): AnnotatedString {
    val context = LocalContext.current
    val regex = remember { Regex("""(https?://(?:music\.youtube\.com|shinymusic\.vercel\.app|youtu\.be|(?:www\.)?youtube\.com)/[\w\-.?&=%/]*)""") }
    val matches = regex.findAll(text).toList()
    if (matches.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var last = 0
        matches.forEach { match ->
            append(text.substring(last, match.range.first))
            val url = match.value
            withLink(
                LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)),
                    linkInteractionListener = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).setPackage(context.packageName))
                        }.onFailure {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                        }
                    },
                )
            ) { append(url) }
            last = match.range.last + 1
        }
        if (last < text.length) append(text.substring(last))
    }
}
