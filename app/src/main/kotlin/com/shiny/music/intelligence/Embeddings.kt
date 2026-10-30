package com.shiny.music.intelligence

import com.shiny.music.db.entities.ArtistEdgeRow
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

class ArtistEmbeddings(private val vectors: Map<String, FloatArray>, val dimensions: Int) {
    val size: Int get() = vectors.size

    fun of(artistId: String): FloatArray? = vectors[artistId]

    fun similarity(artistId: String, target: FloatArray): Double {
        val vector = vectors[artistId] ?: return 0.0
        if (vector.size != target.size) return 0.0
        var dot = 0.0
        for (i in vector.indices) dot += vector[i] * target[i]
        return dot
    }

    fun centroid(weights: Map<String, Double>): FloatArray? {
        if (vectors.isEmpty()) return null
        val sum = FloatArray(dimensions)
        var used = false
        weights.forEach { (id, weight) ->
            val vector = vectors[id] ?: return@forEach
            used = true
            for (i in sum.indices) sum[i] += vector[i] * weight.toFloat()
        }
        if (!used) return null
        return normalise(sum)
    }

    companion object {
        const val DIMENSIONS = 48
        private const val NON_ZERO = 6
        val Empty = ArtistEmbeddings(emptyMap(), DIMENSIONS)

        fun build(rows: Collection<ArtistEdgeRow>, dimensions: Int = DIMENSIONS, seed: Long = 7L): ArtistEmbeddings {
            if (rows.isEmpty()) return Empty
            val contexts = HashMap<String, MutableMap<String, Double>>()
            for (row in rows) {
                if (row.fromId == row.toId) continue
                val weight = ln(1.0 + row.weight.toDouble())
                contexts.getOrPut(row.fromId) { HashMap() }.merge(row.toId, weight, Double::plus)
                contexts.getOrPut(row.toId) { HashMap() }.merge(row.fromId, weight, Double::plus)
            }
            fun index(id: String): List<Pair<Int, Float>> {
                val random = Random(id.hashCode().toLong() xor seed)
                return List(NON_ZERO) { random.nextInt(dimensions) to if (random.nextBoolean()) 1f else -1f }
            }
            val indexes = contexts.keys.associateWith(::index)
            val first = contexts.mapValues { (id, neighbours) ->
                val vector = FloatArray(dimensions)
                indexes.getValue(id).forEach { (i, sign) -> vector[i] += sign }
                neighbours.forEach { (other, weight) ->
                    indexes[other]?.forEach { (i, sign) -> vector[i] += sign * weight.toFloat() }
                }
                vector
            }
            return ArtistEmbeddings(first.mapValues { (_, v) -> normalise(v) }, dimensions)
        }

        private fun normalise(vector: FloatArray): FloatArray {
            var norm = 0.0
            for (v in vector) norm += v * v
            if (norm <= 0.0) return vector
            val scale = (1.0 / sqrt(norm)).toFloat()
            for (i in vector.indices) vector[i] *= scale
            return vector
        }
    }
}
