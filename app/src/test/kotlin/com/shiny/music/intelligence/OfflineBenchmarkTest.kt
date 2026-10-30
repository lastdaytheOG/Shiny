package com.shiny.music.intelligence

import com.shiny.music.db.entities.ArtistEdgeRow
import com.shiny.music.db.entities.CandidateSource
import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.HomeRelatedLink
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.home.HomeRanking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class OfflineBenchmarkTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private class World(
        val artistsPerCluster: Int,
        val clusters: Int,
        val songsPerArtist: Int,
        seed: Long,
    ) {
        val random = Random(seed)
        val artists = (0 until clusters * artistsPerCluster).map { "a$it" }
        fun clusterOf(artist: Int) = artist / artistsPerCluster
        val songs = artists.flatMapIndexed { index, _ -> (0 until songsPerArtist).map { "s${index}_$it" } }
        fun artistOf(song: String) = song.drop(1).substringBefore('_').toInt()

        val hubs = artists.indices.shuffled(random).take(artists.size / 15 + 1)

        private fun noisyArtist(): Int = if (random.nextDouble() < 0.4) hubs.random(random) else random.nextInt(artists.size)

        val relatedEdges = artists.indices.flatMap { index ->
            val own = (0 until artistsPerCluster).map { clusterOf(index) * artistsPerCluster + it }.filter { it != index }
            val near = own.shuffled(random).take(2)
            val noise = List(3) { noisyArtist() }.filter { it != index }
            (near + noise).map { ArtistEdgeRow("a$index", "a$it", 4f, RelationKind.SimilarArtist.name, "a$it") }
        }

        val relatedSongs = songs.flatMap { song ->
            val artist = artistOf(song)
            val near = (0 until 10).map {
                val other = if (random.nextDouble() < 0.45) clusterOf(artist) * artistsPerCluster + random.nextInt(artistsPerCluster) else noisyArtist()
                "s${other}_${random.nextInt(songsPerArtist)}"
            }
            near.filter { it != song }.map { HomeRelatedLink(song, it) }
        }
    }

    private data class Listener(val played: List<String>, val heldOut: Set<String>, val likedClusters: Set<Int>)

    private fun listener(world: World, seed: Long, likes: Int): Listener {
        val random = Random(seed)
        val clusters = (0 until world.clusters).shuffled(random).take(likes).toSet()
        val pool = world.songs.filter { world.clusterOf(world.artistOf(it)) in clusters }
        val knownArtists = pool.map { world.artistOf(it) }.distinct().shuffled(random).let { it.take(it.size / 2) }.toSet()
        val played = pool.filter { world.artistOf(it) in knownArtists && random.nextDouble() < 0.6 }
        val heldOut = (pool - played.toSet()).toSet()
        return Listener(played, heldOut, clusters)
    }

    private fun candidateOf(world: World, song: String, hits: List<SourceHit>) =
        Candidate(song, song, listOf(ArtistRef("a${world.artistOf(song)}", "a${world.artistOf(song)}")), hits = hits)

    private fun stats(played: List<String>, world: World, random: Random): Pair<List<HomeSongStat>, List<HomeArtistStat>> {
        val songStats = played.map { id ->
            val plays = 1 + random.nextInt(8)
            val daysAgo = random.nextInt(60)
            HomeSongStat(id, plays, plays * 200_000L, now - 90 * day, now - daysAgo * day, if (daysAgo < 7) plays else 0, 0, if (daysAgo < 30) plays else 0, 0)
        }
        val artistStats = songStats.groupBy { world.artistOf(it.songId) }.map { (artist, rows) ->
            HomeArtistStat(
                "a$artist",
                plays = rows.sumOf { it.plays },
                weekPlays = rows.sumOf { it.weekPlays },
                monthPlays = rows.sumOf { it.monthPlays },
                lastPlayed = rows.maxOf { it.lastPlayed },
                songs = rows.size,
            )
        }
        return songStats to artistStats
    }

    private data class Results(
        val baseline: OfflineScore,
        val engine: OfflineScore,
        val withEmbedding: OfflineScore,
        val discovery: OfflineScore,
    )

    private fun run(world: World, listeners: Int, k: Int = 20): Results {
        val graph = MusicGraph.build(world.relatedEdges, world.artists.associateWith { it })
        val embeddings = ArtistEmbeddings.build(world.relatedEdges)
        val baselineCases = ArrayList<OfflineEvaluator.Case>()
        val engineCases = ArrayList<OfflineEvaluator.Case>()
        val embeddingCases = ArrayList<OfflineEvaluator.Case>()
        val discoveryCases = ArrayList<OfflineEvaluator.Case>()

        repeat(listeners) { index ->
            val listener = listener(world, 100L + index, likes = 2)
            val random = Random(500L + index)
            val (songStats, artistStats) = stats(listener.played, world, random)
            val playedIds = listener.played.toSet()
            val known = listener.played.map { "a${world.artistOf(it)}" }.toSet()

            val seeds = HomeRanking.seedWeights(songStats)
            val links = world.relatedSongs.filter { it.seedId in seeds && it.songId !in playedIds }
            val baselineIds = HomeRanking.discovery(links, seeds).map { it.songId }
            baselineCases += OfflineEvaluator.Case(baselineIds.map { candidateOf(world, it, emptyList()) }, listener.heldOut, known)

            val taste = TasteProfileBuilder.build(
                TasteInputs(confidence = 0.6, songStats = songStats, artistStats = artistStats, nowMs = now),
            )
            val artistSeeds = RecommendationEngine.seedWeights(taste)
            val reach = graph.reach(artistSeeds, maxHops = 2)
            val topReached = reach.values.sortedByDescending { it.score }.take(30)
            val graphSongs = topReached.flatMap { r ->
                val artist = r.artistId.drop(1).toInt()
                val (source, strength) = RecommendationEngine.graphSources(r)
                (0 until world.songsPerArtist).map { "s${artist}_$it" }
                    .filter { it !in playedIds }
                    .map { candidateOf(world, it, listOf(SourceHit(source, strength))) }
            }
            val relatedCandidates = links.groupBy { it.songId }.map { (song, edges) ->
                val strength = (edges.sumOf { seeds[it.seedId] ?: 0.0 } / 10.0).coerceIn(0.0, 1.0)
                candidateOf(world, song, listOf(SourceHit(CandidateSource.RelatedSong, strength)))
            }
            val pool = relatedCandidates + graphSongs

            val snapshot = IntelligenceSnapshot.Neutral.copy(enabled = true, taste = taste, graph = graph, reach = reach)
            val engineTop = RecommendationEngine.discover(pool, snapshot, emptyMap(), 40).map { it.song }
            engineCases += OfflineEvaluator.Case(engineTop, listener.heldOut, known)

            val vector = embeddings.centroid(artistSeeds)
            val embedded = snapshot.copy(embeddings = embeddings, tasteVector = vector)
            val embeddedTop = RecommendationEngine.discover(pool, embedded, emptyMap(), 40).map { it.song }
            embeddingCases += OfflineEvaluator.Case(embeddedTop, listener.heldOut, known)

            val keen = snapshot.copy(style = RecommendationStyle.Discovery)
            discoveryCases += OfflineEvaluator.Case(RecommendationEngine.discover(pool, keen, emptyMap(), 40).map { it.song }, listener.heldOut, known)
        }
        return Results(
            OfflineEvaluator.score(baselineCases, k),
            OfflineEvaluator.score(engineCases, k),
            OfflineEvaluator.score(embeddingCases, k),
            OfflineEvaluator.score(discoveryCases, k),
        )
    }

    @Test
    fun `on simulated listeners the engine beats the old discovery ranking`() {
        val world = World(artistsPerCluster = 12, clusters = 8, songsPerArtist = 6, seed = 42)
        val results = run(world, listeners = 40)
        println("BENCHMARK baseline ${results.baseline}")
        println("BENCHMARK engine   ${results.engine}")
        println("BENCHMARK embedded ${results.withEmbedding}")
        println("BENCHMARK discovery ${results.discovery}")
        assertTrue("diversity", results.engine.artistDiversity >= results.baseline.artistDiversity)
        assertTrue("overall relevance ${results.engine.ndcg} vs ${results.baseline.ndcg}", results.engine.ndcg >= results.baseline.ndcg)
        assertTrue("new artists that fit", results.discovery.newArtistPrecision >= results.baseline.newArtistPrecision)
    }

    @Test
    fun `the benchmark is deterministic`() {
        val a = run(World(8, 4, 4, 7), listeners = 6)
        val b = run(World(8, 4, 4, 7), listeners = 6)
        assertEquals(a, b)
    }

    @Test
    fun `an evaluation with no cases reports zeros`() {
        assertEquals(0, OfflineEvaluator.score(emptyList(), 10).cases)
    }

    @Test
    fun `ndcg rewards hits nearer the top`() {
        fun c(id: String) = Candidate(id, id, listOf(ArtistRef(id, id)))
        val early = OfflineEvaluator.score(listOf(OfflineEvaluator.Case(listOf(c("hit"), c("x"), c("y")), setOf("hit"), emptySet())), 3)
        val late = OfflineEvaluator.score(listOf(OfflineEvaluator.Case(listOf(c("x"), c("y"), c("hit")), setOf("hit"), emptySet())), 3)
        assertTrue(early.ndcg > late.ndcg)
        assertEquals(early.hitRate, late.hitRate, 0.0)
    }

    @Test
    fun `embeddings of connected artists are closer than strangers`() {
        val rows = listOf(
            ArtistEdgeRow("a", "b", 8f, "SimilarArtist", null),
            ArtistEdgeRow("a", "c", 8f, "SimilarArtist", null),
            ArtistEdgeRow("b", "c", 8f, "SimilarArtist", null),
            ArtistEdgeRow("x", "y", 8f, "SimilarArtist", null),
            ArtistEdgeRow("x", "z", 8f, "SimilarArtist", null),
            ArtistEdgeRow("y", "z", 8f, "SimilarArtist", null),
        )
        val e = ArtistEmbeddings.build(rows)
        val target = e.of("a")!!
        assertTrue(e.similarity("b", target) > e.similarity("x", target))
        assertEquals(1.0, e.similarity("a", target), 1e-3)
    }
}
