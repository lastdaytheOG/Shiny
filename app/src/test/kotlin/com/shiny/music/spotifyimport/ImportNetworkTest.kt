package com.shiny.music.spotifyimport

import com.shiny.music.spotify.Spotify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ImportNetworkTest {

    @Test
    fun `a DNS failure is retried`() {
        // The error the import used to die on.
        val error = UnknownHostException(
            "Unable to resolve host \"api-partner.spotify.com\": No address associated with hostname",
        )
        assertTrue(error.isTransientNetworkFailure())
    }

    @Test
    fun `a network failure wrapped by the client is retried`() {
        assertTrue(RuntimeException("request failed", SocketTimeoutException("timeout")).isTransientNetworkFailure())
    }

    @Test
    fun `Spotify's own brief failures are retried`() {
        assertTrue(Spotify.SpotifyException(503, "Service unavailable").isTransientNetworkFailure())
        assertTrue(Spotify.SpotifyException(502, "Bad gateway").isTransientNetworkFailure())
    }

    @Test
    fun `answers about the request itself are not retried`() {
        assertFalse(Spotify.SpotifyException(401, "Token expired or invalid").isTransientNetworkFailure())
        assertFalse(Spotify.SpotifyException(404, "Not found").isTransientNetworkFailure())
        assertFalse(Spotify.SpotifyException(412, "PersistedQueryNotFound").isTransientNetworkFailure())
        assertFalse(IllegalStateException("Spotify is not connected").isTransientNetworkFailure())
    }
}
