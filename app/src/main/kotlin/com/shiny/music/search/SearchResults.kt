package com.shiny.music.search

import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.SearchSummaryPage
import com.shiny.music.discover.ChartText
import timber.log.Timber
import java.util.Locale

data class SearchSection(val shelf: SearchShelf, val items: List<YTItem>)

data class RankedSummary(
    val top: YTItem?,
    val sections: List<SearchSection>,
    val report: SearchReport,
)

data class RankedPage(val items: List<YTItem>, val report: SearchReport)

data class SearchReport(
    val source: String,
    val query: String,
    val corrected: String?,
    val received: Int,
    val kept: List<Kept>,
    val dropped: List<Dropped>,
) {
    data class Kept(val item: YTItem, val kind: SearchKind, val relevance: Relevance, val rank: Int, val note: String?)
    data class Dropped(val item: YTItem, val kind: SearchKind, val reason: String, val rank: Int)

    fun lines(): List<String> = buildList {
        add(
            "[$source] \"$query\"" + (corrected?.let { " corrected=\"$it\"" } ?: "") +
                " provider=youtube received=$received kept=${kept.size} dropped=${dropped.size}",
        )
        kept.forEachIndexed { index, k ->
            val r = k.relevance
            add(
                "  ${index + 1}. ${k.kind} ${r.tier} ${fmt(r.score)} cov=${fmt(r.coverage)} title=${fmt(r.title)}" +
                    " artist=${fmt(r.artist)} album=${fmt(r.album)} prec=${fmt(r.precision)} ver=${fmt(r.versions)}" +
                    (k.note?.let { " note=$it" } ?: "") + " #${k.rank} ${describe(k.item)}",
            )
        }
        dropped.forEach { add("  x ${it.kind} ${it.reason} #${it.rank} ${describe(it.item)}") }
    }

    private fun fmt(value: Double) = String.format(Locale.ROOT, "%.2f", value)

    private fun describe(item: YTItem): String {
        val credit = artistsOf(item).joinToString { it.name }
        return "\"${item.title.take(80)}\"" + (if (credit.isNotEmpty()) " · ${credit.take(60)}" else "") + " id=${item.id}"
    }
}

object SearchResults {
    const val MAX_BARREN_PAGES = 2
    const val LYRIC_LINE_WORDS = 5

    private val LYRIC_KINDS = setOf(SearchKind.Song, SearchKind.MusicVideo, SearchKind.Video)

    fun summary(query: String, page: SearchSummaryPage): RankedSummary {
        val items = page.summaries.flatMap { it.items }
        val judged = judge(
            "summary", query, page.correctedQuery, items,
            typeahead = false, allowed = null, shown = emptyList(), rescue = true, byContents = setOf(SearchKind.Playlist),
        )
        val top = judged.kept.firstOrNull { it.kind.official && it.relevance.tier >= Tier.Phrase } ?: judged.kept.firstOrNull()
        val others = judged.kept.filter { it !== top }
        val rest = others.groupBy { it.kind.shelf!! }
        val shelves = rest.keys.sortedWith(
            compareBy<SearchShelf> { if (it == SearchShelf.Songs) 0 else 1 }.thenBy { shelf -> rest.getValue(shelf).minOf { it.rank } },
        )
        return RankedSummary(
            top = top?.item,
            sections = shelves.map { shelf -> SearchSection(shelf, rest.getValue(shelf).map { it.item }) },
            report = judged.report.copy(kept = listOfNotNull(top) + others),
        )
    }

    fun page(
        query: String,
        corrected: String?,
        filter: YouTube.SearchFilter,
        items: List<YTItem>,
        shown: List<YTItem>,
        page: Int,
    ): RankedPage {
        val judged = judge(
            "${filterName(filter)} p$page", query, corrected, items,
            typeahead = false, allowed = filter.kinds(), shown = shown, rescue = false, byContents = setOf(SearchKind.Playlist, SearchKind.Artist),
            otherScripts = page == 1,
        )
        return RankedPage(judged.kept.map { it.item }, judged.report)
    }

    fun suggestions(query: String, items: List<YTItem>): RankedPage {
        val judged = judge(
            "typeahead", query, null, items,
            typeahead = true, allowed = null, shown = emptyList(), rescue = false, byContents = emptySet(),
        )
        return RankedPage(judged.kept.map { it.item }, judged.report)
    }

    fun log(report: SearchReport) {
        Timber.tag("Search").d(report.lines().joinToString("\n"))
    }

    private class Candidate(val item: YTItem, val kind: SearchKind, val rank: Int, var relevance: Relevance, var note: String? = null)

    private class Judged(val kept: List<SearchReport.Kept>, val report: SearchReport)

    private fun judge(
        source: String,
        query: String,
        corrected: String?,
        items: List<YTItem>,
        typeahead: Boolean,
        allowed: Set<SearchKind>?,
        shown: List<YTItem>,
        rescue: Boolean,
        byContents: Set<SearchKind>,
        otherScripts: Boolean = true,
    ): Judged {
        val asked = SearchQuery(query, typeahead)
        val correction = corrected
            ?.takeIf { it.isNotBlank() && SearchText.canonical(it) != asked.text }
            ?.let { SearchQuery(it, typeahead) }
        val seen = HashSet<String>().apply { shown.forEach { addAll(keys(it, it.searchKind())) } }
        val dropped = mutableListOf<SearchReport.Dropped>()
        val candidates = mutableListOf<Candidate>()

        items.forEachIndexed { rank, item ->
            val kind = item.searchKind()
            val reason = when {
                item.title.isBlank() -> "missing-title"
                kind == SearchKind.Unsupported -> "unsupported-type:${(item as? SongItem)?.musicVideoType}"
                allowed != null && kind !in allowed -> "wrong-type-for-filter"
                else -> null
            }
            if (reason != null) {
                dropped += SearchReport.Dropped(item, kind, reason, rank)
                return@forEachIndexed
            }
            val itemKeys = keys(item, kind)
            if (itemKeys.any { it in seen }) {
                dropped += SearchReport.Dropped(item, kind, "duplicate", rank)
                return@forEachIndexed
            }
            seen += itemKeys
            val relevance = SearchRelevance.score(asked, item, kind)
            val alternative = correction?.let { SearchRelevance.score(it, item, kind) }
            candidates += Candidate(item, kind, rank, if (alternative != null && better(alternative, relevance)) alternative else relevance)
        }

        val anchors = candidates.filter { it.relevance.tier >= Tier.Phrase }
        val anchorArtists = anchors.flatMap { artistsOf(it.item) }
        val artistIds = anchorArtists.mapNotNull { it.id }.toSet()
        val artistNames = anchorArtists.filter { it.id == null }.map { ChartText.nameKey(it.name) }.toSet()
        val albumIds = anchors.mapNotNull { (it.item as? SongItem)?.album?.id }.toSet()
        val trustProvider = allowed != null || anchors.isNotEmpty()
        for (candidate in candidates) {
            val matchedByContents = candidate.kind in byContents && trustProvider
            if (candidate.relevance.tier >= (if (matchedByContents) Tier.Phrase else Tier.Partial)) continue
            val item = candidate.item
            val note = when {
                item is ArtistItem && (item.id in artistIds || ChartText.nameKey(item.title) in artistNames) -> "artist-of-match"
                item is AlbumItem && item.browseId in albumIds -> "album-of-match"
                otherScripts && candidate.kind.official && candidate.relevance.otherScript -> "other-script"
                matchedByContents -> "provider-match"
                else -> null
            } ?: continue
            candidate.relevance = candidate.relevance.copy(tier = Tier.Related)
            candidate.note = note
        }
        if (rescue && candidates.none { it.relevance.tier >= Tier.Partial } && asked.contentWords >= LYRIC_LINE_WORDS) {
            candidates.firstOrNull()?.takeIf { it.kind in LYRIC_KINDS && it.relevance.tier == Tier.None }?.let {
                it.relevance = it.relevance.copy(tier = Tier.Related)
                it.note = "lyric-line"
            }
        }

        val kept = candidates
            .filter { it.relevance.tier > Tier.None }
            .sortedWith(
                compareByDescending<Candidate> { it.relevance.tier }
                    .thenByDescending { if (it.relevance.tier == Tier.Related) 0.0 else it.relevance.score }
                    .thenBy { it.rank },
            )
            .map { SearchReport.Kept(it.item, it.kind, it.relevance, it.rank, it.note) }
        candidates.filter { it.relevance.tier == Tier.None }.forEach {
            val r = it.relevance
            val why = when {
                it.kind.needsTitleEvidence && r.title == 0.0 -> "no-title-match"
                !r.coreMatched -> "core-missing"
                else -> "below-threshold"
            }
            dropped += SearchReport.Dropped(
                it.item,
                it.kind,
                "$why cov=${String.format(Locale.ROOT, "%.2f", r.coverage)} title=${String.format(Locale.ROOT, "%.2f", r.title)}",
                it.rank,
            )
        }
        dropped.sortBy { it.rank }
        return Judged(kept, SearchReport(source, query, correction?.let { corrected }, items.size, kept, dropped))
    }

    private fun better(a: Relevance, b: Relevance) = a.tier > b.tier || (a.tier == b.tier && a.score > b.score)

    private fun keys(item: YTItem, kind: SearchKind): List<String> {
        val id = item.id.trim()
        val primary = if (id.isNotEmpty()) {
            "id:$id"
        } else {
            "meta:${kind.shelf}|${SearchText.canonical(item.title)}|${artistsOf(item).joinToString(",") { SearchText.canonical(it.name) }}"
        }
        val list = when (item) {
            is AlbumItem -> item.playlistId
            is PlaylistItem -> id
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }?.let { "list:$it" }
        return listOfNotNull(primary, list)
    }

    private fun filterName(filter: YouTube.SearchFilter) = when (filter.value) {
        YouTube.SearchFilter.FILTER_SONG.value -> "songs"
        YouTube.SearchFilter.FILTER_VIDEO.value -> "videos"
        YouTube.SearchFilter.FILTER_ALBUM.value -> "albums"
        YouTube.SearchFilter.FILTER_ARTIST.value -> "artists"
        else -> "playlists"
    }
}
