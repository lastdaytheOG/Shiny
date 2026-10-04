package com.shiny.music.utils

import android.content.Context
import com.shiny.music.BuildConfig
import com.shiny.music.constants.DiscordActivityButton1CustomUrlKey
import com.shiny.music.constants.DiscordActivityButton1EnabledKey
import com.shiny.music.constants.DiscordActivityButton1LabelKey
import com.shiny.music.constants.DiscordActivityButton1UrlSourceKey
import com.shiny.music.constants.DiscordActivityButton2CustomUrlKey
import com.shiny.music.constants.DiscordActivityButton2EnabledKey
import com.shiny.music.constants.DiscordActivityButton2LabelKey
import com.shiny.music.constants.DiscordActivityButton2UrlSourceKey
import com.shiny.music.constants.DiscordListenAlongButtonKey
import com.shiny.music.constants.DiscordShowWhenPausedKey
import com.shiny.music.db.entities.Song
import com.shiny.music.discord.DiscordActivityType
import com.shiny.music.discord.DiscordLog
import com.shiny.music.discord.DiscordPresenceActivity
import com.shiny.music.discord.DiscordPresenceAssets
import com.shiny.music.discord.DiscordPresenceButton
import com.shiny.music.discord.DiscordPresenceTimestamps
import com.shiny.music.discord.DiscordSocialPresenceClient
import com.shiny.music.social.ShinyLinks

/** Builds SHINY's Discord activity for a song and hands it to [DiscordSocialPresenceClient]. */
class DiscordRPC(
    private val context: Context,
    val accessToken: String,
) {
    companion object {
        /** The activity's name: friends see "Listening to SHINY". */
        const val ACTIVITY_NAME = "SHINY"

        /** Art asset uploaded to SHINY's Discord application, shown as the large image. */
        const val LOGO_ASSET_KEY = "shiny_logo"
    }

    suspend fun stopActivity() = DiscordSocialPresenceClient.clearPresence()

    suspend fun closeRPC() = DiscordSocialPresenceClient.close()

    /**
     * Shows [song] as the activity, or clears it when paused and paused songs aren't shown.
     * Throws when the update couldn't be published.
     */
    suspend fun updateSong(
        song: Song,
        currentPlaybackTimeMillis: Long,
        isPaused: Boolean = false,
    ) {
        val showWhenPaused = context.dataStore[DiscordShowWhenPausedKey] ?: false
        if (isPaused && !showWhenPaused) {
            DiscordLog.i("Paused, and paused songs aren't shown: clearing the activity")
            stopActivity()
            return
        }

        val album = (song.song.albumName ?: song.album?.title).toDiscordText()
        val activity =
            DiscordPresenceActivity(
                applicationId = BuildConfig.DISCORD_APPLICATION_ID_LONG,
                name = ACTIVITY_NAME,
                type = DiscordActivityType.Listening,
                details = song.song.title.toDiscordText() ?: ACTIVITY_NAME,
                state = song.artists.joinToString { it.name }.toDiscordText(),
                assets =
                    DiscordPresenceAssets(
                        largeImage = LOGO_ASSET_KEY,
                        largeText = if (isPaused) "Paused" else album ?: ACTIVITY_NAME,
                    ),
                buttons = resolveButtons(song, currentPlaybackTimeMillis),
                timestamps = if (isPaused) DiscordPresenceTimestamps() else playingTimestamps(song, currentPlaybackTimeMillis),
            )

        DiscordSocialPresenceClient.updatePresence(accessToken, activity)
    }

    private fun playingTimestamps(
        song: Song,
        positionMs: Long,
    ): DiscordPresenceTimestamps {
        val startMs = System.currentTimeMillis() - positionMs.coerceAtLeast(0L)
        val durationMs = song.song.duration.toLong() * 1000L
        return DiscordPresenceTimestamps(
            startMs = startMs,
            endMs = if (durationMs > 0L) startMs + durationMs else null,
        )
    }

    private fun resolveButtons(song: Song, positionMs: Long): List<DiscordPresenceButton> {
        val listenAlong = context.dataStore[DiscordListenAlongButtonKey] ?: true
        val button1Label = context.dataStore[DiscordActivityButton1LabelKey] ?: "Listen on YouTube Music"
        val button1Enabled = context.dataStore[DiscordActivityButton1EnabledKey] ?: true
        val button2Label = context.dataStore[DiscordActivityButton2LabelKey] ?: "Go to Shiny"
        val button2Enabled = context.dataStore[DiscordActivityButton2EnabledKey] ?: true
        val button1UrlSource = context.dataStore[DiscordActivityButton1UrlSourceKey] ?: "songurl"
        val button1CustomUrl = context.dataStore[DiscordActivityButton1CustomUrlKey] ?: ""
        val button2UrlSource = context.dataStore[DiscordActivityButton2UrlSourceKey] ?: "custom"
        val button2CustomUrl =
            context.dataStore[DiscordActivityButton2CustomUrlKey]
                ?: "https://github.com/lastdaytheOG/Shiny"

        return buildList {
            // First, so it's the one people see: opens this song where you are in it, in Shiny or on the web.
            // The link carries the time it was made, so the page keeps the start moving until it's tapped.
            if (listenAlong && !song.song.isLocal && !song.song.id.isLocalMediaId()) {
                add(
                    DiscordPresenceButton(
                        label = "Listen along",
                        url = ShinyLinks.listenAlong(
                            videoId = song.song.id,
                            positionMs = positionMs,
                            durationMs = song.song.duration.takeIf { it > 0 }?.times(1000L),
                        ),
                    ),
                )
            }
            if (button1Enabled) {
                val url = resolveUrl(button1UrlSource, song, button1CustomUrl)
                if (button1Label.isNotBlank() && !url.isNullOrBlank()) {
                    add(DiscordPresenceButton(button1Label.toButtonLabel(), url))
                }
            }

            if (button2Enabled) {
                val url = resolveUrl(button2UrlSource, song, button2CustomUrl)
                if (button2Label.isNotBlank() && !url.isNullOrBlank()) {
                    add(DiscordPresenceButton(button2Label.toButtonLabel(), url))
                }
            }
        }.take(2)
    }

    private fun resolveUrl(
        source: String,
        song: Song,
        custom: String,
    ): String? =
        when (source.lowercase()) {
            "songurl" -> {
                song.youtubeMusicUrl()
            }

            "artisturl" -> {
                song.artists
                    .firstOrNull()
                    ?.id
                    ?.takeUnless { song.song.isLocal || it.startsWith("LOCAL_ARTIST_") || it.isLocalMediaId() }
                    ?.let { "https://music.youtube.com/channel/$it" }
            }

            "albumurl" -> {
                song.album?.playlistId?.let { "https://music.youtube.com/playlist?list=$it" }
            }

            "custom" -> {
                custom.normalizeUrl()
            }

            else -> {
                null
            }
        }?.toDiscordUrl()

    private fun String.normalizeUrl(): String? {
        val trimmed = trim()
        if (trimmed.isBlank()) return null
        return if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    /** Discord rejects activity text shorter than 2 characters and cuts it at 128. */
    private fun String?.toDiscordText(): String? = this?.trim()?.takeIf { it.length >= 2 }?.take(128)

    private fun String?.toDiscordUrl(): String? = this?.normalizeUrl()?.take(256)

    private fun String.toButtonLabel(): String = trim().take(32).ifBlank { ACTIVITY_NAME }

    private fun Song.youtubeMusicUrl(): String? =
        song.id
            .takeUnless { song.isLocal || it.isLocalMediaId() }
            ?.let { "https://music.youtube.com/watch?v=$it" }
}
