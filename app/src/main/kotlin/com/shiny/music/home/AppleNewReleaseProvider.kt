package com.shiny.music.home

import com.music.innertube.SharedHttp
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import timber.log.Timber
import java.util.Locale

internal object AppleNewReleaseProvider {
    private const val FEED_LIMIT = 24
    private const val RESOLVE_LIMIT = 8
    private const val FEED_URL = "https://rss.applemarketingtools.com/api/v2"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class FeedResponse(val feed: Feed = Feed())

    @Serializable
    private data class Feed(val results: List<Entry> = emptyList())

    @Serializable
    private data class Entry(
        val name: String = "",
        val artistName: String = "",
        val artworkUrl100: String? = null,
    )

    suspend fun fetch(country: String): List<AlbumItem>? = supervisorScope {
        val entries = download(country)?.take(FEED_LIMIT).orEmpty()
        entries.take(RESOLVE_LIMIT).map { entry -> async { resolve(entry) } }
            .awaitAll()
            .filterNotNull()
            .distinctBy { it.id }
    }

    private suspend fun download(country: String): List<Entry>? = runCatching {
        val storefront = country.takeIf { it.length == 2 }?.lowercase(Locale.ROOT) ?: "us"
        val url = "$FEED_URL/$storefront/music/new-releases/all/$FEED_LIMIT/albums.json".toHttpUrl()
        SharedHttp.client.newCall(
            Request.Builder().url(url).header("Accept", "application/json").build(),
        ).execute().use { response ->
            if (!response.isSuccessful) error("Apple release feed returned ${response.code}")
            json.decodeFromString<FeedResponse>(response.body.string()).feed.results
        }
    }.onFailure {
        Timber.tag("AppleNew").d(it, "Apple release feed unavailable")
    }.getOrNull()

    private suspend fun resolve(entry: Entry): AlbumItem? {
        if (entry.name.isBlank() || entry.artistName.isBlank()) return null
        val query = "${entry.artistName} ${entry.name}"
        val candidates = YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM)
            .onFailure { Timber.tag("AppleNew").d(it, "Could not resolve %s", query) }
            .getOrNull()?.items?.filterIsInstance<AlbumItem>().orEmpty()
        val target = normalise(entry.name)
        val artist = normalise(entry.artistName)
        val match = candidates.map { album ->
            val title = normalise(album.title)
            val albumArtists = normalise(album.artists.orEmpty().joinToString { it.name })
            val score = (if (title == target) 12 else if (title.contains(target) || target.contains(title)) 6 else 0) +
                (if (albumArtists.contains(artist) || artist.contains(albumArtists)) 4 else 0)
            album to score
        }.filter { it.second >= 10 }.maxByOrNull { it.second }?.first ?: return null

        return entry.artworkUrl100
            ?.replace("100x100", "544x544")
            ?.takeIf { it.startsWith("http") }
            ?.let { match.copy(thumbnail = it) }
            ?: match
    }

    private fun normalise(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
