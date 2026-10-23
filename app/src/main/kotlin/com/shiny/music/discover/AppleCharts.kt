package com.shiny.music.discover

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

enum class ChartKind(val path: String, val limit: Int) {
    Songs("songs", 100),
    Albums("albums", 20),
    Videos("music-videos", 20),
}

@Serializable
data class ChartEntry(
    val position: Int,
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val artistUrl: String? = null,
    val artwork: String? = null,
    val url: String? = null,
    val releaseDate: String? = null,
    val genres: List<String> = emptyList(),
    val explicit: Boolean = false,
)

@Serializable
data class AppleChart(
    val storefront: String,
    val kind: ChartKind,
    val title: String? = null,
    val updated: String? = null,
    val entries: List<ChartEntry>,
    val fetchedAt: Long,
)

class ChartFormatException(message: String) : Exception(message)

object AppleChartParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, storefront: String, kind: ChartKind, fetchedAt: Long): AppleChart {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: throw ChartFormatException("not a JSON object")
        val feed = root["feed"] as? JsonObject ?: throw ChartFormatException("no feed")
        val results = when (val r = feed["results"]) {
            null -> emptyList()
            is JsonArray -> r
            else -> throw ChartFormatException("results is not a list")
        }
        val seen = HashSet<String>()
        val entries = results.mapIndexedNotNull { index, element ->
            entry(element as? JsonObject ?: return@mapIndexedNotNull null, index + 1, kind)
        }.filter { seen.add(it.id) }.take(kind.limit)
        return AppleChart(
            storefront = storefront,
            kind = kind,
            title = feed.text("title"),
            updated = feed.text("updated"),
            entries = entries,
            fetchedAt = fetchedAt,
        )
    }

    private fun entry(o: JsonObject, position: Int, kind: ChartKind): ChartEntry? {
        val title = o.text("name") ?: return null
        val artist = o.text("artistName") ?: return null
        val url = o.text("url")?.takeIf(::isWebUrl)
        return ChartEntry(
            position = position,
            id = o.text("id") ?: url ?: "${kind.path}:${artist.lowercase(Locale.ROOT)}:${title.lowercase(Locale.ROOT)}",
            title = title,
            artist = artist,
            artistId = o.text("artistId"),
            artistUrl = o.text("artistUrl")?.takeIf(::isWebUrl),
            artwork = o.text("artworkUrl100")?.takeIf(::isWebUrl),
            url = url,
            releaseDate = o.text("releaseDate"),
            genres = (o["genres"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.text("name") }
                .filterNot { it.equals("Music", ignoreCase = true) },
            explicit = o.text("contentAdvisoryRating")?.startsWith("expl", ignoreCase = true) == true,
        )
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun isWebUrl(value: String) = value.startsWith("https://") || value.startsWith("http://")
}

object AppleArtwork {
    private val SIZED = Regex("""^(https?://[^/]+\.mzstatic\.com/image/thumb/.+)/(\d+)x(\d+)([a-z]{0,4})\.(jpg|jpeg|png|webp)$""")

    fun square(url: String?, px: Int): String? {
        url ?: return null
        val m = SIZED.matchEntire(url) ?: return url
        if (m.groupValues[4] != "bb") return url
        return "${m.groupValues[1]}/${px}x${px}bb.webp"
    }

    fun landscape(url: String?, widthPx: Int): String? {
        url ?: return null
        val m = SIZED.matchEntire(url) ?: return url
        if (m.groupValues[4] != "mv") return url
        val w = m.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return url
        val h = m.groupValues[3].toIntOrNull()?.takeIf { it > 0 } ?: return url
        return "${m.groupValues[1]}/${widthPx}x${widthPx * h / w}mv.webp"
    }
}
