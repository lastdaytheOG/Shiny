

package com.shiny.music.ui.component.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.shiny.music.constants.GridItemSize
import com.shiny.music.constants.GridItemsSizeKey
import com.shiny.music.constants.GridThumbnailHeight
import com.shiny.music.constants.SmallGridThumbnailHeight
import com.shiny.music.ui.component.ShelfTileHalfGutter
import com.shiny.music.ui.component.ShelfTileVerticalPadding
import com.shiny.music.ui.component.TileArtworkToText
import com.shiny.music.ui.theme.ArtworkShape
import com.shiny.music.utils.rememberEnumPreference

/**
 * The loading stand-in for a [com.shiny.music.ui.component.GridItem]. Its padding and
 * artwork shape mirror the real tile's, so content arriving replaces the skeleton in
 * place rather than shifting it.
 */
@Composable
fun GridItemPlaceHolder(
    modifier: Modifier = Modifier,
    thumbnailShape: Shape = ArtworkShape,
    fillMaxWidth: Boolean = false,
) {
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val gridHeight = if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight

    Column(
        modifier =
        if (fillMaxWidth) {
            modifier
                .padding(12.dp)
                .fillMaxWidth()
        } else {
            modifier
                .padding(horizontal = ShelfTileHalfGutter, vertical = ShelfTileVerticalPadding)
                .width(gridHeight)
        },
    ) {
        Spacer(
            modifier =
            if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.height(gridHeight)
            }.aspectRatio(1f)
                .clip(thumbnailShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        Spacer(modifier = Modifier.height(TileArtworkToText))

        TextPlaceholder()

        TextPlaceholder()
    }
}
