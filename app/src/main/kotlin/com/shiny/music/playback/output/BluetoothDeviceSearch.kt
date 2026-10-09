package com.shiny.music.playback.output

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

/**
 * Looks for Bluetooth audio devices nearby, and pairs with one the listener picks.
 *
 * A search is Android's own device discovery: one sweep of about twelve seconds, started when
 * [results] is collected and called off the moment the collection ends, so it never runs
 * behind a closed list. It reports only devices the radio actually heard from, and only ones
 * that say they are audio devices.
 *
 * Pairing is [BluetoothDevice.createBond]: Android shows its own confirmation where the device
 * needs one and connects the device's audio afterwards. An app cannot connect or disconnect a
 * device's audio itself, so nothing here pretends to; what happens after pairing is read back
 * by [AudioOutputMonitor] like any other change.
 *
 * It works on Android 12 and later, with the "Nearby devices" permission. Before that, a
 * search would need the listener's location, which Shiny does not ask for.
 */
class BluetoothDeviceSearch(context: Context) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        runCatching { (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter }.getOrNull()

    /** The permissions a search and a pairing need, where Android has them. */
    val permissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }

    /** True once the listener has allowed Shiny to find and connect nearby devices. */
    fun canSearch(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && adapter != null && permissions.all {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * One search. Emits as devices are found and when the sweep ends; collect it again to
     * search again. If the search cannot start (no permission, Bluetooth off, the radio busy)
     * it reports a finished search that found nothing rather than failing.
     */
    @SuppressLint("MissingPermission") // every call is behind canSearch(), and a refusal is caught
    fun results(): Flow<BluetoothSearchSnapshot> = callbackFlow {
        val found = LinkedHashMap<String, BluetoothDevice>()
        var phase = SearchPhase.Searching

        fun publish() {
            trySend(BluetoothSearchSnapshot(phase, found.values.mapNotNull(::describe)))
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND, BluetoothDevice.ACTION_NAME_CHANGED -> {
                        val device = intent.device() ?: return
                        val address = runCatching { device.address }.getOrNull() ?: return
                        // A name can arrive after the device itself; only audio devices are kept.
                        if (address in found || isAudio(device)) found[address] = device
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> phase = SearchPhase.Finished
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> phase = SearchPhase.Searching
                    BluetoothAdapter.ACTION_STATE_CHANGED ->
                        if (runCatching { adapter?.isEnabled }.getOrNull() != true) phase = SearchPhase.Finished
                    // ACTION_BOND_STATE_CHANGED: the devices' own bond state is read in publish().
                }
                publish()
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        // Exported: these are sent by the Bluetooth stack, not by the system process.
        val registered = runCatching {
            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }.isSuccess

        val started = registered && canSearch() && runCatching {
            // A sweep already under way (Android's own settings, another app) is restarted as ours.
            if (adapter?.isDiscovering == true) adapter.cancelDiscovery()
            adapter?.startDiscovery() == true
        }.getOrDefault(false)
        if (!started) phase = SearchPhase.Finished
        Timber.tag(TAG).d("search started=%s", started)
        publish()

        awaitClose {
            if (registered) runCatching { appContext.unregisterReceiver(receiver) }
            // Left running, a sweep costs battery and can make connected audio stutter.
            if (started && canSearch()) runCatching { if (adapter?.isDiscovering == true) adapter.cancelDiscovery() }
        }
    }.conflate().distinctUntilChanged()

    /**
     * Asks Android to pair with the device at [address]. Returns false if the request was not
     * taken up at all; what becomes of an accepted one arrives through the bond state.
     */
    @SuppressLint("MissingPermission")
    fun pair(address: String): Boolean {
        if (!canSearch()) return false
        return runCatching {
            // Android's guidance: a sweep in progress slows pairing down and can make it fail.
            if (adapter?.isDiscovering == true) adapter.cancelDiscovery()
            val device = adapter?.getRemoteDevice(address) ?: return false
            when (device.bondState) {
                BluetoothDevice.BOND_BONDED, BluetoothDevice.BOND_BONDING -> true
                else -> device.createBond()
            }
        }.onFailure { Timber.tag(TAG).w(it, "Pairing could not be started") }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun isAudio(device: BluetoothDevice): Boolean =
        isAudioDeviceClass(runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull())

    @SuppressLint("MissingPermission")
    private fun describe(device: BluetoothDevice): NearbyDevice? = runCatching {
        val bluetoothClass: BluetoothClass? = device.bluetoothClass
        NearbyDevice(
            address = device.address ?: return null,
            name = device.name?.trim()?.takeIf { it.isNotEmpty() },
            form = formOfBluetoothClass(bluetoothClass?.deviceClass),
            bond = when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> BondState.Bonded
                BluetoothDevice.BOND_BONDING -> BondState.Bonding
                else -> BondState.None
            },
        )
    }.getOrNull()

    private fun Intent.device(): BluetoothDevice? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }.getOrNull()

    private companion object {
        const val TAG = "BluetoothRoute"
    }
}
