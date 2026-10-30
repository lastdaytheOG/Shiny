package com.shiny.music.intelligence

import com.shiny.music.db.entities.CandidateRow
import com.shiny.music.db.entities.CandidateSource
import com.shiny.music.db.entities.Song
import com.shiny.music.models.MediaMetadata

data class ArtistRef(val id: String, val name: String)

data class SourceHit(
    val source: CandidateSource,
    val strength: Double,
    val viaSong: String? = null,
    val viaArtist: String? = null,
)

data class Candidate(
    val songId: String,
    val title: String,
    val artists: List<ArtistRef>,
    val albumId: String? = null,
    val year: Int? = null,
    val durationSec: Int = -1,
    val explicit: Boolean = false,
    val isVideo: Boolean = false,
    val playsOffline: Boolean = false,
    val hits: List<SourceHit> = emptyList(),
) {
    val artistIds: List<String> get() = artists.map { it.id }
    val leadArtist: String get() = artists.firstOrNull()?.let { it.id.ifBlank { it.name } } ?: (albumId ?: songId)
    val bestHit: SourceHit? get() = hits.maxByOrNull { it.strength }
    fun bestHitVia(): String = hits.sortedByDescending { it.strength }.firstOrNull { it.viaSong != null }?.viaSong.orEmpty()
    val source: CandidateSource? get() = bestHit?.source
}

object CandidateAdapters {
    fun of(song: Song, vararg hits: SourceHit): Candidate = Candidate(
        songId = song.id,
        title = song.song.title,
        artists = song.artists.map { ArtistRef(it.id, it.name) },
        albumId = song.song.albumId,
        year = song.song.year,
        durationSec = song.song.duration,
        explicit = song.song.explicit,
        isVideo = song.song.isVideo,
        playsOffline = song.song.isLocal || song.song.isDownloaded,
        hits = hits.toList(),
    )

    fun of(metadata: MediaMetadata, vararg hits: SourceHit): Candidate = Candidate(
        songId = metadata.id,
        title = metadata.title,
        artists = metadata.artists.mapNotNull { artist -> artist.id?.let { ArtistRef(it, artist.name) } ?: ArtistRef(artist.name, artist.name) },
        albumId = metadata.album?.id,
        durationSec = metadata.duration,
        explicit = metadata.explicit,
        isVideo = metadata.isVideoSong,
        hits = hits.toList(),
    )

    fun fromRows(rows: List<CandidateRow>, hits: Map<String, List<SourceHit>>): List<Candidate> =
        rows.groupBy { it.songId }.map { (id, same) ->
            val head = same.first()
            Candidate(
                songId = id,
                title = head.title,
                artists = same
                    .filter { it.artistId != null }
                    .sortedBy { it.position ?: Int.MAX_VALUE }
                    .map { ArtistRef(it.artistId!!, it.artistName.orEmpty()) }
                    .distinctBy { it.id },
                albumId = head.albumId,
                year = head.year,
                durationSec = head.duration,
                explicit = head.explicit,
                isVideo = head.isVideo,
                playsOffline = head.isLocal || head.isDownloaded,
                hits = hits[id].orEmpty(),
            )
        }

    fun merge(candidates: List<Candidate>): List<Candidate> =
        candidates.groupBy { it.songId }.map { (_, same) ->
            if (same.size == 1) same.first() else same.first().copy(hits = same.flatMap { it.hits })
        }
}
