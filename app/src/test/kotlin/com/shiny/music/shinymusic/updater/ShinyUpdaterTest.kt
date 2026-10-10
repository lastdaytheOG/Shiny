package com.shiny.music.shinymusic.updater

import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShinyUpdaterTest {
    @Test
    fun `only a release newer than the installed version is available`() {
        assertTrue(isNewerVersion("v1.2.5", "1.2.4"))
        assertFalse(isNewerVersion("v1.2.3", "1.2.4"))
    }

    @Test
    fun `a release tag with a v prefix matches the installed version`() {
        assertFalse(isNewerVersion("v1.2.4", "1.2.4"))
        assertFalse(isNewerVersion("1.2.4", "v1.2.4"))
    }

    @Test
    fun `a stable release supersedes a beta with the same version`() {
        assertTrue(isNewerVersion("v1.2.4", "b1.2.4"))
    }

    @Test
    fun `GitHub requests include the required API headers`() {
        val connection = RecordingConnection()
        configureGitHubConnection(connection)

        assertEquals("application/vnd.github+json", connection.requestHeaders["Accept"])
        assertEquals("2022-11-28", connection.requestHeaders["X-GitHub-Api-Version"])
        assertEquals("Shiny-Updater", connection.requestHeaders["User-Agent"])
    }

    private class RecordingConnection : HttpURLConnection(URL("http://localhost")) {
        val requestHeaders = mutableMapOf<String, String>()

        override fun setRequestProperty(key: String, value: String) {
            requestHeaders[key] = value
        }

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false
    }
}
