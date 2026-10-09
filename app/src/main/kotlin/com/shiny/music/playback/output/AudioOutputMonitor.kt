package com.shiny.music.playback.output

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.MediaRouter
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicReference

/**
 * Watches the system's audio outputs and Bluetooth audio devices for as long as someone is
 * collecting [snapshots], and for no longer.
 *
 * It only observes. Where sound goes is decided by Android's audio routing and by the player
 * (`MusicService.setPreferredAudioDevice`); nothing here routes, connects or disconnects.
 *
 * Everything is event-driven: Android's audio-device, playback and route callbacks and the
 * Bluetooth broadcasts each ask for one re-read, re-reads are merged and done off the main
 * thread, and a reading equal to the last one is not sent on. Collecting registers the
 * listeners and cancelling the collection removes every one of them, so there is nothing to
 * leak and nothing left running behind a closed sheet. It holds the application context only.
 */
class AudioOutputMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothAdapter: BluetoothAdapter? =
        runCatching { (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter }.getOrNull()
    private val hasBluetoothHardware =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) && bluetoothAdapter != null

    /**
     * Readings of the system, the first as soon as collection starts. [preferredRouteId] is the
     * route the player has been told to use, or null while it follows the system.
     */
    fun snapshots(preferredRouteId: Flow<Int?>): Flow<AudioOutputSnapshot> = callbackFlow {
        val main = Handler(Looper.getMainLooper())
        val refresh = Channel<Unit>(Channel.CONFLATED)
        val changed = { refresh.trySend(Unit); Unit }
        val proxies = Proxies()
        val preferred = AtomicReference<Int?>(null)

        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = changed()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = changed()
        }
        audioManager.registerAudioDeviceCallback(deviceCallback, main)

        // Fires when a player starts, stops or is moved to another device.
        val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) = changed()
        }
        audioManager.registerAudioPlaybackCallback(playbackCallback, main)

        // The system's own route for music changes without any device being added or removed
        // when the listener picks another output in Android's output switcher.
        val mediaRouter = runCatching { appContext.getSystemService(Context.MEDIA_ROUTER_SERVICE) as? MediaRouter }.getOrNull()
        val routeCallback = object : MediaRouter.SimpleCallback() {
            override fun onRouteSelected(router: MediaRouter?, type: Int, info: MediaRouter.RouteInfo?) = changed()
            override fun onRouteChanged(router: MediaRouter?, info: MediaRouter.RouteInfo?) = changed()
        }
        runCatching { mediaRouter?.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, routeCallback) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Only ever a prompt to read again: nothing in the broadcast itself is trusted.
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) proxies.open()
                changed()
            }
        }
        // Exported, because these come from the Bluetooth stack rather than from the system
        // process; a receiver that is not exported would never see them.
        runCatching {
            ContextCompat.registerReceiver(appContext, receiver, bluetoothEvents(), ContextCompat.RECEIVER_EXPORTED)
        }.onFailure { Timber.tag(TAG).w(it, "Bluetooth events unavailable") }

        proxies.onChanged = changed
        proxies.open()

        val reader = launch(Dispatchers.Default) {
            var last: AudioOutputSnapshot? = null
            for (tick in refresh) {
                val snapshot = runCatching { read(preferred.get(), proxies, mediaRouter) }
                    .onFailure { Timber.tag(TAG).w(it, "Reading audio outputs failed") }
                    .getOrNull()
                if (snapshot != null) {
                    if (snapshot != last) log(snapshot)
                    last = snapshot
                    trySend(snapshot)
                }
                // Bluetooth reports one change as several broadcasts in quick succession; the
                // ones that arrive during this pause are read together, as one.
                delay(SETTLE_MS)
            }
        }
        val follower = launch {
            preferredRouteId.collect {
                preferred.set(it)
                changed()
            }
        }
        changed()

        awaitClose {
            reader.cancel()
            follower.cancel()
            refresh.close()
            proxies.onChanged = {}
            runCatching { appContext.unregisterReceiver(receiver) }
            runCatching { mediaRouter?.removeCallback(routeCallback) }
            runCatching { audioManager.unregisterAudioPlaybackCallback(playbackCallback) }
            runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
            proxies.close()
        }
    }.conflate().distinctUntilChanged()

    /** True where Android lets Shiny see Bluetooth devices: always before Android 12. */
    fun canReadBluetooth(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    val bluetoothSupported: Boolean get() = hasBluetoothHardware

    private fun read(preferred: Int?, proxies: Proxies, mediaRouter: MediaRouter?): AudioOutputSnapshot {
        val infos = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val routes = infos.mapNotNull { info ->
            val transport = transportOf(info.type) ?: return@mapNotNull null
            AudioRoute(
                id = info.id,
                // A built-in output's product name is the phone's model, which is not its name here.
                name = if (transport == OutputTransport.BuiltIn) "" else info.productName?.toString().orEmpty().trim(),
                transport = transport,
                address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.address.takeIf { it.isNotBlank() } else null,
            )
        }
        val canRead = canReadBluetooth()
        return AudioOutputSnapshot(
            routes = routes,
            activeRouteIds = activeRoutes(routes, preferred, mediaRouter),
            radio = radio(),
            canReadBluetooth = canRead,
            links = if (canRead && hasBluetoothHardware) proxies.links() else emptyList(),
            paired = if (canRead && hasBluetoothHardware) pairedAudioDevices() else emptyList(),
        )
    }

    /**
     * Where music is going, from the most direct evidence there is down to the least:
     * the device a playing track is actually on, then the route the player was told to use,
     * then the system's own answer for music, then (before Android 13 gave one) the system's
     * selected route.
     */
    private fun activeRoutes(routes: List<AudioRoute>, preferred: Int?, mediaRouter: MediaRouter?): Set<Int> {
        val known = routes.map { it.id }.toSet()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val playing = runCatching {
                audioManager.activePlaybackConfigurations
                    .filter { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA }
                    .mapNotNull { it.audioDeviceInfo?.id }
                    .filter { it in known }
                    .toSet()
            }.getOrDefault(emptySet())
            if (playing.isNotEmpty()) return playing
        }
        if (preferred != null && preferred in known) return setOf(preferred)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val system = runCatching {
                audioManager.getAudioDevicesForAttributes(MusicAttributes).map { it.id }.filter { it in known }.toSet()
            }.getOrDefault(emptySet())
            if (system.isNotEmpty()) return system
        }
        val onBluetooth = runCatching {
            mediaRouter?.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)?.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH
        }.getOrDefault(false)
        val pick = routes.firstOrNull { onBluetooth && it.transport.isBluetooth }
            ?: routes.lastOrNull { !onBluetooth && it.transport != OutputTransport.BuiltIn && !it.transport.isBluetooth }
            ?: routes.firstOrNull { it.transport == OutputTransport.BuiltIn }
        return setOfNotNull(pick?.id)
    }

    @SuppressLint("MissingPermission") // isEnabled needs BLUETOOTH before Android 12, which the manifest holds there
    private fun radio(): BluetoothRadio = when {
        !hasBluetoothHardware -> BluetoothRadio.Unsupported
        runCatching { bluetoothAdapter?.isEnabled == true }.getOrDefault(false) -> BluetoothRadio.On
        else -> BluetoothRadio.Off
    }

    /**
     * The Bluetooth audio profiles, held open while the monitor is collected. Android hands
     * each one over asynchronously, and takes it back when Bluetooth is switched off.
     */
    private inner class Proxies : BluetoothProfile.ServiceListener {
        @Volatile var onChanged: () -> Unit = {}
        private val open = HashMap<Int, BluetoothProfile>()
        private val requested = HashSet<Int>()
        private var closed = false

        @Synchronized
        fun open() {
            val adapter = bluetoothAdapter ?: return
            if (closed || !hasBluetoothHardware || !canReadBluetooth()) return
            if (runCatching { adapter.isEnabled }.getOrDefault(false).not()) return
            for (profile in audioProfiles()) {
                if (profile in open || profile in requested) continue
                val asked = runCatching { adapter.getProfileProxy(appContext, this, profile) }.getOrDefault(false)
                if (asked) requested += profile
            }
        }

        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            synchronized(this) {
                requested -= profile
                if (proxy == null) return
                if (closed) {
                    runCatching { bluetoothAdapter?.closeProfileProxy(profile, proxy) }
                    return
                }
                open[profile] = proxy
            }
            onChanged()
        }

        override fun onServiceDisconnected(profile: Int) {
            synchronized(this) {
                open.remove(profile)
                requested -= profile
            }
            onChanged()
        }

        fun close() {
            // Closing a profile can synchronously deliver onServiceDisconnected(). Do not call
            // into Bluetooth while iterating open: that callback removes an entry and would make
            // HashMap's iterator throw ConcurrentModificationException.
            val toClose = synchronized(this) {
                closed = true
                open.entries.map { (profile, proxy) -> profile to proxy }.also {
                    open.clear()
                    requested.clear()
                }
            }
            toClose.forEach { (profile, proxy) ->
                runCatching { bluetoothAdapter?.closeProfileProxy(profile, proxy) }
            }
        }

        @SuppressLint("MissingPermission") // only called once canReadBluetooth() has said yes
        fun links(): List<BluetoothLink> {
            val current = synchronized(this) { HashMap(open) }
            val seen = HashSet<String>()
            val result = ArrayList<BluetoothLink>()
            for ((profile, proxy) in current) {
                val devices = runCatching { proxy.getDevicesMatchingConnectionStates(LinkStates) }.getOrNull() ?: continue
                for (device in devices) {
                    val address = device.address ?: continue
                    if (!seen.add(address)) continue
                    val state = when (runCatching { proxy.getConnectionState(device) }.getOrNull()) {
                        BluetoothProfile.STATE_CONNECTED -> LinkState.Connected
                        BluetoothProfile.STATE_CONNECTING -> LinkState.Connecting
                        BluetoothProfile.STATE_DISCONNECTING -> LinkState.Disconnecting
                        else -> null
                    } ?: continue
                    result += BluetoothLink(
                        address = address,
                        name = nameOf(device),
                        transport = transportOfProfile(profile),
                        form = if (transportOfProfile(profile) == OutputTransport.HearingAid) {
                            OutputForm.HearingAid
                        } else {
                            formOfBluetoothClass(runCatching { device.bluetoothClass?.deviceClass }.getOrNull())
                        },
                        state = state,
                        battery = if (state == LinkState.Connected) batteryOf(device) else null,
                        codec = if (state == LinkState.Connected && proxy is BluetoothA2dp) codecOf(proxy, device) else null,
                    )
                }
            }
            return result
        }
    }

    /** The audio devices this phone is paired with. Empty while Bluetooth is off: Android lists none then. */
    @SuppressLint("MissingPermission") // only called once canReadBluetooth() has said yes
    private fun pairedAudioDevices(): List<NearbyDevice> = runCatching {
        bluetoothAdapter?.bondedDevices.orEmpty()
            .filter { isAudioDeviceClass(it.bluetoothClass?.majorDeviceClass) }
            .mapNotNull { device ->
                val address = device.address ?: return@mapNotNull null
                NearbyDevice(
                    address = address,
                    name = nameOf(device),
                    form = formOfBluetoothClass(device.bluetoothClass?.deviceClass),
                    bond = BondState.Bonded,
                )
            }
    }.getOrDefault(emptyList())

    @SuppressLint("MissingPermission")
    private fun nameOf(device: BluetoothDevice): String? = runCatching {
        // The name the listener gave it in Bluetooth settings, where they gave one.
        val alias = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) device.alias else null
        (alias?.takeIf { it.isNotBlank() } ?: device.name)?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * The battery level the device reports to Android, or null.
     *
     * `BluetoothDevice.getBatteryLevel()` is the value Android's own Bluetooth settings show,
     * but it is not part of the public SDK. It is looked up once and called only if it is
     * there; anywhere it is not, or for a device that reports nothing, there is no level and
     * the sheet says so rather than showing a number.
     */
    private fun batteryOf(device: BluetoothDevice): Int? {
        val method = batteryMethod ?: return null
        return sanitizeBattery(runCatching { method.invoke(device) as? Int }.getOrNull())
    }

    private val batteryMethod: Method? by lazy {
        runCatching { BluetoothDevice::class.java.getMethod("getBatteryLevel") }.getOrNull()
    }

    /**
     * The codec the A2DP link is using, or null. Android gives this only to system apps from
     * Android 13 on, and never through the public SDK, so on most phones there is none to
     * show. It is never guessed from the device or the track.
     */
    private fun codecOf(a2dp: BluetoothA2dp, device: BluetoothDevice): String? = runCatching {
        val status = codecStatusMethod?.invoke(a2dp, device) ?: return null
        val config = status.javaClass.getMethod("getCodecConfig").invoke(status) ?: return null
        val type = config.javaClass.getMethod("getCodecType").invoke(config) as? Int
        codecName(type, Build.VERSION.SDK_INT)
    }.getOrNull()

    private val codecStatusMethod: Method? by lazy {
        runCatching { BluetoothA2dp::class.java.getMethod("getCodecStatus", BluetoothDevice::class.java) }.getOrNull()
    }

    private fun log(snapshot: AudioOutputSnapshot) {
        // Debug builds only (no tree is planted in release), and never an address.
        Timber.tag(TAG).d(
            "radio=%s canRead=%s routes=%s links=%s paired=%d",
            snapshot.radio,
            snapshot.canReadBluetooth,
            snapshot.routes.joinToString(prefix = "[", postfix = "]") {
                "device=${it.name.ifEmpty { "-" }} deviceType=${it.transport} selected=${it.id in snapshot.activeRouteIds}"
            },
            snapshot.links.joinToString(prefix = "[", postfix = "]") {
                "device=${it.name ?: "-"} connectionState=${it.state} deviceType=${it.form}/${it.transport} " +
                    "battery=${it.battery ?: "unknown"} codec=${it.codec ?: "unknown"}"
            },
            snapshot.paired.size,
        )
    }

    private companion object {
        const val TAG = "BluetoothRoute"

        /** How long changes are gathered before the next read. */
        const val SETTLE_MS = 120L

        val MusicAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val LinkStates = intArrayOf(
            BluetoothProfile.STATE_CONNECTED,
            BluetoothProfile.STATE_CONNECTING,
            BluetoothProfile.STATE_DISCONNECTING,
        )

        /** `BluetoothProfile.LE_AUDIO`, public from Android 13. */
        const val PROFILE_LE_AUDIO = 22

        fun audioProfiles(): List<Int> = buildList {
            add(BluetoothProfile.A2DP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(BluetoothProfile.HEARING_AID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(PROFILE_LE_AUDIO)
        }

        fun transportOfProfile(profile: Int): OutputTransport = when (profile) {
            PROFILE_LE_AUDIO -> OutputTransport.BluetoothLe
            BluetoothProfile.HEARING_AID -> OutputTransport.HearingAid
            else -> OutputTransport.Bluetooth
        }

        fun bluetoothEvents() = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction("android.bluetooth.hearingaid.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.action.LE_AUDIO_CONNECTION_STATE_CHANGED")
            // Sent when a device reports a new battery level; saves ever having to poll for it.
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
        }
    }
}

/**
 * The outputs worth showing, by Android's audio device type. Anything that is not somewhere a
 * listener would choose to hear music (the earpiece, a call's Bluetooth channel, internal
 * buses) is left out. Types are given by number where they are newer than the oldest Android
 * Shiny runs on.
 */
fun transportOf(audioDeviceType: Int): OutputTransport? = when (audioDeviceType) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputTransport.BuiltIn
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL,
    AudioDeviceInfo.TYPE_AUX_LINE,
    AudioDeviceInfo.TYPE_DOCK -> OutputTransport.Wired
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_USB_HEADSET -> OutputTransport.Usb
    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_HDMI_ARC,
    29 /* TYPE_HDMI_EARC */ -> OutputTransport.Hdmi
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputTransport.Bluetooth
    26 /* TYPE_BLE_HEADSET */, 27 /* TYPE_BLE_SPEAKER */, 30 /* TYPE_BLE_BROADCAST */ -> OutputTransport.BluetoothLe
    AudioDeviceInfo.TYPE_HEARING_AID -> OutputTransport.HearingAid
    else -> null
}
