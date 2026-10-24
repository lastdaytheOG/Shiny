package com.shiny.music.ui.liquid.appearance

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberReducedMotion(): Boolean {
    val instant = LocalShinyAppearance.current.transitions == PageTransitions.Instant
    val context = LocalContext.current
    val systemOff = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    return instant || systemOff
}
