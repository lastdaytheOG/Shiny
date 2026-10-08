package com.shiny.music.diagnostics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** A service Shiny talks to. [urls] are tried in order; the first one that answers decides. */
data class ServiceEndpoint(
    val name: String,
    val group: ServiceGroup,
    val urls: List<String>,
) {
    constructor(name: String, group: ServiceGroup, url: String) : this(name, group, listOf(url))
}

enum class ServiceGroup(val title: String) {
    MUSIC("Music"),
    LYRICS("Lyrics"),
    ARTWORK("Artwork and canvas"),
    OTHER("Other"),
}

enum class ProbeState { UP, SLOW, DOWN }

/**
 * @param millis how long the answering URL took; null when nothing answered
 * @param url the URL that answered; null when nothing did
 */
data class ProbeResult(
    val endpoint: ServiceEndpoint,
    val state: ProbeState,
    val millis: Long?,
    val url: String?,
)

/**
 * Asks each service whether it is reachable from this device, all at once.
 *
 * @param reach true if the server at the URL answered. It may throw; that counts as no answer.
 * @param timeoutMs how long one URL may take before the next one is tried
 * @param slowAfterMs an answer slower than this is reported as [ProbeState.SLOW]
 */
class ServiceProbe(
    private val reach: suspend (url: String) -> Boolean,
    private val timeoutMs: Long = 5_000,
    private val slowAfterMs: Long = 1_500,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    /** Emits one result per endpoint, in the order the answers arrive. */
    fun check(endpoints: List<ServiceEndpoint>): Flow<ProbeResult> = channelFlow {
        endpoints.forEach { endpoint -> launch { send(probe(endpoint)) } }
    }

    suspend fun probe(endpoint: ServiceEndpoint): ProbeResult {
        for (url in endpoint.urls) {
            val started = nanoTime()
            val answered = try {
                withTimeout(timeoutMs) { reach(url) }
            } catch (_: TimeoutCancellationException) {
                false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (!answered) continue
            val millis = (nanoTime() - started) / 1_000_000
            return ProbeResult(endpoint, if (millis > slowAfterMs) ProbeState.SLOW else ProbeState.UP, millis, url)
        }
        return ProbeResult(endpoint, ProbeState.DOWN, millis = null, url = null)
    }
}

/**
 * The services Shiny depends on, taken from the hosts its own modules call. Keep it in step with
 * them: a provider that is added or dropped belongs here too.
 */
val ShinyServices: List<ServiceEndpoint> = listOf(
    ServiceEndpoint("YouTube Music", ServiceGroup.MUSIC, "https://music.youtube.com/generate_204"),
    ServiceEndpoint("YouTube", ServiceGroup.MUSIC, "https://www.youtube.com/generate_204"),

    ServiceEndpoint("LRCLIB", ServiceGroup.LYRICS, "https://lrclib.net/api/search?q=shiny"),
    ServiceEndpoint("KuGou", ServiceGroup.LYRICS, "https://lyrics.kugou.com/"),
    ServiceEndpoint("Better Lyrics", ServiceGroup.LYRICS, "https://lyrics-api.boidu.dev/"),
    ServiceEndpoint("SimpMusic", ServiceGroup.LYRICS, "https://api-lyrics.simpmusic.org/"),
    ServiceEndpoint(
        "YouLyPlus", ServiceGroup.LYRICS,
        listOf(
            "https://lyricsplus.binimum.org/",
            "https://lyricsplus.atomix.one/",
            "https://lyricsplus-seven.vercel.app/",
            "https://lyricsplus.prjktla.workers.dev/",
        ),
    ),
    ServiceEndpoint("Paxsenix", ServiceGroup.LYRICS, "https://lyrics.paxsenix.org/"),
    ServiceEndpoint("Unison", ServiceGroup.LYRICS, "https://unison.boidu.dev/"),

    ServiceEndpoint("Apple Music catalogue", ServiceGroup.ARTWORK, "https://amp-api.music.apple.com/"),
    ServiceEndpoint("Apple Music web player", ServiceGroup.ARTWORK, "https://music.apple.com/"),
    ServiceEndpoint("Tidal", ServiceGroup.ARTWORK, "https://api.tidal.com/"),
    ServiceEndpoint("Artist videos", ServiceGroup.ARTWORK, "https://artwork-archivetune.koiiverse.cloud/"),

    ServiceEndpoint("Shazam", ServiceGroup.OTHER, "https://amp.shazam.com/"),
    ServiceEndpoint("Spotify", ServiceGroup.OTHER, "https://api.spotify.com/"),
    ServiceEndpoint("ListenBrainz", ServiceGroup.OTHER, "https://api.listenbrainz.org/"),
    ServiceEndpoint("Discord", ServiceGroup.OTHER, "https://discord.com/api/v10/gateway"),
    ServiceEndpoint("GitHub (updates)", ServiceGroup.OTHER, "https://api.github.com/"),
    ServiceEndpoint("shinymusic.in", ServiceGroup.OTHER, "https://shinymusic.in/"),
)
