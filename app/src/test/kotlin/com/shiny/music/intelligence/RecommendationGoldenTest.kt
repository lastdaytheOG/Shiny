package com.shiny.music.intelligence

import com.shiny.music.db.entities.ArtistDaypartStat
import com.shiny.music.db.entities.ArtistEdgeRow
import com.shiny.music.db.entities.ArtistOutcomeBucket
import com.shiny.music.db.entities.CandidateSource
import com.shiny.music.db.entities.DiscoveryOutcomeStat
import com.shiny.music.db.entities.HomeAlbumStat
import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.HomeSavedArtist
import com.shiny.music.db.entities.HomeSavedSong
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.db.entities.ImpressionFatigue
import com.shiny.music.db.entities.MusicFeedback
import com.shiny.music.db.entities.SongFeature
import com.shiny.music.db.entities.SongOutcomeBucket
import com.shiny.music.db.entities.SourceOutcomeStat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.random.Random

class RecommendationGoldenTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private inner class World(seed: Long, val artists: Int, val songsPerArtist: Int) {
        val random = Random(seed)
        fun song(artist: Int, index: Int) = "s${artist}_$index"
        fun artistOf(song: String) = song.drop(1).substringBefore('_').toInt()
        val allSongs = (0 until artists).flatMap { a -> (0 until songsPerArtist).map { song(a, it) } }
        val played = allSongs.filter { artistOf(it) % 3 != 2 && random.nextDouble() < 0.5 }

        val songStats = played.map { id ->
            val plays = 1 + random.nextInt(12)
            val ago = random.nextInt(120)
            HomeSongStat(
                id, plays, plays * 200_000L, now - 200 * day, now - ago * day,
                if (ago < 7) plays else 0, 0, if (ago < 30) plays else 0, random.nextInt(plays + 1),
            )
        }
        val artistStats = songStats.groupBy { artistOf(it.songId) }.map { (artist, rows) ->
            HomeArtistStat(
                "a$artist", rows.sumOf { it.plays }, rows.sumOf { it.weekPlays }, rows.sumOf { it.monthPlays },
                rows.maxOf { it.lastPlayed }, rows.size,
            )
        }
        val albumStats = (0 until artists / 4).map { HomeAlbumStat("al$it", 3, 5 + random.nextInt(20), now - random.nextInt(90) * day) }
        val songOutcomes = allSongs.filter { random.nextDouble() < 0.35 }.flatMap { id ->
            (0..random.nextInt(4)).map { bucket ->
                SongOutcomeBucket(
                    id, bucket * 2, random.nextInt(4), random.nextInt(2), random.nextInt(4),
                    random.nextLong(600_000), now - bucket * 28 * day,
                )
            }
        }
        val artistOutcomes = songOutcomes.groupBy { artistOf(it.songId) to it.bucket }.map { (key, rows) ->
            ArtistOutcomeBucket("a${key.first}", key.second, rows.sumOf { it.positive }, rows.sumOf { it.partial }, rows.sumOf { it.negative }, rows.maxOf { it.lastAt })
        }
        val saved = allSongs.filter { random.nextDouble() < 0.08 }.map {
            HomeSavedSong(it, liked = random.nextBoolean(), inLibrary = random.nextBoolean(), inPlaylist = random.nextBoolean(), inMix = false)
        }
        val savedArtists = (0 until artists / 5).map { HomeSavedArtist("a$it", "Artist $it", 1 + random.nextInt(9), random.nextInt(4)) }
        val feedback = listOf(
            MusicFeedback("artist", "a${artists - 1}", FeedbackKind.HideArtist.name, null, now - day, null),
            MusicFeedback("artist", "a${artists - 2}", FeedbackKind.LessLikeThis.name, null, now - day, now + 30 * day),
            MusicFeedback("artist", "a${artists - 3}", FeedbackKind.MoreFromArtist.name, null, now - day, now + 30 * day),
            MusicFeedback("artist", "a${artists - 4}", FeedbackKind.SeedArtist.name, "Artist", now - day, null),
            MusicFeedback("song", song(1, 0), FeedbackKind.BlockSong.name, null, now - day, null),
            MusicFeedback("song", song(2, 1), FeedbackKind.NotInterested.name, null, now - day, now + 30 * day),
            MusicFeedback("song", song(4, 0), FeedbackKind.ExploreMore.name, null, now - day, now + 30 * day),
            MusicFeedback("song", song(5, 0), FeedbackKind.SaveForLater.name, null, now - day, null),
        )
        val features = allSongs.filter { random.nextDouble() < 0.3 }.map {
            SongFeature(it, 80f + random.nextInt(90), random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextInt(12), random.nextBoolean())
        }
        val impressions = allSongs.filter { random.nextDouble() < 0.2 }.map {
            ImpressionFatigue(it, 1 + random.nextInt(8), random.nextInt(2), now - random.nextInt(20) * day)
        }
        val daypart = artistStats.map { ArtistDaypartStat(it.artistId, random.nextInt(it.plays + 1), it.plays) }
        val edges = (0 until artists).flatMap { a ->
            (0 until 5).map { offset ->
                val to = if (offset < 3) (a + offset + 1) % artists else random.nextInt(artists)
                val kind = RelationKind.entries[random.nextInt(4)]
                ArtistEdgeRow("a$a", "a$to", 1f + random.nextInt(9), kind.name, "Artist $to")
            }
        }

        val taste = TasteProfileBuilder.build(
            TasteInputs(
                confidence = 0.7,
                songStats = songStats,
                artistStats = artistStats,
                albumStats = albumStats,
                savedSongs = saved,
                savedArtists = savedArtists,
                songOutcomes = songOutcomes,
                artistOutcomes = artistOutcomes,
                sourceStats = CandidateSource.entries.map { SourceOutcomeStat(it.name, random.nextInt(30), random.nextInt(30)) },
                discovery = DiscoveryOutcomeStat(20, 12, 5, 30, 18, 500, 300),
                feedback = feedback,
                daypart = daypart,
                impressions = impressions,
                features = features,
                recentSearches = listOf("artist 3", "artist 7 live"),
                nowMs = now,
                wallNowMs = now + 19_800_000L,
            ),
        )
        val graph = MusicGraph.build(edges, (0 until artists).associate { "a$it" to "Artist $it" })

        fun snapshot(style: RecommendationStyle): IntelligenceSnapshot {
            val reach = graph.reach(RecommendationEngine.seedWeights(taste), StylePlan.of(style).graphHops)
            return IntelligenceSnapshot.Neutral.copy(
                enabled = true,
                style = style,
                taste = taste,
                graph = graph,
                reach = reach,
                badTransitions = mapOf("a0>a1" to 0.8, "a3>a4" to 0.5),
            )
        }

        fun candidate(id: String, hits: List<SourceHit>): Candidate {
            val artist = artistOf(id)
            return Candidate(
                id, "Song $id",
                listOfNotNull(ArtistRef("a$artist", "Artist $artist"), ArtistRef("a${(artist + 1) % artists}", "Artist ${(artist + 1) % artists}").takeIf { artist % 4 == 0 }),
                albumId = "al${artist % (artists / 4)}",
                durationSec = 120 + artist * 3,
                hits = hits,
            )
        }

        fun pool(): List<Candidate> = allSongs.filterIndexed { index, _ -> index % 2 == 0 }.mapIndexed { index, id ->
            val source = CandidateSource.entries[index % 8]
            val via = if (index % 5 == 0) allSongs[(index * 7) % allSongs.size] else null
            candidate(id, listOf(SourceHit(source, 0.1 + (index % 9) / 10.0, viaSong = via)))
        }
    }

    private fun Double.f() = String.format(Locale.ROOT, "%.9f", this)

    private fun line(rec: Recommendation) = buildString {
        append(rec.song.songId).append(' ').append(rec.score.f()).append(' ').append(rec.source)
        append(' ').append(rec.reason)
        append(' ').append(rec.breakdown.total(StylePlan.of(RecommendationStyle.Balanced).weights).f())
    }

    private fun render(): String = buildString {
        for ((label, world) in listOf(
            "small" to World(11, artists = 24, songsPerArtist = 6),
            "large" to World(23, artists = 120, songsPerArtist = 8),
        )) {
            val taste = world.taste
            appendLine("# $label taste")
            appendLine(taste.discovery.toString())
            appendLine(taste.sound.toString())
            taste.artists.values.sortedBy { it.artistId }.forEach { appendLine(it.toString()) }
            taste.songs.values.sortedBy { it.songId }.take(120).forEach { appendLine(it.toString()) }
            appendLine(RecommendationEngine.seedWeights(taste).toString())
            for (style in RecommendationStyle.entries) {
                val snapshot = world.snapshot(style)
                val pool = world.pool()
                appendLine("# $label $style reach")
                snapshot.reach.values.sortedBy { it.artistId }.forEach { appendLine(it.toString()) }
                appendLine("# $label $style discover")
                val titles = pool.mapNotNull { c -> c.hits.firstOrNull()?.viaSong }.associateWith { "Title $it" }
                RecommendationEngine.discover(pool, snapshot, titles, 60).forEach { appendLine(line(it)) }
                appendLine("# $label $style up next")
                val current = pool[3]
                RecommendationEngine.upNext(current, pool.take(80), snapshot, listOf("a3", "a4"), 25).forEach { appendLine(line(it)) }
                appendLine("# $label $style factors")
                pool.take(40).forEach { c ->
                    appendLine("${c.songId} ${RecommendationEngine.rankingFactor(c.songId, c.artistIds, snapshot).f()} ${RecommendationEngine.shufflePreference(c.songId, c.artistIds, snapshot).f()}")
                }
            }
        }
    }.replace("\r\n", "\n")

    @Test
    fun `recommendations stay exactly as recorded`() {
        val file = File("src/test/resources/golden/recommendations.txt")
        val actual = render()
        if (System.getenv("SHINY_UPDATE_GOLDEN") == "1") {
            file.parentFile.mkdirs()
            file.writeText(actual)
            return
        }
        val expected = file.readText().replace("\r\n", "\n")
        val diverged = expected.lines().zip(actual.lines()).withIndex().firstOrNull { (_, pair) -> pair.first != pair.second }
        assertEquals("first difference at line ${diverged?.index}: ${diverged?.value}", expected.length, actual.length)
        assertEquals(expected, actual)
    }

    @Test
    fun `rendering twice gives the same text`() {
        assertEquals(render(), render())
    }
}
