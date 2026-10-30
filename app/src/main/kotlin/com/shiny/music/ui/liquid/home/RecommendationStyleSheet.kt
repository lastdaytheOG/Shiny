package com.shiny.music.ui.liquid.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shiny.music.R
import com.shiny.music.intelligence.IntelligenceRepository
import com.shiny.music.intelligence.RecommendationStyle
import com.shiny.music.ui.component.Material3MenuGroup
import com.shiny.music.ui.component.Material3MenuItemData
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import kotlinx.coroutines.launch

@Composable
fun RecommendationStyleSheet(intelligence: IntelligenceRepository, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val current by intelligence.style.collectAsState(RecommendationStyle.Balanced)
    Column {
        Text(
            stringResource(R.string.style_choose),
            style = LiquidTypography.headline,
            color = Liquid.colors.label,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        )
        Material3MenuGroup(
            items = RecommendationStyle.entries.map { style ->
                Material3MenuItemData(
                    title = { Text(styleName(style)) },
                    description = { Text(styleDescription(style)) },
                    trailingContent = if (style == current) {
                        { Icon(Icons.Rounded.Check, null, tint = Liquid.colors.accent) }
                    } else {
                        null
                    },
                    onClick = {
                        scope.launch {
                            intelligence.setStyle(style)
                            onDone()
                        }
                    },
                )
            },
        )
    }
}
