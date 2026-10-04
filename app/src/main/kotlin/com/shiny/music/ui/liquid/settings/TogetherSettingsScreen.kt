package com.shiny.music.ui.liquid.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shiny.music.BuildConfig
import com.shiny.music.R
import com.shiny.music.constants.ListenTogetherInTopBarKey
import com.shiny.music.constants.ListenTogetherUsernameKey
import com.shiny.music.constants.TogetherLatencyKey
import com.shiny.music.constants.TogetherModeKey
import com.shiny.music.constants.TogetherPlayerReactionsKey
import com.shiny.music.constants.TogetherRequestAlertsKey
import com.shiny.music.constants.TogetherServerUrlKey
import com.shiny.music.together.MODE_PHONE
import com.shiny.music.together.MODE_REMOTE
import com.shiny.music.together.TogetherServer
import com.shiny.music.ui.liquid.LiquidButton
import com.shiny.music.ui.liquid.LiquidSearchField
import com.shiny.music.ui.liquid.together.LocalTogether
import com.shiny.music.ui.liquid.together.spacedCode
import com.shiny.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Listen Together's settings: who you are in a session, how you join one, the one knob
 * sync needs (headphone delay), what may interrupt you, and where the server is.
 */
@Composable
fun TogetherSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = LocalTogether.current
    val state = session?.state?.collectAsState()?.value

    var name by rememberPreference(ListenTogetherUsernameKey, "")
    var mode by rememberPreference(TogetherModeKey, MODE_PHONE)
    var latency by rememberPreference(TogetherLatencyKey, 0)
    var requestAlerts by rememberPreference(TogetherRequestAlertsKey, true)
    var playerReactions by rememberPreference(TogetherPlayerReactionsKey, true)
    var inLibrary by rememberPreference(ListenTogetherInTopBarKey, true)
    var server by rememberPreference(TogetherServerUrlKey, "")

    var editingName by rememberSaveable { mutableStateOf(false) }
    var nameDraft by rememberSaveable { mutableStateOf("") }
    var editingServer by rememberSaveable { mutableStateOf(false) }
    var serverDraft by rememberSaveable { mutableStateOf("") }
    val latencyDraft = remember(latency) { mutableFloatStateOf(latency.toFloat()) }

    SettingsPage(title = stringResource(R.string.listen_together), navController = navController) {
        item(key = "intro") { SettingsIntro(stringResource(R.string.together_settings_intro)) }

        item(key = "open") {
            SettingsSection {
                SettingsNavRow(
                    title = stringResource(R.string.together_settings_open),
                    value = when {
                        state?.isLive == true -> stringResource(R.string.together_settings_live, spacedCode(state.room?.code.orEmpty()))
                        state?.active == true -> stringResource(R.string.together_menu_connecting)
                        else -> null
                    },
                    onClick = { navController.navigate("together") },
                    divider = false,
                )
            }
        }

        item(key = "you") {
            SettingsSection(title = stringResource(R.string.together_settings_you)) {
                SettingsRow(
                    title = stringResource(R.string.together_settings_name),
                    value = name.ifBlank { session?.displayName.orEmpty() },
                    onClick = {
                        nameDraft = name.ifBlank { session?.displayName.orEmpty() }
                        editingName = !editingName
                    },
                    divider = !editingName,
                )
                if (editingName) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SettingsGutter, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiquidSearchField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it.take(24) },
                            placeholder = stringResource(R.string.together_settings_name),
                            modifier = Modifier.weight(1f),
                            onSearch = {
                                session?.rename(nameDraft) ?: run { name = nameDraft.trim() }
                                editingName = false
                            },
                        )
                        LiquidButton(
                            text = stringResource(R.string.done),
                            height = 40.dp,
                            onClick = {
                                session?.rename(nameDraft) ?: run { name = nameDraft.trim() }
                                editingName = false
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                SettingsChoiceRow(
                    title = stringResource(R.string.together_settings_mode),
                    options = listOf(MODE_PHONE, MODE_REMOTE),
                    selected = if (mode == MODE_REMOTE) MODE_REMOTE else MODE_PHONE,
                    label = {
                        if (it == MODE_REMOTE) context.getString(R.string.together_mode_remote)
                        else context.getString(R.string.together_mode_phone)
                    },
                    optionSubtitle = {
                        if (it == MODE_REMOTE) context.getString(R.string.together_settings_mode_remote)
                        else context.getString(R.string.together_settings_mode_phone)
                    },
                    onSelect = { choice ->
                        mode = choice
                        if (session?.state?.value?.isGuest == true) session.setMode(choice)
                    },
                    divider = false,
                )
            }
        }

        item(key = "sync") {
            SettingsSection(title = stringResource(R.string.together_settings_sync)) {
                SettingsSliderRow(
                    title = stringResource(R.string.together_settings_delay),
                    subtitle = stringResource(R.string.together_settings_delay_footer),
                    value = latencyDraft.floatValue,
                    onValueChange = { latencyDraft.floatValue = it },
                    onValueChangeFinished = { latency = latencyDraft.floatValue.toInt() },
                    valueRange = 0f..400f,
                    steps = 19,
                    valueLabel = "${latencyDraft.floatValue.toInt()} ms",
                    divider = false,
                )
            }
        }

        item(key = "alerts") {
            SettingsSection(title = stringResource(R.string.together_settings_alerts)) {
                SettingsToggleRow(
                    title = stringResource(R.string.together_settings_requests),
                    subtitle = stringResource(R.string.together_settings_requests_desc),
                    checked = requestAlerts,
                    onCheckedChange = { requestAlerts = it },
                )
                SettingsToggleRow(
                    title = stringResource(R.string.together_settings_reactions),
                    checked = playerReactions,
                    onCheckedChange = { playerReactions = it },
                    divider = false,
                )
            }
        }

        item(key = "place") {
            SettingsSection(title = stringResource(R.string.together_settings_place)) {
                SettingsToggleRow(
                    title = stringResource(R.string.together_settings_tab),
                    checked = !inLibrary,
                    onCheckedChange = { inLibrary = !it },
                    divider = false,
                )
            }
        }

        item(key = "server") {
            val effective = TogetherServer.base(context)
            SettingsSection(
                title = stringResource(R.string.together_settings_server),
                footer = stringResource(
                    if (server.isNotBlank()) R.string.together_settings_server_footer_custom
                    else R.string.together_settings_server_footer,
                ),
            ) {
                SettingsRow(
                    title = stringResource(R.string.together_settings_server_address),
                    value = when {
                        server.isNotBlank() -> server.removePrefix("https://").removePrefix("http://")
                        BuildConfig.TOGETHER_SERVER_URL.isNotBlank() -> stringResource(R.string.together_settings_server_builtin)
                        effective != null -> effective.removePrefix("http://")
                        else -> stringResource(R.string.together_settings_server_none)
                    },
                    onClick = {
                        serverDraft = server
                        editingServer = !editingServer
                    },
                    divider = !editingServer,
                )
                if (editingServer) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SettingsGutter, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiquidSearchField(
                            value = serverDraft,
                            onValueChange = { serverDraft = it.trim() },
                            placeholder = "https://…workers.dev",
                            modifier = Modifier.weight(1f),
                            onSearch = {
                                server = serverDraft.trimEnd('/')
                                editingServer = false
                            },
                        )
                        LiquidButton(
                            text = stringResource(R.string.done),
                            height = 40.dp,
                            onClick = {
                                server = serverDraft.trimEnd('/')
                                editingServer = false
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                SettingsActionRow(
                    title = stringResource(R.string.together_settings_test),
                    enabled = effective != null,
                    onClick = {
                        val base = TogetherServer.base(context) ?: return@SettingsActionRow
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                runCatching {
                                    com.music.innertube.SharedHttp.client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
                                        .newCall(Request.Builder().url("$base/v1/health").build())
                                        .execute().use { it.isSuccessful && it.body?.string().orEmpty().contains("\"ok\":true") }
                                }.getOrDefault(false)
                            }
                            Toast.makeText(
                                context,
                                if (ok) R.string.together_settings_test_ok else R.string.together_settings_test_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    divider = false,
                )
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }
}
