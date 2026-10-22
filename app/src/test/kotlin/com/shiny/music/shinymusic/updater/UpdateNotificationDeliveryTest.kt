package com.shiny.music.shinymusic.updater

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNotificationDeliveryTest {
    private class Recorder {
        val posted = mutableListOf<String>()
        val remembered = mutableListOf<String>()
        var checks = 0
    }

    private fun deliver(
        recorder: Recorder,
        latest: String?,
        lastNotified: String? = null,
        autoCheck: Boolean = true,
        notifications: Boolean = true,
        postSucceeds: Boolean = true,
        failCheck: Boolean = false,
    ): UpdateDelivery = runBlocking {
        deliverUpdateNotification(
            autoCheck = autoCheck,
            notificationsEnabled = notifications,
            currentVersion = "1.2.7",
            lastNotified = lastNotified,
            check = {
                recorder.checks++
                if (failCheck) throw IOException("offline")
                latest
            },
            post = { recorder.posted += it; postSucceeds },
            remember = { recorder.remembered += it },
        )
    }

    @Test
    fun `a detected newer release posts a notification and is remembered`() {
        val recorder = Recorder()
        assertEquals(UpdateDelivery.Posted, deliver(recorder, latest = "v1.2.8"))
        assertEquals(listOf("v1.2.8"), recorder.posted)
        assertEquals(listOf("v1.2.8"), recorder.remembered)
    }

    @Test
    fun `the same release is not notified twice`() {
        val recorder = Recorder()
        assertEquals(UpdateDelivery.AlreadyNotified, deliver(recorder, latest = "v1.2.8", lastNotified = "v1.2.8"))
        assertTrue(recorder.posted.isEmpty())
    }

    @Test
    fun `a newer release than the last notified one notifies again`() {
        val recorder = Recorder()
        assertEquals(UpdateDelivery.Posted, deliver(recorder, latest = "v1.2.9", lastNotified = "v1.2.8"))
        assertEquals(listOf("v1.2.9"), recorder.posted)
    }

    @Test
    fun `no update or an older release posts nothing`() {
        val recorder = Recorder()
        assertEquals(UpdateDelivery.UpToDate, deliver(recorder, latest = null))
        assertEquals(UpdateDelivery.UpToDate, deliver(recorder, latest = "v1.2.7"))
        assertTrue(recorder.posted.isEmpty())
    }

    @Test
    fun `a blocked notification is not remembered so it can post once allowed`() {
        val recorder = Recorder()
        assertEquals(UpdateDelivery.NotPosted, deliver(recorder, latest = "v1.2.8", postSucceeds = false))
        assertTrue(recorder.remembered.isEmpty())
    }

    @Test
    fun `turned off settings skip the network check`() {
        val recorder = Recorder()
        assertEquals(UpdateDelivery.Disabled, deliver(recorder, latest = "v1.2.8", autoCheck = false))
        assertEquals(UpdateDelivery.Disabled, deliver(recorder, latest = "v1.2.8", notifications = false))
        assertEquals(0, recorder.checks)
    }

    @Test
    fun `a failed check is reported without posting`() {
        val recorder = Recorder()
        assertEquals(UpdateDelivery.CheckFailed, deliver(recorder, latest = "v1.2.8", failCheck = true))
        assertTrue(recorder.posted.isEmpty())
    }

    @Test
    fun `eligibility compares against both the installed and last notified versions`() {
        assertTrue(shouldNotifyAboutUpdate("v1.2.8", "1.2.7", null))
        assertFalse(shouldNotifyAboutUpdate("v1.2.7", "1.2.7", null))
        assertFalse(shouldNotifyAboutUpdate("v1.2.8", "1.2.7", "v1.2.8"))
        assertTrue(shouldNotifyAboutUpdate("v1.2.10", "1.2.7", "v1.2.9"))
    }
}
