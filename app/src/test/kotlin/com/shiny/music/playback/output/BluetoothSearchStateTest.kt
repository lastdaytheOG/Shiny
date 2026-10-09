package com.shiny.music.playback.output

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finding nearby devices and pairing with one: what the list shows, and how a pairing ends. */
class BluetoothSearchStateTest {

    private val speaker = AudioRoute(id = 1, name = "", transport = OutputTransport.BuiltIn)
    private val budsRoute = AudioRoute(id = 7, name = "Storm Buds", transport = OutputTransport.Bluetooth, address = "AA:BB:CC:DD:EE:01")
    private val budsLink = BluetoothLink("AA:BB:CC:DD:EE:01", "Storm Buds", OutputTransport.Bluetooth, OutputForm.Headphones, LinkState.Connected)
    private val pairedBuds = NearbyDevice("AA:BB:CC:DD:EE:01", "Storm Buds", OutputForm.Headphones, BondState.Bonded)
    private val pairedSpeaker = NearbyDevice("AA:BB:CC:DD:EE:02", "Kitchen Speaker", OutputForm.Speaker, BondState.Bonded)
    private val newSpeaker = NearbyDevice("AA:BB:CC:DD:EE:03", "Party Box", OutputForm.Speaker, BondState.None)
    private val newBuds = NearbyDevice("AA:BB:CC:DD:EE:04", "Air Buds", OutputForm.Headphones, BondState.None)

    private fun snapshot(
        routes: List<AudioRoute> = listOf(speaker),
        links: List<BluetoothLink> = emptyList(),
        paired: List<NearbyDevice> = emptyList(),
        radio: BluetoothRadio = BluetoothRadio.On,
    ) = AudioOutputSnapshot(routes, setOf(1), radio, canReadBluetooth = true, links = links, paired = paired)

    private fun searching(vararg found: NearbyDevice) = BluetoothSearchSnapshot(SearchPhase.Searching, found.toList())
    private fun finished(vararg found: NearbyDevice) = BluetoothSearchSnapshot(SearchPhase.Finished, found.toList())

    // ---- whether Shiny can search at all ---------------------------------------------------

    @Test
    fun `searching needs Bluetooth, Android 12 and the permission, in that order`() {
        assertEquals(SearchAvailability.NoBluetooth, searchAvailability(BluetoothRadio.Unsupported, 36, canSearch = true))
        assertEquals(SearchAvailability.BluetoothOff, searchAvailability(BluetoothRadio.Off, 36, canSearch = true))
        // Before Android 12 a search would need the listener's location, which is never asked for.
        assertEquals(SearchAvailability.SettingsOnly, searchAvailability(BluetoothRadio.On, 30, canSearch = true))
        assertEquals(SearchAvailability.PermissionRequired, searchAvailability(BluetoothRadio.On, 31, canSearch = false))
        assertEquals(SearchAvailability.Ready, searchAvailability(BluetoothRadio.On, 31, canSearch = true))
    }

    @Test
    fun `only audio devices are devices`() {
        assertTrue(isAudioDeviceClass(0x0400))
        assertFalse(isAudioDeviceClass(0x0200)) // a phone
        assertFalse(isAudioDeviceClass(0x0100)) // a computer
        assertFalse(isAudioDeviceClass(0x1F00)) // uncategorised
        assertFalse(isAudioDeviceClass(null))
    }

    // ---- what the list shows ---------------------------------------------------------------

    @Test
    fun `a search in progress with nothing found yet shows nothing found`() {
        val state = reduceSearchState(snapshot(), searching(), SearchAvailability.Ready)
        assertTrue(state.searching)
        assertFalse(state.finished)
        assertTrue(state.found.isEmpty())
        assertTrue(state.paired.isEmpty())
    }

    @Test
    fun `devices appear as the radio finds them, by name`() {
        val state = reduceSearchState(snapshot(), searching(newSpeaker, newBuds), SearchAvailability.Ready)
        assertEquals(listOf("Air Buds", "Party Box"), state.found.map { it.name })
        assertTrue(state.found.all { it.bond == BondState.None })
    }

    @Test
    fun `a finished search that found nothing says so, and invents nothing`() {
        val state = reduceSearchState(snapshot(), finished(), SearchAvailability.Ready)
        assertFalse(state.searching)
        assertTrue(state.finished)
        assertTrue(state.found.isEmpty())
    }

    @Test
    fun `a device with no name is not listed`() {
        val nameless = newSpeaker.copy(name = null)
        val blank = newBuds.copy(name = "  ")
        val state = reduceSearchState(snapshot(), searching(nameless, blank), SearchAvailability.Ready)
        assertTrue(state.found.isEmpty())
    }

    @Test
    fun `a device is listed once, under what it is now`() {
        // Connected and playing, paired, and heard again by the search: it is an output, nothing else.
        val state = reduceSearchState(
            snapshot(routes = listOf(speaker, budsRoute), links = listOf(budsLink), paired = listOf(pairedBuds, pairedSpeaker)),
            searching(pairedBuds.copy(bond = BondState.Bonded), pairedSpeaker, newSpeaker, newSpeaker),
            SearchAvailability.Ready,
        )
        assertEquals(listOf("Kitchen Speaker"), state.paired.map { it.name })
        assertEquals(listOf("Party Box"), state.found.map { it.name })
    }

    @Test
    fun `paired devices are shown without a search`() {
        val state = reduceSearchState(snapshot(paired = listOf(pairedSpeaker)), null, SearchAvailability.Ready)
        assertEquals(listOf("Kitchen Speaker"), state.paired.map { it.name })
        assertFalse(state.searching)
        assertFalse(state.finished)
    }

    @Test
    fun `nothing is found where Shiny cannot search`() {
        for (availability in SearchAvailability.entries.filter { it != SearchAvailability.Ready }) {
            val state = reduceSearchState(snapshot(paired = listOf(pairedSpeaker)), searching(newSpeaker), availability)
            assertTrue("$availability", state.found.isEmpty())
            assertFalse("$availability", state.searching)
            // What the phone already knows is still shown.
            assertEquals(1, state.paired.size)
        }
    }

    @Test
    fun `any number of devices can be found`() {
        val many = (1..12).map { NearbyDevice("AA:BB:CC:DD:10:%02d".format(it), "Device $it", OutputForm.Unknown, BondState.None) }
        val state = reduceSearchState(snapshot(), BluetoothSearchSnapshot(SearchPhase.Finished, many), SearchAvailability.Ready)
        assertEquals(12, state.found.size)
    }

    // ---- pairing ---------------------------------------------------------------------------

    private val pairing = PanelRequest(pairing = PairingRequest("AA:BB:CC:DD:EE:03", "Party Box"))

    @Test
    fun `a pairing Android has not reported on yet is still pending`() {
        assertEquals(pairing, settlePairing(pairing, snapshot(), searching(newSpeaker)))
        val state = reduceSearchState(snapshot(), searching(newSpeaker, newBuds), SearchAvailability.Ready, pairing.pairing)
        // While one is pending, it is the one being paired.
        assertEquals("AA:BB:CC:DD:EE:03", state.pairingAddress)
    }

    @Test
    fun `a pairing under way is marked as seen`() {
        val settled = settlePairing(pairing, snapshot(), searching(newSpeaker.copy(bond = BondState.Bonding)))
        assertTrue(settled.pairing!!.seenBonding)
        assertNull(settled.error)
    }

    @Test
    fun `a pairing ends when Android reports the device paired`() {
        val under = pairing.copy(pairing = pairing.pairing!!.copy(seenBonding = true))
        val fromSearch = settlePairing(under, snapshot(), finished(newSpeaker.copy(bond = BondState.Bonded)))
        assertNull(fromSearch.pairing)
        assertNull(fromSearch.error)
        // Also with the list closed, from the phone's own list of paired devices.
        val fromPhone = settlePairing(under, snapshot(paired = listOf(newSpeaker.copy(bond = BondState.Bonded))), null)
        assertNull(fromPhone.pairing)
        // The newly paired device moves out of "found".
        val state = reduceSearchState(snapshot(), finished(newSpeaker.copy(bond = BondState.Bonded)), SearchAvailability.Ready)
        assertTrue(state.found.isEmpty())
        assertEquals(listOf("Party Box"), state.paired.map { it.name })
    }

    @Test
    fun `a pairing that is dropped after it began is a failure naming the device`() {
        val under = pairing.copy(pairing = pairing.pairing!!.copy(seenBonding = true))
        val settled = settlePairing(under, snapshot(), finished(newSpeaker))
        assertNull(settled.pairing)
        assertEquals(OutputError.PairingFailed, settled.error)
        assertEquals("Party Box", settled.errorDeviceName)
    }

    @Test
    fun `a pairing Android never took up times out, one under way does not`() {
        val timedOut = timeOutPairing(pairing)
        assertNull(timedOut.pairing)
        assertEquals(OutputError.PairingFailed, timedOut.error)

        // Android may be waiting on its own confirmation dialog: that is not for Shiny to cut short.
        val under = pairing.copy(pairing = pairing.pairing!!.copy(seenBonding = true))
        assertEquals(under, timeOutPairing(under))
        assertEquals(PanelRequest(), timeOutPairing(PanelRequest()))
    }

    @Test
    fun `a device that disappears mid-pairing is not called paired`() {
        // Gone from the search and never reported paired: still pending, until Android says more.
        val under = pairing.copy(pairing = pairing.pairing!!.copy(seenBonding = true))
        val settled = settlePairing(under, snapshot(), finished())
        assertEquals(under, settled)
    }

    @Test
    fun `a pairing does not disturb a move between outputs`() {
        val both = PanelRequest(pendingRouteId = 7, pairing = PairingRequest("AA:BB:CC:DD:EE:03", "Party Box", seenBonding = true))
        val failed = settlePairing(both, snapshot(routes = listOf(speaker, budsRoute)), finished(newSpeaker))
        assertEquals(7, failed.pendingRouteId)
        assertEquals(OutputError.PairingFailed, failed.error)
    }

    @Test
    fun `a device being paired by Android is shown as pairing even if Shiny did not ask`() {
        val state = reduceSearchState(snapshot(), searching(newSpeaker.copy(bond = BondState.Bonding)), SearchAvailability.Ready)
        assertEquals("AA:BB:CC:DD:EE:03", state.pairingAddress)
    }
}
