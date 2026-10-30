package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Tabs(
    val tabs: List<Tab>,
) {
    @Serializable
    data class Tab(
        val tabRenderer: TabRenderer,
    ) {
        @Serializable
        data class TabRenderer(
            val title: String?,
            val content: Content?,
            val endpoint: NavigationEndpoint?,
        ) {
            @Serializable
            data class Content(
                val sectionListRenderer: SectionListRenderer?,
                val musicQueueRenderer: MusicQueueRenderer?,
            )
        }
    }
}

fun List<Tabs.Tab>.endpointOfPage(pageType: String): BrowseEndpoint? =
    firstNotNullOfOrNull { tab -> tab.tabRenderer.endpoint?.browseEndpoint?.takeIf { it.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType == pageType } }
