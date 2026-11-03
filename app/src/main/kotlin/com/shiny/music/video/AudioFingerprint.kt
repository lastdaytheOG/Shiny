// AudioFingerprint.kt

package com.shiny.music.video

import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

// a recording boiled down to one 32-bit print every 32 ms, which survives a different codec, volume or mix of the same master
internal class AudioPrints(val prints: IntArray, val loud: BooleanArray, val originMs: Long) {
    val size: Int get() = prints.size

    fun timeMs(frame: Int): Long = originMs + frame * FrameMs

    companion object {
        const val SampleRate = 8_000
        const val FrameSize = 2_048
        const val Hop = 256
        const val FrameMs = Hop * 1000L / SampleRate
    }
}

// turns pcm into prints: for each pair of neighbouring pitch bands, whether the lower one gained on the upper since the moment before
internal class AudioPrinter {
    private var resampler: StereoResampler? = null
    private var originMs: Long? = null
    private val window = FloatArray(AudioPrints.FrameSize)
    private var filled = 0
    private var next = 0
    private var sinceFrame = 0
    private val real = FloatArray(AudioPrints.FrameSize)
    private val imaginary = FloatArray(AudioPrints.FrameSize)
    private val bands = FloatArray(BandCount)
    private val previousBands = FloatArray(BandCount)
    private var hasPrevious = false
    private var prints = IntArray(8_192)
    private var energies = FloatArray(8_192)
    private var count = 0

    fun feed(pcm: ShortBuffer, channels: Int, sampleRate: Int, presentationUs: Long) {
        if (originMs == null) originMs = presentationUs / 1000
        val current = resampler?.takeIf { it.inputRate == sampleRate }
            ?: StereoResampler(sampleRate, AudioPrints.SampleRate, CutoffHz).also { resampler = it }
        current.push(pcm, channels) { mid, _ -> addSample(mid) }
    }

    fun finish(): AudioPrints? {
        if (count < MinFrames) return null
        val sorted = energies.copyOf(count).sorted()
        // quiet against the song's own loud parts, so a fade or a silent gap never casts a vote
        val floor = sorted[(count * 0.9).toInt()] * QuietFraction
        val loud = BooleanArray(count) { energies[it] > floor }
        return AudioPrints(prints.copyOf(count), loud, originMs ?: 0L)
    }

    private fun addSample(sample: Float) {
        // a ring of the newest frame's worth of samples; next is where the oldest one sits
        window[next] = sample
        next = (next + 1) % window.size
        if (filled < window.size) filled++
        if (filled < window.size) return
        if (++sinceFrame < AudioPrints.Hop && count > 0) return
        sinceFrame = 0
        analyzeFrame()
    }

    private fun analyzeFrame() {
        for (i in real.indices) {
            real[i] = window[(next + i) % window.size] * Hann[i]
            imaginary[i] = 0f
        }
        fft.transform(real, imaginary)
        var total = 0f
        for (band in 0 until BandCount) {
            var energy = 0f
            for (bin in BandStart[band] until BandStart[band + 1]) energy += real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
            bands[band] = energy
            total += energy
        }
        var print = 0
        if (hasPrevious) {
            for (bit in 0 until BandCount - 1) {
                val now = bands[bit] - bands[bit + 1]
                val before = previousBands[bit] - previousBands[bit + 1]
                if (now - before > 0f) print = print or (1 shl bit)
            }
        }
        System.arraycopy(bands, 0, previousBands, 0, BandCount)
        hasPrevious = true
        if (count == prints.size) {
            prints = prints.copyOf(count * 2)
            energies = energies.copyOf(count * 2)
        }
        prints[count] = print
        energies[count] = total
        count++
    }

    private companion object {
        // 33 bands make 32 bits; 300 Hz to 2 kHz is where music keeps its shape through any codec
        const val BandCount = 33
        const val LowHz = 300.0
        const val HighHz = 2_000.0
        const val CutoffHz = 3_500.0
        const val QuietFraction = 0.001f
        // under 20 s there's too little to place anything
        const val MinFrames = 600

        val fft = Fft(AudioPrints.FrameSize)
        val Hann = FloatArray(AudioPrints.FrameSize) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (AudioPrints.FrameSize - 1))).toFloat() }
        val BandStart = IntArray(BandCount + 1) { band ->
            val hz = LowHz * (HighHz / LowHz).pow(band.toDouble() / BandCount)
            (hz * AudioPrints.FrameSize / AudioPrints.SampleRate).roundToInt()
        }.also { edges -> for (i in 1 until edges.size) if (edges[i] <= edges[i - 1]) edges[i] = edges[i - 1] + 1 }
    }
}

// finds where each stretch of a song is in its music video by their prints, which a chorus heard twice can't fool
internal object AudioMatcher {

    fun match(song: AudioPrints, video: AudioPrints): SongVideoMap? {
        val index = indexOf(video)
        val nodes = ArrayList<Node>()
        val blocks = (song.size + BlockFrames - 1) / BlockFrames
        var loudSongFrames = 0
        for (block in 0 until blocks) {
            val from = block * BlockFrames
            val to = minOf(song.size, from + BlockFrames)
            val loudHere = (from until to).count { song.loud[it] }
            loudSongFrames += loudHere
            if (loudHere < BlockFrames / 4) continue
            candidates(song, from, to, index).forEach { offset ->
                val (best, errors) = refine(song, video, from, to, offset)
                if (errors <= MaxBitErrors) nodes += Node(block, from, to, best, 1f - errors / RandomBitErrors)
            }
        }
        val path = bestPath(nodes)
        val matchedFrames = path.sumOf { node -> (node.from until node.to).count { song.loud[it] } }
        if (path.size < MinBlocks || matchedFrames < loudSongFrames * MinMatchedShare) return null
        return SongVideoMap(runsOf(path, song, video))
    }

    private class Node(val block: Int, val from: Int, val to: Int, val offset: Int, val score: Float)

    // every print the video has, minus the ones it's full of (silence, a held note), which say nothing about where you are
    private fun indexOf(video: AudioPrints): Map<Int, IntArray> {
        val positions = HashMap<Int, MutableList<Int>>()
        for (frame in 0 until video.size) {
            if (video.loud[frame]) positions.getOrPut(video.prints[frame]) { ArrayList(1) } += frame
        }
        return positions.filterValues { it.size <= MaxRepeats }.mapValues { it.value.toIntArray() }
    }

    // where a block could sit in the video: the shifts its prints (and their one-bit slips) vote for most
    private fun candidates(song: AudioPrints, from: Int, to: Int, index: Map<Int, IntArray>): List<Int> {
        val votes = HashMap<Int, Int>()
        for (frame in from until to) {
            if (!song.loud[frame]) continue
            val print = song.prints[frame]
            for (bit in -1 until 32) {
                val probe = if (bit < 0) print else print xor (1 shl bit)
                index[probe]?.forEach { videoFrame ->
                    val offset = videoFrame - frame
                    votes[offset] = (votes[offset] ?: 0) + 1
                }
            }
        }
        val picked = ArrayList<Int>()
        for ((offset, _) in votes.entries.sortedByDescending { (offset, count) -> count + (votes[offset - 1] ?: 0) + (votes[offset + 1] ?: 0) }) {
            if (picked.size >= MaxCandidates) break
            if (picked.none { abs(it - offset) <= 3 }) picked += offset
        }
        return picked
    }

    // the exact shift near a guess, and how many of 32 bits differ there on average
    private fun refine(song: AudioPrints, video: AudioPrints, from: Int, to: Int, guess: Int): Pair<Int, Float> {
        var best = guess
        var bestErrors = Float.MAX_VALUE
        for (offset in guess - 2..guess + 2) {
            val errors = bitErrors(song, video, from, to, offset)
            if (errors < bestErrors) {
                bestErrors = errors
                best = offset
            }
        }
        return best to bestErrors
    }

    private fun bitErrors(song: AudioPrints, video: AudioPrints, from: Int, to: Int, offset: Int): Float {
        var errors = 0
        var compared = 0
        for (frame in from until to) {
            val videoFrame = frame + offset
            if (!song.loud[frame] || videoFrame !in 0 until video.size) continue
            errors += Integer.bitCount(song.prints[frame] xor video.prints[videoFrame])
            compared++
        }
        return if (compared < (to - from) / 4) Float.MAX_VALUE else errors.toFloat() / compared
    }

    // the best run of blocks that only moves forward through the video, a change of shift costing about one block
    private fun bestPath(nodes: List<Node>): List<Node> {
        if (nodes.isEmpty()) return emptyList()
        val sorted = nodes.sortedBy { it.block }
        val total = FloatArray(sorted.size)
        val previous = IntArray(sorted.size) { -1 }
        for (i in sorted.indices) {
            val node = sorted[i]
            total[i] = node.score
            for (j in 0 until i) {
                val before = sorted[j]
                if (before.block >= node.block) continue
                // the video plays on: this block can't start before the last one ended there
                if (node.from + node.offset < before.to + before.offset - SlackFrames) continue
                val penalty = if (abs(node.offset - before.offset) > SameShiftFrames) JumpCost else 0f
                val candidate = total[j] - penalty + node.score
                if (candidate > total[i]) {
                    total[i] = candidate
                    previous[i] = j
                }
            }
        }
        var at = total.indices.maxBy { total[it] }
        val path = ArrayList<Node>()
        while (at != -1) {
            path += sorted[at]
            at = previous[at]
        }
        return path.asReversed()
    }

    // blocks on one shift become one stretch; where two meet, the cut is placed on the exact frame the prints agree with the other side
    private fun runsOf(path: List<Node>, song: AudioPrints, video: AudioPrints): List<SongVideoMap.Run> {
        val groups = ArrayList<MutableList<Node>>()
        for (node in path) {
            val current = groups.lastOrNull()
            if (current != null && abs(node.offset - current.last().offset) <= SameShiftFrames) current += node
            else groups += mutableListOf(node)
        }
        val shifts = groups.map { group -> group.map { it.offset }.sorted()[group.size / 2] }
        val starts = IntArray(groups.size) { groups[it].first().from }
        val ends = IntArray(groups.size) { groups[it].last().to }
        for (g in 0 until groups.size - 1) {
            val split = splitPoint(song, video, groups[g].last().from, groups[g + 1].first().to, shifts[g], shifts[g + 1])
            ends[g] = split
            starts[g + 1] = split
        }
        return groups.indices.map { g ->
            val deltaMs = video.originMs - song.originMs + shifts[g] * AudioPrints.FrameMs
            SongVideoMap.Run(song.timeMs(starts[g]), song.timeMs(ends[g]) - 1, deltaMs)
        }
    }

    private fun splitPoint(song: AudioPrints, video: AudioPrints, from: Int, to: Int, before: Int, after: Int): Int {
        fun errorsAt(frame: Int, offset: Int): Int {
            val videoFrame = frame + offset
            if (!song.loud[frame] || videoFrame !in 0 until video.size) return 16
            return Integer.bitCount(song.prints[frame] xor video.prints[videoFrame])
        }
        // errors if the cut were at each frame: the earlier shift before it, the later one after
        var cost = (from until to).sumOf { errorsAt(it, after) }
        var bestCost = cost
        var best = from
        for (frame in from until to) {
            cost += errorsAt(frame, before) - errorsAt(frame, after)
            if (cost < bestCost) {
                bestCost = cost
                best = frame + 1
            }
        }
        return best
    }

    // about five seconds of song per block
    private const val BlockFrames = 156
    private const val MaxRepeats = 24
    private const val MaxCandidates = 4
    // two unrelated prints differ in 16 bits of 32; the same music, even re-encoded, in well under 11
    private const val RandomBitErrors = 16f
    private const val MaxBitErrors = 10.5f
    private const val SameShiftFrames = 2
    private const val SlackFrames = 6
    private const val JumpCost = 0.6f
    private const val MinBlocks = 3
    private const val MinMatchedShare = 0.35f
}
