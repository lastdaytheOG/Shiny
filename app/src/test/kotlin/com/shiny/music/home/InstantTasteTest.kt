package com.shiny.music.home

import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.HomeSavedArtist
import com.shiny.music.db.entities.HomeSavedSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Instant taste: what Home can know about a listener from the music they chose — likes,
 * saves, playlists, Spotify mixes, device files — before they have played anything.
 */
class InstantTasteTest {

    private val now = LocalDateTime.of(2026, 9, 25, 20, 30).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun signals(
        savedSongs: Int = 0,
        localSongs: Int = 0,
        savedArtists: List<HomeSavedArtist> = emptyList(),
        artistStats: List<HomeArtistStat> = emptyList(),
        events: Int = 0,
    ) = HomeSignals.Empty.copy(
        counts = HomeSignals.Empty.counts.copy(localSongs = localSongs, events = events),
        savedSongs = savedSongs,
        savedArtists = savedArtists,
        artistStats = artistStats,
    )

    @Test
    fun `a full library gives a head start but never passes for listening`() {
        val nothing = HomeTaste.confidence(signals())
        val some = HomeTaste.confidence(signals(savedSongs = 40))
        val huge = HomeTaste.confidence(signals(savedSongs = 5_000, localSongs = 5_000))

        assertEquals(0.0, nothing, 1e-9)
        assertEquals("40 saved songs give half the prior", HomeTaste.MAX_PRIOR / 2, some, 1e-9)
        assertTrue("never above the prior's ceiling", huge < HomeTaste.MAX_PRIOR)
        assertTrue("and never past a real listener's second week", huge < 0.29)
    }

    @Test
    fun `listening only ever adds to what the library said`() {
        val base = signals(savedSongs = 60)
        val listened = base.copy(counts = base.counts.copy(events = 12))
        assertTrue(HomeTaste.confidence(listened) > HomeTaste.confidence(base))
        assertTrue(HomeTaste.confidence(listened) > HomeTaste.confidence(signals(events = 12)))
    }

    @Test
    fun `saved artists reach Warming but a library alone never makes a Favourite`() {
        val few = HomeTaste.savedAffinity(HomeSavedArtist("UCa", "A", songs = 2, liked = 0))
        val many = HomeTaste.savedAffinity(HomeSavedArtist("UCb", "B", songs = 200, liked = 200))

        assertEquals(AffinityTier.Exploring, few.tier)
        assertEquals(AffinityTier.Warming, many.tier)
        assertTrue(many.strength < 0.55)
        assertEquals("no invented plays", 0, many.plays)
    }

    @Test
    fun `device-file artists are found by name and merged with the same artist on YouTube`() {
        val s = signals(
            savedArtists = listOf(
                HomeSavedArtist("LOCAL_ARTIST_1", "Arijit Singh", songs = 6, liked = 0),
                HomeSavedArtist("UCarijit", "Arijit Singh", songs = 2, liked = 2),
                HomeSavedArtist("LOCAL_ARTIST_2", "Nobody Found", songs = 9, liked = 0),
            ),
        )
        val resolved = HomeTaste.savedArtists(s, mapOf("arijit singh" to "UCarijit", "nobody found" to ""))

        assertEquals("the miss stays out; the two Arijits are one", listOf("UCarijit"), resolved.map { it.artistId })
        assertEquals(8, resolved.single().songs)
        assertEquals(2, resolved.single().liked)
    }

    @Test
    fun `where an artist is both played and saved, the play counts stay real`() {
        val played = HomeArtistStat("UCa", plays = 1, weekPlays = 1, monthPlays = 1, lastPlayed = now, songs = 1)
        val s = signals(
            artistStats = listOf(played),
            savedArtists = listOf(HomeSavedArtist("UCa", "A", songs = 30, liked = 10)),
            events = 1,
        )
        val a = HomeTaste.artists(s, now).single()
        assertEquals(1, a.plays)
        assertEquals("one play is weaker than thirty saved songs", AffinityTier.Warming, a.tier)
    }

    @Test
    fun `saved artists fill the top artists after the played ones, never before`() {
        val played = HomeArtistStat("UCplayed", plays = 3, weekPlays = 3, monthPlays = 3, lastPlayed = now, songs = 2)
        val s = signals(
            artistStats = listOf(played),
            savedArtists = listOf(
                HomeSavedArtist("UCsaved", "Saved", songs = 50, liked = 20),
                HomeSavedArtist("UCthin", "Thin", songs = 1, liked = 0),
            ),
            events = 3,
        )
        val top = HomeFeedBuilder.topArtists(s, limit = 6)
        assertEquals(listOf("UCplayed", "UCsaved"), top.map { it.artistId })
        assertEquals(0, top[1].plays)
    }

    @Test
    fun `only device-file names worth a search are looked up, once`() {
        val s = signals(
            savedArtists = listOf(
                HomeSavedArtist("LOCAL_ARTIST_1", "Known", songs = 5, liked = 0),
                HomeSavedArtist("LOCAL_ARTIST_2", "New One", songs = 5, liked = 0),
                HomeSavedArtist("LOCAL_ARTIST_3", "<unknown>", songs = 40, liked = 0),
                HomeSavedArtist("LOCAL_ARTIST_4", "One Song", songs = 1, liked = 0),
                HomeSavedArtist("UCyt", "On YouTube", songs = 9, liked = 0),
            ),
        )
        assertEquals(listOf("New One"), HomeFeedBuilder.unresolvedSavedArtists(s, mapOf("known" to "UCk"), limit = 5))
    }

    @Test
    fun `a like seeds harder than a save, a save than a playlist, a playlist than a Spotify mix`() {
        val w = HomeRanking.savedSeedWeights(
            listOf(
                HomeSavedSong("mix", liked = false, inLibrary = false, inPlaylist = false, inMix = true),
                HomeSavedSong("pl", liked = false, inLibrary = false, inPlaylist = true, inMix = false),
                HomeSavedSong("lib", liked = false, inLibrary = true, inPlaylist = true, inMix = false),
                HomeSavedSong("like", liked = true, inLibrary = true, inPlaylist = false, inMix = false),
                HomeSavedSong("LOCAL_x", liked = true, inLibrary = true, inPlaylist = false, inMix = false),
            ),
        )
        assertEquals(listOf("like", "lib", "pl", "mix"), w.entries.sortedByDescending { it.value }.map { it.key })
    }

    @Test
    fun `a Spotify top track seeds between a like and a library save, and under one real play`() {
        val w = HomeRanking.savedSeedWeights(
            listOf(
                HomeSavedSong("lib", liked = false, inLibrary = true, inPlaylist = false, inMix = false),
                HomeSavedSong("top", liked = false, inLibrary = false, inPlaylist = false, inMix = true, inTop = true),
                HomeSavedSong("like", liked = true, inLibrary = false, inPlaylist = false, inMix = false, inTop = true),
            ),
        )
        assertEquals(listOf("like", "top", "lib"), w.entries.sortedByDescending { it.value }.map { it.key })
        val merged = HomeRanking.mergeSeeds(mapOf("played" to kotlin.math.ln(2.0) + 2.0), w)
        assertEquals("played", merged.keys.first())
    }

    @Test
    fun `one real play outweighs every saved seed`() {
        val played = mapOf("p" to kotlin.math.ln(2.0) + 2.0)
        val saved = (0 until 40).associate { "s$it" to 0.8 } + ("p" to 0.8)
        val merged = HomeRanking.mergeSeeds(played, saved, limit = 25)

        assertEquals("p", merged.keys.first())
        assertEquals("the played weight is kept, not the saved one", played.getValue("p"), merged.getValue("p"), 1e-9)
        assertEquals(25, merged.size)
    }
}
