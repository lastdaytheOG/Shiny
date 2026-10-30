package com.shiny.music.intelligence

import com.shiny.music.db.entities.ArtistEdgeRow
import kotlin.math.ln
import kotlin.math.min

enum class RelationKind(val reliability: Double) {
    CoListen(0.95),
    Transition(0.9),
    SimilarArtist(0.7),
    RelatedSong(0.6),
    Playlist(0.5),
    Sound(0.3),
}

data class GraphEdge(val to: String, val kind: RelationKind, val strength: Double)

data class GraphReach(
    val artistId: String,
    val score: Double,
    val hops: Int,
    val seeds: List<String>,
    val via: String?,
) {
    val isBridge: Boolean get() = seeds.size >= 2
}

class MusicGraph(
    private val edges: Map<String, List<GraphEdge>>,
    val names: Map<String, String>,
) {
    val nodeCount: Int get() = edges.size

    fun neighbours(artistId: String): List<GraphEdge> = edges[artistId].orEmpty()

    fun strength(from: String, to: String): Double =
        neighbours(from).firstOrNull { it.to == to }?.strength
            ?: neighbours(to).firstOrNull { it.to == from }?.strength
            ?: 0.0

    fun reach(
        seeds: Map<String, Double>,
        maxHops: Int,
        hopDecay: Double = 0.5,
        frontierLimit: Int = FRONTIER_LIMIT,
        edgeLimit: Int = EDGE_LIMIT,
    ): Map<String, GraphReach> {
        if (seeds.isEmpty() || edges.isEmpty()) return emptyMap()
        class Node(var score: Double, val hops: Int, var via: String?, val seeds: HashMap<String, Double>)

        val nodes = HashMap<String, Node>()
        var frontier: Map<String, Map<String, Double>> = seeds.filterValues { it > 0.0 }
            .mapValues { (seed, weight) -> mapOf(seed to weight) }
        for (hop in 1..maxHops) {
            val next = HashMap<String, HashMap<String, Double>>()
            val via = HashMap<String, Pair<String, Double>>()
            val ordered = frontier.entries.sortedByDescending { it.value.values.sum() }.take(frontierLimit)
            for ((node, carried) in ordered) {
                for (edge in neighbours(node).take(edgeLimit)) {
                    if (edge.to in seeds) continue
                    val sink = next.getOrPut(edge.to) { HashMap() }
                    var total = 0.0
                    carried.forEach { (seed, value) ->
                        val passed = value * edge.strength * hopDecay
                        sink[seed] = (sink[seed] ?: 0.0) + passed
                        total += passed
                    }
                    if (total > (via[edge.to]?.second ?: 0.0)) via[edge.to] = node to total
                }
            }
            for ((id, bySeed) in next) {
                val gained = bySeed.values.sum()
                val existing = nodes[id]
                if (existing == null) {
                    nodes[id] = Node(gained, hop, via[id]?.first, HashMap(bySeed))
                } else {
                    existing.score += gained
                    bySeed.forEach { (seed, value) -> existing.seeds[seed] = (existing.seeds[seed] ?: 0.0) + value }
                }
            }
            frontier = next
            if (frontier.isEmpty()) break
        }
        return nodes.mapValues { (id, node) ->
            GraphReach(
                artistId = id,
                score = node.score,
                hops = node.hops,
                seeds = node.seeds.entries.sortedByDescending { it.value }.map { it.key },
                via = node.via,
            )
        }
    }

    companion object {
        const val FRONTIER_LIMIT = 64
        const val EDGE_LIMIT = 16
        val Empty = MusicGraph(emptyMap(), emptyMap())

        fun build(rows: Collection<ArtistEdgeRow>, knownNames: Map<String, String> = emptyMap()): MusicGraph {
            val combined = HashMap<Pair<String, String>, HashMap<RelationKind, Double>>()
            val names = HashMap(knownNames)
            for (row in rows) {
                val kind = runCatching { RelationKind.valueOf(row.kind) }.getOrNull() ?: continue
                if (row.fromId == row.toId) continue
                row.toName?.takeIf { it.isNotBlank() }?.let { names.putIfAbsent(row.toId, it) }
                combined.getOrPut(row.fromId to row.toId) { HashMap() }.merge(kind, row.weight.toDouble(), Double::plus)
            }
            val edges = HashMap<String, MutableList<GraphEdge>>()
            for ((pair, kinds) in combined) {
                val (from, to) = pair
                var missed = 1.0
                var strongest = kinds.keys.first()
                var best = -1.0
                for ((kind, weight) in kinds) {
                    val evidence = kind.reliability * min(1.0, ln(1.0 + weight) / ln(1.0 + EVIDENCE_FULL))
                    missed *= 1.0 - evidence
                    if (evidence > best) {
                        best = evidence
                        strongest = kind
                    }
                }
                val strength = 1.0 - missed
                if (strength <= 0.0) continue
                edges.getOrPut(from) { ArrayList() }.add(GraphEdge(to, strongest, strength))
                edges.getOrPut(to) { ArrayList() }.add(GraphEdge(from, strongest, strength * REVERSE_FACTOR))
            }
            val folded = edges.mapValues { (_, list) ->
                list.groupBy { it.to }
                    .map { (_, same) -> same.maxBy { it.strength } }
                    .sortedByDescending { it.strength }
                    .take(MAX_DEGREE)
            }
            return MusicGraph(folded, names)
        }

        private const val EVIDENCE_FULL = 8.0
        private const val REVERSE_FACTOR = 0.8
        private const val MAX_DEGREE = 40
    }
}
