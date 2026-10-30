package com.shiny.music.intelligence

enum class RecommendationStyle { Balanced, Comfort, Discovery, Adventure, DeepListening }

data class RankingWeights(
    val affinity: Double,
    val songFamiliarity: Double,
    val satisfaction: Double,
    val novelty: Double,
    val newArtist: Double,
    val graph: Double,
    val sourceQuality: Double,
    val sourceStrength: Double,
    val fatigue: Double,
    val rejection: Double,
    val ignored: Double,
    val context: Double,
    val sound: Double,
    val embedding: Double,
)

data class StylePlan(
    val weights: RankingWeights,
    val graphHops: Int,
    val freshShareFloor: Double,
    val freshShareCeiling: Double,
    val freshShareScale: Double,
    val maxPerArtist: Int,
    val artistRunLength: Int,
    val sameArtistBonus: Double,
    val relatedArtistBonus: Double,
    val familiarEvery: Int,
    val diversity: Double,
    val abruptPenalty: Double,
    val unexpectedBonus: Double,
    val newArtistShare: Double,
) {
    companion object {
        private val base = RankingWeights(
            affinity = 1.0,
            songFamiliarity = 0.15,
            satisfaction = 1.1,
            novelty = 0.1,
            newArtist = 0.0,
            graph = 0.8,
            sourceQuality = 0.5,
            sourceStrength = 1.2,
            fatigue = 1.0,
            rejection = 2.0,
            ignored = 0.35,
            context = 0.3,
            sound = 0.25,
            embedding = 0.2,
        )

        fun of(style: RecommendationStyle): StylePlan = when (style) {
            RecommendationStyle.Balanced -> StylePlan(
                weights = base,
                graphHops = 2,
                freshShareFloor = 0.0,
                freshShareCeiling = 0.7,
                freshShareScale = 1.0,
                maxPerArtist = 2,
                artistRunLength = 1,
                sameArtistBonus = 0.05,
                relatedArtistBonus = 0.35,
                familiarEvery = 3,
                diversity = 0.35,
                abruptPenalty = 0.2,
                unexpectedBonus = 0.0,
                newArtistShare = 0.3,
            )

            RecommendationStyle.Comfort -> StylePlan(
                weights = base.copy(affinity = 1.5, songFamiliarity = 0.9, novelty = 0.0, newArtist = -0.5, graph = 0.4, satisfaction = 1.3),
                graphHops = 1,
                freshShareFloor = 0.0,
                freshShareCeiling = 0.15,
                freshShareScale = 0.3,
                maxPerArtist = 3,
                artistRunLength = 2,
                sameArtistBonus = 0.2,
                relatedArtistBonus = 0.35,
                familiarEvery = 1,
                diversity = 0.2,
                abruptPenalty = 0.45,
                unexpectedBonus = 0.0,
                newArtistShare = 0.05,
            )

            RecommendationStyle.Discovery -> StylePlan(
                weights = base.copy(affinity = 0.7, songFamiliarity = -0.6, novelty = 0.4, newArtist = 0.3, graph = 1.1, satisfaction = 1.0),
                graphHops = 2,
                freshShareFloor = 0.45,
                freshShareCeiling = 0.8,
                freshShareScale = 1.6,
                maxPerArtist = 1,
                artistRunLength = 1,
                sameArtistBonus = -0.1,
                relatedArtistBonus = 0.4,
                familiarEvery = 5,
                diversity = 0.55,
                abruptPenalty = 0.2,
                unexpectedBonus = 0.1,
                newArtistShare = 0.6,
            )

            RecommendationStyle.Adventure -> StylePlan(
                weights = base.copy(affinity = 0.3, songFamiliarity = -0.9, novelty = 0.7, newArtist = 0.6, graph = 0.7, satisfaction = 0.8, sourceStrength = 0.6),
                graphHops = 3,
                freshShareFloor = 0.6,
                freshShareCeiling = 0.9,
                freshShareScale = 2.0,
                maxPerArtist = 1,
                artistRunLength = 1,
                sameArtistBonus = -0.3,
                relatedArtistBonus = 0.1,
                familiarEvery = 8,
                diversity = 0.75,
                abruptPenalty = 0.05,
                unexpectedBonus = 0.5,
                newArtistShare = 0.85,
            )

            RecommendationStyle.DeepListening -> StylePlan(
                weights = base.copy(affinity = 1.2, songFamiliarity = 0.1, novelty = 0.05, newArtist = 0.0, graph = 1.0, sourceStrength = 0.8),
                graphHops = 1,
                freshShareFloor = 0.1,
                freshShareCeiling = 0.35,
                freshShareScale = 0.7,
                maxPerArtist = 4,
                artistRunLength = 3,
                sameArtistBonus = 0.5,
                relatedArtistBonus = 0.7,
                familiarEvery = 4,
                diversity = 0.1,
                abruptPenalty = 0.9,
                unexpectedBonus = 0.0,
                newArtistShare = 0.15,
            )
        }
    }
}
