package com.shiny.music.home

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// youtube never says what genre a song is but itunes knows an artist's, so each top artist is
// looked up there once a session and its genre is matched onto youtube's own genre tiles
object HomeGenres {
    // artist -> itunes genre, an empty string remembers a miss
    private val known = ConcurrentHashMap<String, String>()

    fun itunesGenre(artistName: String): String? {
        val name = artistName.trim()
        val key = name.lowercase(Locale.ROOT)
        if (key.isEmpty()) return null
        known[key]?.let { return it.ifEmpty { null } }
        val genre = runCatching {
            val url = URL("https://itunes.apple.com/search?term=${URLEncoder.encode(name, "UTF-8")}&media=music&entity=musicArtist&limit=5")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (conn.responseCode != 200) return@runCatching null
            val results = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results") ?: return@runCatching null
            val artists = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            val hit = artists.firstOrNull { it.optString("artistName").trim().equals(name, ignoreCase = true) } ?: artists.firstOrNull()
            hit?.optString("primaryGenreName")?.takeIf { it.isNotBlank() }
        }.getOrNull()
        known[key] = genre.orEmpty()
        return genre
    }

    // youtube's tiles come in the user's language, so they're matched on stems most languages
    // share, an exact title first so "Pop" never lands on "K-Pop"
    fun <T> match(itunesGenre: String, tiles: List<T>, title: (T) -> String): T? {
        val family = familyOf(itunesGenre) ?: return null
        return tiles.firstOrNull { family.exact(title(it)) } ?: tiles.firstOrNull { family.matches(title(it)) }
    }

    fun genreScore(titles: List<String>): Int = titles.count(::isGenre)

    fun isGenre(title: String): Boolean = Family.entries.any { it.matches(title) }

    private fun familyOf(itunesGenre: String): Family? {
        val g = itunesGenre.lowercase(Locale.ROOT)
        return Family.entries.firstOrNull { f -> f.itunes.any { g.contains(it) } }
    }

    // most specific first: k-pop before pop, latin before pop, indie before rock and pop, metal before rock
    private enum class Family(val itunes: List<String>, val youtube: List<String>) {
        KPop(listOf("k-pop"), listOf("k-pop", "kpop")),
        JPop(listOf("j-pop", "anime"), listOf("j-pop", "jpop")),
        Mandopop(listOf("mandopop", "cantopop", "c-pop", "chinese"), listOf("mandopop", "cantopop")),
        HipHop(listOf("hip-hop", "hip hop", "rap"), listOf("hip-hop", "hip hop", "hiphop", "rap")),
        RnB(listOf("r&b", "soul", "funk"), listOf("r&b", "soul")),
        Electronic(listOf("electronic", "dance", "house", "techno", "trance", "edm", "dubstep"), listOf("dance", "electr", "elektr", "dansa", "électr", "eletr")),
        Metal(listOf("metal"), listOf("metal")),
        Latin(listOf("latin", "reggaeton", "salsa", "bachata"), listOf("latin")),
        Alternative(listOf("alternative", "indie"), listOf("indie", "altern")),
        Rock(listOf("rock", "punk", "grunge"), listOf("rock")),
        Jazz(listOf("jazz"), listOf("jazz")),
        Classical(listOf("classical", "opera"), listOf("classical", "klasik", "clásic", "classiq", "klassik", "clássic", "classic")),
        Country(listOf("country", "americana"), listOf("country")),
        Reggae(listOf("reggae", "dancehall", "caribbean"), listOf("reggae")),
        Blues(listOf("blues"), listOf("blues")),
        Folk(listOf("folk", "singer/songwriter", "acoustic"), listOf("folk", "akustik", "acoustic", "acústic")),
        Christian(listOf("christian", "gospel"), listOf("christian", "gospel", "kristen", "rohani", "cristian", "chrétien")),
        Soundtrack(listOf("soundtrack"), listOf("soundtrack", "musical")),
        African(listOf("afro", "african", "amapiano"), listOf("afric", "afrika", "afro")),
        Arabic(listOf("arabic"), listOf("arab")),
        Indian(listOf("bollywood", "indian", "hindi", "punjabi", "tamil"), listOf("india")),
        Brazilian(listOf("brazil", "mpb", "sertanejo", "samba", "bossa"), listOf("brazil", "brasil")),
        Dangdut(listOf("dangdut"), listOf("dangdut")),
        Pop(listOf("pop"), listOf("pop"));

        fun matches(title: String): Boolean {
            val t = title.lowercase(Locale.ROOT)
            return youtube.any { t.contains(it) }
        }

        fun exact(title: String): Boolean {
            val t = title.trim().lowercase(Locale.ROOT)
            return youtube.any { t == it }
        }
    }
}
