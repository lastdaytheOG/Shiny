package com.shiny.music.localmedia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalMusicFilterTest {

    private val none = MediaStoreAudioFlags()

    private fun decide(
        folder: String?,
        name: String = "track.mp3",
        mime: String = "audio/mpeg",
        flags: MediaStoreAudioFlags = none,
        skipped: Set<SkippedSource> = SkippedSource.Default,
        included: Set<String> = emptySet(),
        excluded: Set<String> = emptySet(),
    ) = LocalMusicFilter.decide(folder, name, mime, flags, skipped, included, excluded)

    private val music = LocalMusicDecision.Music

    @Test
    fun `music in ordinary places is music, whatever its tags`() {
        assertEquals(music, decide("Music"))
        assertEquals(music, decide("Music/Artist/Album", name = "01 Song.flac", mime = "audio/flac"))
        assertEquals(music, decide("Music", name = "song.m4a", mime = "audio/mp4"))
        assertEquals(music, decide("Download"))
        assertEquals(music, decide("Download/Music"))
        assertEquals(music, decide("My Stuff/old phone backup"))
        // Unknown artist, no album, long filename: still music. The filter never looks at tags.
        assertEquals(music, decide("Music", name = "a very long file name that goes on and on - unknown artist - track 07 (live).mp3"))
        // A folder can merely mention an app.
        assertEquals(music, decide("Music/WhatsApp songs"))
        assertEquals(music, decide(null))
    }

    @Test
    fun `whatsapp audio and voice notes are messaging, in both storage layouts`() {
        val skipped = LocalMusicDecision.Skipped(SkippedSource.Messaging)
        assertEquals(skipped, decide("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio", name = "AUD-20260812-WA0005.opus", mime = "audio/ogg"))
        assertEquals(skipped, decide("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/202633", name = "PTT-20260812-WA0001.opus", mime = "audio/ogg"))
        assertEquals(skipped, decide("WhatsApp/Media/WhatsApp Audio"))
        assertEquals(skipped, decide("Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Audio"))
        assertEquals(skipped, decide("Telegram/Telegram Audio"))
        assertEquals(skipped, decide("Android/media/org.telegram.messenger/Telegram/Telegram Voice", mime = "audio/ogg"))
    }

    @Test
    fun `recordings are recognised by flag, folder and codec`() {
        val skipped = LocalMusicDecision.Skipped(SkippedSource.Recordings)
        assertEquals(skipped, decide("Recordings"))
        assertEquals(skipped, decide("Recordings/Voice Recorder", name = "Voice 001.m4a", mime = "audio/mp4"))
        assertEquals(skipped, decide("Recordings/Call"))
        assertEquals(skipped, decide("MIUI/sound_recorder/call_rec"))
        assertEquals(skipped, decide("Somewhere", flags = MediaStoreAudioFlags(isRecording = true)))
        assertEquals(skipped, decide("Sounds", name = "memo.amr", mime = "audio/amr"))
        assertEquals(skipped, decide("Music", name = "old.awb", mime = "audio/amr-wb"))
    }

    @Test
    fun `ringtones, notifications, alarms and app files are system sounds`() {
        val skipped = LocalMusicDecision.Skipped(SkippedSource.SystemSounds)
        assertEquals(skipped, decide("Notifications"))
        assertEquals(skipped, decide("Ringtones"))
        assertEquals(skipped, decide("Alarms"))
        assertEquals(skipped, decide("Music", flags = MediaStoreAudioFlags(isNotification = true)))
        assertEquals(skipped, decide("Music", flags = MediaStoreAudioFlags(isRingtone = true)))
        assertEquals(skipped, decide("Music", flags = MediaStoreAudioFlags(isAlarm = true)))
        assertEquals(skipped, decide("Android/data/com.some.game/files/sfx"))
        // Only the standard top-level folder counts; an album called "Alarms" is music.
        assertEquals(music, decide("Music/Alarms"))
    }

    @Test
    fun `a source the user turned back on is music again`() {
        assertEquals(music, decide("WhatsApp/Media/WhatsApp Audio", skipped = setOf(SkippedSource.Recordings, SkippedSource.SystemSounds)))
        assertEquals(music, decide("Recordings", skipped = emptySet()))
    }

    @Test
    fun `always-include folders override the skips but not the exclusions`() {
        val whatsapp = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"
        assertEquals(music, decide(whatsapp, included = setOf("Android/media/com.whatsapp")))
        assertEquals(music, decide("Recordings/Band practice", included = setOf("recordings/band practice")))
        assertEquals(
            LocalMusicDecision.ExcludedFolder,
            decide("Music/Podcasts", included = setOf("Music"), excluded = setOf("Music/Podcasts")),
        )
    }

    @Test
    fun `excluded folders keep their old matching`() {
        val excluded = LocalMusicDecision.ExcludedFolder
        assertEquals(excluded, decide("Download/Telegram", excluded = setOf("Download/Telegram")))
        assertEquals(excluded, decide("Download/Telegram/x", excluded = setOf("Download/Telegram")))
        // A folder of that name anywhere in the path, as before.
        assertEquals(excluded, decide("Music/Telegram/x", excluded = setOf("Telegram")))
        assertEquals(music, decide("Music/Telegrams", excluded = setOf("Telegram")))
    }

    @Test
    fun `paths are compared without case or stray slashes`() {
        assertEquals("music/albums", LocalMusicFilter.normalize("/Music//Albums/"))
        assertEquals(null, LocalMusicFilter.normalize("  "))
        assertEquals(LocalMusicDecision.Skipped(SkippedSource.SystemSounds), decide("/RINGTONES/"))
    }

    @Test
    fun `stored source keys round-trip`() {
        assertEquals(SkippedSource.Default, SkippedSource.fromKeys(DefaultSkippedSourceKeys))
        assertEquals(emptySet<SkippedSource>(), SkippedSource.fromKeys(emptySet()))
        assertEquals(setOf(SkippedSource.Recordings), SkippedSource.fromKeys(setOf("recordings", "unknown")))
    }

    @Test
    fun `the settings signature changes only with the settings`() {
        val a = LocalSongScanConfig(0, setOf("Music/Podcasts"))
        val b = LocalSongScanConfig(0, setOf("music/podcasts/"))
        assertEquals(a.signature, b.signature)
        val c = a.copy(skippedSources = setOf(SkippedSource.Recordings))
        assertNotEquals(a.signature, c.signature)
        val d = a.copy(includedFolders = setOf("Recordings"))
        assertNotEquals(a.signature, d.signature)
    }

    @Test
    fun `formats Shiny cannot play are not supported`() {
        assertEquals(false, SupportedLocalAudio.isSupported("theme.mid", "audio/midi"))
        assertEquals(false, SupportedLocalAudio.isSupported("old itunes.m4p", "audio/mp4"))
        assertEquals(true, SupportedLocalAudio.isSupported("song.flac", "audio/flac"))
        assertEquals(true, SupportedLocalAudio.isSupported("song.m4a", "audio/mp4"))
        assertEquals(true, SupportedLocalAudio.isSupported("song.opus", "audio/ogg"))
    }
}
