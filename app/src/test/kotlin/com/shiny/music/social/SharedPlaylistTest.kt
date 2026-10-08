package com.shiny.music.social

import com.shiny.music.db.entities.ArtistEntity
import com.shiny.music.db.entities.Song
import com.shiny.music.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedPlaylistTest {
    private fun song(
        id: String = "RxabLA7UQ9k",
        isLocal: Boolean = false,
        duration: Int = 275,
        artists: List<ArtistEntity> = listOf(ArtistEntity(id = "UCaO6VoaYJv4kS-TQO_M-N_g", name = "Hans Zimmer")),
    ) = Song(
        song = SongEntity(id = id, title = "Time", duration = duration, thumbnailUrl = "https://lh3.googleusercontent.com/a=w1200-h1200", isLocal = isLocal),
        artists = artists,
    )

    @Test
    fun `a YouTube song goes into the link with its artist`() {
        assertEquals(
            SharedSong(
                id = "RxabLA7UQ9k",
                title = "Time",
                artists = listOf(SharedArtist("Hans Zimmer", "UCaO6VoaYJv4kS-TQO_M-N_g")),
                duration = 275,
                thumbnail = "https://lh3.googleusercontent.com/a=w1200-h1200",
            ),
            song().toSharedSong(),
        )
    }

    @Test
    fun `a file on the phone has nothing to link to`() {
        assertNull(song(isLocal = true).toSharedSong())
        assertNull(song(id = "content://media/external/audio/media/42").toSharedSong())
    }

    @Test
    fun `an artist made on this phone keeps its name and loses its id`() {
        val shared = song(artists = listOf(ArtistEntity(id = "LAabcdefgh", name = "Brother Dege"))).toSharedSong()
        assertEquals(listOf(SharedArtist("Brother Dege", null)), shared?.artists)
    }

    @Test
    fun `an unknown length is left out`() {
        assertNull(song(duration = -1).toSharedSong()?.duration)
    }

    @Test
    fun `a shared song opens as a playable song`() {
        val item = SharedSong(id = "RxabLA7UQ9k", title = "Time", artists = listOf(SharedArtist("Hans Zimmer"))).toSongItem()
        assertEquals("RxabLA7UQ9k", item.id)
        assertEquals("Hans Zimmer", item.artists.single().name)
        assertNull(item.artists.single().id)
        // No artwork came with it: YouTube's own picture of the video stands in.
        assertEquals("https://i.ytimg.com/vi/RxabLA7UQ9k/hqdefault.jpg", item.thumbnail)
    }

    @Test
    fun `the upload leaves out what the server doesn't need`() {
        val body = SocialApi.json.encodeToString(
            SharedPlaylistUpload.serializer(),
            SharedPlaylistUpload("Mix", listOf(SharedSong(id = "RxabLA7UQ9k", title = "Time", artists = listOf(SharedArtist("Hans Zimmer"))))),
        )
        assertEquals("""{"name":"Mix","songs":[{"id":"RxabLA7UQ9k","title":"Time","artists":[{"name":"Hans Zimmer"}]}]}""", body)
    }
}
