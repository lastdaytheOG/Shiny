package com.shiny.music.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRelevanceTest {

    private fun tier(query: String, item: com.music.innertube.models.YTItem, typeahead: Boolean = false) =
        SearchRelevance.score(SearchQuery(query, typeahead), item).tier

    private fun score(query: String, item: com.music.innertube.models.YTItem) =
        SearchRelevance.score(SearchQuery(query), item).score

    @Test
    fun `normalising keeps hardtekk, slowed and remix as words`() {
        assertEquals("terranova hardtekk slowed", SearchText.canonical(TERRANOVA))
        assertEquals("janina terranova remix", SearchText.canonical("Janina - Terranova Remix"))
        assertEquals(listOf("terranova", "hardtekk", "slowed"), SearchQuery(TERRANOVA).tokens)
    }

    @Test
    fun `stylised letters fold back to plain words`() {
        assertEquals(
            "po pi po hardtekk ultra slowed reverb",
            SearchText.canonical("𝐏𝐎 𝐏𝐈 𝐏𝐎 𝐇𝐀𝐑𝐃𝐓𝐄𝐊𝐊 (𝐔𝐋𝐓𝐑𝐀 𝐒𝐋𝐎𝐖𝐄𝐃 + 𝐑𝐄𝐕𝐄𝐑𝐁)"),
        )
        assertEquals("last summer hardtekk super slowed", SearchText.canonical("Last summer - Hardtekk (𝙨𝙪𝙥𝙚𝙧 𝙨𝙡𝙤𝙬𝙚𝙙)"))
    }

    @Test
    fun `common spellings of the same version compare equal`() {
        val plain = SearchText.canonical("Terranova (Slowed + Reverb)")
        assertEquals(plain, SearchText.canonical("Terranova (Slowed & Reverb)"))
        assertEquals(plain, SearchText.canonical("terranova slowed and reverb"))
        assertEquals(SearchText.canonical("Terranova (Sped Up)"), SearchText.canonical("Terranova (Speed Up)"))
        assertEquals(SearchText.canonical("Terranova (Remix)"), SearchText.canonical("Terranova RMX"))
        assertEquals(SearchText.canonical("Song ft. Someone"), SearchText.canonical("Song featuring Someone"))
    }

    @Test
    fun `a misspelt version word still asks for that version`() {
        assertEquals(setOf("slowed"), SearchQuery("terranova slowerd").versions)
        assertEquals(listOf(1.0, 0.5), SearchQuery("terranova slowerd").weights)
        assertEquals(setOf("slowed"), SearchQuery("terranova sloved").versions)
        assertEquals(setOf("slowed", "reverb"), SearchQuery("kesariya slowd reverbed").versions)
        assertEquals(setOf("nightcore"), SearchQuery("terranova nightcor").versions)
        assertEquals(setOf("terranova"), SearchQuery("Terranova (slowerd)").core)

        assertEquals("he showed me", SearchQuery("he showed me").text)
        assertEquals("revert", SearchQuery("revert").text)
    }

    @Test
    fun `version words alone do not make another song a match`() {
        assertEquals(Tier.None, tier("terranova sped up", song("RIPTIDE (HARDTEKK) (Sped Up)", "AvantTEKK")))
        assertEquals(Tier.None, tier("kesariya slowed reverb", song("Gerua (Slowed)", "Arijit Singh")))
        assertEquals(Tier.Strong, tier("slowed reverb", song("Gerua (Slowed + Reverb)", "Arijit Singh")))
    }

    @Test
    fun `version words weigh less than names, and bracketed words mark the name`() {
        val query = SearchQuery(TERRANOVA)
        assertEquals(listOf(1.0, 1.0, 0.5), query.weights)
        assertEquals(setOf("slowed"), query.versions)
        assertEquals(setOf("terranova"), query.core)
        assertTrue(SearchQuery("terranova hardtekk slowed").core.isEmpty())
    }

    @Test
    fun `each tier is reached by the match it describes`() {
        assertEquals(Tier.Exact, tier(TERRANOVA, song("terranova (Hardtekk) (slowed)", "treit")))
        assertEquals(Tier.Exact, tier(TERRANOVA, video("Terranova (hardtekk) Slowed", "not my main")))
        assertEquals(Tier.TitleArtist, tier("janina terranova", song("Terranova", "Janina")))
        assertEquals(Tier.TitleArtist, tier("Terranova Janina", song("Terranova", "Janina")))
        assertEquals(Tier.Strong, tier(TERRANOVA, song("terranova (Hardtekk) (super slowed)", "treit")))
        assertEquals(Tier.Strong, tier("terranova slowed", song("Terranova (Slowed Down Version)", "Janina")))
        assertEquals(Tier.Phrase, tier(TERRANOVA, song("Terranova (Original Radio Mix)", "Janina")))
        assertEquals(Tier.Partial, tier("terranova hardtekk slowed", song("HERO HARDTEKK (Ultra Slowed)", "Riversmelt")))
        assertEquals(Tier.None, tier(TERRANOVA, song("HERO HARDTEKK (Ultra Slowed)", "Riversmelt")))
        assertEquals(Tier.None, tier(TERRANOVA, song("MONTAGEM NOCHE", "LUMIX")))
    }

    @Test
    fun `stronger tiers score the same item kinds above weaker ones`() {
        val exact = score(TERRANOVA, song("terranova (Hardtekk) (slowed)", "treit"))
        val strong = score(TERRANOVA, song("terranova (Hardtekk) (super slowed)", "treit"))
        assertTrue(exact > strong)
    }

    @Test
    fun `fuzzy and prefix matches count, but less than the real word`() {
        val exact = SearchRelevance.score(SearchQuery("terranova"), song("Terranova", "Janina"))
        val fuzzy = SearchRelevance.score(SearchQuery("terranva"), song("Terranova", "Janina"))
        val prefix = SearchRelevance.score(SearchQuery("terran"), song("Terranova", "Janina"))
        assertEquals(Tier.Exact, exact.tier)
        assertEquals(Tier.Partial, fuzzy.tier)
        assertEquals(Tier.Partial, prefix.tier)
        assertTrue(exact.coverage > fuzzy.coverage && fuzzy.coverage > prefix.coverage)
    }

    @Test
    fun `while typing, the last word may be unfinished`() {
        assertEquals(Tier.Strong, tier("terranova (hard", song("terranova (Hardtekk)", "treit"), typeahead = true))
        assertTrue(tier("terranova (hard", song("terranova (Hardtekk)", "treit")) < Tier.Strong)
        assertEquals(Tier.Strong, tier("terranova jan", song("Terranova (Pandera Airplay edit)", "Janina"), typeahead = true))
    }

    @Test
    fun `a channel name alone never makes an upload relevant`() {
        assertEquals(Tier.None, tier("terranova", video("Two of the largest gold projects in Canada", "Terranova Resources")))
        assertEquals(Tier.Strong, tier("terranova", song("Hold the Line", "Terranova")))
    }

    @Test
    fun `a title in another script is flagged rather than judged`() {
        val relevance = SearchRelevance.score(SearchQuery("sahiba"), song("साहिबा", "Jasleen Royal"))
        assertEquals(Tier.None, relevance.tier)
        assertTrue(relevance.otherScript)
        assertEquals(Tier.Exact, tier("साहिबा", song("साहिबा", "Jasleen Royal")))
    }

    @Test
    fun `the item kind follows the provider's video type`() {
        assertEquals(SearchKind.Song, song("a", "b").searchKind())
        assertEquals(SearchKind.Song, song("a", "b", type = null).searchKind())
        assertEquals(SearchKind.MusicVideo, musicVideo("a", "b").searchKind())
        assertEquals(SearchKind.Video, video("a", "b").searchKind())
        assertEquals(SearchKind.Video, song("a", "b", type = "MUSIC_VIDEO_TYPE_SOMETHING_NEW").searchKind())
        assertEquals(SearchKind.Unsupported, episode("a").searchKind())
        assertEquals(SearchKind.Album, album("a").searchKind())
        assertEquals(SearchKind.Artist, artist("a").searchKind())
        assertEquals(SearchKind.Playlist, playlist("a", null).searchKind())
    }
}
