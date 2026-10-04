package com.shiny.music.discord

import com.shiny.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * Rich Presence art assets of SHINY's Discord application (Developer Portal → Rich Presence → Art
 * Assets). A Gateway activity names an application asset by its ID, so a key like `shiny_logo` is
 * looked up in the application's public asset list; no token is involved.
 */
object DiscordArtAssets {
    private val SNOWFLAKE = Regex("^[0-9]{17,20}$")
    private val client = com.music.innertube.SharedHttp.client
    private val mutex = Mutex()

    @Volatile private var ids: Map<String, String>? = null

    /** The value for `large_image`/`small_image`: an asset ID for a key; IDs and `mp:` images unchanged. */
    suspend fun resolve(image: String): String? {
        val value = image.trim()
        if (value.isEmpty()) return null
        if (SNOWFLAKE.matches(value) || value.contains(':')) return value

        val key = value.lowercase()
        val known = ids ?: mutex.withLock { ids ?: fetch()?.also { ids = it } }
        known?.get(key)?.let { return it }
        DiscordLog.w(
            if (known == null) {
                "Art asset list unavailable; sending \"$key\" as the image"
            } else {
                "Application ${BuildConfig.DISCORD_APPLICATION_ID} has no art asset \"$key\"; sending the key as the image"
            },
        )
        return key
    }

    private suspend fun fetch(): Map<String, String>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://discord.com/api/v10/oauth2/applications/${BuildConfig.DISCORD_APPLICATION_ID}/assets")
                    .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val assets = JSONArray(response.body.string())
                    (0 until assets.length()).associate { i ->
                        val asset = assets.getJSONObject(i)
                        asset.getString("name").lowercase() to asset.getString("id")
                    }
                }
            }.onSuccess { DiscordLog.i("Art assets loaded: $it") }
                .onFailure { DiscordLog.w("Art asset list could not be loaded", it) }
                .getOrNull()
        }
}
