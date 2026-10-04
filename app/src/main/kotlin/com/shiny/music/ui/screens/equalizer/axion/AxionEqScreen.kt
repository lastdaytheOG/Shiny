package com.shiny.music.ui.screens.equalizer.axion

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import com.shiny.music.ui.liquid.LiquidTopAppBar as TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.shiny.music.R
import com.shiny.music.eq.data.SavedEQProfile
import com.shiny.music.ui.component.Material3SettingsGroup
import com.shiny.music.ui.component.Material3SettingsItem
import com.shiny.music.ui.utils.backToMain
import kotlin.math.abs
import kotlin.math.roundToInt
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AxionEqScreen(
    onBackClick: () -> Unit,
    viewModel: AxionEqViewModel = hiltViewModel()
) {
    val enabled by viewModel.enabled.collectAsState()
    val bandGains by viewModel.bandGains.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val preamp by viewModel.preamp.collectAsState()
    val inListenTogether = com.shiny.music.ui.liquid.together.LocalTogether.current
        ?.state?.collectAsState()?.value?.active == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shiny_equalizer)) },
                navigationIcon = {
                    com.shiny.music.ui.component.IconButton(
                        onClick = onBackClick,
                        onLongClick = {}
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
        ) {
            // One switch for everything on this screen: bands, effects and speed. Off by
            // default, and off again each time Shiny starts (see SoundFxEngine.enabled).
            com.shiny.music.ui.liquid.settings.SettingsSection(
                footer = if (enabled) {
                    "Turns itself off the next time Shiny opens."
                } else {
                    "Off: songs play as they are. Turn it on, or change anything below, to hear it."
                },
            ) {
                com.shiny.music.ui.liquid.settings.SettingsToggleRow(
                    title = stringResource(R.string.shiny_equalizer),
                    subtitle = if (enabled) "On" else "Off",
                    checked = enabled,
                    onCheckedChange = { viewModel.setEnabled(it) },
                    divider = false,
                )
            }

            // Off, the controls stay usable (a change switches the Equaliser on) but read as resting.
            Column(Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.55f }) {
            if (enabled) {
                // The switch to hear the song without any of it.
                AbSwitch(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp))
            }

            SoundEffectsSections(inListenTogether = inListenTogether)

            com.shiny.music.ui.liquid.settings.SettingsSection(title = "Bands") {}
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            EqResponseCurve(bandGains = bandGains, preampDb = preamp, enabled = enabled)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                ToggleButton(
                    checked = mode == 0,
                    onCheckedChange = { viewModel.setMode(0) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                ) {
                    Text(stringResource(R.string.eq_simple))
                }
                ToggleButton(
                    checked = mode == 1,
                    onCheckedChange = { viewModel.setMode(1) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                ) {
                    Text(stringResource(R.string.eq_advanced))
                }
            }

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    fadeIn().togetherWith(fadeOut()).using(SizeTransform(clip = false))
                },
                label = "eqMode",
            ) { currentMode ->
                val isDirty by viewModel.isDirty.collectAsState()
                var showSaveDialog by remember { mutableStateOf(false) }

                if (showSaveDialog) {
                    SavePresetDialog(
                        onDismiss = { showSaveDialog = false },
                        onSave = { name ->
                            viewModel.saveCustomProfile(name)
                            showSaveDialog = false
                        }
                    )
                }

                when (currentMode) {
                    0 -> SimpleEqMode(
                        bandGains = bandGains,
                        enabled = true,
                        viewModel = viewModel,
                        isDirty = isDirty,
                        onSaveClick = { showSaveDialog = true }
                    )
                    else -> AdvancedEqMode(
                        bandGains = bandGains,
                        preamp = preamp,
                        enabled = true,
                        onBandChange = { band, value ->
                            viewModel.setBandGain(band, value)
                        },
                        onPreampChange = { viewModel.setPreamp(it) },
                        onReset = {
                            viewModel.reset()
                        }
                    )
                }
            }
            }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
private fun SimpleEqMode(
    bandGains: FloatArray,
    enabled: Boolean,
    viewModel: AxionEqViewModel,
    isDirty: Boolean,
    onSaveClick: () -> Unit
) {
    
    var bass by remember { mutableFloatStateOf(0f) }
    var mid by remember { mutableFloatStateOf(0f) }
    var treble by remember { mutableFloatStateOf(0f) }

    fun syncFromBands() {
        
        bass = bandGains[1] / 50f
        mid = (bandGains[4] + bandGains[5]) / 2f / 50f
        treble = bandGains[8] / 50f
    }

    fun applyTriangle() {
        val bv = (bass * 50f).coerceIn(-600f, 600f)
        val mv = (mid * 50f).coerceIn(-600f, 600f)
        val tv = (treble * 50f).coerceIn(-600f, 600f)
        val newGains = FloatArray(10)
        
        
        newGains[0] = bv * 1.1f
        newGains[1] = bv * 1.0f
        newGains[2] = bv * 0.7f + mv * 0.3f
        newGains[3] = bv * 0.2f + mv * 0.8f
        newGains[4] = mv * 1.0f
        newGains[5] = mv * 1.0f
        newGains[6] = mv * 0.8f + tv * 0.2f
        newGains[7] = mv * 0.3f + tv * 0.7f
        newGains[8] = tv * 1.0f
        newGains[9] = tv * 1.15f 
        
        viewModel.setBandsGains(newGains, fromUser = true)
    }

    LaunchedEffect(bandGains) {
        syncFromBands()
    }

    val shinyPresets = listOf(
        R.string.eq_preset_flat to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        R.string.eq_preset_shiny_signature to floatArrayOf(150f, 100f, 50f, 0f, -20f, 0f, 80f, 150f, 200f, 150f),
        R.string.eq_preset_acoustic to floatArrayOf(150f, 150f, 50f, 75f, 100f, 75f, 125f, 175f, 150f, 75f),

        R.string.eq_preset_bass_boost to floatArrayOf(500f, 400f, 250f, 100f, 0f, -50f, 0f, 100f, 200f, 300f),
        R.string.eq_preset_pure_clarity to floatArrayOf(-100f, -50f, 0f, 50f, 150f, 250f, 300f, 250f, 150f, 100f),
        R.string.eq_preset_soft_bass to floatArrayOf(200f, 180f, 140f, 80f, 30f, 20f, 60f, 90f, 110f, 130f),
        R.string.eq_preset_electronic to floatArrayOf(350f, 280f, 120f, -50f, -150f, 50f, 180f, 300f, 400f, 500f),
        R.string.eq_preset_rock to floatArrayOf(300f, 220f, 150f, 50f, -100f, 120f, 200f, 250f, 320f, 380f),
        R.string.eq_preset_pop to floatArrayOf(-150f, 0f, 100f, 180f, 250f, 220f, 150f, 80f, -50f, -120f),
        R.string.eq_preset_jazz to floatArrayOf(150f, 100f, 60f, 140f, 200f, 180f, 120f, 180f, 220f, 200f),
        R.string.eq_preset_voice to floatArrayOf(-250f, -150f, 0f, 200f, 400f, 380f, 200f, 120f, 0f, -120f),
    )

    // The everyday names: Flat and Bass Boost are above; Vocal and Clear are Voice and Pure Clarity.
    val morePresets = listOf(
        R.string.eq_preset_deep_bass to floatArrayOf(550f, 450f, 250f, 50f, -50f, -50f, 0f, 0f, 50f, 100f),
        R.string.eq_preset_hip_hop to floatArrayOf(400f, 300f, 100f, 150f, -50f, -50f, 50f, 0f, 100f, 150f),
        R.string.eq_preset_classical to floatArrayOf(200f, 150f, 100f, 50f, -50f, -50f, 0f, 100f, 150f, 200f),
        R.string.eq_preset_night to floatArrayOf(100f, 100f, 50f, 0f, 0f, -50f, -100f, -150f, -200f, -250f),
    )

    val dolbyPresets = listOf(
        R.string.eq_preset_dolby_open to floatArrayOf(150f, 180f, 220f, 180f, 160f, 210f, 250f, 280f, 180f, 80f),
        R.string.eq_preset_dolby_rich to floatArrayOf(100f, 160f, 200f, 220f, 280f, 260f, 240f, 200f, 150f, 50f),
        R.string.eq_preset_dolby_focused to floatArrayOf(-300f, -50f, 130f, 180f, 220f, 120f, 140f, 100f, -50f, -300f),
    )

    val diracPresets = listOf(
        R.string.eq_preset_dirac_music to floatArrayOf(200f, 140f, 80f, 0f, 30f, 80f, 140f, 200f, 280f, 350f),
        R.string.eq_preset_dirac_movie to floatArrayOf(300f, 250f, 150f, 0f, 70f, 120f, 180f, 250f, 320f, 400f),
        R.string.eq_preset_dirac_game to floatArrayOf(150f, 250f, 200f, 0f, 80f, 150f, 300f, 450f, 400f, 280f),
    )

    val customProfiles by viewModel.customProfiles.collectAsState()
    var showManageDialog by remember { mutableStateOf(false) }

    if (showManageDialog) {
        ManagePresetsDialog(
            customProfiles = customProfiles,
            onDismiss = { showManageDialog = false },
            onDeleteSelected = { ids ->
                viewModel.deleteProfiles(ids)
                showManageDialog = false
            }
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp), 
    ) {
        CircularEqControl(
            bass = bass, mid = mid, treble = treble,
            enabled = enabled,
            onBassChange = { bass = it; applyTriangle() },
            onMidChange = { mid = it; applyTriangle() },
            onTrebleChange = { treble = it; applyTriangle() },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(horizontal = 8.dp)
                .aspectRatio(1f),
        )

        AnimatedVisibility(
            visible = isDirty && enabled,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedButton(
                onClick = onSaveClick,
                modifier = Modifier.padding(bottom = 8.dp),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.eq_save),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (customProfiles.isNotEmpty()) {
            PresetSection(
                title = stringResource(R.string.eq_label_custom),
                presets = customProfiles.map { -1 to it.bands.map { it.gain.toFloat() * 50f }.toFloatArray() },
                presetNames = customProfiles.map { it.name },
                enabled = enabled,
                viewModel = viewModel,
                bandGains = bandGains,
                onEditClick = { showManageDialog = true }
            )
        }

        shinyPresets.chunked(4).forEach { chunk ->
            PresetSection(
                title = if (shinyPresets.first() in chunk) stringResource(R.string.eq_label_shiny) else "",
                presets = chunk,
                enabled = enabled,
                viewModel = viewModel,
                bandGains = bandGains
            )
        }
        PresetSection(stringResource(R.string.eq_label_more), morePresets, null, enabled, viewModel, bandGains)
        PresetSection(stringResource(R.string.eq_label_dolby), dolbyPresets, null, enabled, viewModel, bandGains)
        PresetSection(stringResource(R.string.eq_label_dirac), diracPresets, null, enabled, viewModel, bandGains)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PresetSection(
    title: String,
    presets: List<Pair<Int, FloatArray>>,
    presetNames: List<String>? = null,
    enabled: Boolean,
    viewModel: AxionEqViewModel,
    bandGains: FloatArray,
    onEditClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (title.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                
                if (onEditClick != null && enabled) {
                    androidx.compose.material3.IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            presets.forEachIndexed { index, (nameRes, bands) ->
                val name = presetNames?.getOrNull(index) ?: stringResource(nameRes)
                val isSelected = remember(bandGains) {
                    bandGains.size == bands.size && 
                    bandGains.zip(bands).all { (g, b) -> abs(g - b) < 10f }
                }

                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { if (enabled) viewModel.setBandsGains(bands) },
                    enabled = enabled,
                    shapes = when {
                        presets.size == 1 || index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        index == presets.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    
    val cardShape = AbsoluteSmoothCornerShape(30.dp, 60)
    val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
    val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 320.dp),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = blockShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        
                        Text(
                            text = stringResource(R.string.eq_save_dialog_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text(stringResource(R.string.eq_save_name_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = actionShape,
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    
                    OutlinedButton(
                        onClick = { if (name.isNotBlank()) onSave(name) },
                        enabled = name.isNotBlank(),
                        shape = actionShape,
                    ) {
                        Text(text = stringResource(R.string.eq_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedEqMode(
    bandGains: FloatArray,
    preamp: Float,
    enabled: Boolean,
    onBandChange: (Int, Float) -> Unit,
    onPreampChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val bandLabels = arrayOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (band in 0..9) {
                    EqBandSlider(
                        label = bandLabels[band],
                        value = bandGains[band],
                        enabled = enabled,
                        onValueChange = { onBandChange(band, it) },
                    )
                }
            }
        }

        // Preamp: the level into the bands. Boosts never clip either way (the EQ keeps its
        // own headroom), but a lower preamp keeps a heavy boost from leaning on the limiter.
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.eq_preamp), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = formatDb(preamp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            Slider(
                value = preamp,
                onValueChange = { onPreampChange(if (kotlin.math.abs(it) < 0.25f) 0f else (it * 2).roundToInt() / 2f) },
                valueRange = -12f..6f,
                enabled = enabled,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            OutlinedButton(onClick = onReset) {
                Icon(Icons.Rounded.Replay, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.eq_reset))
            }
        }
    }
}

@Composable
private fun EqBandSlider(
    label: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            // Stored as dB × 50 (±600 is ±12 dB).
            text = formatDb(value / 50f),
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier.height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = -600f..600f,
                enabled = enabled,
                modifier = Modifier
                    .width(200.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                            )
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(
                                -placeable.width / 2 + placeable.height / 2,
                                placeable.width / 2 - placeable.height / 2
                            )
                        }
                    }
                    .graphicsLayer { rotationZ = -90f },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatDb(db: Float): String = when {
    kotlin.math.abs(db) < 0.05f -> "0 dB"
    db > 0 -> "+%.1f dB".format(db)
    else -> "−%.1f dB".format(-db)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagePresetsDialog(
    customProfiles: List<SavedEQProfile>,
    onDismiss: () -> Unit,
    onDeleteSelected: (List<String>) -> Unit
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    
    val cardShape = AbsoluteSmoothCornerShape(30.dp, 60)
    val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
    val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 320.dp),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = blockShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.eq_manage_presets),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        if (customProfiles.isEmpty()) {
                            Text(
                                text = stringResource(R.string.eq_no_custom_presets),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(customProfiles) { profile ->
                                    val isSelected = selectedIds.contains(profile.id)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable {
                                                if (isSelected) selectedIds.remove(profile.id)
                                                else selectedIds.add(profile.id)
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                if (it == true) selectedIds.add(profile.id)
                                                else selectedIds.remove(profile.id)
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = profile.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss, shape = actionShape) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    
                    if (selectedIds.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { onDeleteSelected(selectedIds.toList()) },
                            shape = actionShape,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(text = stringResource(R.string.eq_delete_selected))
                        }
                    }
                }
            }
        }
    }
}
