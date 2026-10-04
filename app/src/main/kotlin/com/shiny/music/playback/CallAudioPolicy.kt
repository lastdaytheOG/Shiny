package com.shiny.music.playback

import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * When Shiny has to stay quiet for a phone call.
 *
 * Music that starts while the phone rings is muted by the system, and with headphones
 * connected Android also caps the ringtone at the music's level — so both go quiet and the
 * call is missed. Telecom takes audio focus for the ringtone and the call, which is the main
 * guard; the audio mode is read as well because it needs no phone permission and also covers
 * a dialer that sets the call mode without taking focus.
 */
internal object CallAudioPolicy {

    /**
     * True for the modes Telecom sets while a call rings, is screened or is in progress.
     * [AudioManager.MODE_IN_COMMUNICATION] is left out on purpose: voice chat apps (Discord,
     * games) hold it for as long as someone sits in a channel, and people play music then.
     * Their calls take audio focus, which stops Shiny through the focus path instead.
     */
    fun isPhoneCall(audioMode: Int): Boolean = when (audioMode) {
        AudioManager.MODE_RINGTONE,
        AudioManager.MODE_IN_CALL,
        MODE_CALL_SCREENING,
        MODE_CALL_REDIRECT -> true
        else -> false
    }

    /**
     * Whether a newly connected device means "headphones for music". A Bluetooth SCO device is
     * the headset's call channel: it appears exactly when a call starts, so resuming on it
     * restarted the music on top of the ringtone.
     */
    fun isMusicHeadphones(deviceType: Int): Boolean =
        deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP

    // AudioManager.MODE_CALL_SCREENING (API 30) and MODE_CALL_REDIRECT (API 33). The values
    // are fixed platform constants; an older phone simply never reports them.
    private const val MODE_CALL_SCREENING = 4
    private const val MODE_CALL_REDIRECT = 5
}
