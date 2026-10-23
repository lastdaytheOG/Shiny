package com.shiny.music.discover

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ChartMatchLiveProbe {

    @Test
    fun probe() = runBlocking {
        assumeTrue("set SHINY_CHARTS_LIVE=2 to match a live chart against YouTube Music", System.getenv("SHINY_CHARTS_LIVE") == "2")
        val out = StringBuilder()
        val source = AppleRssSource()
        val gate = Semaphore(4)
        for (storefront in (System.getenv("SHINY_PROBE_REGIONS") ?: "in,gb,us").split(',')) {
            System.getenv("SHINY_PROBE_HL")?.let { com.music.innertube.YouTube.locale = com.music.innertube.models.YouTubeLocale(storefront.uppercase(), it) }
            val resolver = ChartResolver(YouTubeChartCatalog)
            for (kind in listOf(ChartKind.Songs, ChartKind.Videos, ChartKind.Albums)) {
                val chart = runCatching { source.fetch(storefront, kind) }.getOrNull() ?: continue
                val results = chart.entries.map { e ->
                    async { gate.withPermit { e to if (kind == ChartKind.Albums) resolver.album(e) else resolver.song(e, kind) } }
                }.awaitAll()
                val exact = results.count { (it.second as? Resolution.Found)?.alternative == false }
                out.appendLine("== $storefront $kind exact $exact/${chart.entries.size}")
                for ((e, r) in results) {
                    val line = when (r) {
                        is Resolution.Found -> {
                            val (title, artists) = when (val item = r.item) {
                                is com.music.innertube.models.SongItem -> item.title to item.artists.joinToString { it.name }
                                is com.music.innertube.models.AlbumItem -> item.title to item.artists.orEmpty().joinToString { it.name }
                                else -> "" to ""
                            }
                            val same = ChartText.normalize(title) == ChartText.normalize(e.title) &&
                                ChartText.splitCredits(e.artist).all { c -> ChartText.normalize(artists).contains(ChartText.normalize(c)) }
                            when {
                                r.alternative -> "ALTERNATIVE $title — $artists"
                                !same -> "accepted: $title — $artists"
                                else -> null
                            }
                        }
                        is Resolution.Ambiguous -> "CHOICES " + r.choices.joinToString(" / ") { "${if (it.isAlbum) "album" else "song"}: ${it.title} — ${it.artists}" }
                        is Resolution.NotFound -> "NO MATCH"
                        is Resolution.Failed -> "FAILED"
                    } ?: continue
                    out.appendLine("#${e.position} [${e.title}] by [${e.artist}] -> $line")
                }
                if (kind == ChartKind.Songs) {
                    val artists = ChartArtistRanking.rank(chart.entries)
                    val missed = artists.map { a -> async { gate.withPermit { a to resolver.artist(a) } } }.awaitAll()
                        .filter { it.second !is Resolution.Found }
                    out.appendLine("== $storefront artists found ${artists.size - missed.size}/${artists.size}")
                    missed.forEach { (a, r) -> out.appendLine("artist [${a.name}] -> $r") }
                }
            }
        }
        File(System.getenv("SHINY_PROBE_OUT") ?: "build/chart-probe.txt").writeText(out.toString())
    }
}
