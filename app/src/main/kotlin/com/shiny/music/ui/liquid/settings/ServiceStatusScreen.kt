package com.shiny.music.ui.liquid.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.music.innertube.SharedHttp
import com.shiny.music.diagnostics.ProbeResult
import com.shiny.music.diagnostics.ProbeState
import com.shiny.music.diagnostics.ServiceGroup
import com.shiny.music.diagnostics.ServiceProbe
import com.shiny.music.diagnostics.ShinyServices
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Developer diagnostics: which of the services Shiny depends on can be reached from this device,
 * and how quickly. It checks when opened and again on request.
 */
@Composable
fun ServiceStatusScreen(navController: NavController) {
    val probe = remember { ServiceProbe(reach = ::answers) }
    val results = remember { mutableStateMapOf<String, ProbeResult>() }
    var round by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(true) }

    LaunchedEffect(round) {
        results.clear()
        checking = true
        probe.check(ShinyServices).collect { results[it.endpoint.name] = it }
        checking = false
    }

    SettingsPage(title = "Service status", navController = navController) {
        ServiceGroup.entries.forEach { group ->
            val services = ShinyServices.filter { it.group == group }
            item(key = group.name) {
                SettingsSection(title = group.title) {
                    services.forEachIndexed { index, service ->
                        val result = results[service.name]
                        SettingsValueRow(
                            title = service.name,
                            subtitle = host(result?.url ?: service.urls.first()),
                            value = statusLabel(result),
                            divider = index != services.lastIndex,
                        )
                    }
                }
            }
        }
        item(key = "again") {
            SettingsSection(footer = "Reachable means the server answered. It does not test signing in or playback.") {
                SettingsActionRow(
                    title = if (checking) "Checking…" else "Check again",
                    enabled = !checking,
                    onClick = { round++ },
                    divider = false,
                )
            }
        }
    }
}

internal fun statusLabel(result: ProbeResult?): String = when (result?.state) {
    null -> "Checking…"
    ProbeState.UP -> "${result.millis} ms"
    ProbeState.SLOW -> "Slow · ${"%.1f".format(java.util.Locale.ROOT, (result.millis ?: 0) / 1000f)} s"
    ProbeState.DOWN -> "No answer"
}

internal fun host(url: String): String = url.substringAfter("://").substringBefore('/')

private val prober by lazy {
    SharedHttp.client.newBuilder()
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
}

/** True when the server answered at all; a 4xx still means it is there. */
private suspend fun answers(url: String): Boolean = suspendCancellableCoroutine { continuation ->
    val call = prober.newCall(Request.Builder().url(url).header("Range", "bytes=0-0").build())
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resume(false)
        }

        override fun onResponse(call: Call, response: Response) {
            val answered = response.use { it.code < 500 }
            if (continuation.isActive) continuation.resume(answered)
        }
    })
}
