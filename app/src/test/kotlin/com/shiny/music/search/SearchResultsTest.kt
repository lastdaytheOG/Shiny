package com.shiny.music.search

import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultsTest {

    private fun RankedSummary.reason(title: String) = report.dropped.filter { it.item.title == title }.map { it.reason }

    @Test
    fun `terranova hardtekk slowed puts the exact recording first and keeps finance items out`() {
        val ranked = SearchResults.summary(TERRANOVA, terranovaPage)

        assertEquals("terranova (Hardtekk) (slowed)", ranked.top?.title)
        assertEquals(
            listOf(
                "terranova (Hardtekk) (super slowed)",
                "Terranova (Slowed Down Version)",
                "Terranova (Hardstyle) (Super Slowed)",
                "Terranova (Original Radio Mix)",
            ),
            ranked.shelf(SearchShelf.Songs).titles(),
        )
        assertEquals(listOf("Terranova (hardtekk) Slowed", "terranova x hardtekk (slowed)"), ranked.shelf(SearchShelf.Videos).titles())
        assertEquals(3, ranked.shelf(SearchShelf.Albums).size)
        assertEquals(listOf("treit", "Janina"), ranked.shelf(SearchShelf.Artists).titles())
        assertEquals(listOf("Ultra slowed/cool/funk", "HARDTEKK LARP"), ranked.shelf(SearchShelf.Playlists).titles())
        assertEquals(
            listOf(SearchShelf.Songs, SearchShelf.Videos, SearchShelf.Albums, SearchShelf.Playlists, SearchShelf.Artists),
            ranked.sections.map { it.shelf },
        )

        val shown = ranked.all().titles()
        assertTrue(shown.none { "Aphria" in it || "gold projects" in it })
        assertTrue(shown.none { it.startsWith("Hard Techno") || it.startsWith("HERO") || it.startsWith("MONTAGEM") || it.startsWith("Last summer") })

        assertTrue(ranked.report.dropped.filter { it.kind == SearchKind.Unsupported }.all { it.reason.startsWith("unsupported-type:MUSIC_VIDEO_TYPE_PODCAST_EPISODE") })
        assertEquals(2, ranked.report.dropped.count { it.kind == SearchKind.Unsupported })
        assertTrue(ranked.reason("What Aphria Financials Mean for Investors").contains("unsupported-type:MUSIC_VIDEO_TYPE_PODCAST_EPISODE"))
        assertTrue(ranked.reason("What Aphria Financials Mean for Investors").any { it.startsWith("no-title-match") })
        assertTrue(ranked.reason("Two of the largest gold projects in Canada").single().startsWith("no-title-match"))
        assertTrue(ranked.reason("HERO HARDTEKK (Ultra Slowed)").single().startsWith("core-missing"))
    }

    @Test
    fun `exact title and artist beats versions and covers`() {
        val ranked = SearchResults.summary(
            "Blinding Lights The Weeknd",
            page(
                "Songs" to listOf(
                    song("Blinding Lights (Slowed)", "The Weeknd"),
                    song("Blinding Lights", "Cover Nation"),
                    song("Blinding Lights", "The Weeknd"),
                    song("Save Your Tears", "The Weeknd"),
                ),
                "Videos" to listOf(musicVideo("The Weeknd - Blinding Lights (Official Video)", "The Weeknd")),
            ),
        )

        val top = ranked.top as SongItem
        assertEquals("Blinding Lights", top.title)
        assertEquals("The Weeknd", top.artists.single().name)
        assertEquals(listOf("Blinding Lights (Slowed)", "Blinding Lights"), ranked.shelf(SearchShelf.Songs).titles())
        assertEquals("Cover Nation", (ranked.shelf(SearchShelf.Songs)[1] as SongItem).artists.single().name)
        assertEquals(listOf("The Weeknd - Blinding Lights (Official Video)"), ranked.shelf(SearchShelf.Videos).titles())
        assertFalse("Save Your Tears" in ranked.all().titles())
    }

    @Test
    fun `partial title and artist still finds the song`() {
        val ranked = SearchResults.summary(
            "blinding weeknd",
            page("Songs" to listOf(song("Blinding Lights", "Cover Nation"), song("Blinding Lights", "The Weeknd"), song("Hero", "Miss Papaya"))),
        )
        assertEquals("The Weeknd", (ranked.top as SongItem).artists.single().name)
        assertEquals(listOf("Blinding Lights"), ranked.shelf(SearchShelf.Songs).titles())
        assertFalse("Hero" in ranked.all().titles())

        val prefix = SearchResults.summary("terran", page("Songs" to listOf(song("Terranova", "Janina"), song("Hero", "Miss Papaya"))))
        assertEquals("Terranova", prefix.top?.title)
        assertTrue(prefix.sections.isEmpty())
    }

    @Test
    fun `typeahead accepts an unfinished last word`() {
        val ranked = SearchResults.suggestions(
            "terranova jan",
            listOf(
                song("Terranova", "Some Other"),
                video("Janina - Terranova Remix", "alviloid"),
                song("Terranova (Pandera Airplay edit)", "Janina"),
                episode("Terranova Gold quarterly update"),
                song("Bang Bang Bang", "lil yappa"),
            ),
        )
        assertEquals(
            listOf("Terranova (Pandera Airplay edit)", "Janina - Terranova Remix", "Terranova"),
            ranked.items.titles(),
        )
    }

    @Test
    fun `asking for a remix ranks remixes first and keeps the original`() {
        val versions = page(
            "Songs" to listOf(
                song("Terranova", "Janina"),
                song("Terranova (Remix)", "Janina"),
                song("Terranova (Slowed Down Version)", "Janina"),
            ),
        )

        val remix = SearchResults.summary("Terranova remix", versions)
        assertEquals("Terranova (Remix)", remix.top?.title)
        assertEquals(listOf("Terranova", "Terranova (Slowed Down Version)"), remix.shelf(SearchShelf.Songs).titles())

        val plain = SearchResults.summary("Terranova", versions)
        assertEquals("Terranova", plain.top?.title)
        assertEquals(setOf("Terranova (Remix)", "Terranova (Slowed Down Version)"), plain.shelf(SearchShelf.Songs).titles().toSet())
    }

    @Test
    fun `covers and alternate spellings of a version are found`() {
        val cover = SearchResults.summary(
            "Blinding Lights cover",
            page(
                "Songs" to listOf(song("Blinding Lights", "The Weeknd"), song("Blinding Lights (Cover)", "Cover Nation")),
                "Videos" to listOf(video("Blinding Lights - Acoustic Cover", "Jane Doe")),
            ),
        )
        assertEquals("Blinding Lights (Cover)", cover.top?.title)
        assertEquals(listOf("Blinding Lights"), cover.shelf(SearchShelf.Songs).titles())
        assertEquals(listOf("Blinding Lights - Acoustic Cover"), cover.shelf(SearchShelf.Videos).titles())

        val spedUp = SearchResults.summary(
            "terranova sped up",
            page("Songs" to listOf(song("Terranova (Sped Up Version)", "Janina"), song("Terranova (Speed Up)", "puremind"))),
        )
        assertEquals("Terranova (Speed Up)", spedUp.top?.title)
        assertEquals(listOf("Terranova (Sped Up Version)"), spedUp.shelf(SearchShelf.Songs).titles())
    }

    @Test
    fun `asking for a version keeps every version of the song with the asked one first`() {
        val versions = page(
            "Songs" to listOf(
                song("Terranova (Original Radio Mix)", "Janina"),
                song("Terranova (Sped Up Version)", "Janina"),
                song("Terranova (Slowed Down Version)", "Janina"),
                song("terranova (Hardtekk) (super slowed)", "treit"),
                song("RIPTIDE (HARDTEKK) (Sped Up)", "AvantTEKK"),
            ),
            "Videos" to listOf(video("Nightcore - Terranova (Janina)", "Susan Darkheartz")),
        )

        val spedUp = SearchResults.summary("terranova speed up", versions)
        assertEquals("Terranova (Sped Up Version)", spedUp.top?.title)
        assertEquals(
            setOf("Terranova (Original Radio Mix)", "Terranova (Slowed Down Version)", "terranova (Hardtekk) (super slowed)"),
            spedUp.shelf(SearchShelf.Songs).titles().toSet(),
        )
        assertEquals(listOf("Nightcore - Terranova (Janina)"), spedUp.shelf(SearchShelf.Videos).titles())
        assertTrue(spedUp.reason("RIPTIDE (HARDTEKK) (Sped Up)").single().startsWith("core-missing"))

        val misspelt = SearchResults.summary("terranova slowerd", versions)
        assertEquals("Terranova (Slowed Down Version)", misspelt.top?.title)
        assertEquals(
            listOf("terranova (Hardtekk) (super slowed)", "Terranova (Original Radio Mix)", "Terranova (Sped Up Version)"),
            misspelt.shelf(SearchShelf.Songs).titles(),
        )
    }

    @Test
    fun `playlists and artists the provider found stay, after the ones named for the song`() {
        val playlists = SearchResults.page(
            TERRANOVA,
            null,
            YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST,
            listOf(
                playlist("Larp edits playlist songs", "rodrix2"),
                playlist("Hardtekk/Hardstyle/Jumpstyle mix", "Aidenthebacon"),
                playlist("Terranova hardtekk slowed", "someone"),
                playlist("Gym", "Krystal"),
            ),
            shown = emptyList(),
            page = 1,
        )
        assertEquals(
            listOf("Terranova hardtekk slowed", "Larp edits playlist songs", "Hardtekk/Hardstyle/Jumpstyle mix", "Gym"),
            playlists.items.titles(),
        )
        assertEquals(listOf(null, "provider-match", "provider-match", "provider-match"), playlists.report.kept.map { it.note })

        val artists = SearchResults.page(
            "kesariya slowed reverb",
            null,
            YouTube.SearchFilter.FILTER_ARTIST,
            listOf(artist("Pritam"), artist("Arijit Singh")),
            shown = emptyList(),
            page = 1,
        )
        assertEquals(listOf("Pritam", "Arijit Singh"), artists.items.titles())
    }

    @Test
    fun `provider playlists never fill an otherwise empty page or the typeahead`() {
        val nonsense = SearchResults.summary(
            "zzqxv blorptastic wumpf",
            page("Songs" to listOf(song("Bkjhgfgf", "Nisha Singh")), "Playlists" to listOf(playlist("Gym", "Krystal"))),
        )
        assertNull(nonsense.top)
        assertTrue(nonsense.sections.isEmpty())

        val typed = SearchResults.suggestions("terranova", listOf(song("Terranova", "Janina"), playlist("Gym", "Krystal")))
        assertEquals(listOf("Terranova"), typed.items.titles())
    }

    @Test
    fun `an official release takes the top result before a user playlist with the same words`() {
        val ranked = SearchResults.summary(
            "blinding lights cover",
            page(
                "Albums" to listOf(album("Blinding Lights (Rock Cover)", "Mike Covers")),
                "Playlists" to listOf(playlist("Blinding lights - Cover", "C -Linfa")),
            ),
        )
        assertEquals("Blinding Lights (Rock Cover)", ranked.top?.title)
        assertEquals(listOf("Blinding lights - Cover"), ranked.shelf(SearchShelf.Playlists).titles())
        assertEquals("Blinding Lights (Rock Cover)", ranked.report.kept.first().item.title)
    }

    @Test
    fun `nothing relevant leaves the page empty`() {
        val ranked = SearchResults.summary(
            "zzqxv blorptastic wumpf",
            page(
                "Top result" to listOf(song("Surface MMMMMCCCXVMMMVIII (peduncle, receptacle, petal)", "Borasisi")),
                "Songs" to listOf(song("Bkjhgfgf", "Nisha Singh"), song("WQLGJBUHPCPF", "Phlit")),
                "Videos" to listOf(musicVideo("Sharp Dressed Man", "ZZ Top"), episode("Kesariya (Synthwave)")),
            ),
        )
        assertNull(ranked.top)
        assertTrue(ranked.sections.isEmpty())
        assertEquals(5, ranked.report.dropped.size)
    }

    @Test
    fun `a lyric line keeps only the provider's own top pick`() {
        val ranked = SearchResults.summary(
            "we are no strangers to love you know the rules",
            page(
                "Top result" to listOf(video("Rick Astley - Never Gonna Give You Up (Lyrics)", "Young Pilgrim Music")),
                "Songs" to listOf(song("Pass The Dutchie", "Musical Youth"), song("Stressed Out", "twenty one pilots")),
            ),
        )
        assertEquals("Rick Astley - Never Gonna Give You Up (Lyrics)", ranked.top?.title)
        assertTrue(ranked.sections.isEmpty())
        assertEquals("lyric-line", ranked.report.kept.single().note)
    }

    @Test
    fun `the songs tab drops videos and episodes the provider mixed in`() {
        val ranked = SearchResults.page(
            TERRANOVA,
            null,
            YouTube.SearchFilter.FILTER_SONG,
            listOf(
                song("terranova (Hardtekk) (slowed)", "treit", id = "x1"),
                video("Hard Techno Is Not A Crime ØTJE · GEWOONRAVES", "Stern Radio Rochlitz"),
                episode("What Aphria Financials Mean for Investors"),
                song("HERO HARDTEKK (Ultra Slowed)", "Riversmelt"),
                video("Terranova (hardtekk) Slowed", "not my main"),
                song("terranova (Hardtekk) (slowed)", "treit", id = "x1"),
            ),
            shown = emptyList(),
            page = 1,
        )
        assertEquals(listOf("terranova (Hardtekk) (slowed)"), ranked.items.titles())
        assertEquals(
            listOf("wrong-type-for-filter", "unsupported-type", "core-missing", "wrong-type-for-filter", "duplicate"),
            ranked.report.dropped.map { it.reason.substringBefore(':').substringBefore(' ') },
        )
    }

    @Test
    fun `the videos tab keeps official and user videos that match`() {
        val ranked = SearchResults.page(
            TERRANOVA,
            null,
            YouTube.SearchFilter.FILTER_VIDEO,
            listOf(
                video("terranova x hardtekk (slowed)", "Real_F22"),
                musicVideo("Terranova (Hardtekk) (Slowed) [Official Video]", "treit"),
                song("terranova (Hardtekk) (slowed)", "treit"),
                episode("Two of the largest gold projects in Canada are moving ahead"),
                video("What Aphria Financials Mean for Investors", "Money Professor"),
            ),
            shown = emptyList(),
            page = 1,
        )
        assertEquals(listOf("Terranova (Hardtekk) (Slowed) [Official Video]", "terranova x hardtekk (slowed)"), ranked.items.titles())
    }

    @Test
    fun `an official song in another script stays, an upload in another script does not`() {
        val ranked = SearchResults.summary(
            "sahiba",
            page(
                "Songs" to listOf(song("Sahiba", "Aditya Rikhari"), song("साहिबा", "Jasleen Royal")),
                "Videos" to listOf(video("साहिबा full video", "Some Channel")),
            ),
        )
        assertEquals("Sahiba", ranked.top?.title)
        assertEquals(listOf("साहिबा"), ranked.shelf(SearchShelf.Songs).titles())
        assertEquals("other-script", ranked.report.kept.single { it.item.title == "साहिबा" }.note)
        assertTrue(ranked.shelf(SearchShelf.Videos).isEmpty())

        val songs = listOf(song("साहिबा", "Jasleen Royal"))
        assertEquals(listOf("साहिबा"), SearchResults.page("sahiba", null, YouTube.SearchFilter.FILTER_SONG, songs, emptyList(), 1).items.titles())
        assertTrue(SearchResults.page(TERRANOVA, null, YouTube.SearchFilter.FILTER_SONG, listOf(song("Т Λ Ξ Н", "Δ R X Ø И T Δ S")), emptyList(), 8).items.isEmpty())
    }

    @Test
    fun `the provider's spelling correction is used when it helps`() {
        val songs = page("Songs" to listOf(song("Terranova (Pandera Airplay edit)", "Janina")), corrected = "terranova janina")
        val corrected = SearchResults.summary("terranva janina", songs)
        assertEquals(Tier.TitleArtist, corrected.report.kept.single().relevance.tier)
        assertEquals("terranova janina", corrected.report.corrected)

        val uncorrected = SearchResults.summary("terranva janina", songs.copy(correctedQuery = null))
        assertEquals(Tier.Partial, uncorrected.report.kept.single().relevance.tier)
    }

    @Test
    fun `duplicates show once, by id or by title and artist when the id is missing`() {
        val ranked = SearchResults.summary(
            TERRANOVA,
            page(
                "Top result" to listOf(song("terranova (Hardtekk) (slowed)", "treit", id = "x1")),
                "Songs" to listOf(
                    song("terranova (Hardtekk) (slowed)", "treit", id = "x1"),
                    song("Terranova", "Janina", id = ""),
                    song("Terranova", "Janina", id = ""),
                ),
            ),
        )
        assertEquals("x1", ranked.top?.id)
        assertEquals(listOf("Terranova"), ranked.shelf(SearchShelf.Songs).titles())
        assertEquals(2, ranked.report.dropped.count { it.reason == "duplicate" })

        val releases = SearchResults.summary(
            "terranova hardtekk",
            page(
                "Albums" to listOf(album("terranova (Hardtekk)", "treit", list = "OLAK-1")),
                "Playlists" to listOf(playlist("terranova (Hardtekk)", "treit", id = "OLAK-1")),
            ),
        )
        assertTrue(releases.top is AlbumItem)
        assertTrue(releases.sections.isEmpty())
    }

    @Test
    fun `missing metadata is tolerated and a missing title is dropped`() {
        val ranked = SearchResults.summary(
            "terranova",
            page(
                "Songs" to listOf(
                    SongItem(id = "s1", title = "Terranova", artists = emptyList(), thumbnail = "t"),
                    SongItem(id = "s2", title = "   ", artists = emptyList(), thumbnail = "t"),
                ),
                "Albums" to listOf(AlbumItem(browseId = "MPRE-a", playlistId = "", title = "Terranova", artists = null, thumbnail = "t")),
                "Playlists" to listOf(playlist("Terranova", author = null)),
            ),
        )
        assertEquals("s1", ranked.top?.id)
        assertEquals(listOf(SearchShelf.Albums, SearchShelf.Playlists), ranked.sections.map { it.shelf })
        assertEquals(listOf("missing-title"), ranked.report.dropped.map { it.reason })
    }

    @Test
    fun `later pages skip what is already on screen`() {
        val first = song("terranova (Hardtekk) (slowed)", "treit", id = "x1")
        val ranked = SearchResults.page(
            TERRANOVA,
            null,
            YouTube.SearchFilter.FILTER_SONG,
            listOf(first, song("terranova (Hardtekk) (super slowed)", "treit", id = "x2")),
            shown = listOf(first),
            page = 2,
        )
        assertEquals(listOf("x2"), ranked.items.map { it.id })
    }

    @Test
    fun `the report names every exclusion and the final order`() {
        val lines = SearchResults.summary(TERRANOVA, terranovaPage).report.lines()
        assertTrue(lines.first().startsWith("[summary] \"$TERRANOVA\" provider=youtube received=22 kept=14 dropped=8"))
        assertTrue(lines[1].startsWith("  1. Song Exact 1.00"))
        assertTrue(lines.any { it.startsWith("  x Unsupported unsupported-type:MUSIC_VIDEO_TYPE_PODCAST_EPISODE") })
    }
}
