package com.shiny.music.ui.liquid.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.shiny.music.LocalDatabase
import com.shiny.music.R
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.stats.StatsPage

@Composable
fun LiquidStatsScreen(navController: NavController) {
    val actions = rememberLiquidActions(navController) ?: return
    val queueTitle = stringResource(R.string.stats_history)
    StatsPage(
        database = LocalDatabase.current,
        onBack = { navController.navigateUp() },
        onPlay = { songs, index -> actions.playSongs(queueTitle, songs, index) },
        onMenu = { song -> actions.menu(song) },
    )
}
