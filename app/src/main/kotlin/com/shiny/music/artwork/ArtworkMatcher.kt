package com.shiny.music.artwork

import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale

/** A song as Apple's catalogue lists it, as far as choosing a cover needs. */
@Serializable
data class CatalogCandidate(
    val trackId: Long = 0,
    /** The album it is on: what the album's motion artwork is asked for by. */
    val collectionId: Long = 0,
    val trackName: String = "",
    val artistName: String = "",
    val collectionName: String = "",
    val collectionArtistName: String = "",
    val trackTimeMillis: Long = 0,
    val artworkUrl100: String = "",
)

/**
 * Deciding whether a catalogue entry is the song being played.
 *
 * A wrong cover is worse than a soft one, so nothing here takes the first result on trust:
 * the artist has to agree and the title has to be the same name, and anything short of that
 * is no match at all.
 */
object ArtworkMatcher {
    private val bracketed = Regex("""\[[^\]]*]|\([^)]*\)|\{[^}]*\}""")
    private val quotes = Regex("""[“”"«»‘’]""")
    private val featuring = Regex("""\b(feat\.?|ft\.?|featuring)\b.*$""", RegexOption.IGNORE_CASE)
    private val videoWords = Regex(
        """\b(official\s+)?(music\s+)?(video|mv|m/v|lyrics?|lyric video|audio|visuali[sz]er|performance video|live)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val official = Regex("""\bofficial\b""", RegexOption.IGNORE_CASE)
    private val separators = Regex("""\s[-–—|:]\s""")
    private val spaces = Regex("""\s+""")
    private val editionSuffix = Regex(
        """\s*[-–(\[]\s*(deluxe|expanded|remaster(ed)?|bonus|special|anniversary|single|ep)\b.*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Letters and digits only, lower case, accents folded: what two spellings of a name share. */
    fun key(text: String): String {
        val folded = Normalizer.normalize(text, Normalizer.Form.NFKD).lowercase(Locale.ROOT)
        return buildString { for (ch in folded) if (ch.isLetterOrDigit()) append(ch) }
    }

    /** An album's name without "(Deluxe)", "- Single", "[Remastered]" and the like. */
    fun albumKey(album: String): String = key(editionSuffix.replace(bracketed.replace(album, " "), ""))

    private val releaseKind = Regex("""\s*[-–]\s*(single|ep)\s*$""", RegexOption.IGNORE_CASE)

    /**
     * An album's whole name, edition and all: "GUTS" and "GUTS (spilled)" are different
     * releases with different covers, and Apple gives motion artwork to one edition and not
     * another. Only the catalogue's own "- Single" and "- EP" are taken off, which a song's
     * own album name never carries.
     */
    fun editionKey(album: String): String = key(releaseKind.replace(album, ""))

    /** A release named plainly: not "(Deluxe)", not "(Remixes)", not a single. */
    private fun isPlainRelease(album: String): Boolean =
        album.isNotBlank() && !bracketed.containsMatchIn(album) && !album.trimEnd().endsWith("Single", ignoreCase = true)

    /**
     * Words that make a recording a different one: the catalogue lists "Blinding Lights
     * (Remix)" and "Kill Bill (Acoustic)" beside the song itself, on singles of their own.
     */
    private val otherVersion = Regex(
        """\b(remix(ed)?|mix|live|acoustic|instrumental|karaoke|sped\s*up|slowed|demo|cover|a\s*cappella|acapella|piano|lullaby|stripped|orchestral|nightcore|reverb)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Whether [theirs] is named as another version of the song than [wanted] is: one says
     * "remix" or "live" and the other does not, whichever way round.
     */
    private fun isOtherVersion(theirs: String, wanted: String): Boolean =
        otherVersion.findAll(theirs).any { !wanted.contains(it.value, ignoreCase = true) } ||
            otherVersion.findAll(wanted).any { !theirs.contains(it.value, ignoreCase = true) }

    /**
     * The song's name out of a video's title: `KATSEYE (캣츠아이) "Animal" Official MV` is
     * "Animal", `SZA - Kill Bill (Official Video)` is "Kill Bill". Brackets, quotes, featured
     * artists, the words that describe the video and the artist's own name are taken away.
     */
    fun songTitle(title: String, artists: List<String>): String {
        var t = bracketed.replace(title, " ")
        t = quotes.replace(t, " ")
        t = featuring.replace(t, " ")
        t = videoWords.replace(t, " ")
        t = official.replace(t, " ")
        val artistKeys = artists.map(::key).filter { it.isNotEmpty() }
        fun isArtist(part: String): Boolean {
            val k = key(part)
            return k.isNotEmpty() && artistKeys.any { a -> k == a || (a.length >= 3 && k in a) }
        }
        val parts = separators.split(t).map { it.trim() }.filter { it.isNotEmpty() }
        val named = parts.filterNot(::isArtist).ifEmpty { parts }
        var name = named.joinToString(" ")
        // "KATSEYE Animal": the artist's name leading the title with no dash after it.
        for (artist in artists.sortedByDescending { it.length }) {
            if (name.length > artist.length + 1 && name.startsWith("$artist ", ignoreCase = true)) {
                name = name.substring(artist.length)
            }
        }
        return spaces.replace(name, " ").trim(' ', '-', '–', '—', '|', ':')
    }

    /**
     * The candidate that is this song, or null when none is. The artist has to agree, and the
     * title has to be the same name or one sitting plainly inside the other. Among those that
     * pass, the same album and the same length count for it, and a compilation against.
     *
     * Which release it is matters as much as which song: the catalogue lists one recording
     * on several (the album, its deluxe edition, a remix single), each with its own cover, and
     * motion artwork belongs to one of them. So the album named exactly beats one that only
     * shares a name with it, the song itself beats a remix or a live take of it, and where
     * nothing else decides, the plainly named release beats an edition of it.
     */
    fun choose(
        candidates: List<CatalogCandidate>,
        title: String,
        artists: List<String>,
        album: String?,
        durationSeconds: Int,
    ): CatalogCandidate? {
        var best: CatalogCandidate? = null
        var bestScore = 0
        for (candidate in candidates) {
            val score = score(candidate, title, artists, album, durationSeconds) ?: continue
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    /** How well [candidate] fits, or null when it is not this song at all. */
    private fun score(candidate: CatalogCandidate, title: String, artists: List<String>, album: String?, durationSeconds: Int): Int? {
        val wanted = key(songTitle(title, artists))
        val artistKeys = artists.map(::key).filter { it.length >= 2 }
        if (wanted.isEmpty() || artistKeys.isEmpty()) return null
        if (candidate.artworkUrl100.isBlank()) return null
        val theirArtist = key(candidate.artistName)
        if (artistKeys.none { it in theirArtist || (theirArtist.length >= 3 && theirArtist in it) }) return null
        val theirs = key(bracketed.replace(candidate.trackName, " "))
        if (theirs.isEmpty()) return null
        var score = when {
            theirs == wanted -> 6
            theirs.length >= 4 && wanted.length >= 4 && (theirs in wanted || wanted in theirs) &&
                minOf(theirs.length, wanted.length) * 2 >= maxOf(theirs.length, wanted.length) -> 3
            else -> return null
        }
        if (isOtherVersion(candidate.trackName, title)) score -= 2
        if (!album.isNullOrBlank()) {
            when {
                editionKey(candidate.collectionName) == editionKey(album) -> score += 4
                albumKey(candidate.collectionName) == albumKey(album) -> score += 2
            }
        }
        if (isPlainRelease(candidate.collectionName)) score += 1
        if (durationSeconds > 0 && candidate.trackTimeMillis > 0 &&
            kotlin.math.abs(candidate.trackTimeMillis / 1000 - durationSeconds) <= 4
        ) {
            score += 1
        }
        if (key(candidate.collectionArtistName) == "variousartists") score -= 3
        return score.takeIf { it > 0 }
    }

    /**
     * The other catalogue entries of the release [chosen] is on: the same song, by the same
     * artist, on an album of exactly the same name under another id. Apple keeps one album
     * under several ids (the explicit and the clean one, a later delivery of it), and its
     * motion artwork is attached to some of them and not the rest. Never another edition.
     */
    fun siblings(
        candidates: List<CatalogCandidate>,
        chosen: CatalogCandidate,
        title: String,
        artists: List<String>,
    ): List<CatalogCandidate> {
        val wanted = key(songTitle(title, artists))
        val release = editionKey(chosen.collectionName)
        if (release.isEmpty()) return emptyList()
        return candidates.filter { candidate ->
            candidate.collectionId > 0 && candidate.collectionId != chosen.collectionId &&
                editionKey(candidate.collectionName) == release &&
                key(bracketed.replace(candidate.trackName, " ")) == wanted &&
                !isOtherVersion(candidate.trackName, title) &&
                score(candidate, title, artists, null, 0) != null
        }.distinctBy { it.collectionId }.take(MaxSiblings)
    }

    /** How many other ids of a release are asked about its motion artwork, at most. */
    const val MaxSiblings = 3

    /**
     * The other releases this same recording is on, best fit first: the single it came out
     * as, the album, its deluxe edition. They matter only for a song that does not say which
     * release it is from (a music video names no album): any of them is then as much its
     * release as [chosen], and the one Apple gave motion artwork to is the one to show.
     * A song that names its album is never moved to another.
     */
    fun alternates(
        candidates: List<CatalogCandidate>,
        chosen: CatalogCandidate,
        title: String,
        artists: List<String>,
    ): List<CatalogCandidate> {
        val wanted = key(songTitle(title, artists))
        val release = editionKey(chosen.collectionName)
        return candidates
            .filter { candidate ->
                candidate.collectionId > 0 && candidate.collectionId != chosen.collectionId &&
                    editionKey(candidate.collectionName) != release &&
                    key(bracketed.replace(candidate.trackName, " ")) == wanted &&
                    !isOtherVersion(candidate.trackName, title) &&
                    key(candidate.collectionArtistName) != "variousartists"
            }
            .mapNotNull { candidate -> score(candidate, title, artists, null, 0)?.let { candidate to it } }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { editionKey(it.collectionName) }
            .take(MaxAlternates)
    }

    /** How many other releases of an album-less song are asked about their motion artwork, at most. */
    const val MaxAlternates = 3

    /**
     * Raised whenever [choose] changes what it picks. What an older one picked is looked up
     * again, once, instead of being believed for ever.
     */
    const val Version = 2

    private val sizeInUrl = Regex("""/\d+x\d+[a-z]{0,2}(-\d+)?\.(jpg|jpeg|png|webp)$""", RegexOption.IGNORE_CASE)

    /**
     * Apple's address for the same picture [px] pixels square. Their artwork addresses end in
     * the size (`…/100x100bb.jpg`) or carry `{w}x{h}` to fill in; anything else is not one of
     * theirs and gives null. Asking is not the same as getting: the picture is only used once
     * it has actually loaded.
     */
    fun sized(artwork: String, px: Int, best: Boolean = false): String? {
        val address = when {
            "{w}" in artwork -> artwork.replace("{w}", px.toString()).replace("{h}", px.toString())
                .replace("{c}", "bb").replace("{f}", "jpg")
            sizeInUrl.containsMatchIn(artwork) -> sizeInUrl.replace(artwork, "/${px}x${px}bb.jpg")
            else -> return null
        }
        return if (best) bestQuality(address) else address
    }

    /**
     * The same address asking for the least compression Apple's image server gives (`-100`):
     * full colour resolution and all but lossless. Measured on a 1080-pixel cover it is 48 dB
     * against the uncompressed picture where the ordinary file is 35 dB, and four times the
     * bytes, so it is asked for only where a picture is drawn pixel for pixel: the Poster.
     */
    fun bestQuality(address: String): String = plainJpeg.replace(address) { "${it.groupValues[1]}-100.jpg" }

    private val plainJpeg = Regex("""(/\d+x\d+[a-z]{0,2})\.jpg$""")

    /**
     * The size most of Apple's masters are. Some are larger (4000 and 3703 pixels have been
     * seen), and asking for more than a master has gives the master; nothing on a phone is
     * drawn from this many pixels, so the larger ones are not asked for.
     */
    const val MasterPx = 3000

    /** The size to ask for on a screen [screenPx] wide: a little over, in steps, never absurd. */
    fun sizeFor(screenPx: Int): Int = ((screenPx * 1.1f / 200f).toInt() + 1).coerceIn(5, 10) * 200
}
