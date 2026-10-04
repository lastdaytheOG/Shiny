package com.shiny.music.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor

/**
 * The player's processor chain with one more stage after Sonic (speed and pitch), where
 * the sound effects go: 8D and reverb then act on the song at the speed it is heard, and
 * the limiter is the very last thing before the output. The effects never change how many
 * frames there are, so the player's position and duration maths stay Sonic's.
 */
@UnstableApi
class FxAudioProcessorChain(
    processors: Array<AudioProcessor>,
    silenceSkipping: SilenceSkippingAudioProcessor,
    sonic: SonicAudioProcessor,
    afterSonic: AudioProcessor,
) : DefaultAudioSink.DefaultAudioProcessorChain(processors, silenceSkipping, sonic) {
    private val all: Array<AudioProcessor> = super.getAudioProcessors() + afterSonic

    override fun getAudioProcessors(): Array<AudioProcessor> = all
}
