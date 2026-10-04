package com.shiny.music.spotifyimport

import com.shiny.music.home.HomeSpotifyMix
import com.shiny.music.spotify.models.SpotifyHomeFeed
import com.shiny.music.spotify.models.SpotifyHomeFeedItem
import com.shiny.music.spotify.models.SpotifyHomeFeedSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyMixesRepositoryTest {

    private fun playlist(id: String, owner: String? = "Spotify", madeFor: String? = null, description: String? = null) =
        SpotifyHomeFeedItem.Playlist(
            uri = "spotify:playlist:$id",
            id = id,
            name = "Mix $id",
            description = description,
            format = null,
            totalCount = 50,
            imageUrl = "https://i.scdn.co/$id",
            extractedColorHex = null,
            ownerName = owner,
            madeForUsername = madeFor,
        )

    private fun feed(vararg sections: List<SpotifyHomeFeedItem>) = SpotifyHomeFeed(
        greeting = null,
        sections = sections.mapIndexed { i, items -> SpotifyHomeFeedSection("s$i", "Section $i", "HomeGenericSectionData", items.size, items) },
    )

    @Test
    fun `the mixes Spotify made for the listener are picked, in Spotify's order, once each`() {
        val picked = SpotifyMixesRepository.pickMixes(
            feed(
                listOf(playlist("editorial"), playlist("dm1", madeFor = "me")),
                listOf(playlist("dw", madeFor = "me"), playlist("dm1", madeFor = "me")),
            ),
        )
        assertEquals(listOf("dm1", "dw"), picked.map { it.id })
    }

    @Test
    fun `without madeFor marks, Spotify's own playlists stand in and user playlists are left out`() {
        val picked = SpotifyMixesRepository.pickMixes(feed(listOf(playlist("a"), playlist("mine", owner = "someone"), playlist("b"))))
        assertEquals(listOf("a", "b"), picked.map { it.id })
    }

    @Test
    fun `at most MAX_MIXES are kept`() {
        val many = (0 until 20).map { playlist("m$it", madeFor = "me") }
        assertEquals(SpotifyMixesRepository.MAX_MIXES, SpotifyMixesRepository.pickMixes(feed(many)).size)
    }

    @Test
    fun `a merge keeps the sync state of mixes already known and starts new ones unsynced`() {
        val old = listOf(HomeSpotifyMix("dm1", "Old name", localPlaylistId = "SPOTIFY_MIX_dm1", songCount = 48, syncedAt = 99))
        val merged = SpotifyMixesRepository.merge(old, listOf(playlist("dm1", madeFor = "me"), playlist("dw", madeFor = "me")))

        assertEquals("Mix dm1", merged[0].name)
        assertEquals(48, merged[0].songCount)
        assertEquals(99L, merged[0].syncedAt)
        assertEquals(0, merged[1].songCount)
        assertEquals(0L, merged[1].syncedAt)
        assertEquals("SPOTIFY_MIX_dw", merged[1].localPlaylistId)
    }

    @Test
    fun `descriptions lose Spotify's HTML and blank ones are dropped`() {
        val merged = SpotifyMixesRepository.merge(
            emptyList(),
            listOf(
                playlist("a", description = "Drake, Travis Scott &amp; <a href=\"spotify:artist:1\">more</a>"),
                playlist("b", description = "<a href=\"x\"></a>"),
            ),
        )
        assertEquals("Drake, Travis Scott & more", merged[0].description)
        assertNull(merged[1].description)
    }
}
