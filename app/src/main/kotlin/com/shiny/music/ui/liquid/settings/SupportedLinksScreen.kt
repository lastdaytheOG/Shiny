package com.shiny.music.ui.liquid.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController

/**
 * Which web links open straight in Shiny: one switch per kind, read live from Android.
 *
 * Android doesn't let an app switch its own links, so a switch opens Android's "Open by
 * default" page, and the page re-reads the state when the listener comes back.
 */
@Composable
fun SupportedLinksScreen(navController: NavController) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readLinkState(context)) }

    // Coming back from Android's settings is the moment the answer changes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state = readLinkState(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsPage(title = "Supported links", navController = navController) {
        item(key = "web") {
            SettingsSection(title = "Web links") {
                LinkGroups.forEachIndexed { index, group ->
                    val divider = index != LinkGroups.lastIndex
                    if (state.readable) {
                        val status = state.statusOf(group)
                        SettingsToggleRow(
                            title = group.title,
                            checked = status == LinkStatus.On || status == LinkStatus.Some,
                            onCheckedChange = { openLinkSettings(context) },
                            divider = divider,
                        )
                    } else {
                        // Before Android 12 nothing can be read; the row still leads to the setting.
                        SettingsRow(
                            title = group.title,
                            chevron = true,
                            divider = divider,
                            onClick = { openLinkSettings(context) },
                        )
                    }
                }
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }
}

/** One kind of link, as the listener thinks of it, with the hosts Android knows it by. */
private data class LinkGroup(val title: String, val hosts: List<String>)

private val LinkGroups = listOf(
    LinkGroup("YouTube Music", listOf("music.youtube.com")),
    LinkGroup("YouTube", listOf("youtube.com", "www.youtube.com", "m.youtube.com")),
    LinkGroup("youtu.be", listOf("youtu.be")),
    LinkGroup("Shiny links", listOf("shinymusic.in", "www.shinymusic.in")),
)

private enum class LinkStatus { On, Some, Off, Unknown }

/**
 * What Android says about Shiny's links. [readable] is false before Android 12, which has
 * no way for an app to read this.
 */
private data class LinkState(
    val readable: Boolean,
    val allowed: Boolean = true,
    val hostOn: Map<String, Boolean> = emptyMap(),
) {
    fun statusOf(group: LinkGroup): LinkStatus {
        if (!readable) return LinkStatus.Unknown
        if (!allowed) return LinkStatus.Off
        val known = group.hosts.mapNotNull { hostOn[it] }
        return when {
            known.isEmpty() -> LinkStatus.Unknown
            known.all { it } -> LinkStatus.On
            known.any { it } -> LinkStatus.Some
            else -> LinkStatus.Off
        }
    }
}

private fun readLinkState(context: Context): LinkState {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return LinkState(readable = false)
    return try {
        val manager = context.getSystemService(DomainVerificationManager::class.java)
            ?: return LinkState(readable = false)
        val user = manager.getDomainVerificationUserState(context.packageName)
            ?: return LinkState(readable = false)
        LinkState(
            readable = true,
            allowed = user.isLinkHandlingAllowed,
            hostOn = user.hostToStateMap.mapValues { (_, value) ->
                value == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
                    value == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
            },
        )
    } catch (e: Exception) {
        LinkState(readable = false)
    }
}

private fun openLinkSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${context.packageName}"))
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        if (e is ActivityNotFoundException || e is SecurityException) {
            Toast.makeText(context, "Cannot open system settings", Toast.LENGTH_SHORT).show()
        }
    }
}
