package com.music.innertube.models.body

import com.music.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?,
    /** A page's own filter form, e.g. the charts page's country picker. Omitted when null. */
    val formData: FormData? = null,
)

@Serializable
data class FormData(
    val selectedValues: List<String>,
)
