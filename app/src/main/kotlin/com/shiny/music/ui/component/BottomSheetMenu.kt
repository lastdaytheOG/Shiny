

package com.shiny.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LocalSheetAtmosphere
import com.shiny.music.ui.liquid.PanelMargin
import com.shiny.music.ui.liquid.SheetAtmosphere
import com.shiny.music.ui.liquid.sheetAtmosphere

val LocalMenuState = compositionLocalOf { MenuState() }

@Stable
class MenuState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)

    fun show(content: @Composable ColumnScope.() -> Unit) {
        isVisible = true
        this.content = content
    }

    fun dismiss() {
        isVisible = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedBottomSheet(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    var lastContent by remember { mutableStateOf(content) }

    LaunchedEffect(content) {
        if (isVisible) {
            lastContent = content
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    if (!sheetState.isVisible && !isVisible) {
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
        // While showing, draw the content just handed in. `lastContent` only catches up in
        // an effect after this composition, so drawing it here put the previous sheet's
        // content (another song's details) on screen for the first frame. It is still what
        // a hiding sheet draws, so the content cannot change under the exit animation.
        content = if (isVisible) content else lastContent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState,
    background: Color = BottomSheetDefaults.ContainerColor,
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val liquid = Liquid.colors
    // An iOS sheet: a deep ground the menu's panels are raised off, with the continuous
    // corner of a modal card.
    val sheetColor = if (background == BottomSheetDefaults.ContainerColor) liquid.sheetBackground else background
    val dark = liquid.isDark
    // The colour the sheet takes from the artwork its menu is about (see MenuHeader).
    val atmosphere = remember { SheetAtmosphere() }
    LaunchedEffect(atmosphere) { atmosphere.follow(context) }
    // Fully open, the sheet stops short of the status bar and the page shows above it,
    // instead of running under the clock.
    val topGap = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + SheetTopGap

    AnimatedBottomSheet(
        isVisible = state.isVisible,
        onDismissRequest = {
            focusManager.clearFocus()
            state.isVisible = false
        },
        sheetState = sheetState,
        containerColor = sheetColor,
        contentColor = liquid.label,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        scrimColor = Color.Black.copy(alpha = 0.4f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SheetHandleBand)
                    .sheetAtmosphere(atmosphere, dark),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 5.dp)
                        .background(liquid.tertiaryLabel, CircleShape)
                )
            }
        },
        // The sheet already starts below the status bar, so only the sides and foot are inset.
        contentWindowInsets = {
            BottomSheetDefaults.windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        },
        modifier = modifier
            .padding(top = topGap)
            .fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // Its slice starts under the handle's, and runs on over the sheet's bottom inset.
                .sheetAtmosphere(atmosphere, dark, top = SheetHandleBand, below = SheetFootBleed)
        ) {
            CompositionLocalProvider(LocalSheetAtmosphere provides atmosphere) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PanelMargin)
                ) {
                    state.content(this)
                }
            }
        }
    }
}

/** Between the status bar and the head of a fully open sheet. */
private val SheetTopGap = 8.dp

/** The strip at the head of a sheet that holds its grabber. */
private val SheetHandleBand = 24.dp

/** Taller than any navigation bar: how far the sheet's colour runs past its content. */
private val SheetFootBleed = 96.dp
