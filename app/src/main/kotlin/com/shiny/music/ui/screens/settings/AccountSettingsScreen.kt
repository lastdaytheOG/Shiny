package com.shiny.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.utils.parseCookieString
import com.shiny.music.R
import com.shiny.music.constants.AccountChannelHandleKey
import com.shiny.music.constants.AccountEmailKey
import com.shiny.music.constants.AccountNameKey
import com.shiny.music.constants.DataSyncIdKey
import com.shiny.music.constants.InnerTubeCookieKey
import com.shiny.music.constants.ListenBrainzEnabledKey
import com.shiny.music.constants.ListenBrainzTokenKey
import com.shiny.music.constants.SavedAccountsKey
import com.shiny.music.constants.SpotifySpDcKey
import com.shiny.music.constants.UseLoginForBrowse
import com.shiny.music.constants.VisitorDataKey
import com.shiny.music.constants.YtmSyncKey
import com.shiny.music.models.AccountData
import com.shiny.music.ui.liquid.ButtonTone
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidAlert
import com.shiny.music.ui.liquid.LiquidButton
import com.shiny.music.ui.liquid.LiquidPullDownMenu
import com.shiny.music.ui.liquid.LiquidSearchField
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.MenuAction
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.settings.SettingsActionRow
import com.shiny.music.ui.liquid.settings.SettingsGutter
import com.shiny.music.ui.liquid.settings.SettingsNavRow
import com.shiny.music.ui.liquid.settings.SettingsPage
import com.shiny.music.ui.liquid.settings.SettingsRow
import com.shiny.music.ui.liquid.settings.SettingsSection
import com.shiny.music.ui.liquid.settings.SettingsTailSpace
import com.shiny.music.ui.liquid.settings.SettingsToggleRow
import com.shiny.music.ui.liquid.settings.SpotifyGreen
import com.shiny.music.utils.rememberPreference
import com.shiny.music.viewmodels.AccountSettingsViewModel
import com.shiny.music.viewmodels.HomeViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * Your accounts, in the settings' own language: who you are on YouTube Music (or the way
 * in), the accounts this phone remembers, what the sign-in is used for, then the other
 * services — Spotify, Amazon Music, ListenBrainz — and signing out last.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val colors = Liquid.colors

    val (accountNamePref, _) = rememberPreference(AccountNameKey, "")
    val (accountEmail, _) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, _) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, _) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, _) = rememberPreference(DataSyncIdKey, "")
    val (savedAccountsJson, onSavedAccountsJsonChange) = rememberPreference(SavedAccountsKey, "[]")
    val (spotifySpDc, _) = rememberPreference(SpotifySpDcKey, "")
    var useLoginForBrowse by rememberPreference(UseLoginForBrowse, true)
    var ytmSync by rememberPreference(YtmSyncKey, true)
    var listenBrainzEnabled by rememberPreference(ListenBrainzEnabledKey, false)
    var listenBrainzToken by rememberPreference(ListenBrainzTokenKey, "")

    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }

    // The activity's instance, which already holds the account. A screen-scoped one would
    // start a second Home pipeline just to read a name and an avatar.
    val homeViewModel: HomeViewModel = hiltViewModel(
        viewModelStoreOwner = androidx.activity.compose.LocalActivity.current as androidx.activity.ComponentActivity,
    )
    val accountViewModel: AccountSettingsViewModel = hiltViewModel()
    val accountName by homeViewModel.accountName.collectAsState()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()

    val savedAccounts = remember(savedAccountsJson) {
        runCatching { Json.decodeFromString<List<AccountData>>(savedAccountsJson) }.getOrDefault(emptyList())
    }
    val displayName = accountNamePref.ifBlank { accountName }.takeIf { it.isNotBlank() && it != "Guest" }

    var confirmSwitch by remember { mutableStateOf<AccountData?>(null) }
    var accountMenu by remember { mutableStateOf<String?>(null) }
    var showSignOut by remember { mutableStateOf(false) }
    var showAmazon by remember { mutableStateOf(false) }
    var editingToken by rememberSaveable { mutableStateOf(false) }
    var tokenDraft by rememberSaveable { mutableStateOf("") }

    // Keep this phone's list of accounts current with the one signed in.
    LaunchedEffect(isLoggedIn, innerTubeCookie, accountImageUrl, displayName) {
        if (!isLoggedIn || displayName == null) return@LaunchedEffect
        val accounts = savedAccounts.toMutableList()
        val index = accounts.indexOfFirst { it.cookie == innerTubeCookie }
        if (index == -1) {
            accounts += AccountData(
                name = displayName,
                email = accountEmail,
                channelHandle = accountChannelHandle,
                cookie = innerTubeCookie,
                visitorData = visitorData,
                dataSyncId = dataSyncId,
                avatarUrl = accountImageUrl.orEmpty(),
            )
            onSavedAccountsJsonChange(Json.encodeToString(accounts))
        } else if (accounts[index].name.isBlank() || accounts[index].avatarUrl.isBlank()) {
            accounts[index] = accounts[index].copy(
                name = displayName,
                avatarUrl = accountImageUrl ?: accounts[index].avatarUrl,
            )
            onSavedAccountsJsonChange(Json.encodeToString(accounts))
        }
    }

    SettingsPage(title = stringResource(R.string.account), navController = navController) {
        item(key = "profile") {
            ProfileHeader(
                signedIn = isLoggedIn,
                name = displayName,
                detail = listOf(accountEmail, accountChannelHandle).firstOrNull { it.isNotBlank() },
                avatarUrl = accountImageUrl,
                onSignIn = { navController.navigate("login") },
            )
        }

        if (isLoggedIn) {
            item(key = "youtube") {
                SettingsSection(title = stringResource(R.string.account_youtube_section)) {
                    SettingsToggleRow(
                        title = stringResource(R.string.more_content),
                        subtitle = stringResource(R.string.more_content_desc),
                        checked = useLoginForBrowse,
                        onCheckedChange = {
                            YouTube.useLoginForBrowse = it
                            useLoginForBrowse = it
                        },
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.yt_sync),
                        subtitle = stringResource(R.string.yt_sync_desc),
                        checked = ytmSync,
                        onCheckedChange = { ytmSync = it },
                        divider = false,
                    )
                }
            }
        }

        if (savedAccounts.isNotEmpty()) {
            item(key = "accounts") {
                SettingsSection(title = stringResource(R.string.account_accounts_section)) {
                    savedAccounts.forEach { account ->
                        val current = account.cookie == innerTubeCookie
                        Box {
                            AccountRow(
                                account = account,
                                current = current,
                                onClick = { if (!current) confirmSwitch = account },
                                onMore = if (!current) ({ accountMenu = account.cookie }) else null,
                            )
                            Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)) {
                                LiquidPullDownMenu(
                                    expanded = accountMenu == account.cookie,
                                    onDismiss = { accountMenu = null },
                                    entries = listOf(
                                        MenuAction(stringResource(R.string.account_remove), icon = Icons.Rounded.Delete, destructive = true) {
                                            onSavedAccountsJsonChange(Json.encodeToString(savedAccounts - account))
                                        },
                                    ),
                                )
                            }
                        }
                    }
                    SettingsRow(
                        title = stringResource(R.string.account_add_another),
                        onClick = { navController.navigate("login") },
                        divider = false,
                    )
                }
            }
        }

        item(key = "services") {
            SettingsSection(title = stringResource(R.string.account_services_section)) {
                SettingsNavRow(
                    title = stringResource(R.string.account_spotify),
                    subtitle = stringResource(if (spotifySpDc.isNotBlank()) R.string.account_spotify_connected else R.string.account_spotify_off),
                    glyph = { ServiceGlyph(SpotifyGreen, R.drawable.ic_spotify, Color.Black) },
                    onClick = {
                        navController.navigate(if (spotifySpDc.isNotBlank()) "settings/spotify_import" else "settings/spotify_import?login=true")
                    },
                )
                SettingsNavRow(
                    title = stringResource(R.string.account_amazon),
                    subtitle = stringResource(R.string.account_amazon_desc),
                    glyph = { ServiceGlyph(Color(0xFF25D1DA), R.drawable.music_note, Color.Black) },
                    onClick = { showAmazon = true },
                    divider = false,
                )
            }
        }

        item(key = "listenbrainz") {
            SettingsSection(
                title = "ListenBrainz",
                footer = stringResource(R.string.account_listenbrainz_footer),
            ) {
                SettingsToggleRow(
                    title = stringResource(R.string.account_listenbrainz_toggle),
                    subtitle = stringResource(R.string.account_listenbrainz_desc),
                    checked = listenBrainzEnabled,
                    onCheckedChange = { listenBrainzEnabled = it },
                )
                SettingsRow(
                    title = stringResource(R.string.account_listenbrainz_token),
                    value = if (listenBrainzToken.isBlank()) stringResource(R.string.account_listenbrainz_token_none)
                            else "••••" + listenBrainzToken.takeLast(4),
                    onClick = {
                        tokenDraft = listenBrainzToken
                        editingToken = !editingToken
                    },
                    divider = false,
                )
                if (editingToken) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SettingsGutter, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiquidSearchField(
                            value = tokenDraft,
                            onValueChange = { tokenDraft = it.trim() },
                            placeholder = stringResource(R.string.account_listenbrainz_token),
                            modifier = Modifier.weight(1f),
                            onSearch = {
                                listenBrainzToken = tokenDraft
                                editingToken = false
                            },
                        )
                        LiquidButton(
                            text = stringResource(R.string.done),
                            height = 40.dp,
                            onClick = {
                                listenBrainzToken = tokenDraft
                                editingToken = false
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        if (isLoggedIn) {
            item(key = "sign_out") {
                SettingsSection {
                    SettingsActionRow(
                        title = stringResource(R.string.account_sign_out),
                        destructive = true,
                        onClick = { showSignOut = true },
                        divider = false,
                    )
                }
            }
        }

        item(key = "tail") { SettingsTailSpace(40.dp) }
    }

    confirmSwitch?.let { account ->
        LiquidAlert(
            title = stringResource(R.string.account_switch_title, account.name),
            message = stringResource(R.string.account_switch_message),
            confirmLabel = stringResource(R.string.account_switch_confirm),
            onConfirm = {
                accountViewModel.saveTokenAndRestart(
                    context = context,
                    cookie = account.cookie,
                    visitorData = account.visitorData,
                    dataSyncId = account.dataSyncId,
                    accountName = account.name,
                    accountEmail = account.email,
                    accountChannelHandle = account.channelHandle,
                )
            },
            onDismiss = { confirmSwitch = null },
        )
    }

    if (showSignOut) {
        SignOutDialog(
            onKeepData = {
                showSignOut = false
                accountViewModel.logoutKeepData(context, onInnerTubeCookieChange)
                navController.navigateUp()
            },
            onClearData = {
                showSignOut = false
                accountViewModel.logoutAndClearSyncedContent(context, onInnerTubeCookieChange)
            },
            onDismiss = { showSignOut = false },
        )
    }

    if (showAmazon) {
        AmazonDialog(
            onFind = { query ->
                showAmazon = false
                navController.navigate("search/${URLEncoder.encode(query, "UTF-8")}")
            },
            onDismiss = { showAmazon = false },
        )
    }
}

/** You, big: the picture, the name, the address — or, signed out, the way in. */
@Composable
private fun ProfileHeader(
    signedIn: Boolean,
    name: String?,
    detail: String?,
    avatarUrl: String?,
    onSignIn: () -> Unit,
) {
    val colors = Liquid.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PageMargin)
            .padding(top = 8.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(colors.secondaryFill),
            contentAlignment = Alignment.Center,
        ) {
            if (signedIn && !avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Rounded.Person, null, tint = colors.secondaryLabel, modifier = Modifier.size(52.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (signedIn) name ?: stringResource(R.string.account) else stringResource(R.string.account_signed_out_title),
            style = LiquidTypography.title2,
            color = colors.label,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val sub = if (signedIn) detail else stringResource(R.string.account_signed_out_text)
        if (!sub.isNullOrBlank()) {
            Text(
                text = sub,
                style = LiquidTypography.subheadline,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp).widthIn(max = 340.dp),
            )
        }
        if (!signedIn) {
            Spacer(Modifier.height(18.dp))
            LiquidButton(
                text = stringResource(R.string.account_sign_in),
                tone = ButtonTone.Filled,
                height = 50.dp,
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountData,
    current: Boolean,
    onClick: () -> Unit,
    onMore: (() -> Unit)?,
) {
    val colors = Liquid.colors
    SettingsRow(
        title = account.name,
        subtitle = listOf(account.email, account.channelHandle).firstOrNull { it.isNotBlank() },
        glyph = {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(colors.secondaryFill),
                contentAlignment = Alignment.Center,
            ) {
                if (account.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = account.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Rounded.Person, null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
                }
            }
        },
        trailing = {
            if (current) {
                Icon(Icons.Rounded.Check, stringResource(R.string.account_current), tint = colors.accent, modifier = Modifier.size(22.dp))
            } else if (onMore != null) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.quaternaryFill)
                        .clickable(onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MoreHoriz, null, tint = colors.secondaryLabel, modifier = Modifier.size(18.dp))
                }
            }
        },
        // The row switches to the account; its ⋯ holds the rest.
        onClick = { if (!current) onClick() },
    )
}

@Composable
private fun ServiceGlyph(color: Color, icon: Int, tint: Color) {
    Box(
        Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(15.dp))
    }
}

/** Signing out asks one thing: keep what YouTube Music synced here, or take it away too. */
@Composable
private fun SignOutDialog(onKeepData: () -> Unit, onClearData: () -> Unit, onDismiss: () -> Unit) {
    val colors = Liquid.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(310.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(if (colors.isDark) Color(0xFF2C2C2E) else Color(0xFFF9F9F9))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.logout_dialog_title), style = LiquidTypography.headline, color = colors.label, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.logout_dialog_message),
                style = LiquidTypography.footnote,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LiquidButton(text = stringResource(R.string.logout_keep_data), tone = ButtonTone.Filled, onClick = onKeepData, modifier = Modifier.fillMaxWidth())
            LiquidButton(
                text = stringResource(R.string.logout_clear_data),
                contentColor = colors.destructive,
                onClick = onClearData,
                modifier = Modifier.fillMaxWidth(),
            )
            LiquidButton(
                text = stringResource(android.R.string.cancel),
                tone = ButtonTone.Glass,
                contentColor = colors.label,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Amazon Music keeps its library to itself, so this is the honest version: share a song or
 * album from Amazon Music to Shiny (the share carries its name), or type the name here.
 */
@Composable
private fun AmazonDialog(onFind: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = Liquid.colors
    var query by rememberSaveable { mutableStateOf("") }
    val isLink = query.contains("amazon.", ignoreCase = true) || query.contains("amzn.", ignoreCase = true)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(if (colors.isDark) Color(0xFF1C1C1E) else Color.White)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceGlyph(Color(0xFF25D1DA), R.drawable.music_note, Color.Black)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.amazon_title), style = LiquidTypography.title3, color = colors.label)
            }
            Text(stringResource(R.string.amazon_text), style = LiquidTypography.subheadline, color = colors.secondaryLabel)
            LiquidSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.amazon_hint),
                modifier = Modifier.fillMaxWidth(),
                onSearch = { if (query.isNotBlank() && !isLink) onFind(query.trim()) },
            )
            if (isLink) {
                Text(stringResource(R.string.amazon_link_hint), style = LiquidTypography.footnote, color = colors.destructive)
            }
            LiquidButton(
                text = stringResource(R.string.amazon_find),
                tone = ButtonTone.Filled,
                enabled = query.isNotBlank() && !isLink,
                height = 50.dp,
                onClick = { onFind(query.trim()) },
                modifier = Modifier.fillMaxWidth(),
            )
            LiquidButton(
                text = stringResource(android.R.string.cancel),
                height = 46.dp,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
