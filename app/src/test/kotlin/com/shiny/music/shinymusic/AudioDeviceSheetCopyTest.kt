package com.shiny.music.shinymusic

import com.shiny.music.playback.output.BluetoothAudioDeviceUiState
import com.shiny.music.playback.output.LinkState
import com.shiny.music.playback.output.OutputError
import com.shiny.music.playback.output.OutputForm
import com.shiny.music.playback.output.OutputTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the audio device sheet says about a device: only what is known, in plain words. */
class AudioDeviceSheetCopyTest {

    private fun device(
        name: String = "Storm Buds",
        transport: OutputTransport = OutputTransport.Bluetooth,
        form: OutputForm = OutputForm.Unknown,
        state: LinkState = LinkState.Connected,
        selected: Boolean = true,
        battery: Int? = null,
        codec: String? = null,
        routeId: Int? = 7,
    ) = BluetoothAudioDeviceUiState(
        deviceId = "route:7",
        routeId = routeId,
        name = name,
        transport = transport,
        form = form,
        connectionState = state,
        isSelectedRoute = selected,
        batteryLevel = battery,
        codec = codec,
    )

    @Test
    fun `the head line carries every figure Android reports`() {
        assertEquals("Connected · Playing here · 48% · AAC", headStatus(device(battery = 48, codec = "AAC")))
        assertEquals("Connected · Playing here · 48%", headStatus(device(battery = 48)))
        assertEquals("Connected · Playing here · LDAC", headStatus(device(codec = "LDAC")))
    }

    @Test
    fun `a battery or a codec that is not reported is simply not there`() {
        val line = headStatus(device())
        assertEquals("Connected · Playing here", line)
        assertFalse(line.any { it.isDigit() })
        assertFalse(line.contains('%'))
    }

    @Test
    fun `a real zero is a battery level`() {
        assertEquals("Connected · Playing here · 0%", headStatus(device(battery = 0)))
    }

    @Test
    fun `there is never a signal figure`() {
        val line = headStatus(device(battery = 80, codec = "LDAC"))
        for (word in listOf("signal", "strong", "weak", "dBm", "RSSI")) {
            assertFalse(word, line.contains(word, ignoreCase = true))
        }
    }

    @Test
    fun `an LE Audio device says how it is connected`() {
        assertEquals("Connected · Playing here · LE Audio", headStatus(device(transport = OutputTransport.BluetoothLe)))
    }

    @Test
    fun `a connected but unselected Bluetooth device is never called playing`() {
        assertEquals("Connected", headStatus(device(selected = false)))
    }

    @Test
    fun `an output that is not Bluetooth says only where the music is`() {
        assertEquals("Playing here", headStatus(device(name = "", transport = OutputTransport.BuiltIn)))
        assertEquals("Playing here", headStatus(device(name = "", transport = OutputTransport.Wired)))
    }

    @Test
    fun `a device being connected or dropped says so, with no figures`() {
        assertEquals("Connecting…", headStatus(device(state = LinkState.Connecting, selected = false, battery = 50)))
        assertEquals("Disconnecting…", headStatus(device(state = LinkState.Disconnecting, battery = 50)))
    }

    @Test
    fun `connected and playing are said separately in the list`() {
        assertEquals("Connected · Playing here", deviceStatus(device()))
        assertEquals("Connected", deviceStatus(device(selected = false)))
        assertEquals("Playing here", deviceStatus(device(name = "", transport = OutputTransport.BuiltIn)))
        assertEquals("Available", deviceStatus(device(name = "", transport = OutputTransport.BuiltIn, selected = false)))
        assertEquals("Connecting…", deviceStatus(device(state = LinkState.Connecting, selected = false)))
        assertEquals("Disconnecting…", deviceStatus(device(state = LinkState.Disconnecting)))
    }

    @Test
    fun `an output without a name is named by what it is`() {
        assertEquals("This phone", deviceName(device(name = "", transport = OutputTransport.BuiltIn)))
        assertEquals("Wired headphones", deviceName(device(name = "", transport = OutputTransport.Wired)))
        assertEquals("Bluetooth device", deviceName(device(name = "")))
        assertEquals("Storm Buds", deviceName(device()))
    }

    @Test
    fun `a device of unknown kind is only called a Bluetooth device`() {
        assertEquals("Bluetooth device", deviceKind(OutputTransport.Bluetooth, OutputForm.Unknown))
        assertEquals("Bluetooth headphones", deviceKind(OutputTransport.Bluetooth, OutputForm.Headphones))
        assertEquals("Bluetooth speaker", deviceKind(OutputTransport.BluetoothLe, OutputForm.Speaker))
        assertEquals("Phone speaker", deviceKind(OutputTransport.BuiltIn, OutputForm.Phone))
    }

    @Test
    fun `a screen reader hears the device, its state and what is known of it`() {
        assertEquals(
            "Bluetooth headphones, Storm Buds, connected, playing here, battery 48 percent, codec AAC",
            headDescription(device(form = OutputForm.Headphones, battery = 48, codec = "AAC")),
        )
        assertEquals("Bluetooth device, Storm Buds, connected", headDescription(device(selected = false)))
        assertEquals("Phone speaker, This phone, playing here", headDescription(device(name = "", transport = OutputTransport.BuiltIn)))
    }

    @Test
    fun `every failure has words`() {
        for (error in OutputError.entries) {
            assertTrue(errorText(error, null).isNotBlank())
            assertTrue(errorText(error, "Storm Buds").isNotBlank())
        }
        assertEquals("Couldn't move the music to Storm Buds.", errorText(OutputError.SwitchTimedOut, "Storm Buds"))
        assertEquals("Storm Buds is no longer available.", errorText(OutputError.RouteDisappeared, "Storm Buds"))
        assertEquals("Couldn't pair with Storm Buds.", errorText(OutputError.PairingFailed, "Storm Buds"))
    }
}
