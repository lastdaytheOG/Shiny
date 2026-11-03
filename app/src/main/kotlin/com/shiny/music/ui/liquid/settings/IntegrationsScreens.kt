package com.shiny.music.ui.liquid.settings

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.shiny.music.R
import com.shiny.music.constants.InnerTubeCookieKey
import com.shiny.music.constants.SpotifySpDcKey
import com.shiny.music.playback.scrobble.ScrobbleException
import com.shiny.music.playback.scrobble.ScrobbleSite
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.rememberPreference
import androidx.datastore.preferences.core.edit
import com.music.innertube.utils.parseCookieString
import kotlinx.coroutines.launch

// a row per service: its tile, its name, one short line, and the page it opens
@Composable
fun IntegrationsSettingsScreen(navController: NavController) {
    val cookie by rememberPreference(InnerTubeCookieKey, "")
    val spDc by rememberPreference(SpotifySpDcKey, "")
    val lastFm by rememberPreference(ScrobbleSite.LASTFM.usernameKey, "")
    val libreFm by rememberPreference(ScrobbleSite.LIBREFM.usernameKey, "")
    val youTubeIn = remember(cookie) { "SAPISID" in parseCookieString(cookie) }
    SettingsPage(title = "Integrations", navController = navController, search = false) {
        item(key = "rows") {
            Column(Modifier.padding(horizontal = SettingsCardMargin).padding(top = 8.dp)) {
                IntegrationRow("YouTube Music", if (youTubeIn) "Signed in" else "Sign in to sync your library", tile = {
                    Tile(Color(0xFFFF0000)) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                }) { navController.navigate("settings/youtube") }
                IntegrationRow("Spotify", if (spDc.isNotBlank()) "Connected" else "Bring your playlists over", tile = {
                    Tile(Color(0xFF1DB954)) { Icon(painterResource(R.drawable.ic_spotify), null, tint = Color.Black, modifier = Modifier.size(20.dp)) }
                }) { navController.navigate(if (spDc.isNotBlank()) "settings/spotify_import" else "settings/spotify_import?login=true") }
                IntegrationRow("Last.fm", if (lastFm.isNotBlank()) lastFm else "Scrobble what you play", tile = {
                    Tile(Color(0xFFD51007)) { Text("as", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black) }
                }) { navController.navigate("settings/scrobble/LASTFM") }
                IntegrationRow("Discord", "Show what you're playing", tile = {
                    Tile(Color(0xFF5865F2)) { Icon(painterResource(R.drawable.discord), null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                }) { navController.navigate("settings/discord") }
                IntegrationRow("Libre.fm", if (libreFm.isNotBlank()) libreFm else "Scrobble what you play", tile = {
                    Tile(Color(0xFF2B2B2B)) { Text("lfm", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black) }
                }) { navController.navigate("settings/scrobble/LIBREFM") }
            }
        }
        item(key = "tail") { SettingsTailSpace(40.dp) }
    }
}

@Composable
private fun Tile(color: Color, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IntegrationRow(name: String, line: String, tile: @Composable () -> Unit, onClick: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .pressScale(interaction, 0.98f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (colors.isDark) Color(0xFF232323) else settingsCard())
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tile()
        Column(Modifier.padding(start = 12.dp)) {
            Text(name, color = colors.label, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, color = colors.label.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// one scrobbling service: sign in with its username and password, then a switch and a way out
@Composable
fun ScrobbleServiceScreen(navController: NavController, site: ScrobbleSite) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = Liquid.colors
    val username by rememberPreference(site.usernameKey, "")
    val session by rememberPreference(site.sessionKey, "")
    var enabled by rememberPreference(site.enabledKey, true)
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }

    SettingsPage(title = site.label, navController = navController, search = false) {
        when {
            !site.api.isConfigured -> item(key = "off") {
                SettingsSection {
                    SettingsRow(title = "Not available in this build", subtitle = "It needs a ${site.label} API key")
                }
            }
            session.isNotBlank() -> item(key = "signed_in") {
                SettingsSection {
                    SettingsRow(title = username, subtitle = "Signed in")
                    SettingsToggleRow(title = "Scrobble", checked = enabled, onCheckedChange = { enabled = it })
                    SettingsNavRow(title = "Open profile", onClick = { open(site.profilePage + username) })
                    SettingsActionRow(
                        title = "Sign out",
                        destructive = true,
                        onClick = {
                            scope.launch {
                                context.dataStore.edit {
                                    it.remove(site.sessionKey)
                                    it.remove(site.usernameKey)
                                }
                            }
                        },
                    )
                }
            }
            else -> item(key = "sign_in") {
                Column(Modifier.padding(horizontal = SettingsCardMargin).padding(top = 12.dp)) {
                    Field(value = user, onChange = { user = it }, hint = "Username")
                    Spacer(Modifier.height(10.dp))
                    Field(value = password, onChange = { password = it }, hint = "Password", secret = true)
                    error?.let {
                        Text(it, color = colors.destructive, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, top = 10.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    val interaction = remember { MutableInteractionSource() }
                    val ready = user.isNotBlank() && password.isNotBlank() && !busy
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .pressScale(interaction, 0.97f)
                            .clip(CircleShape)
                            .background(if (ready) colors.label else colors.label.copy(alpha = 0.25f))
                            .combinedClickable(interactionSource = interaction, indication = null, enabled = ready) {
                                busy = true
                                error = null
                                scope.launch {
                                    try {
                                        val s = site.api.signIn(user.trim(), password)
                                        context.dataStore.edit {
                                            it[site.sessionKey] = s.key
                                            it[site.usernameKey] = s.name
                                            it[site.enabledKey] = true
                                        }
                                        password = ""
                                    } catch (e: ScrobbleException) {
                                        error = if (e.isWrongLogin) "Wrong username or password" else "Couldn't sign in, try again"
                                    } catch (e: Exception) {
                                        error = "Couldn't reach ${site.label}"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (busy) "Signing in…" else "Sign in", color = if (colors.isDark) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Create an account",
                        color = colors.secondaryLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 18.dp)
                            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open(site.signUpUrl) },
                    )
                }
            }
        }
        item(key = "tail") { SettingsTailSpace(40.dp) }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, secret: Boolean = false) {
    val colors = Liquid.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(if (colors.isDark) Color(0xFF1C1C1E) else colors.quaternaryFill)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(hint, color = colors.secondaryLabel, fontSize = 17.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.label, fontSize = 17.sp),
            cursorBrush = SolidColor(colors.label),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
