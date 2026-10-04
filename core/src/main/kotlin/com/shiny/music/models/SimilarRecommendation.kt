

package com.shiny.music.models

import com.music.innertube.models.YTItem
import com.shiny.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
