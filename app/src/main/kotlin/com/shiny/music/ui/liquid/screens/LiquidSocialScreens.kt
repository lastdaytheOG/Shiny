package com.shiny.music.ui.liquid.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.music.innertube.models.WatchEndpoint
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.R
import com.shiny.music.constants.DiscordAvatarUrlKey
import com.shiny.music.constants.DiscordListenAlongButtonKey
import com.shiny.music.constants.DiscordNameKey
import com.shiny.music.constants.DiscordShowWhenPausedKey
import com.shiny.music.constants.DiscordTokenKey
import com.shiny.music.constants.DiscordUsernameKey
import com.shiny.music.constants.EnableDiscordRPCKey
import com.shiny.music.discord.DiscordAuthCoordinator
import com.shiny.music.discord.DiscordOAuthRepository
import com.shiny.music.models.MediaMetadata
import com.shiny.music.playback.queues.YouTubeQueue
import com.shiny.music.social.Friend
import com.shiny.music.social.FriendsResponse
import com.shiny.music.social.GoogleSignIn
import com.shiny.music.social.ListeningState
import com.shiny.music.social.PresenceFreshness
import com.shiny.music.social.NowPlaying
import com.shiny.music.social.ShareMode
import com.shiny.music.social.ShinyLinks
import com.shiny.music.social.SocialApiException
import com.shiny.music.social.SocialRepository
import com.shiny.music.social.SocialUser
import com.shiny.music.ui.component.Material3SettingsGroup
import com.shiny.music.ui.component.Material3SettingsItem
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.ButtonTone
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidAlert
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.LiquidButton
import com.shiny.music.ui.liquid.LiquidSearchField
import com.shiny.music.ui.liquid.LiquidSwitch
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.NowPlayingBars
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.rememberRowHighlight
import com.shiny.music.utils.rememberPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

// Friends & Profile settings, the Discord connection, and the Friends list (Shiny social; server in /server).

private const val FRIENDS_REFRESH_MS = 20_000L
private val USERNAME = Regex("^[a-z0-9_.]{3,20}$")

@HiltViewModel
class SocialViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SocialRepository,
) : ViewModel() {
    val token: StateFlow<String?> = repository.token
    val user: StateFlow<SocialUser?> = repository.user
    val friends: StateFlow<FriendsResponse?> = repository.friends
    val privateSession: StateFlow<Boolean> = repository.privateSession

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** When [friends] was last fetched: a friend's song has moved on by this much since. */
    var friendsFetchedAt: Long = 0L
        private set

    /** One-off messages, shown as toasts. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** The server's clock minus this phone's; friends' presence timestamps are on the server's. */
    val serverClockOffsetMs: Long get() = repository.api.serverClockOffsetMs

    fun signIn(activity: Activity) = launchBusy {
        val result = GoogleSignIn.requestIdToken(activity).mapCatching { repository.signInWithGoogle(it).getOrThrow() }
        val error = result.exceptionOrNull()
        if (error == null) {
            refreshFriends()
        } else if (error !is GoogleSignIn.Cancelled) {
            report(error)
        }
    }

    fun refreshAccount() {
        viewModelScope.launch { repository.refreshAccount() }
    }

    fun refreshFriends() {
        viewModelScope.launch {
            repository.refreshFriends().onSuccess { friendsFetchedAt = System.currentTimeMillis() }
        }
    }

    fun saveUsername(username: String) = launchBusy {
        repository.setUsername(username).fold({ say(context.getString(R.string.social_username_saved)) }, ::report)
    }

    fun setShareMode(mode: ShareMode) = launchBusy { repository.setShareMode(mode).onFailure(::report) }

    fun setPrivateSession(enabled: Boolean) {
        viewModelScope.launch { repository.setPrivateSession(enabled) }
    }

    fun signOut() = launchBusy { repository.signOut() }

    fun deleteAccount() = launchBusy { repository.deleteAccount().onFailure(::report) }

    /** [onSent] runs only when the request went through, so a mistyped name stays in the field to fix. */
    fun addFriend(username: String, onSent: () -> Unit = {}) = launchBusy {
        val name = username.trim().removePrefix("@").lowercase()
        if (name.isEmpty()) return@launchBusy
        repository.requestFriend(name).fold(
            onSuccess = { status ->
                onSent()
                say(context.getString(if (status == "friends") R.string.social_now_friends else R.string.social_request_sent, name))
            },
            onFailure = ::report,
        )
    }

    fun acceptFriend(username: String) = launchBusy { repository.acceptFriend(username).onFailure(::report) }

    fun removeRequest(username: String) = launchBusy { repository.removeRequest(username).onFailure(::report) }

    fun removeFriend(username: String) = launchBusy { repository.removeFriend(username).onFailure(::report) }

    private fun launchBusy(block: suspend () -> Unit): Job =
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } finally {
                _busy.value = false
            }
        }

    private fun say(message: String) {
        messages.tryEmit(message)
    }

    private fun report(error: Throwable) {
        val message =
            when (error) {
                // The server's messages are written for people.
                is SocialApiException -> error.message
                is IOException -> context.getString(R.string.social_error, error.message ?: error.javaClass.simpleName)
                else -> error.message
            }
        say(message ?: context.getString(R.string.social_error, error.javaClass.simpleName))
    }
}

@Composable
fun SocialSettingsScreen(
    navController: NavController,
    viewModel: SocialViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val colors = Liquid.colors
    val token by viewModel.token.collectAsState()
    val user by viewModel.user.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val privateSession by viewModel.privateSession.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    CollectMessages(viewModel)
    LaunchedEffect(token) { if (token != null) viewModel.refreshAccount() }

    LargeTitlePage(
        title = stringResource(R.string.social_title),
        background = colors.groupedBackground,
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        if (token == null) {
            item(key = "sign_in") {
                IntroCard(
                    glyph = { Icon(Icons.Rounded.People, null, tint = colors.accent, modifier = Modifier.size(34.dp)) },
                    title = stringResource(R.string.social_sign_in_title),
                    message = stringResource(R.string.social_sign_in_desc),
                    actionLabel = stringResource(R.string.social_sign_in_google),
                    busy = busy,
                    busyLabel = stringResource(R.string.social_signing_in),
                    onAction = { context.findActivity()?.let { viewModel.signIn(it) } },
                )
            }
            return@LargeTitlePage
        }

        val me = user
        item(key = "account") { AccountHeader(me) }

        item(key = "username") {
            UsernameGroup(current = me?.username, busy = busy, onSave = { viewModel.saveUsername(it) })
        }

        item(key = "sharing") {
            val mode = ShareMode.fromWire(me?.shareMode)
            SettingsSection(
                title = stringResource(R.string.social_sharing),
                items = listOf(
                    Material3SettingsItem(
                        customIcon = glyph(Icons.Rounded.Lock),
                        title = { Text(stringResource(R.string.social_share_off)) },
                        trailingContent = { SelectedMark(mode == ShareMode.Off) },
                        onClick = { viewModel.setShareMode(ShareMode.Off) },
                    ),
                    Material3SettingsItem(
                        customIcon = glyph(Icons.Rounded.People),
                        title = { Text(stringResource(R.string.social_share_friends)) },
                        trailingContent = { SelectedMark(mode == ShareMode.Friends) },
                        onClick = { viewModel.setShareMode(ShareMode.Friends) },
                    ),
                    Material3SettingsItem(
                        customIcon = glyph(Icons.Rounded.Public),
                        title = { Text(stringResource(R.string.social_share_public)) },
                        description = { Text(stringResource(R.string.social_share_public_desc)) },
                        trailingContent = { SelectedMark(mode == ShareMode.Public) },
                        onClick = { viewModel.setShareMode(ShareMode.Public) },
                    ),
                    Material3SettingsItem(
                        customIcon = glyph(Icons.Rounded.VisibilityOff),
                        title = { Text(stringResource(R.string.social_private_session)) },
                        description = { Text(stringResource(R.string.social_private_session_desc)) },
                        trailingContent = { LiquidSwitch(checked = privateSession, onCheckedChange = { viewModel.setPrivateSession(it) }) },
                        onClick = { viewModel.setPrivateSession(!privateSession) },
                    ),
                ),
            )
        }

        val username = me?.username
        if (me != null && username != null) {
            item(key = "profile") {
                val profileUrl = me.profileUrl ?: ShinyLinks.profile(username)
                val badgeUrl = me.badgeUrl ?: ShinyLinks.badge(username)
                val badgeCopied = stringResource(R.string.social_badge_copied)
                SettingsSection(
                    title = stringResource(R.string.social_profile),
                    items = listOf(
                        Material3SettingsItem(
                            customIcon = glyph(Icons.Rounded.Link),
                            title = { Text(stringResource(R.string.social_profile_page)) },
                            description = { Text(profileUrl.removePrefix("https://"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = { openInBrowser(context, profileUrl) },
                        ),
                        Material3SettingsItem(
                            customIcon = glyph(Icons.Rounded.IosShare),
                            title = { Text(stringResource(R.string.social_share_profile)) },
                            onClick = { shareText(context, profileUrl) },
                        ),
                        Material3SettingsItem(
                            customIcon = glyph(Icons.Rounded.ContentCopy),
                            title = { Text(stringResource(R.string.social_badge)) },
                            onClick = {
                                copyText(context, "[![Listening on Shiny]($badgeUrl)]($profileUrl)")
                                Toast.makeText(context, badgeCopied, Toast.LENGTH_SHORT).show()
                            },
                        ),
                    ),
                    footer = if (ShareMode.fromWire(me.shareMode) == ShareMode.Public) null else stringResource(R.string.social_profile_private_hint),
                )
            }
        }

        item(key = "friends_discord") {
            SettingsSection(
                items = listOf(
                    Material3SettingsItem(
                        customIcon = glyph(Icons.Rounded.People),
                        title = { Text(stringResource(R.string.social_friends)) },
                        description = { Text(stringResource(R.string.social_friends_desc)) },
                        onClick = { navController.navigate("friends") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.discord),
                        title = { Text(stringResource(R.string.discord_settings_title)) },
                        description = { Text(stringResource(R.string.discord_settings_desc)) },
                        onClick = { navController.navigate("settings/discord") },
                    ),
                ),
            )
        }

        item(key = "account_actions") {
            SettingsSection(
                items = listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.social_sign_out)) },
                        onClick = { confirmSignOut = true },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.social_delete_account), color = colors.destructive) },
                        onClick = { confirmDelete = true },
                    ),
                ),
            )
        }
    }

    if (confirmSignOut) {
        LiquidAlert(
            title = stringResource(R.string.social_sign_out_confirm),
            confirmLabel = stringResource(R.string.social_sign_out),
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
    if (confirmDelete) {
        LiquidAlert(
            title = stringResource(R.string.social_delete_account_title),
            message = stringResource(R.string.social_delete_account_message),
            confirmLabel = stringResource(R.string.social_delete_account),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAccount()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun AccountHeader(user: SocialUser?) {
    val colors = Liquid.colors
    Row(
        modifier = Modifier
            .padding(horizontal = PageMargin)
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.secondaryGroupedBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            model = user?.avatar,
            modifier = Modifier.size(60.dp),
            shape = CircleShape,
            placeholder = Icons.Rounded.Person,
            hairline = false,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = user?.name ?: user?.username?.let { "@$it" } ?: stringResource(R.string.social_account),
                style = LiquidTypography.title3,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = user?.username?.let { "@$it" } ?: user?.email.orEmpty(),
                style = LiquidTypography.footnote,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UsernameGroup(
    current: String?,
    busy: Boolean,
    onSave: (String) -> Unit,
) {
    val colors = Liquid.colors
    var text by rememberSaveable(current) { mutableStateOf(current.orEmpty()) }
    val canSave = !busy && text != current && USERNAME.matches(text)
    val rules = stringResource(R.string.social_username_rules)
    SettingsSection(
        title = stringResource(R.string.social_username),
        items = listOf(
            Material3SettingsItem(
                customIcon = glyph(Icons.Rounded.AlternateEmail),
                title = {
                    val hint = stringResource(R.string.social_username_hint)
                    BasicTextField(
                        value = text,
                        onValueChange = { value ->
                            text = value.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '.' }.take(20)
                        },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = colors.label),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(text) }),
                        decorationBox = { field ->
                            Box {
                                if (text.isEmpty()) Text(hint, color = colors.tertiaryLabel)
                                field()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                trailingContent = {
                    Text(
                        text = stringResource(R.string.social_save),
                        style = LiquidTypography.headline,
                        color = if (canSave) colors.accent else colors.tertiaryLabel,
                        modifier = Modifier
                            .clickable(enabled = canSave) { onSave(text) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                },
            ),
        ),
        footer = if (current == null) "${stringResource(R.string.social_username_needed)} $rules" else rules,
    )
}

@Composable
fun DiscordSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val colors = Liquid.colors
    val scope = rememberCoroutineScope()
    val (token, _) = rememberPreference(DiscordTokenKey, "")
    val (name, _) = rememberPreference(DiscordNameKey, "")
    val (username, _) = rememberPreference(DiscordUsernameKey, "")
    val (avatar, _) = rememberPreference(DiscordAvatarUrlKey, "")
    val (showActivity, setShowActivity) = rememberPreference(EnableDiscordRPCKey, true)
    val (showWhenPaused, setShowWhenPaused) = rememberPreference(DiscordShowWhenPausedKey, false)
    val (listenAlong, setListenAlong) = rememberPreference(DiscordListenAlongButtonKey, true)
    var connectJob by remember { mutableStateOf<Job?>(null) }
    val connected = stringResource(R.string.discord_connect_done)
    val failed = stringResource(R.string.discord_connect_failed)

    // Discord's login page opens in the browser and comes back through DiscordOAuthCallbackActivity.
    val connect: () -> Unit = {
        val session = DiscordOAuthRepository.createAuthorizationSession()
        connectJob?.cancel()
        connectJob = scope.launch {
            try {
                // The coordinator replays the last redirect: only this attempt's state counts.
                val redirect = DiscordAuthCoordinator.redirects.first { it.getQueryParameter("state") == session.state }
                DiscordOAuthRepository.completeAuthorization(context, session, redirect)
                    .onSuccess {
                        setShowActivity(true)
                        Toast.makeText(context, connected, Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { Toast.makeText(context, failed.format(it.message ?: it.javaClass.simpleName), Toast.LENGTH_LONG).show() }
            } finally {
                connectJob = null
            }
        }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, session.authorizationUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            connectJob?.cancel()
            Toast.makeText(context, failed.format(it.message ?: it.javaClass.simpleName), Toast.LENGTH_LONG).show()
        }
    }

    // A session saved before SHINY had its own Discord application can't be used: this turns it into "Reconnect".
    LaunchedEffect(Unit) { DiscordOAuthRepository.migrateLegacySession(context) }

    LargeTitlePage(
        title = stringResource(R.string.discord_settings_title),
        background = colors.groupedBackground,
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        if (token.isBlank()) {
            // The account is still known but its session is gone: another application's, expired or revoked.
            val reconnect = name.isNotBlank() || username.isNotBlank()
            item(key = "connect") {
                IntroCard(
                    glyph = { Icon(painterResource(R.drawable.discord), null, tint = DiscordBlurple, modifier = Modifier.size(34.dp)) },
                    title = stringResource(if (reconnect) R.string.discord_reconnect_title else R.string.discord_connect),
                    message = if (reconnect) {
                        stringResource(R.string.discord_reconnect_desc, "@${username.ifBlank { name }}")
                    } else {
                        stringResource(R.string.discord_connect_desc)
                    },
                    actionLabel = stringResource(if (reconnect) R.string.discord_reconnect else R.string.discord_connect),
                    busy = connectJob != null,
                    busyLabel = stringResource(R.string.discord_connect_waiting),
                    onAction = connect,
                    onCancel = { connectJob?.cancel() },
                )
            }
            if (reconnect) {
                item(key = "forget") {
                    SettingsSection(
                        items = listOf(
                            Material3SettingsItem(
                                title = { Text(stringResource(R.string.discord_disconnect), color = colors.destructive) },
                                onClick = { scope.launch { DiscordOAuthRepository.clearSession(context) } },
                            ),
                        ),
                    )
                }
            }
            return@LargeTitlePage
        }

        item(key = "account") {
            Row(
                modifier = Modifier
                    .padding(horizontal = PageMargin)
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.secondaryGroupedBackground)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(model = avatar.ifBlank { null }, modifier = Modifier.size(52.dp), shape = CircleShape, placeholder = Icons.Rounded.Person, hairline = false)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                ) {
                    Text(name.ifBlank { username }, style = LiquidTypography.headline, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.discord_connected_as, "@${username.ifBlank { name }}"),
                        style = LiquidTypography.footnote,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item(key = "activity") {
            SettingsSection(
                items = listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.discord_show_activity)) },
                        trailingContent = { LiquidSwitch(checked = showActivity, onCheckedChange = setShowActivity) },
                        onClick = { setShowActivity(!showActivity) },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.discord_listen_along_button)) },
                        description = { Text(stringResource(R.string.discord_listen_along_button_desc)) },
                        enabled = showActivity,
                        trailingContent = { LiquidSwitch(checked = listenAlong, onCheckedChange = setListenAlong, enabled = showActivity) },
                        onClick = { setListenAlong(!listenAlong) },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.discord_show_when_paused)) },
                        description = { Text(stringResource(R.string.discord_show_when_paused_desc)) },
                        enabled = showActivity,
                        trailingContent = { LiquidSwitch(checked = showWhenPaused, onCheckedChange = setShowWhenPaused, enabled = showActivity) },
                        onClick = { setShowWhenPaused(!showWhenPaused) },
                    ),
                ),
            )
        }

        item(key = "disconnect") {
            SettingsSection(
                items = listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.discord_disconnect), color = colors.destructive) },
                        onClick = { scope.launch { DiscordOAuthRepository.clearSession(context) } },
                    ),
                ),
            )
        }
    }
}

private val DiscordBlurple = Color(0xFF5865F2)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FriendsScreen(
    navController: NavController,
    addUsername: String?,
    viewModel: SocialViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val colors = Liquid.colors
    val playerConnection = LocalPlayerConnection.current
    val together = com.shiny.music.ui.liquid.together.LocalTogether.current
    val token by viewModel.token.collectAsState()
    val user by viewModel.user.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var pendingAdd by rememberSaveable { mutableStateOf(addUsername?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }

    CollectMessages(viewModel)
    // Friends' songs change while you look: refresh while the screen is on screen. Not while
    // Shiny sits in the background, where it would wake the radio every 20 s for nothing.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(token, lifecycle) {
        if (token == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshFriends()
                delay(FRIENDS_REFRESH_MS)
            }
        }
    }

    // What each friend is doing is judged against the clock, not only on each refresh: a
    // response that hasn't changed isn't re-emitted, so a song that went stale would otherwise
    // keep reading as live. Every 15 s is enough for minutes-long windows.
    val clock by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000L)
            value = System.currentTimeMillis()
        }
    }

    // Plays the friend's song in Shiny from where they are in it.
    fun listenAlong(song: NowPlaying) {
        val drift = if (song.isPlaying) (System.currentTimeMillis() - viewModel.friendsFetchedAt).coerceIn(0L, 60_000L) else 0L
        var position = song.positionMs + drift
        song.durationMs?.let { if (position >= it) position = 0L }
        val metadata = MediaMetadata(
            id = song.trackId,
            title = song.title.orEmpty(),
            artists = listOfNotNull(song.artist?.let { MediaMetadata.Artist(id = null, name = it) }),
            duration = ((song.durationMs ?: 0L) / 1000L).toInt(),
            thumbnailUrl = song.thumbnail,
        )
        // With a first item the queue starts at `position`; the rest of the radio follows.
        playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = song.trackId), metadata), startPositionMs = position)
    }

    LargeTitlePage(
        title = stringResource(R.string.social_friends),
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        if (token == null) {
            item(key = "signed_out") {
                EmptyState(
                    icon = Icons.Rounded.People,
                    title = stringResource(R.string.social_sign_in_for_friends),
                    message = stringResource(R.string.social_sign_in_desc),
                    actionLabel = stringResource(R.string.social_title),
                    onAction = { navController.navigate("settings/social") },
                )
            }
            return@LargeTitlePage
        }

        if (user != null && user?.username == null) {
            item(key = "username_needed") {
                Text(
                    text = stringResource(R.string.social_username_needed),
                    style = LiquidTypography.subheadline,
                    color = colors.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("settings/social") }
                        .padding(horizontal = PageMargin, vertical = 8.dp),
                )
            }
        }

        item(key = "add") {
            Row(
                modifier = Modifier
                    .padding(horizontal = PageMargin)
                    .padding(top = 4.dp, bottom = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidSearchField(
                    value = query,
                    onValueChange = { query = it.trim() },
                    placeholder = stringResource(R.string.social_add_friend_hint),
                    onSearch = {
                        if (it.isNotBlank() && !busy) viewModel.addFriend(it) { query = "" }
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                LiquidButton(
                    text = stringResource(R.string.social_add),
                    icon = Icons.Rounded.PersonAdd,
                    onClick = { viewModel.addFriend(query) { query = "" } },
                    enabled = query.isNotBlank() && !busy,
                    height = 42.dp,
                )
            }
        }

        val data = friends
        val incoming = data?.incoming.orEmpty()
        val outgoing = data?.outgoing.orEmpty()
        val serverNow = clock + viewModel.serverClockOffsetMs
        // Listening first, then paused, then everyone else; the server's order within each.
        val list = data?.friends.orEmpty()
            .map { it to PresenceFreshness.state(it.nowPlaying, serverNow) }
            .sortedBy { (_, state) -> state.ordinal }

        if (incoming.isNotEmpty()) {
            item(key = "incoming_title") { SectionTitle(stringResource(R.string.social_requests)) }
            items(incoming, key = { "incoming_${it.username}" }) { person ->
                PersonRow(person) {
                    LiquidButton(
                        text = stringResource(R.string.social_accept),
                        onClick = { person.username?.let { viewModel.acceptFriend(it) } },
                        tone = ButtonTone.Filled,
                        height = 32.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    LiquidButton(
                        text = stringResource(R.string.social_decline),
                        onClick = { person.username?.let { viewModel.removeRequest(it) } },
                        height = 32.dp,
                    )
                }
            }
        }

        if (outgoing.isNotEmpty()) {
            item(key = "outgoing_title") { SectionTitle(stringResource(R.string.social_sent_requests)) }
            items(outgoing, key = { "outgoing_${it.username}" }) { person ->
                PersonRow(person) {
                    LiquidButton(
                        text = stringResource(R.string.social_cancel_request),
                        onClick = { person.username?.let { viewModel.removeRequest(it) } },
                        height = 32.dp,
                    )
                }
            }
        }

        if (list.isNotEmpty()) {
            if (incoming.isNotEmpty() || outgoing.isNotEmpty()) {
                item(key = "friends_title") { SectionTitle(stringResource(R.string.social_friends)) }
            }
            items(list, key = { (friend, _) -> "friend_${friend.username}" }) { (friend, state) ->
                FriendRow(
                    friend = friend,
                    state = state,
                    onListenAlong = { listenAlong(it) },
                    onJoinRoom = { code ->
                        together?.join(code)
                        navController.navigate("together")
                    },
                    onRemove = { confirmRemove = friend.username },
                )
            }
        } else if (data != null && incoming.isEmpty() && outgoing.isEmpty()) {
            item(key = "empty") {
                val profile = user?.username?.let { ShinyLinks.profile(it) }
                EmptyState(
                    icon = Icons.Rounded.People,
                    title = stringResource(R.string.social_no_friends),
                    message = stringResource(R.string.social_no_friends_desc),
                    actionLabel = profile?.let { stringResource(R.string.social_share_profile) },
                    onAction = profile?.let { link -> { shareText(context, link) } },
                )
            }
        } else if (data == null) {
            item(key = "loading") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center,
                ) { ActivityIndicator() }
            }
        }
    }

    val adding = pendingAdd
    if (adding != null && token != null) {
        LiquidAlert(
            title = stringResource(R.string.social_add_from_link, adding),
            confirmLabel = stringResource(R.string.social_add),
            onConfirm = {
                pendingAdd = null
                viewModel.addFriend(adding)
            },
            onDismiss = { pendingAdd = null },
        )
    }
    confirmRemove?.let { username ->
        LiquidAlert(
            title = stringResource(R.string.social_remove_friend_confirm, username),
            confirmLabel = stringResource(R.string.social_remove_friend),
            destructive = true,
            onConfirm = {
                confirmRemove = null
                viewModel.removeFriend(username)
            },
            onDismiss = { confirmRemove = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FriendRow(
    friend: Friend,
    state: ListeningState,
    onListenAlong: (NowPlaying) -> Unit,
    onJoinRoom: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = Liquid.colors
    // A song they have stopped reporting is not theirs any more: show them as not listening.
    val song = friend.nowPlaying?.takeIf { state != ListeningState.NotListening }
    val playing = state == ListeningState.Playing
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = rememberRowHighlight(),
                onClick = { song?.let(onListenAlong) },
                onLongClick = onRemove,
            )
            .padding(horizontal = PageMargin, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Artwork(
                model = song?.thumbnail ?: friend.avatar,
                modifier = Modifier.size(56.dp),
                shape = if (song != null) RoundedCornerShape(8.dp) else CircleShape,
                placeholder = if (song != null) Icons.Rounded.MusicNote else Icons.Rounded.Person,
            )
            // Whose song it is, over the artwork.
            if (song != null && friend.avatar != null) {
                Artwork(
                    model = friend.avatar,
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp),
                    shape = CircleShape,
                    placeholder = Icons.Rounded.Person,
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = friend.name ?: "@${friend.username}",
                style = LiquidTypography.headline,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (song != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playing) {
                        NowPlayingBars(playing = true, color = colors.accent, modifier = Modifier.size(12.dp))
                    } else {
                        // Still bars read as "listening"; a paused friend gets the pause glyph.
                        Icon(Icons.Rounded.Pause, null, tint = colors.tertiaryLabel, modifier = Modifier.size(13.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    val track = listOfNotNull(song.title, song.artist).joinToString(" · ")
                    Text(
                        text = if (playing) {
                            track.ifBlank { stringResource(R.string.social_listening_now) }
                        } else {
                            listOf(stringResource(R.string.social_paused), track).filter { it.isNotBlank() }.joinToString(" · ")
                        },
                        style = LiquidTypography.subheadline,
                        color = if (playing) colors.secondaryLabel else colors.tertiaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = "@${friend.username} · ${stringResource(R.string.social_not_listening)}",
                    style = LiquidTypography.subheadline,
                    color = colors.tertiaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (song != null) {
            val room = song.roomCode
            if (room != null) {
                Spacer(Modifier.width(8.dp))
                LiquidButton(text = stringResource(R.string.social_join_room), onClick = { onJoinRoom(room) }, height = 32.dp)
            } else if (playing) {
                // Nothing to listen along to while they are paused; tapping the row still plays the song.
                Spacer(Modifier.width(8.dp))
                LiquidButton(text = stringResource(R.string.social_listen_along), onClick = { onListenAlong(song) }, height = 32.dp)
            }
        }
    }
}

@Composable
private fun PersonRow(
    person: Friend,
    actions: @Composable () -> Unit,
) {
    val colors = Liquid.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageMargin, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(model = person.avatar, modifier = Modifier.size(44.dp), shape = CircleShape, placeholder = Icons.Rounded.Person)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text("@${person.username}", style = LiquidTypography.headline, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            person.name?.let {
                Text(it, style = LiquidTypography.subheadline, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = LiquidTypography.footnote,
        color = Liquid.colors.secondaryLabel,
        modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = 22.dp, bottom = 6.dp),
    )
}

/** An inset settings group with an optional footnote under it. */
@Composable
private fun SettingsSection(
    items: List<Material3SettingsItem>,
    title: String? = null,
    footer: String? = null,
) {
    Column(Modifier.padding(horizontal = PageMargin)) {
        Material3SettingsGroup(title = title, items = items)
        footer?.let {
            Text(
                text = it,
                style = LiquidTypography.footnote,
                color = Liquid.colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
    }
}

/** The first thing on a screen that needs an account: what it does, and the button to connect. */
@Composable
private fun IntroCard(
    glyph: @Composable () -> Unit,
    title: String,
    message: String,
    actionLabel: String,
    busy: Boolean,
    busyLabel: String,
    onAction: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val colors = Liquid.colors
    Column(
        modifier = Modifier
            .padding(horizontal = PageMargin)
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.secondaryGroupedBackground)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.fill),
            contentAlignment = Alignment.Center,
        ) { glyph() }
        Spacer(Modifier.height(14.dp))
        Text(title, style = LiquidTypography.title3, color = colors.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = LiquidTypography.subheadline, color = colors.secondaryLabel, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        if (busy) {
            ActivityIndicator()
            Spacer(Modifier.height(8.dp))
            Text(busyLabel, style = LiquidTypography.footnote, color = colors.secondaryLabel, textAlign = TextAlign.Center)
            if (onCancel != null) {
                Text(
                    text = stringResource(android.R.string.cancel),
                    style = LiquidTypography.headline,
                    color = colors.accent,
                    modifier = Modifier
                        .clickable(onClick = onCancel)
                        .padding(12.dp),
                )
            }
        } else {
            LiquidButton(
                text = actionLabel,
                onClick = onAction,
                tone = ButtonTone.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SelectedMark(selected: Boolean) {
    if (selected) Icon(Icons.Rounded.Check, null, tint = Liquid.colors.accent, modifier = Modifier.size(20.dp))
}

private fun glyph(icon: ImageVector): @Composable () -> Unit = { Icon(icon, null, modifier = Modifier.size(18.dp)) }

@Composable
private fun CollectMessages(viewModel: SocialViewModel) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/** In a browser even though Shiny handles its own links: this is your own page. */
private fun openInBrowser(context: Context, url: String) {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val inBrowser = Intent(view).apply {
        selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
    }
    runCatching { context.startActivity(inBrowser) }.recoverCatching { context.startActivity(view) }
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Shiny", text))
}
