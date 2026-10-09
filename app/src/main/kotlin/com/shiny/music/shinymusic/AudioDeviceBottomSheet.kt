@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.shiny.music.shinymusic

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaRouter2
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.R
import com.shiny.music.playback.output.AudioOutputMonitor
import com.shiny.music.playback.output.BluetoothAudioDeviceUiState
import com.shiny.music.playback.output.BluetoothDeviceSearch
import com.shiny.music.playback.output.BluetoothPanelState
import com.shiny.music.playback.output.BluetoothPermission
import com.shiny.music.playback.output.BluetoothSearchSnapshot
import com.shiny.music.playback.output.BondState
import com.shiny.music.playback.output.DeviceSearchUiState
import com.shiny.music.playback.output.LinkState
import com.shiny.music.playback.output.NearbyDeviceUiState
import com.shiny.music.playback.output.OutputError
import com.shiny.music.playback.output.OutputForm
import com.shiny.music.playback.output.OutputTransport
import com.shiny.music.playback.output.PairingRequest
import com.shiny.music.playback.output.PanelRequest
import com.shiny.music.playback.output.SearchAvailability
import com.shiny.music.playback.output.reducePanelState
import com.shiny.music.playback.output.reduceSearchState
import com.shiny.music.playback.output.searchAvailability
import com.shiny.music.playback.output.settlePairing
import com.shiny.music.playback.output.settleRequest
import com.shiny.music.playback.output.timeOutPairing
import com.shiny.music.playback.output.timeOutRequest
import com.shiny.music.shinymusic.shapes.RoundedStarShape
import com.shiny.music.ui.liquid.ActivityIndicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Where the music is playing, and the devices it could play on.
 *
 * The sheet is the one the output button has always opened: the device in use at the top with
 * the Bluetooth mark, the arrow that opens the device list, the volume, and Done. What it shows
 * inside is read from Android as it changes ([AudioOutputMonitor]) and reduced to one
 * [BluetoothPanelState]; the sheet draws that state and works nothing out for itself. Every
 * figure is one Android reported: a battery level only when the device gives one, a codec only
 * when Android does, and no signal strength at all, because Android offers none.
 *
 * The list finds new Bluetooth devices itself ([BluetoothDeviceSearch]) and pairs with the one
 * the listener taps. Moving the music between outputs is the player's own route preference.
 * Opening and closing the sheet touches nothing in playback.
 */
@Composable
fun AudioDeviceBottomSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val service = LocalPlayerConnection.current?.service

    var currentVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }
    var isUserDragging by remember { mutableStateOf(false) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    DisposableEffect(Unit) {
        val volumeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (streamType == AudioManager.STREAM_MUSIC && !isUserDragging) {
                        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    }
                }
            }
        }
        val registered = runCatching {
            context.registerReceiver(volumeChangeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        }.isSuccess
        onDispose {
            if (registered) runCatching { context.unregisterReceiver(volumeChangeReceiver) }
        }
    }

    // ---- What Android reports, for as long as the sheet is on screen ----------------------
    val monitor = remember(context) { AudioOutputMonitor(context) }
    val search = remember(context) { BluetoothDeviceSearch(context) }
    // Bumped when a Bluetooth permission changes hands, so the system is read from scratch.
    var readEpoch by remember { mutableIntStateOf(0) }
    val snapshot by remember(monitor, service, readEpoch) {
        monitor.snapshots(service?.preferredDeviceId ?: flowOf(null))
    }.collectAsStateWithLifecycle(initialValue = null)

    var request by remember { mutableStateOf(PanelRequest()) }
    var deniedForGood by rememberSaveable { mutableStateOf(false) }
    var askedThisTime by remember { mutableStateOf(false) }
    var showDevicePopup by remember { mutableStateOf(false) }
    var searchEpoch by remember { mutableIntStateOf(0) }

    val availability = remember(snapshot, readEpoch) {
        snapshot?.let { searchAvailability(it.radio, Build.VERSION.SDK_INT, search.canSearch()) }
    }
    // A search runs only while the list is open (or a pairing it began is still under way), and
    // stops with it: collecting is what starts the radio looking, cancelling is what stops it.
    val searching = (showDevicePopup || request.pairing != null) && availability == SearchAvailability.Ready
    val searchSnapshot by remember(search, searching, searchEpoch) {
        if (searching) search.results() else flowOf<BluetoothSearchSnapshot?>(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    // A request is done when the system shows it done, and failed when the system shows that.
    LaunchedEffect(snapshot, searchSnapshot) {
        val current = snapshot ?: return@LaunchedEffect
        request = settlePairing(settleRequest(request, current), current, searchSnapshot)
    }
    val pendingRoute = request.pendingRouteId
    val latestSnapshot = rememberUpdatedState(snapshot)
    LaunchedEffect(pendingRoute) {
        if (pendingRoute == null) return@LaunchedEffect
        delay(SWITCH_TIMEOUT_MS)
        if (request.pendingRouteId == pendingRoute) {
            Timber.tag(LOG_TAG).d("Route change timed out")
            // Back to wherever Android would send the music, rather than a route that never took.
            service?.setPreferredAudioDevice(null)
            request = timeOutRequest(request, latestSnapshot.value)
        }
    }
    val pairingAddress = request.pairing?.address
    LaunchedEffect(pairingAddress) {
        if (pairingAddress == null) return@LaunchedEffect
        delay(PAIRING_START_TIMEOUT_MS)
        if (request.pairing?.address == pairingAddress) request = timeOutPairing(request)
    }
    val error = request.error
    LaunchedEffect(error) {
        if (error == null) return@LaunchedEffect
        delay(ERROR_SHOWN_MS)
        if (request.error == error) request = request.copy(error = null, errorDeviceName = null)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.isNotEmpty() && results.values.all { it }) {
            deniedForGood = false
            readEpoch++
        } else {
            // Android stops showing its prompt after a second refusal; from then on only Settings can grant it.
            val activity = context.findActivity()
            deniedForGood = activity != null && results.filterValues { !it }.keys.none {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
        }
    }
    // The outcome is not read: whether Bluetooth came on is something the monitor sees for itself.
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    val state = remember(snapshot, request, deniedForGood) {
        snapshot?.let {
            val permission = when {
                it.canReadBluetooth -> BluetoothPermission.Granted
                deniedForGood -> BluetoothPermission.DeniedForGood
                else -> BluetoothPermission.Denied
            }
            reducePanelState(it, permission, request)
        }
    }
    val searchState = remember(snapshot, searchSnapshot, availability, request.pairing) {
        val current = snapshot
        val available = availability
        if (current == null || available == null) null else reduceSearchState(current, searchSnapshot, available, request.pairing)
    }

    fun openBluetoothSettings() {
        if (!context.open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) {
            request = request.copy(error = OutputError.SettingsUnavailable, errorDeviceName = null)
        }
    }

    fun askForBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !deniedForGood) {
            askedThisTime = true
            permissionLauncher.launch(search.permissions)
        } else if (!context.open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))) {
            request = request.copy(error = OutputError.SettingsUnavailable, errorDeviceName = null)
        }
    }

    val actions = AudioDeviceSheetActions(
        onToggleDevices = {
            showDevicePopup = !showDevicePopup
            // Opening the list is asking to look for devices: Android's prompt, once, if it is needed.
            if (showDevicePopup && availability == SearchAvailability.PermissionRequired && !askedThisTime && !deniedForGood) {
                askForBluetooth()
            }
        },
        onSelect = { device ->
            val routeId = device.routeId
            if (routeId == null) {
                // Connected to the phone but not an output Android offers apps: only Android can make it one.
                if (!context.showSystemOutputSwitcher()) openBluetoothSettings()
            } else if (device.canSelect) {
                request = if (service?.setPreferredAudioDevice(routeId) == true) {
                    request.copy(pendingRouteId = routeId, error = null, errorDeviceName = null)
                } else {
                    request.copy(pendingRouteId = null, error = OutputError.RouteDisappeared, errorDeviceName = device.name.ifBlank { null })
                }
            }
        },
        // Paired but not connected: connecting a device's audio is Android's to do, on Android's screens.
        onConnectPaired = { if (!context.showSystemOutputSwitcher()) openBluetoothSettings() },
        onPair = { device ->
            if (request.pairing == null) {
                request = if (search.pair(device.address)) {
                    request.copy(pairing = PairingRequest(device.address, device.name), error = null, errorDeviceName = null)
                } else {
                    request.copy(error = OutputError.PairingFailed, errorDeviceName = device.name)
                }
            }
        },
        onSearchAgain = { searchEpoch++ },
        onAllowBluetooth = { askForBluetooth() },
        onTurnOnBluetooth = {
            // Android's own "turn on Bluetooth?" prompt needs the permission; without it, its settings page.
            val asked = monitor.canReadBluetooth() && runCatching {
                enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }.isSuccess
            if (!asked) openBluetoothSettings()
        },
        onOpenBluetoothSettings = { openBluetoothSettings() },
        onDone = onDismiss,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        modifier = modifier,
    ) {
        AudioDeviceSheetContent(
            state = state,
            search = searchState,
            devicesShown = showDevicePopup,
            currentVolume = currentVolume,
            maxVolume = maxVolume,
            actions = actions,
            volumeControl = {
                VolumeControlRow(
                    label = stringResource(R.string.volume),
                    icon = Icons.Filled.MusicNote,
                    volume = currentVolume,
                    maxVolume = maxVolume,
                    onVolumeChange = { newVolume ->
                        currentVolume = newVolume
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume.toInt(), 0)
                    },
                    onDragStart = { isUserDragging = true },
                    onDragEnd = { isUserDragging = false },
                )
            },
        )
    }
}

/** What the sheet can ask for. Every one is a request; what happens shows up as a new state. */
internal class AudioDeviceSheetActions(
    val onToggleDevices: () -> Unit,
    val onSelect: (BluetoothAudioDeviceUiState) -> Unit,
    val onConnectPaired: (NearbyDeviceUiState) -> Unit,
    val onPair: (NearbyDeviceUiState) -> Unit,
    val onSearchAgain: () -> Unit,
    val onAllowBluetooth: () -> Unit,
    val onTurnOnBluetooth: () -> Unit,
    val onOpenBluetoothSettings: () -> Unit,
    val onDone: () -> Unit,
)

/** The sheet's content for one state: the frame it has always had, drawn from what is known now. */
@Composable
internal fun AudioDeviceSheetContent(
    state: BluetoothPanelState?,
    search: DeviceSearchUiState?,
    devicesShown: Boolean,
    currentVolume: Float,
    maxVolume: Int,
    actions: AudioDeviceSheetActions,
    volumeControl: @Composable () -> Unit,
) {
    val activeDevice = state?.devices?.current
    // The arrow is there whenever there is a list to open: other outputs, or devices to look for.
    val hasList = state != null &&
        (state.devices.others.isNotEmpty() || (search != null && search.availability != SearchAvailability.NoBluetooth))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
            .animateContentSize()
            .testTag(AudioDeviceSheetTags.Sheet)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AudioDeviceRow(
                        device = activeDevice,
                        modifier = Modifier.weight(1f)
                    )

                    AnimatedVisibility(
                        visible = hasList,
                        enter = fadeIn() + androidx.compose.animation.expandHorizontally(),
                        exit = fadeOut() + androidx.compose.animation.shrinkHorizontally()
                    ) {
                        val chevronRotation by animateFloatAsState(
                            targetValue = if (devicesShown) 180f else 0f,
                            animationSpec = tween(durationMillis = 300),
                            label = "chevron"
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            val isActive = activeDevice?.isSelectedRoute == true
                            Surface(
                                onClick = actions.onToggleDevices,
                                shape = CircleShape,
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .size(72.dp)
                                    .semantics {
                                        contentDescription = if (devicesShown) "Hide audio devices" else "Show audio devices"
                                    }
                                    .testTag(AudioDeviceSheetTags.Chevron)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .graphicsLayer { rotationZ = chevronRotation },
                                        tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = hasList && devicesShown,
                enter = expandVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(150))
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state != null) DeviceList(state, search, actions)
                }
            }
        }

        volumeControl()

        val errorState = state as? BluetoothPanelState.ConnectionError
        AnimatedVisibility(
            visible = errorState != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            // Held while it leaves, so the words do not vanish ahead of the row.
            val shown = remember { arrayOf("") }
            if (errorState != null) shown[0] = errorText(errorState.error, errorState.deviceName)
            Text(
                text = shown[0],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .semantics { liveRegion = LiveRegionMode.Assertive }
                    .testTag(AudioDeviceSheetTags.Error),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val batteryLevel = activeDevice?.takeIf { it.isBluetooth }?.batteryLevel
            if (batteryLevel != null) {
                val density = LocalDensity.current
                val strokeWidthPx = with(density) { 4.dp.toPx() }
                val wavyStroke = remember(strokeWidthPx) {
                    Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                }

                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(56.dp)
                        .clearAndSetSemantics { contentDescription = "Battery, $batteryLevel percent" }
                        .testTag(AudioDeviceSheetTags.Battery),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator(
                        progress = { batteryLevel.toFloat() / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                        stroke = wavyStroke,
                        trackStroke = wavyStroke,
                        gapSize = 3.dp
                    )
                    Text(
                        text = "$batteryLevel%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Button(
                onClick = actions.onDone,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .semantics { contentDescription = "Done, close Bluetooth panel" }
                    .testTag(AudioDeviceSheetTags.Done)
            ) {
                Text(stringResource(R.string.done))
            }
        }
    }
}

/**
 * The device list, the way Apple's output picker lays one out: every output in a row with a
 * tick on the one that is playing, then the devices this phone knows, then the ones nearby.
 */
@Composable
private fun DeviceList(
    state: BluetoothPanelState,
    search: DeviceSearchUiState?,
    actions: AudioDeviceSheetActions,
) {
    val outputs = state.devices.all
    val connecting = (state as? BluetoothPanelState.Connecting)?.device
    val paired = search?.paired.orEmpty()
    val found = search?.found.orEmpty()
    val pairingAddress = search?.pairingAddress

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Text(
            text = stringResource(R.string.audio_devices),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        outputs.forEachIndexed { index, device ->
            val name = deviceName(device)
            val status = deviceStatus(device)
            val moving = device.deviceId == connecting?.deviceId
            DeviceListRow(
                icon = deviceGlyph(device.transport, phone = Icons.Filled.PhoneAndroid),
                title = name,
                subtitle = status,
                spoken = "${deviceKind(device.transport, device.form)}, $name, $status",
                selected = device.isSelectedRoute,
                action = when {
                    device.canSelect -> "Play on $name"
                    device.routeId == null -> "Open Android's output options for $name"
                    else -> null
                },
                trailing = when {
                    moving || device.connectionState != LinkState.Connected -> RowTrailing.Busy
                    device.isSelectedRoute -> RowTrailing.Tick
                    else -> RowTrailing.None
                },
                enabled = !moving && device.connectionState == LinkState.Connected && !device.isSelectedRoute,
                divider = index != outputs.lastIndex,
                tag = AudioDeviceSheetTags.Output,
                onClick = { actions.onSelect(device) },
            )
        }

        if (paired.isNotEmpty()) {
            ListSectionHeader("My devices", busy = false)
            paired.forEachIndexed { index, device ->
                DeviceListRow(
                    icon = Icons.Filled.Bluetooth,
                    title = device.name.ifBlank { "Bluetooth device" },
                    subtitle = "Not connected",
                    spoken = "${deviceKind(OutputTransport.Bluetooth, device.form)}, ${device.name.ifBlank { "Bluetooth device" }}, not connected",
                    action = "Open Android's output options to connect ${device.name.ifBlank { "this device" }}",
                    trailing = RowTrailing.None,
                    selected = false,
                    enabled = true,
                    divider = index != paired.lastIndex,
                    tag = AudioDeviceSheetTags.Paired,
                    onClick = { actions.onConnectPaired(device) },
                )
            }
        }

        if (search != null) when (search.availability) {
            SearchAvailability.NoBluetooth -> {
                Text(
                    text = "This phone has no Bluetooth, so no other device can be connected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp).testTag(AudioDeviceSheetTags.NoBluetooth),
                )
            }
            SearchAvailability.BluetoothOff -> NoticeRow(
                icon = Icons.Filled.BluetoothDisabled,
                title = "Bluetooth is off",
                subtitle = "Turn it on to find headphones or a speaker",
                button = "Turn on",
                spoken = "Turn on Bluetooth",
                tag = AudioDeviceSheetTags.BluetoothOff,
                onClick = actions.onTurnOnBluetooth,
            )
            SearchAvailability.PermissionRequired -> {
                val canAsk = (state as? BluetoothPanelState.PermissionRequired)?.canAsk ?: true
                NoticeRow(
                    icon = Icons.Filled.Bluetooth,
                    title = "Find nearby devices",
                    subtitle = "Allow Shiny to find and connect to Bluetooth devices",
                    button = if (canAsk) "Allow" else "Settings",
                    spoken = if (canAsk) "Allow Bluetooth access" else "Open Settings to allow Bluetooth access",
                    tag = AudioDeviceSheetTags.Permission,
                    onClick = actions.onAllowBluetooth,
                )
            }
            SearchAvailability.SettingsOnly -> NoticeRow(
                icon = Icons.Filled.Bluetooth,
                title = "Pair a new device",
                subtitle = "Opens Android's Bluetooth settings",
                button = "Open",
                spoken = "Pair a new audio device in Android's Bluetooth settings",
                tag = AudioDeviceSheetTags.Settings,
                onClick = actions.onOpenBluetoothSettings,
            )
            SearchAvailability.Ready -> {
                ListSectionHeader("Other devices", busy = search.searching)
                found.forEachIndexed { index, device ->
                    val pairing = device.address == pairingAddress || device.bond == BondState.Bonding
                    DeviceListRow(
                        icon = Icons.Filled.Bluetooth,
                        title = device.name,
                        subtitle = if (pairing) "Pairing…" else null,
                        spoken = "${deviceKind(OutputTransport.Bluetooth, device.form)}, ${device.name}, " +
                            if (pairing) "pairing" else "not paired",
                        action = "Pair with ${device.name}",
                        trailing = if (pairing) RowTrailing.Busy else RowTrailing.None,
                        selected = false,
                        // One pairing at a time.
                        enabled = pairingAddress == null && !pairing,
                        divider = true,
                        tag = AudioDeviceSheetTags.Found,
                        onClick = { actions.onPair(device) },
                    )
                }
                when {
                    search.searching && found.isEmpty() -> ListNote("Searching…", AudioDeviceSheetTags.Searching)
                    search.finished && found.isEmpty() -> ListNote("No devices found", AudioDeviceSheetTags.NothingFound)
                }
                if (search.finished) {
                    DeviceListRow(
                        icon = Icons.Rounded.Refresh,
                        title = "Search again",
                        subtitle = null,
                        spoken = "Search again for Bluetooth devices",
                        action = "Search again",
                        trailing = RowTrailing.None,
                        selected = false,
                        enabled = pairingAddress == null,
                        divider = false,
                        tag = AudioDeviceSheetTags.SearchAgain,
                        onClick = actions.onSearchAgain,
                    )
                }
            }
        }
    }
}

private enum class RowTrailing { None, Tick, Busy }

@Composable
private fun ListSectionHeader(title: String, busy: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 18.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) {
                if (busy) contentDescription = "$title, searching"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (busy) {
            Spacer(Modifier.width(8.dp))
            ActivityIndicator(size = 14.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ListNote(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(tag),
    )
}

/** One device: its mark, its name, where it stands, and a tick or a spinner at the end. */
@Composable
private fun DeviceListRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    spoken: String,
    action: String?,
    trailing: RowTrailing,
    selected: Boolean,
    enabled: Boolean,
    divider: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val rowShape = RoundedCornerShape(18.dp)
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val supportingColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .background(containerColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.foundation.LocalIndication.current,
                    enabled = enabled && action != null,
                    onClickLabel = action,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
                    this.selected = selected
                }
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = contentColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = supportingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when (trailing) {
                RowTrailing.Tick -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                RowTrailing.Busy -> ActivityIndicator(size = 18.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RowTrailing.None -> Unit
            }
        }
        if (divider && !selected) {
            Box(
                Modifier
                    .padding(start = 40.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

/** Something in the way of finding devices, with the one thing that clears it. */
@Composable
private fun NoticeRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    button: String,
    spoken: String,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.foundation.LocalIndication.current,
                onClickLabel = spoken,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$title. $subtitle" }
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = button,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

/** The device in use, at the head of the sheet, with its mark where it has always been. */
@Composable
private fun AudioDeviceRow(
    device: BluetoothAudioDeviceUiState?,
    modifier: Modifier = Modifier,
) {
    val isActiveDevice = device?.isSelectedRoute == true

    val containerColor = if (isActiveDevice) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (isActiveDevice) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    val name = device?.let(::deviceName)
    val status = device?.let(::headStatus)
    Surface(
        modifier = modifier
            .testTag(AudioDeviceSheetTags.Head)
            .clearAndSetSemantics {
                contentDescription = if (device == null) "Audio output" else headDescription(device)
                liveRegion = LiveRegionMode.Polite
            },
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(onContainer.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = deviceGlyph(device?.transport, phone = Icons.Filled.Speaker),
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = name ?: " ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = status ?: "Looking for outputs",
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis,
                    color = onContainer.copy(alpha = 0.74f),
                )
            }
        }
    }
}

// ---- What the sheet says: plain functions of the state, so they can be checked without a screen ----

/**
 * A device's mark. Every Bluetooth device carries the Bluetooth mark, as it always has here;
 * the others are marked by what they are.
 */
private fun deviceGlyph(transport: OutputTransport?, phone: ImageVector): ImageVector = when (transport) {
    OutputTransport.Bluetooth, OutputTransport.BluetoothLe -> Icons.Filled.Bluetooth
    OutputTransport.HearingAid -> Icons.Filled.Hearing
    OutputTransport.Wired -> Icons.Filled.Headphones
    OutputTransport.Usb -> Icons.Filled.Usb
    OutputTransport.Hdmi -> Icons.Filled.Tv
    OutputTransport.BuiltIn -> phone
    null -> Icons.Filled.Speaker
}

/** The device's own name, or what it is when Android gave none. */
internal fun deviceName(device: BluetoothAudioDeviceUiState): String = device.name.ifBlank {
    when (device.transport) {
        OutputTransport.BuiltIn -> "This phone"
        OutputTransport.Wired -> "Wired headphones"
        OutputTransport.Usb -> "USB audio"
        OutputTransport.Hdmi -> "HDMI"
        OutputTransport.HearingAid -> "Hearing aid"
        OutputTransport.Bluetooth, OutputTransport.BluetoothLe -> "Bluetooth device"
    }
}

/** What kind of thing a device is, for a screen reader. Only as much as Android said. */
internal fun deviceKind(transport: OutputTransport, form: OutputForm): String = when (transport) {
    OutputTransport.BuiltIn -> "Phone speaker"
    OutputTransport.Wired -> "Wired output"
    OutputTransport.Usb -> "USB audio"
    OutputTransport.Hdmi -> "HDMI output"
    OutputTransport.HearingAid -> "Hearing aid"
    OutputTransport.Bluetooth, OutputTransport.BluetoothLe -> when (form) {
        OutputForm.Headphones -> "Bluetooth headphones"
        OutputForm.Speaker -> "Bluetooth speaker"
        OutputForm.Car -> "Car audio"
        OutputForm.Tv -> "Bluetooth display"
        else -> "Bluetooth device"
    }
}

/**
 * Where a device stands, in the list. Connected and playing are two different things: a
 * Bluetooth device that is connected says so, and only the one the music is going to says
 * "Playing here".
 */
internal fun deviceStatus(device: BluetoothAudioDeviceUiState): String = when {
    device.connectionState == LinkState.Connecting -> "Connecting…"
    device.connectionState == LinkState.Disconnecting -> "Disconnecting…"
    device.isSelectedRoute && device.isBluetooth -> "Connected · Playing here"
    device.isSelectedRoute -> "Playing here"
    device.isBluetooth -> "Connected"
    else -> "Available"
}

/**
 * The line under the name at the head of the sheet: where the device stands, then whatever
 * Android reports about it. A battery level or a codec that is not reported is simply not
 * there; nothing stands in for it.
 */
internal fun headStatus(device: BluetoothAudioDeviceUiState): String = when {
    device.connectionState == LinkState.Connecting -> "Connecting…"
    device.connectionState == LinkState.Disconnecting -> "Disconnecting…"
    device.isBluetooth -> buildString {
        append("Connected")
        if (device.isSelectedRoute) append(" · Playing here")
        device.batteryLevel?.let { append(" · ").append(it).append('%') }
        device.codec?.let { append(" · ").append(it) }
        if (device.isBleAudio) append(" · LE Audio")
    }
    device.isSelectedRoute -> "Playing here"
    else -> "Available"
}

internal fun headDescription(device: BluetoothAudioDeviceUiState): String = buildString {
    append(deviceKind(device.transport, device.form)).append(", ").append(deviceName(device)).append(", ")
    append(
        when {
            device.connectionState == LinkState.Connecting -> "connecting"
            device.connectionState == LinkState.Disconnecting -> "disconnecting"
            device.isSelectedRoute && device.isBluetooth -> "connected, playing here"
            device.isSelectedRoute -> "playing here"
            device.isBluetooth -> "connected"
            else -> "available"
        }
    )
    device.batteryLevel?.let { append(", battery ").append(it).append(" percent") }
    device.codec?.let { append(", codec ").append(it) }
}

internal fun errorText(error: OutputError, deviceName: String?): String = when (error) {
    OutputError.SwitchTimedOut ->
        if (deviceName != null) "Couldn't move the music to $deviceName." else "Couldn't move the music to that output."
    OutputError.RouteDisappeared ->
        if (deviceName != null) "$deviceName is no longer available." else "That output is no longer available."
    OutputError.SettingsUnavailable -> "Couldn't open Android's settings on this phone."
    OutputError.PairingFailed ->
        if (deviceName != null) "Couldn't pair with $deviceName." else "Couldn't pair with that device."
}

internal object AudioDeviceSheetTags {
    const val Sheet = "audio_sheet"
    const val Head = "audio_head"
    const val Chevron = "audio_chevron"
    const val Output = "audio_output"
    const val Paired = "audio_paired"
    const val Found = "audio_found"
    const val Searching = "audio_searching"
    const val NothingFound = "audio_nothing_found"
    const val SearchAgain = "audio_search_again"
    const val Error = "audio_error"
    const val Permission = "audio_permission"
    const val BluetoothOff = "audio_bluetooth_off"
    const val NoBluetooth = "audio_no_bluetooth"
    const val Settings = "audio_bluetooth_settings"
    const val Battery = "audio_battery"
    const val Done = "audio_done"
}

private const val LOG_TAG = "BluetoothRoute"

/** How long a move to another output is given before it is called off. */
private const val SWITCH_TIMEOUT_MS = 4_000L

/** How long Android is given to take a pairing up. Once it has, the pairing is Android's to finish. */
private const val PAIRING_START_TIMEOUT_MS = 12_000L

/** How long a failure stays on the sheet. */
private const val ERROR_SHOWN_MS = 5_000L

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Starts [intent], or says it could not: a phone may have no screen for it. */
private fun Context.open(intent: Intent): Boolean = runCatching {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

/** Android's own output switcher (Android 14 and later), where a paired device can be connected and made the output. */
private fun Context.showSystemOutputSwitcher(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        runCatching { SystemOutputSwitcher.show(this) }.getOrDefault(false)

/** Kept in a class of its own, so phones older than the API never have to load it. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private object SystemOutputSwitcher {
    fun show(context: Context): Boolean = MediaRouter2.getInstance(context).showSystemOutputSwitcher()
}

@Composable
fun VolumeControlRow(
    label: String,
    icon: ImageVector,
    volume: Float,
    maxVolume: Int,
    onVolumeChange: (Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val safeMax = maxVolume.coerceAtLeast(1)
    var isDragging by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = if (volume > 0f) icon else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${volume.toInt()} / $safeMax",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Slider(
                value = volume.coerceIn(0f, safeMax.toFloat()),
                onValueChange = { newValue ->
                    if (!isDragging) {
                        isDragging = true
                        onDragStart()
                    }
                    onVolumeChange(newValue)
                },
                onValueChangeFinished = {
                    if (isDragging) {
                        isDragging = false
                        onDragEnd()
                    }
                },
                valueRange = 0f..safeMax.toFloat(),
                steps = (safeMax - 1).coerceAtLeast(0),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "$label, ${volume.toInt()} of $safeMax" },
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}
