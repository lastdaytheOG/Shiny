package com.shiny.music.home

import com.shiny.music.db.entities.CandidateSource
import com.shiny.music.db.entities.RecSurface
import com.shiny.music.intelligence.ImpressionItem

object HomeImpressions {
    const val VISIBLE_ON_A_SHELF = 8

    fun of(section: HomeSection): Pair<RecSurface, List<ImpressionItem>>? = when (section) {
        is HeroMixSection -> RecSurface.HomeMix to section.songs.take(VISIBLE_ON_A_SHELF).map { ImpressionItem(it.id) }
        is DiscoverSection -> RecSurface.HomeDiscover to section.picks.take(VISIBLE_ON_A_SHELF).map {
            ImpressionItem(it.song.id, it.source ?: CandidateSource.RelatedSong, it.reason)
        }
        is RotationSection -> RecSurface.HomeRotation to section.songs.take(VISIBLE_ON_A_SHELF).map {
            ImpressionItem(it.song.id, CandidateSource.Rotation)
        }
        is AroundNowSection -> RecSurface.HomeAroundNow to section.songs.take(VISIBLE_ON_A_SHELF).map {
            ImpressionItem(it.id, CandidateSource.Daypart)
        }
        is RediscoverSection -> RecSurface.HomeRediscover to section.songs.take(VISIBLE_ON_A_SHELF).map {
            ImpressionItem(it.song.id, CandidateSource.Rediscovery)
        }
        is ContinueSection -> RecSurface.HomeContinue to section.songs.take(VISIBLE_ON_A_SHELF).map {
            ImpressionItem(it.id, CandidateSource.Recent)
        }
        else -> null
    }
}
