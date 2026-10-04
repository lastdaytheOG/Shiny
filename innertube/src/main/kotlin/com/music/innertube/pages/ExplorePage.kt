package com.music.innertube.pages

import com.music.innertube.models.AlbumItem

@kotlinx.serialization.Serializable
data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
)
