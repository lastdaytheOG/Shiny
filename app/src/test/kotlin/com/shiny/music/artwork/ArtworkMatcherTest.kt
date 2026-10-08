package com.shiny.music.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkMatcherTest {

    private fun candidate(
        track: String,
        artist: String,
        album: String = "",
        albumArtist: String = "",
        millis: Long = 0,
        art: String = "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/aa/bb/cover.jpg/100x100bb.jpg",
        albumId: Long = 2,
    ) = CatalogCandidate(
        trackId = 1,
        collectionId = albumId,
        trackName = track,
        artistName = artist,
        collectionName = album,
        collectionArtistName = albumArtist,
        trackTimeMillis = millis,
        artworkUrl100 = art,
    )

    @Test
    fun `a video's title is reduced to the song's name`() {
        assertEquals("Animal", ArtworkMatcher.songTitle("KATSEYE (캣츠아이) \"Animal\" Official MV", listOf("KATSEYE")))
        assertEquals("Kill Bill", ArtworkMatcher.songTitle("SZA - Kill Bill (Official Video)", listOf("SZA")))
        assertEquals("Saturn", ArtworkMatcher.songTitle("SZA - Saturn (Lyric Video)", listOf("SZA")))
        assertEquals("luther", ArtworkMatcher.songTitle("Kendrick Lamar - luther (Official Music Video)", listOf("Kendrick Lamar", "SZA")))
        assertEquals("QUEBEC", ArtworkMatcher.songTitle("DRAKE - QUEBEC", listOf("Drake")))
        // Featured artists are not part of the name.
        assertEquals("Kiss Me More", ArtworkMatcher.songTitle("Kiss Me More feat. SZA", listOf("Doja Cat")))
        assertEquals("Kiss Me More", ArtworkMatcher.songTitle("Kiss Me More (ft. SZA)", listOf("Doja Cat")))
        // A plain title is left as it is.
        assertEquals("Dai Dai", ArtworkMatcher.songTitle("Dai Dai", listOf("Shakira", "Burna Boy")))
        assertEquals("Choosin' Texas", ArtworkMatcher.songTitle("Choosin' Texas", listOf("Ella Langley")))
    }

    @Test
    fun `names are compared without case, accents, spaces or punctuation`() {
        assertEquals("beyonce", ArtworkMatcher.key("Beyoncé"))
        assertEquals("choosintexas", ArtworkMatcher.key("Choosin'  Texas"))
        assertEquals("", ArtworkMatcher.key(" - "))
        // Re-release suffixes are not part of an album's name.
        assertEquals("sos", ArtworkMatcher.albumKey("SOS (Deluxe)"))
        assertEquals("ctrl", ArtworkMatcher.albumKey("Ctrl [Remastered]"))
        assertEquals("saturn", ArtworkMatcher.albumKey("Saturn - Single"))
    }

    @Test
    fun `the exact song by the same artist is chosen`() {
        val found = ArtworkMatcher.choose(
            listOf(candidate("Kill Bill", "SZA", album = "SOS")),
            title = "Kill Bill",
            artists = listOf("SZA"),
            album = "SOS",
            durationSeconds = 0,
        )
        assertEquals("SOS", found?.collectionName)
    }

    @Test
    fun `a wrong first result is passed over for the right one`() {
        val found = ArtworkMatcher.choose(
            listOf(
                candidate("Kill Bill (Karaoke Version)", "Karaoke Stars", album = "Karaoke Hits"),
                candidate("Kill Bill Vol. 1 Theme", "Movie Sounds", album = "Soundtracks"),
                candidate("Kill Bill", "SZA", album = "SOS"),
            ),
            title = "SZA - Kill Bill (Official Video)",
            artists = listOf("SZA"),
            album = null,
            durationSeconds = 0,
        )
        assertEquals("SOS", found?.collectionName)
    }

    @Test
    fun `another artist's song of the same name is never used`() {
        assertNull(
            ArtworkMatcher.choose(
                listOf(candidate("Animal", "Maroon 5"), candidate("Animal", "Neon Trees")),
                title = "KATSEYE (캣츠아이) \"Animal\" Official MV",
                artists = listOf("KATSEYE"),
                album = null,
                durationSeconds = 0,
            )
        )
    }

    @Test
    fun `the same artist's other songs are not used either`() {
        assertNull(
            ArtworkMatcher.choose(
                listOf(candidate("Snooze", "SZA"), candidate("Good Days", "SZA"), candidate("Kiss Me More", "Doja Cat")),
                title = "SZA - Kill Bill (Official Video)",
                artists = listOf("SZA"),
                album = null,
                durationSeconds = 0,
            )
        )
    }

    @Test
    fun `a collaboration matches on any of its artists`() {
        val found = ArtworkMatcher.choose(
            listOf(candidate("Dai Dai", "Shakira & Burna Boy", album = "Dai Dai - Single")),
            title = "Dai Dai",
            artists = listOf("Shakira", "Burna Boy"),
            album = null,
            durationSeconds = 0,
        )
        assertEquals("Dai Dai - Single", found?.collectionName)
    }

    @Test
    fun `the album it is on and its length break a tie, and a compilation loses one`() {
        val candidates = listOf(
            candidate("Snooze", "SZA", album = "Now That's What I Call Music", albumArtist = "Various Artists"),
            candidate("Snooze", "SZA", album = "SOS", millis = 201_000),
            candidate("Snooze", "SZA", album = "Snooze (Acoustic) - Single", millis = 230_000),
        )
        assertEquals(
            "SOS",
            ArtworkMatcher.choose(candidates, "SZA - Snooze (Official Video)", listOf("SZA"), album = "SOS (Deluxe)", durationSeconds = 0)?.collectionName,
        )
        assertEquals(
            "SOS",
            ArtworkMatcher.choose(candidates, "SZA - Snooze (Official Video)", listOf("SZA"), album = null, durationSeconds = 202)?.collectionName,
        )
    }

    // The catalogue's own answers on 6 October 2026, in its order, for the searches named.

    /** "Olivia Rodrigo vampire": the later edition is listed first, and has no motion artwork. */
    private val vampire = listOf(
        candidate("vampire", "Olivia Rodrigo", album = "GUTS (spilled)", millis = 220_000, albumId = 1736994853),
        candidate("vampire", "Olivia Rodrigo", album = "GUTS", millis = 220_000, albumId = 1694767605),
    )

    /** "The Weeknd Blinding Lights". */
    private val blindingLights = listOf(
        candidate("Blinding Lights (Remix)", "The Weeknd & ROSALÍA", album = "Blinding Lights (Remix) - Single", millis = 216_000, albumId = 1542842760),
        candidate("Blinding Lights (Live)", "The Weeknd", album = "Live At SoFi Stadium", millis = 253_000, albumId = 1676301803),
        candidate("Blinding Lights", "The Weeknd", album = "After Hours", millis = 200_000, albumId = 1499378108),
        candidate("Blinding Lights", "The Weeknd", album = "After Hours", millis = 200_000, albumId = 1499385848),
        candidate("Blinding Lights", "The Weeknd", album = "The Highlights (Deluxe)", millis = 200_000, albumId = 1729918970),
        candidate("Blinding Lights", "The Weeknd", album = "After Hours (Deluxe)", millis = 200_000, albumId = 1505683624),
        candidate("Blinding Lights", "The Weeknd", album = "After Hours (Deluxe)", millis = 200_000, albumId = 1615102584),
        candidate("Blinding Lights (Instrumental)", "The Weeknd", album = "After Hours (Deluxe)", millis = 202_000, albumId = 1531551389),
        candidate("Blinding Lights", "The Weeknd", album = "After Hours (Deluxe)", millis = 200_000, albumId = 1615103111),
        candidate("Blinding Lights", "The Weeknd", album = "After Hours (Deluxe)", millis = 200_000, albumId = 1505683705),
    )

    @Test
    fun `the edition named exactly is chosen over one that only shares its name`() {
        fun album(named: String) = ArtworkMatcher.choose(vampire, "vampire", listOf("Olivia Rodrigo"), album = named, durationSeconds = 220)
        assertEquals(1694767605L, album("GUTS")?.collectionId)
        assertEquals(1736994853L, album("GUTS (spilled)")?.collectionId)
        // A name the catalogue does not have exactly still finds the album it is an edition of.
        assertEquals("After Hours", ArtworkMatcher.choose(blindingLights, "Blinding Lights", listOf("The Weeknd"), "After Hours (Japan Edition)", 200)?.collectionName)
        assertEquals(1505683624L, ArtworkMatcher.choose(blindingLights, "Blinding Lights", listOf("The Weeknd"), "After Hours (Deluxe)", 200)?.collectionId)
    }

    @Test
    fun `with no album to go by, the plainly named release is chosen, not an edition of it`() {
        assertEquals("GUTS", ArtworkMatcher.choose(vampire, "Olivia Rodrigo - vampire (Official Video)", listOf("Olivia Rodrigo"), null, 0)?.collectionName)
    }

    @Test
    fun `the song itself is chosen over a remix or a live take of it, and a remix over the song when a remix is what is playing`() {
        // A video runs longer than the track, so its length says nothing here.
        assertEquals(
            "After Hours",
            ArtworkMatcher.choose(blindingLights, "The Weeknd - Blinding Lights (Official Video)", listOf("The Weeknd"), null, 262)?.collectionName,
        )
        assertEquals(
            "Blinding Lights (Remix) - Single",
            ArtworkMatcher.choose(blindingLights, "Blinding Lights (Remix)", listOf("The Weeknd", "ROSALÍA"), null, 216)?.collectionName,
        )
        assertEquals(
            "Live At SoFi Stadium",
            ArtworkMatcher.choose(blindingLights, "Blinding Lights (Live)", listOf("The Weeknd"), null, 0)?.collectionName,
        )
    }

    @Test
    fun `the other ids of a release are the same album by name, never another edition or version`() {
        val artists = listOf("The Weeknd")
        val deluxe = ArtworkMatcher.choose(blindingLights, "Blinding Lights", artists, "After Hours (Deluxe)", 200)!!
        assertEquals(
            listOf(1615102584L, 1615103111L, 1505683705L),
            ArtworkMatcher.siblings(blindingLights, deluxe, "Blinding Lights", artists).map { it.collectionId },
        )
        val standard = ArtworkMatcher.choose(blindingLights, "Blinding Lights", artists, "After Hours", 200)!!
        assertEquals(listOf(1499385848L), ArtworkMatcher.siblings(blindingLights, standard, "Blinding Lights", artists).map { it.collectionId })
        // A release listed once has no other ids.
        val spilled = ArtworkMatcher.choose(vampire, "vampire", listOf("Olivia Rodrigo"), "GUTS (spilled)", 220)!!
        assertEquals(emptyList<Long>(), ArtworkMatcher.siblings(vampire, spilled, "vampire", listOf("Olivia Rodrigo")).map { it.collectionId })
    }

    @Test
    fun `the other releases of a recording are other albums it is on, each once, and never a remix or a compilation`() {
        val artists = listOf("The Weeknd")
        val video = "The Weeknd - Blinding Lights (Official Video)"
        val hits = candidate("Blinding Lights", "The Weeknd", album = "Now That's What I Call Music", albumArtist = "Various Artists", albumId = 77)
        val all = blindingLights + hits
        val chosen = ArtworkMatcher.choose(all, video, artists, null, 0)!!
        assertEquals("After Hours", chosen.collectionName)
        assertEquals(
            listOf("The Highlights (Deluxe)", "After Hours (Deluxe)"),
            ArtworkMatcher.alternates(all, chosen, video, artists).map { it.collectionName },
        )
    }

    @Test
    fun `a short name inside a long title is not a match`() {
        assertNull(
            ArtworkMatcher.choose(
                listOf(candidate("Go", "The Chemical Brothers")),
                title = "The Chemical Brothers - Got To Keep On",
                artists = listOf("The Chemical Brothers"),
                album = null,
                durationSeconds = 0,
            )
        )
    }

    @Test
    fun `a song with nothing to identify it matches nothing`() {
        assertNull(ArtworkMatcher.choose(listOf(candidate("Animal", "KATSEYE")), title = "Animal", artists = emptyList(), album = null, durationSeconds = 0))
        assertNull(ArtworkMatcher.choose(listOf(candidate("Animal", "KATSEYE")), title = "  ", artists = listOf("KATSEYE"), album = null, durationSeconds = 0))
    }

    @Test
    fun `apple artwork is asked for at the size wanted`() {
        val address = "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/aa/bb/cover.jpg/100x100bb.jpg"
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/aa/bb/cover.jpg/1400x1400bb.jpg",
            ArtworkMatcher.sized(address, 1400),
        )
        // The other form Apple gives an address in.
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/aa/bb/cover.jpg/1200x1200bb.jpg",
            ArtworkMatcher.sized("https://is1-ssl.mzstatic.com/image/thumb/Music/v4/aa/bb/cover.jpg/{w}x{h}bb.jpg", 1200),
        )
        // Not one of theirs: left alone rather than guessed at.
        assertNull(ArtworkMatcher.sized("https://example.com/cover.jpg", 1400))
        assertNull(ArtworkMatcher.sized("https://example.com/cover.jpg", 1400, best = true))
        // The master, and the least compressed file of a size.
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/aa/bb/cover.jpg/3000x3000bb.jpg",
            ArtworkMatcher.sized(address, ArtworkMatcher.MasterPx),
        )
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/aa/bb/cover.jpg/1080x1080bb-100.jpg",
            ArtworkMatcher.sized(address, 1080, best = true),
        )
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/aa/bb/cover.jpg/1440x1440bb-100.jpg",
            ArtworkMatcher.sized("https://is1-ssl.mzstatic.com/image/thumb/Music/v4/aa/bb/cover.jpg/{w}x{h}{c}.{f}", 1440, best = true),
        )
        // An address that already says its quality is sized like any other.
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/aa/bb/cover.jpg/1080x1080bb.jpg",
            ArtworkMatcher.sized("https://is1-ssl.mzstatic.com/image/thumb/Music/v4/aa/bb/cover.jpg/600x600bb-60.jpg", 1080),
        )
        // A little over the screen, in steps of 200, within reason even for a very large screen.
        assertEquals(1200, ArtworkMatcher.sizeFor(1080))
        assertEquals(1400, ArtworkMatcher.sizeFor(1264))
        assertEquals(1600, ArtworkMatcher.sizeFor(1440))
        assertEquals(1000, ArtworkMatcher.sizeFor(480))
        assertEquals(2000, ArtworkMatcher.sizeFor(4000))
    }
}
