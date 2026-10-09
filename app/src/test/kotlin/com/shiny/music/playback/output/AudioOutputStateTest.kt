package com.shiny.music.playback.output

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The audio output panel's state, worked out from readings of the system. */
class AudioOutputStateTest {

    private val speaker = AudioRoute(id = 1, name = "", transport = OutputTransport.BuiltIn)
    private val buds = AudioRoute(id = 7, name = "Storm Buds", transport = OutputTransport.Bluetooth, address = "AA:BB:CC:DD:EE:01")
    private val budsLink = BluetoothLink(
        address = "AA:BB:CC:DD:EE:01",
        name = "Storm Buds",
        transport = OutputTransport.Bluetooth,
        form = OutputForm.Headphones,
        state = LinkState.Connected,
        battery = 48,
        codec = "AAC",
    )
    private val speakerLink = BluetoothLink(
        address = "AA:BB:CC:DD:EE:02",
        name = "Kitchen Speaker",
        transport = OutputTransport.Bluetooth,
        form = OutputForm.Speaker,
        state = LinkState.Connected,
    )

    private fun snapshot(
        routes: List<AudioRoute> = listOf(speaker),
        active: Set<Int> = setOf(1),
        radio: BluetoothRadio = BluetoothRadio.On,
        canRead: Boolean = true,
        links: List<BluetoothLink> = emptyList(),
    ) = AudioOutputSnapshot(routes, active, radio, canRead, links)

    private fun state(
        snapshot: AudioOutputSnapshot,
        permission: BluetoothPermission = BluetoothPermission.Granted,
        request: PanelRequest = PanelRequest(),
    ) = reducePanelState(snapshot, permission, request)

    // ---- which state -----------------------------------------------------------------------

    @Test
    fun `a phone without Bluetooth is unavailable and offers no connection`() {
        val state = state(snapshot(radio = BluetoothRadio.Unsupported))
        assertTrue(state is BluetoothPanelState.Unavailable)
        assertFalse(state.canConnectAnother)
        // The output it does have is still shown, and shown as playing.
        assertEquals(OutputTransport.BuiltIn, state.devices.current?.transport)
        assertTrue(state.devices.current!!.isSelectedRoute)
    }

    @Test
    fun `Bluetooth switched off is its own state`() {
        val state = state(snapshot(radio = BluetoothRadio.Off))
        assertTrue(state is BluetoothPanelState.BluetoothDisabled)
        assertFalse(state.canConnectAnother)
    }

    @Test
    fun `Bluetooth on with nothing connected`() {
        val state = state(snapshot())
        assertTrue(state is BluetoothPanelState.NoDeviceConnected)
        assertTrue(state.canConnectAnother)
        assertTrue(state.devices.others.isEmpty())
    }

    @Test
    fun `one connected device that the music is on`() {
        val state = state(snapshot(routes = listOf(speaker, buds), active = setOf(7), links = listOf(budsLink)))
        assertTrue(state is BluetoothPanelState.Connected)
        val current = state.devices.current!!
        assertEquals("Storm Buds", current.name)
        assertTrue(current.isSelectedRoute)
        assertEquals(LinkState.Connected, current.connectionState)
        assertEquals(48, current.batteryLevel)
        assertEquals("AAC", current.codec)
        assertEquals(OutputForm.Headphones, current.form)
        // The phone's speaker is there to move to, and is not playing.
        val other = state.devices.others.single()
        assertEquals(OutputTransport.BuiltIn, other.transport)
        assertFalse(other.isSelectedRoute)
        assertTrue(other.canSelect)
    }

    @Test
    fun `a device on its way in is connecting`() {
        val state = state(snapshot(links = listOf(budsLink.copy(state = LinkState.Connecting))))
        assertTrue(state is BluetoothPanelState.Connecting)
        assertEquals("Storm Buds", (state as BluetoothPanelState.Connecting).device.name)
        // It is not an output yet, so it is neither playing nor something to move the music to.
        assertFalse(state.device.isSelectedRoute)
        assertFalse(state.device.canSelect)
        assertNull(state.device.batteryLevel)
    }

    @Test
    fun `a device on its way out is disconnecting`() {
        val state = state(
            snapshot(routes = listOf(speaker, buds), active = setOf(7), links = listOf(budsLink.copy(state = LinkState.Disconnecting)))
        )
        assertTrue(state is BluetoothPanelState.Disconnecting)
        assertEquals(LinkState.Disconnecting, (state as BluetoothPanelState.Disconnecting).device.connectionState)
    }

    @Test
    fun `a device disconnected from outside leaves nothing of itself behind`() {
        val connected = snapshot(routes = listOf(speaker, buds), active = setOf(7), links = listOf(budsLink))
        assertTrue(state(connected) is BluetoothPanelState.Connected)
        val after = state(snapshot())
        assertTrue(after is BluetoothPanelState.NoDeviceConnected)
        assertEquals(OutputTransport.BuiltIn, after.devices.current?.transport)
        assertTrue(after.devices.all.none { it.isBluetooth })
    }

    @Test
    fun `Bluetooth turned off while a device was connected`() {
        // Android drops the route and the link with the radio.
        val after = state(snapshot(radio = BluetoothRadio.Off))
        assertTrue(after is BluetoothPanelState.BluetoothDisabled)
        assertTrue(after.devices.all.none { it.isBluetooth })
    }

    // ---- permission ------------------------------------------------------------------------

    @Test
    fun `without the permission the output in use is still shown, without its details`() {
        // What the audio system reports needs no permission; battery, codec and the rest do.
        val state = state(
            snapshot(routes = listOf(speaker, buds), active = setOf(7), canRead = false),
            permission = BluetoothPermission.Denied,
        )
        assertTrue(state is BluetoothPanelState.PermissionRequired)
        assertTrue((state as BluetoothPanelState.PermissionRequired).canAsk)
        val current = state.devices.current!!
        assertEquals("Storm Buds", current.name)
        assertTrue(current.isSelectedRoute)
        assertNull(current.batteryLevel)
        assertNull(current.codec)
        assertEquals(OutputForm.Unknown, current.form)
    }

    @Test
    fun `a permission Android no longer asks for is sent to Settings`() {
        val state = state(snapshot(canRead = false), permission = BluetoothPermission.DeniedForGood)
        assertFalse((state as BluetoothPanelState.PermissionRequired).canAsk)
    }

    @Test
    fun `a revoked permission returns the panel to asking`() {
        val granted = state(snapshot(routes = listOf(speaker, buds), active = setOf(7), links = listOf(budsLink)))
        assertTrue(granted is BluetoothPanelState.Connected)
        val revoked = state(snapshot(routes = listOf(speaker, buds), active = setOf(7), canRead = false), BluetoothPermission.Denied)
        assertTrue(revoked is BluetoothPanelState.PermissionRequired)
        assertNull(revoked.devices.current!!.batteryLevel)
    }

    // ---- connected is not playing ----------------------------------------------------------

    @Test
    fun `two connected devices, one playing`() {
        val state = state(
            snapshot(routes = listOf(speaker, buds), active = setOf(7), links = listOf(budsLink, speakerLink))
        )
        assertTrue(state is BluetoothPanelState.Connected)
        assertEquals("Storm Buds", state.devices.current!!.name)
        val kitchen = state.devices.others.first { it.name == "Kitchen Speaker" }
        assertEquals(LinkState.Connected, kitchen.connectionState)
        assertFalse(kitchen.isSelectedRoute)
        // Android is not offering it as an output, so Shiny cannot move the music to it.
        assertFalse(kitchen.isSystemRoute)
        assertFalse(kitchen.canSelect)
        assertEquals(1, state.devices.all.count { it.isSelectedRoute })
        assertEquals(2, state.devices.connectedBluetooth.size)
    }

    @Test
    fun `a connected device is not playing while the phone's speaker is`() {
        val state = state(snapshot(routes = listOf(speaker, buds), active = setOf(1), links = listOf(budsLink)))
        assertEquals(OutputTransport.BuiltIn, state.devices.current!!.transport)
        val other = state.devices.others.single()
        assertEquals("Storm Buds", other.name)
        assertFalse(other.isSelectedRoute)
        assertTrue(other.canSelect)
    }

    @Test
    fun `two routes are both playing only when the system says both are`() {
        val second = AudioRoute(id = 9, name = "Kitchen Speaker", transport = OutputTransport.BluetoothLe, address = "AA:BB:CC:DD:EE:02")
        val state = state(
            snapshot(
                routes = listOf(speaker, buds, second),
                active = setOf(7, 9),
                links = listOf(budsLink, speakerLink.copy(transport = OutputTransport.BluetoothLe)),
            )
        )
        assertEquals(2, state.devices.all.count { it.isSelectedRoute })
        assertTrue(state.devices.others.first { it.name == "Kitchen Speaker" }.isSelectedRoute)
        assertTrue(state.devices.others.first { it.name == "Kitchen Speaker" }.isBleAudio)
        assertFalse(state.devices.others.first { it.transport == OutputTransport.BuiltIn }.isSelectedRoute)
    }

    @Test
    fun `no fixed number of devices is assumed`() {
        val links = (1..5).map { speakerLink.copy(address = "AA:BB:CC:DD:EE:1$it", name = "Speaker $it") }
        val state = state(snapshot(links = links))
        assertEquals(5, state.devices.connectedBluetooth.size)
        assertTrue(state.devices.connectedBluetooth.none { it.isSelectedRoute })
    }

    // ---- what Android does not report is not shown -----------------------------------------

    @Test
    fun `a device that reports no battery has none`() {
        for (unknown in listOf(null, -1, -100, 101)) {
            val state = state(
                snapshot(routes = listOf(speaker, buds), active = setOf(7), links = listOf(budsLink.copy(battery = unknown)))
            )
            assertNull("battery $unknown", state.devices.current!!.batteryLevel)
        }
        assertEquals(0, sanitizeBattery(0))
        assertEquals(100, sanitizeBattery(100))
    }

    @Test
    fun `a device with no codec reported has none`() {
        for (unknown in listOf(null, "", " ")) {
            val state = state(
                snapshot(routes = listOf(speaker, buds), active = setOf(7), links = listOf(budsLink.copy(codec = unknown)))
            )
            assertNull(state.devices.current!!.codec)
        }
    }

    @Test
    fun `codec numbers Android does not define are not named`() {
        assertEquals("SBC", codecName(0, 30))
        assertEquals("AAC", codecName(1, 30))
        assertEquals("aptX", codecName(2, 30))
        assertEquals("aptX HD", codecName(3, 30))
        assertEquals("LDAC", codecName(4, 30))
        assertNull(codecName(5, 31))
        assertEquals("LC3", codecName(5, 33))
        assertNull(codecName(6, 33))
        assertEquals("Opus", codecName(6, 34))
        assertNull(codecName(null, 34))
        assertNull(codecName(1_000_000, 34))
        assertNull(codecName(-1, 34))
    }

    @Test
    fun `a device of unknown kind stays unknown`() {
        assertEquals(OutputForm.Unknown, formOfBluetoothClass(null))
        assertEquals(OutputForm.Unknown, formOfBluetoothClass(0x1F00)) // uncategorised
        assertEquals(OutputForm.Unknown, formOfBluetoothClass(0x0400)) // audio/video, unspecified
        assertEquals(OutputForm.Headphones, formOfBluetoothClass(0x0404))
        assertEquals(OutputForm.Headphones, formOfBluetoothClass(0x0418))
        assertEquals(OutputForm.Speaker, formOfBluetoothClass(0x0414))
        assertEquals(OutputForm.Car, formOfBluetoothClass(0x0420))
        assertEquals(OutputForm.Tv, formOfBluetoothClass(0x043C))
        val state = state(
            snapshot(routes = listOf(speaker, buds), active = setOf(7), links = listOf(budsLink.copy(form = OutputForm.Unknown)))
        )
        assertEquals(OutputForm.Unknown, state.devices.current!!.form)
    }

    // ---- telling devices apart -------------------------------------------------------------

    @Test
    fun `a link is matched to its route by address, also when the address is masked`() {
        assertTrue(sameAddress("AA:BB:CC:DD:EE:01", "aa:bb:cc:dd:ee:01"))
        assertTrue(sameAddress("XX:XX:XX:XX:EE:01", "AA:BB:CC:DD:EE:01"))
        assertFalse(sameAddress("XX:XX:XX:XX:EE:02", "AA:BB:CC:DD:EE:01"))
        assertFalse(sameAddress("AA:BB:CC:DD:EE:02", "AA:BB:CC:DD:EE:01"))

        // Two devices with the same name: the address decides whose battery is whose.
        val twin = budsLink.copy(address = "AA:BB:CC:DD:EE:99", battery = 5)
        val state = state(snapshot(routes = listOf(speaker, buds), active = setOf(7), links = listOf(twin, budsLink)))
        assertEquals(48, state.devices.current!!.batteryLevel)
    }

    @Test
    fun `two same-named devices with no address to go by share nothing`() {
        val noAddress = buds.copy(address = null)
        val twin = budsLink.copy(address = "AA:BB:CC:DD:EE:99", battery = 5)
        val state = state(snapshot(routes = listOf(speaker, noAddress), active = setOf(7), links = listOf(twin, budsLink)))
        // Rather than guess which one is playing, the route is shown without either's details.
        assertNull(state.devices.current!!.batteryLevel)
    }

    @Test
    fun `a device Android lists under two audio profiles appears once`() {
        val ble = AudioRoute(id = 8, name = "Storm Buds", transport = OutputTransport.BluetoothLe, address = "AA:BB:CC:DD:EE:01")
        val state = state(snapshot(routes = listOf(speaker, buds, ble), active = setOf(8), links = listOf(budsLink)))
        assertEquals(1, state.devices.all.count { it.name == "Storm Buds" })
        assertTrue(state.devices.current!!.isSelectedRoute)
    }

    @Test
    fun `outputs that are not Bluetooth are shown for what they are`() {
        val wired = AudioRoute(id = 3, name = "", transport = OutputTransport.Wired)
        val usb = AudioRoute(id = 4, name = "DAC", transport = OutputTransport.Usb)
        val state = state(snapshot(routes = listOf(speaker, wired, usb), active = setOf(3)))
        assertTrue(state is BluetoothPanelState.NoDeviceConnected)
        assertEquals(OutputForm.Headphones, state.devices.current!!.form)
        assertFalse(state.devices.current!!.isBluetooth)
        assertEquals(listOf(OutputTransport.Usb, OutputTransport.BuiltIn), state.devices.others.map { it.transport })
    }

    // ---- moving the music ------------------------------------------------------------------

    private val both = snapshot(routes = listOf(speaker, buds), active = setOf(7), links = listOf(budsLink))

    @Test
    fun `moving the music shows as connecting until the system has it there`() {
        val request = PanelRequest(pendingRouteId = 1)
        val during = state(both, request = request)
        assertTrue(during is BluetoothPanelState.Connecting)
        assertEquals(OutputTransport.BuiltIn, (during as BluetoothPanelState.Connecting).device.transport)
        // Still playing where it was: nothing is shown as moved before it has.
        assertEquals("Storm Buds", during.devices.current!!.name)

        assertEquals(request, settleRequest(request, both))
        val arrived = both.copy(activeRouteIds = setOf(1))
        val settled = settleRequest(request, arrived)
        assertNull(settled.pendingRouteId)
        assertNull(settled.error)
        assertTrue(state(arrived, request = settled) is BluetoothPanelState.Connected)
    }

    @Test
    fun `a route that disappears while the music is moving to it is an error`() {
        val request = PanelRequest(pendingRouteId = 7)
        val gone = snapshot()
        val settled = settleRequest(request, gone)
        assertEquals(OutputError.RouteDisappeared, settled.error)
        assertNull(settled.pendingRouteId)
        val state = state(gone, request = settled)
        assertTrue(state is BluetoothPanelState.ConnectionError)
        // The error does not hide what is really playing.
        assertEquals(OutputTransport.BuiltIn, state.devices.current!!.transport)
    }

    @Test
    fun `a move that takes too long is an error naming the device`() {
        val request = PanelRequest(pendingRouteId = 7)
        val onSpeaker = both.copy(activeRouteIds = setOf(1))
        val timedOut = timeOutRequest(request, onSpeaker)
        assertEquals(OutputError.SwitchTimedOut, timedOut.error)
        assertEquals("Storm Buds", timedOut.errorDeviceName)
        assertNull(timedOut.pendingRouteId)
        val state = state(onSpeaker, request = timedOut) as BluetoothPanelState.ConnectionError
        assertEquals("Storm Buds", state.deviceName)
        assertTrue(state.canConnectAnother)
    }

    @Test
    fun `a move that already finished is not timed out`() {
        val done = PanelRequest()
        assertEquals(done, timeOutRequest(done, both))
    }

    @Test
    fun `switching quickly follows the last choice only`() {
        // Speaker asked for, then the buds again before the first had settled.
        val onSpeaker = both.copy(activeRouteIds = setOf(1))
        var request = PanelRequest(pendingRouteId = 1)
        request = PanelRequest(pendingRouteId = 7)
        // The system reporting the abandoned choice does not settle the newer one.
        request = settleRequest(request, onSpeaker)
        assertEquals(7, request.pendingRouteId)
        assertTrue(state(onSpeaker, request = request) is BluetoothPanelState.Connecting)
        request = settleRequest(request, both)
        assertNull(request.pendingRouteId)
        assertNull(request.error)
    }

    @Test
    fun `asking for the route already in use is not a pending move`() {
        val state = state(both, request = PanelRequest(pendingRouteId = 7))
        assertTrue(state is BluetoothPanelState.Connected)
    }

    @Test
    fun `an error with Bluetooth off does not offer a connection`() {
        val state = state(snapshot(radio = BluetoothRadio.Off), request = PanelRequest(error = OutputError.SettingsUnavailable))
        assertTrue(state is BluetoothPanelState.ConnectionError)
        assertFalse(state.canConnectAnother)
    }

    @Test
    fun `the same reading always gives the same state`() {
        val request = PanelRequest(pendingRouteId = 1)
        assertEquals(state(both, request = request), state(both, request = request))
        assertEquals(outputDevices(both), outputDevices(both.copy()))
    }

    @Test
    fun `an empty reading is a state, not a crash`() {
        val state = state(snapshot(routes = emptyList(), active = emptySet()))
        assertNull(state.devices.current)
        assertTrue(state.devices.others.isEmpty())
    }
}
