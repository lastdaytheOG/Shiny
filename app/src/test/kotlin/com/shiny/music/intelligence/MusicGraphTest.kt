package com.shiny.music.intelligence

import com.shiny.music.db.entities.ArtistEdgeRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicGraphTest {
    private fun edge(from: String, to: String, kind: RelationKind = RelationKind.SimilarArtist, weight: Float = 4f, name: String? = to) =
        ArtistEdgeRow(from, to, weight, kind.name, name)

    @Test
    fun `relationships are not equally trusted`() {
        val graph = MusicGraph.build(
            listOf(
                edge("a", "listened", RelationKind.CoListen),
                edge("a", "provider", RelationKind.RelatedSong),
                edge("a", "playlist", RelationKind.Playlist),
            ),
        )
        assertTrue(graph.strength("a", "listened") > graph.strength("a", "provider"))
        assertTrue(graph.strength("a", "provider") > graph.strength("a", "playlist"))
    }

    @Test
    fun `more evidence strengthens an edge up to a limit`() {
        val weak = MusicGraph.build(listOf(edge("a", "b", weight = 1f))).strength("a", "b")
        val strong = MusicGraph.build(listOf(edge("a", "b", weight = 8f))).strength("a", "b")
        val huge = MusicGraph.build(listOf(edge("a", "b", weight = 8_000f))).strength("a", "b")
        assertTrue(strong > weak)
        assertEquals(strong, huge, 1e-9)
    }

    @Test
    fun `two independent sources strengthen one relationship`() {
        val one = MusicGraph.build(listOf(edge("a", "b", RelationKind.SimilarArtist))).strength("a", "b")
        val both = MusicGraph.build(
            listOf(edge("a", "b", RelationKind.SimilarArtist), edge("a", "b", RelationKind.CoListen)),
        ).strength("a", "b")
        assertTrue(both > one)
        assertTrue(both <= 1.0)
    }

    @Test
    fun `edges work both ways but the stated direction is stronger`() {
        val graph = MusicGraph.build(listOf(edge("a", "b")))
        assertTrue(graph.neighbours("b").any { it.to == "a" })
        assertTrue(graph.neighbours("a").first().strength > graph.neighbours("b").first().strength)
    }

    @Test
    fun `multi-hop reach finds an artist similar-artist lookups would miss`() {
        val graph = MusicGraph.build(listOf(edge("mine", "b"), edge("b", "hidden")))
        val oneHop = graph.reach(mapOf("mine" to 1.0), maxHops = 1)
        val twoHops = graph.reach(mapOf("mine" to 1.0), maxHops = 2)

        assertNull(oneHop["hidden"])
        val reach = twoHops.getValue("hidden")
        assertEquals(2, reach.hops)
        assertEquals("b", reach.via)
        assertTrue(reach.score < twoHops.getValue("b").score)
    }

    @Test
    fun `an artist reached from two of the listener's artists is a bridge`() {
        val graph = MusicGraph.build(listOf(edge("rock", "bridge"), edge("electronic", "bridge"), edge("rock", "other")))
        val reach = graph.reach(mapOf("rock" to 1.0, "electronic" to 0.8), maxHops = 1)

        assertTrue(reach.getValue("bridge").isBridge)
        assertEquals(listOf("rock", "electronic"), reach.getValue("bridge").seeds)
        assertFalse(reach.getValue("other").isBridge)
        assertTrue(reach.getValue("bridge").score > reach.getValue("other").score)
    }

    @Test
    fun `seeds are never reported as their own discoveries`() {
        val graph = MusicGraph.build(listOf(edge("a", "b"), edge("b", "a")))
        assertFalse("a" in graph.reach(mapOf("a" to 1.0, "b" to 1.0), maxHops = 3))
    }

    @Test
    fun `self edges and unknown kinds are dropped`() {
        val graph = MusicGraph.build(listOf(edge("a", "a"), ArtistEdgeRow("a", "b", 3f, "Nonsense", "b")))
        assertEquals(0, graph.nodeCount)
    }

    @Test
    fun `a cycle-heavy graph stays bounded`() {
        val rows = (0 until 400).flatMap { i -> (1..12).map { j -> edge("n$i", "n${(i + j) % 400}") } }
        val graph = MusicGraph.build(rows)
        val started = System.nanoTime()
        val reach = graph.reach(mapOf("n0" to 1.0, "n100" to 1.0), maxHops = 3)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue(reach.isNotEmpty())
        assertTrue("took $ms ms", ms < 500)
    }

    @Test
    fun `an empty graph reaches nothing`() {
        assertTrue(MusicGraph.Empty.reach(mapOf("a" to 1.0), maxHops = 2).isEmpty())
        assertTrue(MusicGraph.build(listOf(edge("a", "b"))).reach(emptyMap(), maxHops = 2).isEmpty())
    }
}
