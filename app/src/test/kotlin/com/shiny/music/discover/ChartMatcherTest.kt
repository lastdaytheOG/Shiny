package com.shiny.music.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMatcherTest {

    private fun target(title: String, credit: String, slug: String? = null) =
        MatchTarget(title, credit, slug?.let { "https://music.apple.com/us/artist/$it/1" })

    private fun fit(target: MatchTarget, title: String, vararg artists: String, kind: CandidateKind = CandidateKind.Song) =
        ChartMatcher.assess(target, MatchCandidate(title, artists.toList(), kind = kind)).fit

    @Test
    fun `normalising folds accents, stylised letters and apostrophes but keeps the words`() {
        assertEquals("krsna", ChartText.normalize("KR\$NA"))
        assertEquals("beyonce", ChartText.normalize("Beyoncé"))
        assertEquals("mo", ChartText.normalize("MØ"))
        assertEquals("pink", ChartText.normalize("P!nk"))
        assertEquals(ChartText.normalize("Don't Stop"), ChartText.normalize("Don’t  Stop"))
        assertEquals("mere naseeb mein baby h", ChartText.normalize("Mere Naseeb mein.... Baby H"))
        assertEquals("100", ChartText.normalize("$100"))
        assertEquals("साहिबा", ChartText.normalize("साहिबा"))
    }

    @Test
    fun `stylised artist names match their plain spellings and nothing else`() {
        assertTrue(ChartMatcher.sameArtist("KR\$NA", "Krsna"))
        assertTrue(ChartMatcher.sameArtist("Sachet-Parampara", "Sachet Parampara"))
        assertTrue(ChartMatcher.sameArtist("The Weeknd", "Weeknd"))
        assertFalse(ChartMatcher.sameArtist("KR\$NA", "Krishna"))
        assertFalse(ChartMatcher.sameArtist("Karan Aujla", "Karan Aujla Fans"))
    }

    @Test
    fun `the lead artist comes from Apple's artist link wherever it sits in the credits`() {
        val credit = "Super Subu, Ritesh G Rao, HEISENBERG, Anirudh Ravichander & Vignesh Shivan"
        assertEquals("Anirudh Ravichander", ChartText.leadArtist(credit, "https://music.apple.com/in/artist/anirudh-ravichander/480976476"))
        assertEquals("Sachet-Parampara", ChartText.leadArtist("Sachet-Parampara & Irshad Kamil", "https://music.apple.com/in/artist/sachet-parampara/1"))
        assertEquals("Super Subu", ChartText.leadArtist(credit, null))
        assertEquals("藤井 風", ChartText.leadArtist("Someone & 藤井 風", "https://music.apple.com/jp/artist/%E8%97%A4%E4%BA%95-%E9%A2%A8/1486113150"))
        assertEquals(listOf("Pritam", "Arijit Singh", "Amitabh Bhattacharya"), ChartText.splitCredits("Pritam, Arijit Singh & Amitabh Bhattacharya"))
    }

    @Test
    fun `featuring credits in or out of brackets do not change the title`() {
        val t = target("Big Dawgs (feat. Kalki)", "Hanumankind", "hanumankind")
        assertEquals(Fit.Exact, fit(t, "Big Dawgs", "Hanumankind", "Kalki"))
        assertEquals(Fit.Exact, fit(t, "Big Dawgs feat. Kalki", "Hanumankind"))
        assertEquals(Fit.Exact, fit(target("Song ft. Other", "Lead"), "Song (feat. Other)", "Lead"))
        val wide = target("点描の唄 (feat. 井上苑子)", "Mrs. GREEN APPLE")
        assertEquals(Fit.Exact, fit(wide, "点描の唄（feat. 井上苑子）", "Mrs. GREEN APPLE"))
        assertEquals(Fit.None, fit(wide, "点描の唄 【LIVE】（feat. Sonoko Inoue）", "Mrs. GREEN APPLE"))
        assertEquals(listOf("Awich", "CHICO CARLITO", "ONE OK ROCK"), ChartText.splitCredits("Awich、CHICO CARLITO、ONE OK ROCK"))
    }

    @Test
    fun `soundtrack and from-the-film suffixes are descriptive, not a different version`() {
        val t = target("Sahiba (From \"Dhurandhar\")", "Jasmine Sandlas & Shashwat Sachdev", "jasmine-sandlas")
        assertEquals(Fit.Exact, fit(t, "Sahiba", "Jasmine Sandlas"))
        assertEquals(Fit.Exact, fit(target("Song - From \"Some Film\"", "Lead"), "Song", "Lead"))
        assertTrue(ChartMatcher.tags("Mix Tape Love (From the Motion Picture \"Mix Tape\")").hard.isEmpty())
    }

    @Test
    fun `remasters and deluxe editions are the same recording, other versions are not`() {
        val t = target("Song", "Artist")
        assertEquals(Fit.Exact, fit(t, "Song (Remastered 2011)", "Artist"))
        assertEquals(Fit.Exact, fit(t, "Song (Original Mix)", "Artist"))
        assertEquals(Fit.None, fit(t, "Song (Taylor's Version)", "Artist"))
        assertEquals(Fit.None, fit(t, "Song (Sped Up)", "Artist"))
        assertEquals(Fit.None, fit(t, "Song - Instrumental", "Artist"))
        assertEquals(Fit.None, fit(t, "Song (Acoustic)", "Artist"))
        assertEquals(Fit.None, fit(t, "Song (Tutorial)", "Artist"))
        assertEquals(Fit.None, fit(t, "Song (Karaoke Version)", "Sing King"))
        assertEquals(Fit.None, fit(t, "Song (Cover)", "Artist"))
    }

    @Test
    fun `another artist's or member's version is offered, never played as the original`() {
        assertEquals(Fit.Partial, fit(target("Something in the Orange", "Zach Bryan"), "Something in the Orange (Z&E's Version)", "Zach Bryan"))
        assertEquals(Fit.Partial, fit(target("恋、はじめました。", "=LOVE"), "恋、はじめました。 (Korean ver.) - Koi, Hajimemashita.", "=LOVE"))
        assertEquals(Fit.Partial, fit(target("Yes! 東京", "M!LK"), "Yes! 東京 (M!LK ver. 体験入学)", "M!LK"))
        assertEquals(Fit.Exact, fit(target("スパークル", "RADWIMPS"), "スパークル (original ver.) - Sparkle", "RADWIMPS"))
        assertEquals(Fit.None, fit(target("Billie Jean (Long Version)", "Michael Jackson"), "Billie Jean", "Michael Jackson"))
        assertEquals(Fit.None, fit(target("Dreams (Take 2)", "Fleetwood Mac"), "Dreams", "Fleetwood Mac"))
    }

    @Test
    fun `a named language must agree, an unnamed one may differ`() {
        val hindi = MatchTarget("Ramayana (Original Motion Picture Soundtrack) - Hindi", "A.R. Rahman & Kumar Vishwas", isAlbum = true)
        assertEquals(Fit.None, ChartMatcher.assess(hindi, MatchCandidate("Ramayana (Kannada)", listOf("A.R. Rahman"), kind = CandidateKind.Album)).fit)
        assertEquals(Fit.Exact, ChartMatcher.assess(hindi, MatchCandidate("Ramayana (Hindi)", listOf("A.R. Rahman"), kind = CandidateKind.Album)).fit)
        assertEquals(Fit.Exact, fit(target("Hukum Reloaded", "Anirudh Ravichander & Super Subu"), "Hukum Reloaded - Tamil (From \"Jailer 2\")", "Anirudh Ravichander"))
    }

    @Test
    fun `a live or session chart item may fall back to the studio recording, never the reverse`() {
        assertEquals(Fit.Alternative, fit(target("Die On This Hill (At KOKO)", "SIENNA SPIRO"), "Die On This Hill", "SIENNA SPIRO"))
        assertEquals(Fit.Alternative, fit(target("Die On This Hill (At KOKO)", "SIENNA SPIRO"), "Die On This Hill (Unplugged)", "SIENNA SPIRO"))
        assertEquals(
            Fit.Alternative,
            fit(target("チラチLOVE (from M!LK ARENA TOUR 2025-2026「SMILE POP!」 LIVE at 国立代々木競技場)", "M!LK"), "チラチLOVE - Chirachi LOVE", "M!LK"),
        )
        assertEquals(Fit.Alternative, fit(target("End of Beginning (Apple Music Sessions)", "Dylan Gossett"), "End of Beginning", "Dylan Gossett"))
        assertEquals(Fit.Alternative, fit(target("Song (Live)", "Artist"), "Song", "Artist"))
        assertEquals(Fit.Exact, fit(target("Song (Live)", "Artist"), "Song (Live at Wembley)", "Artist"))
        assertEquals(Fit.None, fit(target("Song", "Artist"), "Song (Live)", "Artist"))
        assertEquals(Fit.None, fit(target("Song (Live)", "Artist"), "Song (Remix)", "Artist"))
    }

    @Test
    fun `confidence needs the title and the artist`() {
        val t = target("Tum Ho", "Pritam & Mohit Chauhan", "pritam")
        assertEquals(Fit.Exact, fit(t, "Tum Ho", "Pritam"))
        assertEquals(Fit.Exact, fit(t, "Tum Ho", "Mohit Chauhan"))
        assertEquals(Fit.Partial, fit(t, "Tum Ho Paas", "Mohit Chauhan"))
        assertEquals(Fit.None, fit(t, "Tum Ho", "Someone Else"))
        assertEquals(Fit.None, fit(t, "Kuch Aur", "Pritam"))
        assertEquals(Fit.Partial, fit(target("petal in the pavement", "Ariana Grande"), "petal", "Ariana Grande"))
    }

    @Test
    fun `unofficial uploads are rejected outright`() {
        val t = target("Ask of You", "Raphael Saadiq")
        assertEquals(Fit.None, fit(t, "Ask of You", "Raphael Saadiq", kind = CandidateKind.Other))
        assertEquals(Fit.Exact, fit(t, "Ask of You (Official Video)", "Raphael Saadiq", kind = CandidateKind.OfficialVideo))
    }

    @Test
    fun `the audio recording is preferred over the official video`() {
        val t = target("Song", "Artist")
        val decision = ChartMatcher.decide(
            t,
            listOf(
                MatchCandidate("Song (Official Video)", listOf("Artist"), kind = CandidateKind.OfficialVideo),
                MatchCandidate("Song", listOf("Artist"), kind = CandidateKind.Song),
            ),
        )
        assertEquals(1, decision?.index)
    }

    @Test
    fun `an unknown artist only counts for an album with a matching title and year`() {
        val soundtrack = MatchTarget("Dhurandhar (Original Motion Picture Soundtrack)", "Various Artists", year = 2025, isAlbum = true)
        assertEquals(Fit.Exact, ChartMatcher.assess(soundtrack, MatchCandidate("Dhurandhar", listOf("Shashwat Sachdev"), kind = CandidateKind.Album, year = 2025)).fit)
        assertEquals(Fit.Partial, ChartMatcher.assess(soundtrack, MatchCandidate("Dhurandhar", listOf("Someone"), kind = CandidateKind.Album, year = 2015)).fit)
        assertEquals(Fit.None, ChartMatcher.assess(soundtrack, MatchCandidate("Dhurandhar The Revenge", listOf("Shashwat Sachdev"), kind = CandidateKind.Album, year = 2026)).fit)
        val song = MatchTarget("Ashke", "Karan Aujla")
        assertEquals(Fit.Partial, ChartMatcher.assess(song, MatchCandidate("Ashke", listOf("Various Artists"))).fit)
        assertNull(ChartMatcher.decide(song, listOf(MatchCandidate("Ashke", listOf("Various Artists")))))
    }

    @Test
    fun `chart years come from Apple's release date`() {
        assertEquals(2025, ChartText.year("2025-12-05"))
        assertNull(ChartText.year(null))
        assertNull(ChartText.year("soon"))
        assertTrue(ChartText.isVarious("Various Artists"))
        assertFalse(ChartText.isVarious("Various Cruelties"))
    }
}
