package com.shiny.music.spotifyimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyPlaylistLinkTest {
    private val id = "37i9dQZF1DXcBWIGoYBM5M"

    @Test
    fun `a web link gives its playlist`() {
        assertEquals(id, SpotifyPlaylistLink.idOf("https://open.spotify.com/playlist/$id"))
        assertEquals(id, SpotifyPlaylistLink.idOf("http://open.spotify.com/playlist/$id/"))
        assertEquals(id, SpotifyPlaylistLink.idOf("open.spotify.com/playlist/$id"))
        assertEquals(id, SpotifyPlaylistLink.idOf("  https://open.spotify.com/playlist/$id\n"))
    }

    @Test
    fun `what Share adds to a link is ignored`() {
        assertEquals(id, SpotifyPlaylistLink.idOf("https://open.spotify.com/playlist/$id?si=abc123&pt=9f#top"))
        assertEquals(id, SpotifyPlaylistLink.idOf("https://open.spotify.com/intl-de/playlist/$id?si=abc"))
        assertEquals(id, SpotifyPlaylistLink.idOf("https://open.spotify.com/user/someone/playlist/$id"))
        assertEquals(id, SpotifyPlaylistLink.idOf("https://open.spotify.com/embed/playlist/$id"))
    }

    @Test
    fun `a spotify URI and a bare id work too`() {
        assertEquals(id, SpotifyPlaylistLink.idOf("spotify:playlist:$id"))
        assertEquals(id, SpotifyPlaylistLink.idOf("spotify:user:someone:playlist:$id"))
        assertEquals(id, SpotifyPlaylistLink.idOf(id))
    }

    @Test
    fun `anything that is not a playlist is refused`() {
        assertNull(SpotifyPlaylistLink.idOf(""))
        assertNull(SpotifyPlaylistLink.idOf("   "))
        assertNull(SpotifyPlaylistLink.idOf("https://open.spotify.com/album/$id"))
        assertNull(SpotifyPlaylistLink.idOf("https://open.spotify.com/track/$id?si=x"))
        assertNull(SpotifyPlaylistLink.idOf("spotify:track:$id"))
        assertNull(SpotifyPlaylistLink.idOf("https://open.spotify.com/playlist/"))
        assertNull(SpotifyPlaylistLink.idOf("https://open.spotify.com/playlist/too-short"))
        assertNull(SpotifyPlaylistLink.idOf("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5!"))
        assertNull(SpotifyPlaylistLink.idOf("my road trip playlist"))
    }

    @Test
    fun `a link to another site is refused even when it looks the part`() {
        assertNull(SpotifyPlaylistLink.idOf("https://example.com/playlist/$id"))
        assertNull(SpotifyPlaylistLink.idOf("https://notspotify.com/playlist/$id"))
        assertNull(SpotifyPlaylistLink.idOf("https://open.spotify.com.evil.example/playlist/$id"))
    }

    @Test
    fun `the canonical form is the plain web link`() {
        assertEquals(
            "https://open.spotify.com/playlist/$id",
            SpotifyPlaylistLink.canonical("spotify:playlist:$id"),
        )
        assertNull(SpotifyPlaylistLink.canonical("https://open.spotify.com/album/$id"))
    }
}
