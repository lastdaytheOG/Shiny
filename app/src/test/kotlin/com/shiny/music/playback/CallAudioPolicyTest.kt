package com.shiny.music.playback

import android.media.AudioDeviceInfo
import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallAudioPolicyTest {

    @Test
    fun `ringing and calls hold the music`() {
        assertTrue(CallAudioPolicy.isPhoneCall(AudioManager.MODE_RINGTONE))
        assertTrue(CallAudioPolicy.isPhoneCall(AudioManager.MODE_IN_CALL))
        assertTrue(CallAudioPolicy.isPhoneCall(4)) // MODE_CALL_SCREENING
        assertTrue(CallAudioPolicy.isPhoneCall(5)) // MODE_CALL_REDIRECT
    }

    @Test
    fun `normal playback and voice chat do not`() {
        assertFalse(CallAudioPolicy.isPhoneCall(AudioManager.MODE_NORMAL))
        assertFalse(CallAudioPolicy.isPhoneCall(AudioManager.MODE_IN_COMMUNICATION))
        assertFalse(CallAudioPolicy.isPhoneCall(6)) // MODE_COMMUNICATION_REDIRECT
        assertFalse(CallAudioPolicy.isPhoneCall(AudioManager.MODE_INVALID))
    }

    @Test
    fun `a headset joining a call is not headphones for music`() {
        assertTrue(CallAudioPolicy.isMusicHeadphones(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertFalse(CallAudioPolicy.isMusicHeadphones(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
        assertFalse(CallAudioPolicy.isMusicHeadphones(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
    }
}
