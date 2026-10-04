

package com.shiny.music.ui.screens.settings

import android.content.Intent

import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import com.shiny.music.ui.liquid.LiquidSwitch as Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.shiny.music.ui.liquid.LiquidTopAppBar as TopAppBar
import com.shiny.music.ui.liquid.settings.SettingsVisibility
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.shiny.music.LocalPlayerAwareWindowInsets
import com.shiny.music.R
import com.shiny.music.constants.AppLanguageKey
import com.shiny.music.constants.ContentCountryKey
import com.shiny.music.constants.ContentLanguageKey
import com.shiny.music.constants.CountryCodeToName
import com.shiny.music.constants.EnableBetterLyricsKey
import com.shiny.music.constants.EnableKugouKey
import com.shiny.music.constants.EnableLrcLibKey
import com.shiny.music.constants.EnableSimpMusicKey
import com.shiny.music.constants.EnableYouLyPlusKey
import com.shiny.music.constants.EnablePaxsenixKey
import com.shiny.music.constants.FetchFasterLyricsKey

import com.shiny.music.constants.LanguageCodeToName
import com.shiny.music.constants.LyricsProviderOrderKey
import com.shiny.music.constants.ProxyEnabledKey
import com.shiny.music.constants.ProxyPasswordKey
import com.shiny.music.constants.ProxyTypeKey
import com.shiny.music.constants.ProxyUrlKey
import com.shiny.music.constants.ProxyUsernameKey
import com.shiny.music.constants.QuickPicks
import com.shiny.music.constants.QuickPicksKey
import com.shiny.music.constants.SYSTEM_DEFAULT
import com.shiny.music.constants.ShowArtistBackgroundVideoKey
import com.shiny.music.ui.component.EnumDialog
import com.shiny.music.ui.component.IconButton
import com.shiny.music.ui.component.Material3SettingsGroup
import com.shiny.music.ui.component.Material3SettingsItem
import com.shiny.music.ui.utils.backToMain
import com.shiny.music.utils.rememberEnumPreference
import com.shiny.music.utils.rememberPreference
import androidx.compose.ui.text.font.FontWeight
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.music.innertube.models.IpVersion
import com.shiny.music.constants.IpVersionKey

import com.shiny.music.lyrics.LyricsProviderRegistry
import com.shiny.music.ui.component.DraggableLyricsProviderItem
import com.shiny.music.ui.component.DraggableLyricsProviderList
import androidx.compose.runtime.mutableStateListOf
import com.shiny.music.utils.PlaybackLogManager
import com.shiny.music.ui.component.PlaybackLogsDialog
import androidx.compose.runtime.collectAsState
import java.net.Proxy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val scrollState = androidx.compose.foundation.rememberScrollState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    
    val (appLanguage, onAppLanguageChange) = rememberPreference(key = AppLanguageKey, defaultValue = SYSTEM_DEFAULT)

    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")

    val (showArtistBackgroundVideo, onShowArtistBackgroundVideoChange) = rememberPreference(key = ShowArtistBackgroundVideoKey, defaultValue = true)
    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(key = ProxyEnabledKey, defaultValue = false)
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(key = ProxyTypeKey, defaultValue = Proxy.Type.HTTP)
    val (proxyUrl, onProxyUrlChange) = rememberPreference(key = ProxyUrlKey, defaultValue = "host:port")
    val (proxyUsername, onProxyUsernameChange) = rememberPreference(key = ProxyUsernameKey, defaultValue = "username")
    val (proxyPassword, onProxyPasswordChange) = rememberPreference(key = ProxyPasswordKey, defaultValue = "password")
    val (enableKugou, onEnableKugouChange) = rememberPreference(key = EnableKugouKey, defaultValue = true)
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (enableBetterLyrics, onEnableBetterLyricsChange) = rememberPreference(key = EnableBetterLyricsKey, defaultValue = true)
    val (enableSimpMusic, onEnableSimpMusicChange) = rememberPreference(key = EnableSimpMusicKey, defaultValue = true)
    val (enableYouLyPlus, onEnableYouLyPlusChange) = rememberPreference(key = EnableYouLyPlusKey, defaultValue = true)
    val (enablePaxsenix, onEnablePaxsenixChange) = rememberPreference(key = EnablePaxsenixKey, defaultValue = true)
    val (fetchFasterLyrics, onFetchFasterLyricsChange) = rememberPreference(key = FetchFasterLyricsKey, defaultValue = true)

    val (lyricsProviderOrder, onLyricsProviderOrderChange) = rememberPreference(
        key = LyricsProviderOrderKey,
        defaultValue = "",
    )
    val (quickPicks, onQuickPicksChange) = rememberEnumPreference(key = QuickPicksKey, defaultValue = QuickPicks.QUICK_PICKS)
    val (ipVersion, onIpVersionChange) = rememberEnumPreference(
        IpVersionKey,
        defaultValue = IpVersion.AUTO
    )

    var showPlaybackLogsDialog by rememberSaveable { mutableStateOf(false) }
    val playbackLogs by PlaybackLogManager.logs.collectAsState()

    var showProxyConfigurationDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showProxyConfigurationDialog) {
        var expandedDropdown by remember { mutableStateOf(false) }

        var tempProxyUrl by rememberSaveable { mutableStateOf(proxyUrl) }
        var tempProxyUsername by rememberSaveable { mutableStateOf(proxyUsername) }
        var tempProxyPassword by rememberSaveable { mutableStateOf(proxyPassword) }
        var authEnabled by rememberSaveable { mutableStateOf(proxyUsername.isNotBlank() || proxyPassword.isNotBlank()) }

        AlertDialog(
            onDismissRequest = { showProxyConfigurationDialog = false },
            title = {
                Text(stringResource(R.string.config_proxy))
            },
            text = {
                val dialogScrollState = androidx.compose.foundation.rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(dialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expandedDropdown,
                        onExpandedChange = { expandedDropdown = !expandedDropdown },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = proxyType.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.proxy_type)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false }
                        ) {
                            listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS).forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        onProxyTypeChange(type)
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = tempProxyUrl,
                        onValueChange = { tempProxyUrl = it },
                        label = { Text(stringResource(R.string.proxy_url)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.enable_authentication))
                        Switch(
                            checked = authEnabled,
                            onCheckedChange = {
                                authEnabled = it
                                if (!it) {
                                    tempProxyUsername = ""
                                    tempProxyPassword = ""
                                }
                            }
                        )
                    }

                    AnimatedVisibility(visible = authEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tempProxyUsername,
                                onValueChange = { tempProxyUsername = it },
                                label = { Text(stringResource(R.string.proxy_username)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = tempProxyPassword,
                                onValueChange = { tempProxyPassword = it },
                                label = { Text(stringResource(R.string.proxy_password)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onProxyUrlChange(tempProxyUrl)
                        onProxyUsernameChange(if (authEnabled) tempProxyUsername else "")
                        onProxyPasswordChange(if (authEnabled) tempProxyPassword else "")
                        showProxyConfigurationDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showProxyConfigurationDialog = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    var showContentLanguageDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showContentLanguageDialog) {
        EnumDialog(
            onDismiss = { showContentLanguageDialog = false },
            onSelect = {
                onContentLanguageChange(it)
                showContentLanguageDialog = false
            },
            title = stringResource(R.string.content_language),
            current = contentLanguage,
            values = (listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList()),
            valueText = {
                LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    var showContentCountryDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showContentCountryDialog) {
        EnumDialog(
            onDismiss = { showContentCountryDialog = false },
            onSelect = {
                onContentCountryChange(it)
                showContentCountryDialog = false
            },
            title = stringResource(R.string.content_country),
            current = contentCountry,
            values = (listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList()),
            valueText = {
                CountryCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    var showAppLanguageDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showAppLanguageDialog) {
        EnumDialog(
            onDismiss = { showAppLanguageDialog = false },
            onSelect = {
                onAppLanguageChange(it)
                showAppLanguageDialog = false
            },
            title = stringResource(R.string.app_language),
            current = appLanguage,
            values = (listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList()),
            valueText = {
                LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    var showProviderPriorityDialog by rememberSaveable { mutableStateOf(false) }

    if (showProviderPriorityDialog) {
        val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
        
        val userToggleable = setOf("YouLyPlus", "Paxsenix", "BetterLyrics", "SimpMusic", "LrcLib", "Kugou")
        val enabledProviders = setOfNotNull(
            "LrcLib".takeIf { enableLrclib },
            "Kugou".takeIf { enableKugou },
            "BetterLyrics".takeIf { enableBetterLyrics },
            "SimpMusic".takeIf { enableSimpMusic },
            "YouLyPlus".takeIf { enableYouLyPlus },
            "Paxsenix".takeIf { enablePaxsenix },
        )

        
        val savedOrder = LyricsProviderRegistry.deserializeProviderOrder(lyricsProviderOrder)
        val normalizedOrder = savedOrder + defaultOrder.filter { it !in savedOrder }

        val lyricsIcon = painterResource(R.drawable.lyrics)
        val draggableItems = remember { mutableStateListOf<DraggableLyricsProviderItem>() }

        LaunchedEffect(normalizedOrder, enabledProviders) {
            val orderedEnabled = normalizedOrder.filter { it in enabledProviders }
            draggableItems.clear()
            draggableItems.addAll(
                orderedEnabled.map { name ->
                    DraggableLyricsProviderItem(
                        id = name,
                        name = LyricsProviderRegistry.getDisplayName(name),
                        icon = lyricsIcon,
                    )
                }
            )
        }

        val cardShape = AbsoluteSmoothCornerShape(30.dp, 60)
        val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
        val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

        BasicAlertDialog(onDismissRequest = { showProviderPriorityDialog = false }) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 360.dp),
                shape = cardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    
                    Surface(
                        shape = blockShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = AbsoluteSmoothCornerShape(12.dp, 60),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.lyrics),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = stringResource(R.string.lyrics_provider_priority),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            }

                            Text(
                                text = stringResource(R.string.lyrics_provider_priority_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    
                    Surface(
                        shape = blockShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                    ) {
                        DraggableLyricsProviderList(
                            items = draggableItems,
                            onItemsReordered = { reordered ->
                                val enabledOrder = reordered.map { it.id }
                                
                                val rest = normalizedOrder.filter { it !in enabledProviders }
                                onLyricsProviderOrderChange(
                                    LyricsProviderRegistry.serializeProviderOrder(enabledOrder + rest)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        )
                    }

                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = { showProviderPriorityDialog = false },
                            shape = actionShape,
                        ) {
                            Text(text = stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }

    var showQuickPicksDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showQuickPicksDialog) {
        EnumDialog(
            onDismiss = { showQuickPicksDialog = false },
            onSelect = {
                onQuickPicksChange(it)
                showQuickPicksDialog = false
            },
            title = stringResource(R.string.set_quick_picks),
            current = quickPicks,
            values = QuickPicks.values().toList(),
            valueText = {
                when (it) {
                    QuickPicks.QUICK_PICKS -> stringResource(R.string.quick_picks)
                    QuickPicks.LAST_LISTEN -> stringResource(R.string.last_song_listened)
                }
            }
        )
    }

    var showIpVersionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showIpVersionDialog) {
        EnumDialog(
            onDismiss = { showIpVersionDialog = false },
            onSelect = {
                onIpVersionChange(it)
                showIpVersionDialog = false
            },
            title = stringResource(R.string.network_ip_version),
            current = ipVersion,
            values = IpVersion.entries,
            valueText = {
                when (it) {
                    IpVersion.AUTO -> stringResource(R.string.ip_version_auto)
                    IpVersion.IPV4 -> stringResource(R.string.ip_version_ipv4)
                    IpVersion.IPV6 -> stringResource(R.string.ip_version_ipv6)
                }
            }
        )
    }

    if (showPlaybackLogsDialog) {
        PlaybackLogsDialog(
            logs = playbackLogs,
            onClear = { PlaybackLogManager.clearLogs() },
            onDismiss = { showPlaybackLogsDialog = false }
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.general),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.content_language)) },
                    description = {
                        Text(
                            LanguageCodeToName.getOrElse(contentLanguage) { stringResource(R.string.system_default) }
                        )
                    },
                    onClick = { showContentLanguageDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.location_on),
                    title = { Text(stringResource(R.string.content_country)) },
                    description = {
                        Text(
                            CountryCodeToName.getOrElse(contentCountry) { stringResource(R.string.system_default) }
                        )
                    },
                    onClick = { showContentCountryDialog = true }
                ),
            )
        )

        

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.artist_page_settings),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.slow_motion_video),
                    title = { Text(stringResource(R.string.show_artist_background_video)) },
                    description = { Text(stringResource(R.string.show_artist_background_video_desc)) },
                    trailingContent = {
                        Switch(
                            checked = showArtistBackgroundVideo,
                            onCheckedChange = onShowArtistBackgroundVideoChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showArtistBackgroundVideo) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowArtistBackgroundVideoChange(!showArtistBackgroundVideo) }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.app_language),
            items = listOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.language),
                        title = { Text(stringResource(R.string.app_language)) },
                        description = { Text(stringResource(R.string.app_language_desc)) },
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APP_LOCALE_SETTINGS,
                                    "package:${context.packageName}".toUri()
                                )
                            )
                        }
                    )
                } else {
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.language),
                        title = { Text(stringResource(R.string.app_language)) },
                        description = {
                            Text(
                                LanguageCodeToName.getOrElse(appLanguage) { stringResource(R.string.system_default) }
                            )
                        },
                        onClick = { showAppLanguageDialog = true }
                    )
                }
            )
        )

        

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.proxy),
            items = buildList {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.network_node),
                    title = { Text(stringResource(R.string.network_ip_version)) },
                    description = {
                        Text(
                            when (ipVersion) {
                                IpVersion.AUTO -> stringResource(R.string.ip_version_auto)
                                IpVersion.IPV4 -> stringResource(R.string.ip_version_ipv4)
                                IpVersion.IPV6 -> stringResource(R.string.ip_version_ipv6)
                            }
                        )
                    },
                    onClick = { showIpVersionDialog = true }
                ))
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.wifi_proxy),
                        title = { Text(stringResource(R.string.enable_proxy)) },
                        description = { Text(stringResource(R.string.enable_proxy_desc)) },
                        trailingContent = {
                            Switch(
                                checked = proxyEnabled,
                                onCheckedChange = onProxyEnabledChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (proxyEnabled) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onProxyEnabledChange(!proxyEnabled) }
                    )
                )
                if (proxyEnabled) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.settings),
                            title = { Text(stringResource(R.string.config_proxy)) },
                            description = { Text(stringResource(R.string.config_proxy_desc)) },
                            onClick = { showProxyConfigurationDialog = true }
                        )
                    )
                }
            }
        )

        

        // Hidden, not removed: the providers, their order and romanisation keep working on
        // whatever is saved (or the defaults). See SettingsVisibility.
        if (SettingsVisibility.LYRICS) {
        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.lyrics),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.enable_lrclib)) },
                    description = { Text(stringResource(R.string.enable_lrclib_desc)) },
                    trailingContent = {
                        Switch(
                            checked = enableLrclib,
                            onCheckedChange = onEnableLrclibChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableLrclib) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableLrclibChange(!enableLrclib) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.enable_kugou)) },
                    description = { Text(stringResource(R.string.enable_kugou_desc)) },
                    trailingContent = {
                        Switch(
                            checked = enableKugou,
                            onCheckedChange = onEnableKugouChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableKugou) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableKugouChange(!enableKugou) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.enable_better_lyrics)) },
                    description = { Text(stringResource(R.string.enable_better_lyrics_desc)) },
                    trailingContent = {
                        Switch(
                            checked = enableBetterLyrics,
                            onCheckedChange = onEnableBetterLyricsChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableBetterLyrics) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableBetterLyricsChange(!enableBetterLyrics) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.enable_simpmusic)) },
                    description = { Text(stringResource(R.string.enable_simpmusic_desc)) },
                    trailingContent = {
                        Switch(
                            checked = enableSimpMusic,
                            onCheckedChange = onEnableSimpMusicChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableSimpMusic) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableSimpMusicChange(!enableSimpMusic) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text("YouLyPlus") },
                    description = { Text("LyricsPlus multi-server provider (YouLy+ extension backend)") },
                    trailingContent = {
                        Switch(
                            checked = enableYouLyPlus,
                            onCheckedChange = onEnableYouLyPlusChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableYouLyPlus) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableYouLyPlusChange(!enableYouLyPlus) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text("PaxSenix") },
                    description = { Text("Syllable-level synced lyrics, where the track has them") },
                    trailingContent = {
                        Switch(
                            checked = enablePaxsenix,
                            onCheckedChange = onEnablePaxsenixChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enablePaxsenix) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnablePaxsenixChange(!enablePaxsenix) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.speed),
                    title = { Text("Fetch faster lyrics") },
                    description = { Text("Search for lyrics in parallel and choose the provider that responds first, ignoring priority") },
                    trailingContent = {
                        Switch(
                            checked = fetchFasterLyrics,
                            onCheckedChange = onFetchFasterLyricsChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (fetchFasterLyrics) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onFetchFasterLyricsChange(!fetchFasterLyrics) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_provider_priority)) },
                    description = { Text(stringResource(R.string.lyrics_provider_priority_desc)) },
                    onClick = { showProviderPriorityDialog = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language_korean_latin),
                    title = { Text(stringResource(R.string.lyrics_romanization)) },
                    description = { Text(stringResource(R.string.lyrics_romanization_desc)) },
                    onClick = { navController.navigate("settings/content/romanization") }
                )
            )
        )
        }




























        

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.misc),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.home_outlined),
                    title = { Text(stringResource(R.string.set_quick_picks)) },
                    description = {
                        Text(
                            when (quickPicks) {
                                QuickPicks.QUICK_PICKS -> stringResource(R.string.quick_picks)
                                QuickPicks.LAST_LISTEN -> stringResource(R.string.last_song_listened)
                            }
                        )
                    },
                    onClick = { showQuickPicksDialog = true }
                )
            )
        )

        

        // Playback logs are hidden (SettingsVisibility), not removed: playback is still logged.
        // Release builds then have nothing left in this group, so it isn't drawn at all.
        val logItems = buildList {
            if (SettingsVisibility.PLAYBACK_LOGS) add(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.bug_report),
                    title = { Text(stringResource(R.string.playback_logs)) },
                    description = { Text(stringResource(R.string.playback_logs_desc)) },
                    onClick = { showPlaybackLogsDialog = true }
                )
            )
            // Endpoint diagnostics are for debugging Shiny, not for listeners.
            if (com.shiny.music.BuildConfig.DEBUG) add(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.sync),
                    title = { Text(stringResource(R.string.service_uptime)) },
                    description = { Text(stringResource(R.string.service_uptime_desc)) },
                    onClick = { navController.navigate("uptime") }
                )
            )
        }
        if (logItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Material3SettingsGroup(
                title = stringResource(R.string.logs_heading),
                items = logItems
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.content)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}
