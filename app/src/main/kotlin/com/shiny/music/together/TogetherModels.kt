package com.shiny.music.together

import com.shiny.music.models.MediaMetadata
import kotlinx.serialization.Serializable

/*
 * The room as the Together server describes it (server/together/PROTOCOL.md). Field names
 * match the wire so these decode directly.
 */

@Serializable
data class TogetherTrack(
    val id: String,
    val title: String,
    val artist: String = "",
    val durationMs: Long = 0,
    val album: String? = null,
    val thumbnail: String? = null,
    val artistId: String? = null,
    val explicit: Boolean = false,
    /** A file on the host's phone: nobody else can play it. */
    val local: Boolean = false,
)

@Serializable
data class TogetherPerson(val id: String? = null, val name: String)

@Serializable
data class TogetherMember(
    val id: String,
    val name: String,
    val mode: String = MODE_PHONE,
    val connected: Boolean = true,
    val joinedAt: Long = 0,
)

@Serializable
data class TogetherSettings(
    /** "ask": the host lets people in. "open": anyone with the code walks in. */
    val approval: String = "ask",
    val guestsCanAdd: Boolean = true,
    val guestsCanControl: Boolean = false,
    val votesReorder: Boolean = true,
)

@Serializable
data class TogetherPlayback(
    val track: TogetherTrack? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    /** The position at server time [at]; while playing it advances at [rate] from there. */
    val positionMs: Long = 0,
    val at: Long = 0,
    val rate: Float = 1f,
)

@Serializable
data class TogetherQueueItem(
    val uid: String,
    val track: TogetherTrack,
    val addedBy: TogetherPerson? = null,
    /** Added by a guest and not yet in the host's player. */
    val pending: Boolean = false,
)

@Serializable
data class TogetherSkip(
    val trackId: String? = null,
    val voters: List<String> = emptyList(),
    val needed: Int = 2,
)

@Serializable
data class TogetherReply(val id: String = "", val name: String = "", val text: String = "")

@Serializable
data class TogetherChatMessage(
    val id: String,
    val from: TogetherPerson,
    val text: String,
    val at: Long,
    val replyTo: TogetherReply? = null,
)

@Serializable
data class TogetherRequest(val id: String, val name: String)

@Serializable
data class TogetherRoom(
    val code: String,
    val hostId: String? = null,
    val settings: TogetherSettings = TogetherSettings(),
    val members: List<TogetherMember> = emptyList(),
    val playback: TogetherPlayback? = null,
    val queue: List<TogetherQueueItem> = emptyList(),
    val votes: Map<String, List<String>> = emptyMap(),
    val skip: TogetherSkip = TogetherSkip(),
    val chat: List<TogetherChatMessage> = emptyList(),
    val requests: List<TogetherRequest> = emptyList(),
)

@Serializable
data class TogetherPreview(
    val code: String,
    val host: String? = null,
    val listeners: Int = 0,
    val approval: String = "ask",
    val playing: PreviewTrack? = null,
) {
    @Serializable
    data class PreviewTrack(val title: String, val artist: String = "", val thumbnail: String? = null)
}

@Serializable
data class TogetherCreated(val code: String, val hostKey: String, val invite: String? = null)

/** A reaction as it arrives: who sent which emoji. */
data class TogetherReaction(val emoji: String, val from: String, val mine: Boolean, val key: Long)

const val MODE_PHONE = "phone"
const val MODE_REMOTE = "remote"

/** The emoji the server accepts, in the order the reaction bar shows them. */
val TogetherReactions = listOf("🔥", "❤️", "😂", "😍", "👏", "🎉", "🥹", "💃")

fun MediaMetadata.toTogetherTrack(local: Boolean = false) = TogetherTrack(
    id = id,
    title = title,
    artist = artists.joinToString { it.name },
    durationMs = duration.coerceAtLeast(0) * 1000L,
    album = album?.title,
    thumbnail = thumbnailUrl?.takeIf { it.startsWith("https://") },
    artistId = artists.firstOrNull()?.id,
    explicit = explicit,
    local = local,
)

fun TogetherTrack.toMediaMetadata(addedBy: String? = null) = MediaMetadata(
    id = id,
    title = title,
    artists = artist.split(", ").filter { it.isNotBlank() }.mapIndexed { index, name ->
        MediaMetadata.Artist(id = if (index == 0) artistId else null, name = name)
    }.ifEmpty { listOf(MediaMetadata.Artist(id = artistId, name = artist)) },
    duration = (durationMs / 1000L).toInt(),
    thumbnailUrl = thumbnail,
    album = album?.let { MediaMetadata.Album(id = "", title = it) },
    explicit = explicit,
    suggestedBy = addedBy,
)
