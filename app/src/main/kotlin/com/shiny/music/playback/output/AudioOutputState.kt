package com.shiny.music.playback.output

/**
 * What the audio output panel knows and shows, with nothing of Android in it: the monitor
 * reads the system into an [AudioOutputSnapshot], and [reducePanelState] turns that, plus what
 * the listener has asked for, into the one [BluetoothPanelState] the sheet draws.
 *
 * Every value here is something Android reported. A field Android does not report for a
 * device is null, and the sheet leaves it out: there is no signal strength at all, because no
 * public API gives one for a connected audio device.
 */

/** How sound reaches an output. From the audio device's type, never from its name. */
enum class OutputTransport { BuiltIn, Wired, Usb, Hdmi, Bluetooth, BluetoothLe, HearingAid }

val OutputTransport.isBluetooth: Boolean
    get() = this == OutputTransport.Bluetooth || this == OutputTransport.BluetoothLe || this == OutputTransport.HearingAid

/** What an output is, for its picture. [Unknown] when Android does not say. */
enum class OutputForm { Phone, Headphones, Speaker, Car, Tv, HearingAid, Usb, Unknown }

/** Whether the Bluetooth radio can be used at all. */
enum class BluetoothRadio { Unsupported, Off, On }

/** Where a Bluetooth device stands with this phone, as its audio profile reports it. */
enum class LinkState { Connecting, Connected, Disconnecting }

/** Whether Shiny may read Bluetooth devices (Android 12 and later ask for this). */
enum class BluetoothPermission { Granted, Denied, DeniedForGood }

/** An output Android can route audio to right now. */
data class AudioRoute(
    /** Android's id for the audio device; changes when the device reconnects. */
    val id: Int,
    val name: String,
    val transport: OutputTransport,
    /** Kept in memory to tell two devices apart. Never shown and never logged. */
    val address: String? = null,
)

/** A Bluetooth audio device this phone is connected to, whether or not sound goes to it. */
data class BluetoothLink(
    val address: String,
    val name: String?,
    val transport: OutputTransport,
    val form: OutputForm,
    val state: LinkState,
    /** 0..100, or null when the device does not report one. */
    val battery: Int? = null,
    /** The codec in use, or null when Android will not say. */
    val codec: String? = null,
)

/** One reading of the system. */
data class AudioOutputSnapshot(
    val routes: List<AudioRoute>,
    /** The routes sound is going to. More than one only when the system itself plays to several. */
    val activeRouteIds: Set<Int>,
    val radio: BluetoothRadio,
    /** False only where Android withholds Bluetooth devices until the listener allows it. */
    val canReadBluetooth: Boolean,
    val links: List<BluetoothLink>,
    /** Bluetooth audio devices this phone is paired with, connected or not. */
    val paired: List<NearbyDevice> = emptyList(),
)

/** Whether a Bluetooth device is paired with this phone. */
enum class BondState { None, Bonding, Bonded }

/** A Bluetooth audio device that is not an output yet: one the phone is paired with, or one found nearby. */
data class NearbyDevice(
    /** Tells devices apart. Never shown and never logged. */
    val address: String,
    val name: String?,
    val form: OutputForm,
    val bond: BondState,
)

enum class SearchPhase { Searching, Finished }

/** One reading of a search for nearby devices: only what the radio has actually found. */
data class BluetoothSearchSnapshot(
    val phase: SearchPhase,
    val found: List<NearbyDevice>,
)

/** One output, as the sheet shows it. */
data class BluetoothAudioDeviceUiState(
    /** Stable for as long as the device stays connected; the sheet's list key. */
    val deviceId: String,
    /** The route to hand to the player, or null for a device that is connected but not a route. */
    val routeId: Int?,
    /** Empty when Android gave no name; the sheet then names it by its kind. */
    val name: String,
    val transport: OutputTransport,
    val form: OutputForm,
    val connectionState: LinkState,
    /** Sound is going to this device. Connected alone does not mean this. */
    val isSelectedRoute: Boolean,
    val batteryLevel: Int?,
    val codec: String?,
) {
    val isBluetooth: Boolean get() = transport.isBluetooth
    val isBleAudio: Boolean get() = transport == OutputTransport.BluetoothLe
    /** One of the outputs Android offers, so playback can be moved to it from the sheet. */
    val isSystemRoute: Boolean get() = routeId != null
    val canSelect: Boolean get() = routeId != null && !isSelectedRoute && connectionState == LinkState.Connected
}

/** What plays now, and what else is there. */
data class OutputDevices(
    /** The device the sheet is about: where sound is going. Null only before anything is known. */
    val current: BluetoothAudioDeviceUiState?,
    /** Every other output: connected Bluetooth devices, and routes sound could be moved to. */
    val others: List<BluetoothAudioDeviceUiState>,
) {
    val all: List<BluetoothAudioDeviceUiState> get() = listOfNotNull(current) + others
    val connectedBluetooth: List<BluetoothAudioDeviceUiState>
        get() = all.filter { it.isBluetooth && it.connectionState == LinkState.Connected }
}

/** Why something the listener asked for did not happen. */
enum class OutputError { SwitchTimedOut, RouteDisappeared, SettingsUnavailable, PairingFailed }

/**
 * The panel, as one of a fixed set of states. The sheet draws exactly what it is given and
 * works nothing out for itself.
 */
sealed interface BluetoothPanelState {
    val devices: OutputDevices

    /** The phone can pair another audio device: it has Bluetooth, and nothing is in the way. */
    val canConnectAnother: Boolean get() = false

    /** This phone has no Bluetooth. Its other outputs are still shown. */
    data class Unavailable(override val devices: OutputDevices) : BluetoothPanelState

    /** Android will not list Bluetooth devices until the listener allows it. */
    data class PermissionRequired(
        override val devices: OutputDevices,
        /** False once Android has stopped showing its prompt; the way in is then Settings. */
        val canAsk: Boolean,
    ) : BluetoothPanelState {
        override val canConnectAnother get() = true
    }

    data class BluetoothDisabled(override val devices: OutputDevices) : BluetoothPanelState

    data class NoDeviceConnected(override val devices: OutputDevices) : BluetoothPanelState {
        override val canConnectAnother get() = true
    }

    /** A device is connecting, or playback is being moved to [device]. */
    data class Connecting(
        override val devices: OutputDevices,
        val device: BluetoothAudioDeviceUiState,
    ) : BluetoothPanelState

    data class Connected(override val devices: OutputDevices) : BluetoothPanelState {
        override val canConnectAnother get() = true
    }

    data class Disconnecting(
        override val devices: OutputDevices,
        val device: BluetoothAudioDeviceUiState,
    ) : BluetoothPanelState

    data class ConnectionError(
        override val devices: OutputDevices,
        val error: OutputError,
        /** The device it was about, when there was one. */
        val deviceName: String?,
        override val canConnectAnother: Boolean,
    ) : BluetoothPanelState
}

/** What the listener has asked for that has not happened yet, and what went wrong last. */
data class PanelRequest(
    /** The route playback is being moved to. */
    val pendingRouteId: Int? = null,
    val error: OutputError? = null,
    val errorDeviceName: String? = null,
    /** The device being paired, one at a time. */
    val pairing: PairingRequest? = null,
)

/** A pairing the listener started. [seenBonding] is true once Android has reported it under way. */
data class PairingRequest(
    val address: String,
    val name: String,
    val seenBonding: Boolean = false,
)

/**
 * The state for one reading of the system. A pure function: the same inputs always give the
 * same state, and nothing is remembered between calls.
 */
fun reducePanelState(
    snapshot: AudioOutputSnapshot,
    permission: BluetoothPermission,
    request: PanelRequest = PanelRequest(),
): BluetoothPanelState {
    val devices = outputDevices(snapshot)
    val bluetoothUsable = snapshot.radio == BluetoothRadio.On

    request.error?.let { error ->
        return BluetoothPanelState.ConnectionError(
            devices = devices,
            error = error,
            deviceName = request.errorDeviceName,
            canConnectAnother = bluetoothUsable,
        )
    }

    request.pendingRouteId?.let { pending ->
        val target = devices.all.firstOrNull { it.routeId == pending }
        if (target != null && !target.isSelectedRoute) {
            return BluetoothPanelState.Connecting(devices, target)
        }
    }
    devices.all.firstOrNull { it.connectionState == LinkState.Connecting }?.let {
        return BluetoothPanelState.Connecting(devices, it)
    }
    devices.all.firstOrNull { it.connectionState == LinkState.Disconnecting }?.let {
        return BluetoothPanelState.Disconnecting(devices, it)
    }

    return when {
        snapshot.radio == BluetoothRadio.Unsupported -> BluetoothPanelState.Unavailable(devices)
        snapshot.radio == BluetoothRadio.Off -> BluetoothPanelState.BluetoothDisabled(devices)
        // The snapshot is what was actually readable; [permission] only says how to ask.
        !snapshot.canReadBluetooth ->
            BluetoothPanelState.PermissionRequired(devices, canAsk = permission != BluetoothPermission.DeniedForGood)
        devices.all.none { it.isBluetooth } -> BluetoothPanelState.NoDeviceConnected(devices)
        else -> BluetoothPanelState.Connected(devices)
    }
}

/**
 * Follows a request to move playback to another route until the system shows it there, the
 * route goes away, or it has taken too long. The latest request wins: asking for a second
 * route while the first is pending simply replaces it.
 */
fun settleRequest(request: PanelRequest, snapshot: AudioOutputSnapshot): PanelRequest {
    val pending = request.pendingRouteId ?: return request
    val route = snapshot.routes.firstOrNull { it.id == pending }
    return when {
        route == null -> PanelRequest(error = OutputError.RouteDisappeared, pairing = request.pairing)
        pending in snapshot.activeRouteIds -> request.copy(pendingRouteId = null)
        else -> request
    }
}

/** The pending request ran out of time. A request that already settled is left alone. */
fun timeOutRequest(request: PanelRequest, snapshot: AudioOutputSnapshot?): PanelRequest {
    val pending = request.pendingRouteId ?: return request
    val name = snapshot?.routes?.firstOrNull { it.id == pending }?.name?.takeIf { it.isNotBlank() }
    return PanelRequest(error = OutputError.SwitchTimedOut, errorDeviceName = name, pairing = request.pairing)
}

/** Routes and Bluetooth links brought together into the devices the sheet lists. */
fun outputDevices(snapshot: AudioOutputSnapshot): OutputDevices {
    // A link can stand behind more than one route: Android lists some headsets once per audio profile.
    val matched = HashSet<BluetoothLink>()
    val bluetoothRoutes = snapshot.routes.count { it.transport.isBluetooth }

    val fromRoutes = snapshot.routes.map { route ->
        val link = if (route.transport.isBluetooth) {
            matchLink(route, snapshot.links, onlyBluetoothRoute = bluetoothRoutes == 1)?.also { matched += it }
        } else {
            null
        }
        BluetoothAudioDeviceUiState(
            deviceId = "route:${route.id}",
            routeId = route.id,
            name = link?.name?.takeIf { it.isNotBlank() } ?: route.name,
            transport = route.transport,
            form = link?.form ?: formOf(route.transport),
            // A route exists only while its device is connected; a link on its way out says so.
            connectionState = if (link?.state == LinkState.Disconnecting) LinkState.Disconnecting else LinkState.Connected,
            isSelectedRoute = route.id in snapshot.activeRouteIds,
            batteryLevel = link?.reportedBattery(),
            codec = link?.reportedCodec(),
        )
    }.distinctSameDevice()

    // Connected to the phone, but not an output Android is offering: shown, never as playing.
    val linksOnly = snapshot.links.filter { it !in matched }.mapIndexed { index, link ->
        BluetoothAudioDeviceUiState(
            deviceId = "link:$index:${link.name.orEmpty()}",
            routeId = null,
            name = link.name.orEmpty(),
            transport = link.transport,
            form = link.form,
            connectionState = link.state,
            isSelectedRoute = false,
            batteryLevel = link.reportedBattery(),
            codec = link.reportedCodec(),
        )
    }

    val all = fromRoutes + linksOnly
    // Bluetooth first among the routes in use: it is what the sheet is mostly opened for.
    val current = all.filter { it.isSelectedRoute }.minByOrNull { if (it.isBluetooth) 0 else 1 }
    val others = all.filter { it !== current }.sortedWith(
        compareBy<BluetoothAudioDeviceUiState>(
            { if (it.isSelectedRoute) 0 else 1 },
            { if (it.isBluetooth) 0 else 1 },
            { if (it.transport == OutputTransport.BuiltIn) 1 else 0 },
            { it.name.lowercase() },
        )
    )
    return OutputDevices(current = current, others = others)
}

/** A level or a codec belongs to a link that is up; one being made or dropped has neither to show. */
private fun BluetoothLink.reportedBattery(): Int? = if (state == LinkState.Connected) sanitizeBattery(battery) else null

private fun BluetoothLink.reportedCodec(): String? = if (state == LinkState.Connected) codec?.takeIf { it.isNotBlank() } else null

/**
 * The link behind a Bluetooth route. By address when Android gives one; otherwise by name, and
 * only when that leaves no doubt. With one Bluetooth route and one connected link, they are
 * the same device.
 */
private fun matchLink(route: AudioRoute, links: List<BluetoothLink>, onlyBluetoothRoute: Boolean): BluetoothLink? {
    val address = route.address
    if (!address.isNullOrBlank()) {
        links.firstOrNull { sameAddress(it.address, address) }?.let { return it }
    }
    val sameName = links.filter { !it.name.isNullOrBlank() && it.name.equals(route.name, ignoreCase = true) }
    if (sameName.size == 1) return sameName.single()
    val connected = links.filter { it.state != LinkState.Connecting }
    return if (onlyBluetoothRoute && connected.size == 1 && sameName.isEmpty()) connected.single() else null
}

/**
 * Whether two Bluetooth addresses name the same device. Android may hand an app a masked
 * address (`XX:XX:XX:XX:AB:CD`), of which only the end can be compared.
 */
internal fun sameAddress(a: String, b: String): Boolean {
    if (a.equals(b, ignoreCase = true)) return true
    val masked = a.startsWith("XX", ignoreCase = true) || b.startsWith("XX", ignoreCase = true)
    return masked && a.length >= 5 && b.length >= 5 && a.takeLast(5).equals(b.takeLast(5), ignoreCase = true)
}

/** Android lists some devices twice (a headset's two audio profiles): one entry is kept, the one in use first. */
private fun List<BluetoothAudioDeviceUiState>.distinctSameDevice(): List<BluetoothAudioDeviceUiState> =
    sortedBy { if (it.isSelectedRoute) 0 else 1 }
        .distinctBy { device ->
            val family = if (device.isBluetooth) "bluetooth" else device.transport.name
            if (device.name.isBlank()) "${device.deviceId}" else "$family|${device.name.lowercase()}"
        }

internal fun formOf(transport: OutputTransport): OutputForm = when (transport) {
    OutputTransport.BuiltIn -> OutputForm.Phone
    OutputTransport.Wired -> OutputForm.Headphones
    OutputTransport.Usb -> OutputForm.Usb
    OutputTransport.Hdmi -> OutputForm.Tv
    OutputTransport.HearingAid -> OutputForm.HearingAid
    // Bluetooth says what a device is through its class, read separately; without it, unknown.
    OutputTransport.Bluetooth, OutputTransport.BluetoothLe -> OutputForm.Unknown
}

/** A battery level worth showing: Android reports -1 (and worse) for "not known". */
fun sanitizeBattery(level: Int?): Int? = level?.takeIf { it in 0..100 }

/**
 * The picture for a Bluetooth device, from its device class (`BluetoothClass.Device`). Only
 * the classes that say what the thing is are mapped; the rest stay [OutputForm.Unknown].
 */
fun formOfBluetoothClass(deviceClass: Int?): OutputForm = when (deviceClass) {
    0x0404, 0x0408, 0x0418 -> OutputForm.Headphones // wearable headset, hands-free, headphones
    0x0414, 0x041C, 0x0428 -> OutputForm.Speaker // loudspeaker, portable audio, hi-fi
    0x0420 -> OutputForm.Car
    0x0424, 0x0438, 0x043C -> OutputForm.Tv // set-top box, monitor, display with loudspeaker
    else -> OutputForm.Unknown
}

/**
 * The name of an A2DP codec from Android's source-codec type. LC3 and Opus took their numbers
 * in Android 13 and 14; before that the same numbers meant nothing a device could be using.
 */
fun codecName(codecType: Int?, sdk: Int): String? = when (codecType) {
    0 -> "SBC"
    1 -> "AAC"
    2 -> "aptX"
    3 -> "aptX HD"
    4 -> "LDAC"
    5 -> if (sdk >= 33) "LC3" else null
    6 -> if (sdk >= 34) "Opus" else null
    else -> null
}

// ---- Finding and pairing devices ---------------------------------------------------------

/** Whether Shiny can look for new Bluetooth devices itself, and if not, what is in the way. */
enum class SearchAvailability {
    /** This phone has no Bluetooth. */
    NoBluetooth,
    BluetoothOff,

    /**
     * Before Android 12 a search needs the location permission. Shiny does not ask for the
     * listener's location to find headphones, so there new devices are paired in Android's settings.
     */
    SettingsOnly,

    /** Android 12 and later: the listener has not allowed Shiny to find and connect nearby devices. */
    PermissionRequired,
    Ready,
}

fun searchAvailability(radio: BluetoothRadio, sdk: Int, canSearch: Boolean): SearchAvailability = when {
    radio == BluetoothRadio.Unsupported -> SearchAvailability.NoBluetooth
    radio == BluetoothRadio.Off -> SearchAvailability.BluetoothOff
    sdk < 31 -> SearchAvailability.SettingsOnly
    !canSearch -> SearchAvailability.PermissionRequired
    else -> SearchAvailability.Ready
}

/** A device in the list that is not an output: paired but not connected, or found nearby. */
data class NearbyDeviceUiState(
    val address: String,
    val name: String,
    val form: OutputForm,
    val bond: BondState,
)

/** The part of the device list below the outputs. */
data class DeviceSearchUiState(
    val availability: SearchAvailability,
    /** The radio is looking right now. */
    val searching: Boolean,
    /** A search ran to its end; with nothing in [found], nothing was there to find. */
    val finished: Boolean,
    /** Paired with this phone and not connected. Android connects these, not Shiny. */
    val paired: List<NearbyDeviceUiState>,
    /** Found nearby and not paired: these can be paired from the list. */
    val found: List<NearbyDeviceUiState>,
    /** The address being paired. While there is one, no other pairing can be started. */
    val pairingAddress: String?,
)

/**
 * What the list shows under the outputs. A device appears once: one that is already an
 * output or connected is not repeated as paired, and a paired one is not repeated as found.
 * A found device without a name is left out: there would be nothing to tell it by.
 */
fun reduceSearchState(
    snapshot: AudioOutputSnapshot,
    search: BluetoothSearchSnapshot?,
    availability: SearchAvailability,
    pairing: PairingRequest? = null,
): DeviceSearchUiState {
    val inUse: List<String> = snapshot.links.map { it.address } + snapshot.routes.mapNotNull { it.address }
    fun String.isInUse() = inUse.any { sameAddress(it, this) }

    // Just paired from the list: Android's own list of paired devices may be a moment behind.
    val newlyBonded = search?.found.orEmpty().filter { it.bond == BondState.Bonded }
    val paired = (snapshot.paired + newlyBonded)
        .filter { !it.address.isInUse() }
        .distinctBy { it.address.uppercase() }
        .map { it.toUi(BondState.Bonded) }
        .sortedBy { it.name.lowercase() }

    val found = search?.found.orEmpty()
        .filter { it.bond != BondState.Bonded && !it.name.isNullOrBlank() && !it.address.isInUse() }
        .filter { candidate -> snapshot.paired.none { sameAddress(it.address, candidate.address) } }
        .distinctBy { it.address.uppercase() }
        .map { it.toUi(it.bond) }
        .sortedBy { it.name.lowercase() }

    val ready = availability == SearchAvailability.Ready
    return DeviceSearchUiState(
        availability = availability,
        searching = ready && search?.phase == SearchPhase.Searching,
        finished = ready && search?.phase == SearchPhase.Finished,
        paired = paired,
        found = if (ready) found else emptyList(),
        pairingAddress = pairing?.address ?: found.firstOrNull { it.bond == BondState.Bonding }?.address,
    )
}

private fun NearbyDevice.toUi(bond: BondState) =
    NearbyDeviceUiState(address = address, name = name.orEmpty().trim(), form = form, bond = bond)

/**
 * Follows a pairing until Android reports the device paired, or reports the pairing dropped
 * after it had begun. Nothing is assumed in between: while Android says nothing, it is pending.
 */
fun settlePairing(request: PanelRequest, snapshot: AudioOutputSnapshot, search: BluetoothSearchSnapshot?): PanelRequest {
    val pairing = request.pairing ?: return request
    val known = snapshot.paired.any { sameAddress(it.address, pairing.address) } ||
        snapshot.links.any { sameAddress(it.address, pairing.address) }
    val bond = if (known) {
        BondState.Bonded
    } else {
        search?.found?.firstOrNull { sameAddress(it.address, pairing.address) }?.bond
    }
    return when {
        bond == BondState.Bonded -> request.copy(pairing = null)
        bond == BondState.Bonding -> request.copy(pairing = pairing.copy(seenBonding = true))
        bond == BondState.None && pairing.seenBonding ->
            PanelRequest(pendingRouteId = request.pendingRouteId, error = OutputError.PairingFailed, errorDeviceName = pairing.name)
        else -> request
    }
}

/** Android never took the pairing up. One that is under way is left to Android to finish. */
fun timeOutPairing(request: PanelRequest): PanelRequest {
    val pairing = request.pairing ?: return request
    if (pairing.seenBonding) return request
    return PanelRequest(pendingRouteId = request.pendingRouteId, error = OutputError.PairingFailed, errorDeviceName = pairing.name)
}

/** Whether a Bluetooth device class is an audio device (`BluetoothClass.Device.Major.AUDIO_VIDEO`). */
fun isAudioDeviceClass(majorDeviceClass: Int?): Boolean = majorDeviceClass == 0x0400
