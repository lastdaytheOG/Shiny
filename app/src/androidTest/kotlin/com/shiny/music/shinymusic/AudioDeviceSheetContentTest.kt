package com.shiny.music.shinymusic

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shiny.music.playback.output.AudioOutputSnapshot
import com.shiny.music.playback.output.AudioRoute
import com.shiny.music.playback.output.BluetoothAudioDeviceUiState
import com.shiny.music.playback.output.BluetoothLink
import com.shiny.music.playback.output.BluetoothPanelState
import com.shiny.music.playback.output.BluetoothPermission
import com.shiny.music.playback.output.BluetoothRadio
import com.shiny.music.playback.output.BluetoothSearchSnapshot
import com.shiny.music.playback.output.BondState
import com.shiny.music.playback.output.DeviceSearchUiState
import com.shiny.music.playback.output.LinkState
import com.shiny.music.playback.output.NearbyDevice
import com.shiny.music.playback.output.NearbyDeviceUiState
import com.shiny.music.playback.output.OutputError
import com.shiny.music.playback.output.OutputForm
import com.shiny.music.playback.output.OutputTransport
import com.shiny.music.playback.output.PanelRequest
import com.shiny.music.playback.output.SearchAvailability
import com.shiny.music.playback.output.SearchPhase
import com.shiny.music.playback.output.reducePanelState
import com.shiny.music.playback.output.reduceSearchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The audio device sheet, drawn for each of its states on a real screen. */
@RunWith(AndroidJUnit4::class)
class AudioDeviceSheetContentTest {

    @get:Rule
    val compose = createComposeRule()

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
    private val pairedSpeaker = NearbyDevice("AA:BB:CC:DD:EE:02", "Kitchen Speaker", OutputForm.Speaker, BondState.Bonded)
    private val partyBox = NearbyDevice("AA:BB:CC:DD:EE:03", "Party Box", OutputForm.Speaker, BondState.None)
    private val airBuds = NearbyDevice("AA:BB:CC:DD:EE:04", "Air Buds", OutputForm.Headphones, BondState.None)

    private class Taps {
        var toggled = 0
        var selected: BluetoothAudioDeviceUiState? = null
        var connectPaired: NearbyDeviceUiState? = null
        var paired: NearbyDeviceUiState? = null
        var searchAgain = 0
        var allow = 0
        var turnOn = 0
        var settings = 0
        var done = 0
    }

    private fun snapshot(
        routes: List<AudioRoute> = listOf(speaker),
        active: Set<Int> = setOf(1),
        radio: BluetoothRadio = BluetoothRadio.On,
        canRead: Boolean = true,
        links: List<BluetoothLink> = emptyList(),
        paired: List<NearbyDevice> = emptyList(),
    ) = AudioOutputSnapshot(routes, active, radio, canRead, links, paired)

    private fun show(
        snapshot: AudioOutputSnapshot?,
        search: BluetoothSearchSnapshot? = null,
        availability: SearchAvailability = SearchAvailability.Ready,
        permission: BluetoothPermission = BluetoothPermission.Granted,
        request: PanelRequest = PanelRequest(),
        open: Boolean = true,
    ): Taps {
        val taps = Taps()
        val actions = AudioDeviceSheetActions(
            onToggleDevices = { taps.toggled++ },
            onSelect = { taps.selected = it },
            onConnectPaired = { taps.connectPaired = it },
            onPair = { taps.paired = it },
            onSearchAgain = { taps.searchAgain++ },
            onAllowBluetooth = { taps.allow++ },
            onTurnOnBluetooth = { taps.turnOn++ },
            onOpenBluetoothSettings = { taps.settings++ },
            onDone = { taps.done++ },
        )
        val state: BluetoothPanelState? = snapshot?.let { reducePanelState(it, permission, request) }
        val searchState: DeviceSearchUiState? = snapshot?.let { reduceSearchState(it, search, availability, request.pairing) }
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AudioDeviceSheetContent(
                    state = state,
                    search = searchState,
                    devicesShown = open,
                    currentVolume = 5f,
                    maxVolume = 15,
                    actions = actions,
                    volumeControl = {},
                )
            }
        }
        return taps
    }

    private val playingOnBuds = snapshot(routes = listOf(speaker, buds), active = setOf(7), links = listOf(budsLink))

    @Test
    fun connectedDevice_showsItsRealFigures() {
        val taps = show(playingOnBuds, open = false)

        compose.onNodeWithTag(AudioDeviceSheetTags.Head)
            .assertIsDisplayed()
            .assertContentDescriptionContains("Bluetooth headphones, Storm Buds, connected, playing here, battery 48 percent, codec AAC")
        compose.onNodeWithTag(AudioDeviceSheetTags.Battery).assertContentDescriptionContains("Battery, 48 percent")
        // The list stays shut until it is asked for.
        assertTrue(compose.onAllNodesWithTag(AudioDeviceSheetTags.Output).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag(AudioDeviceSheetTags.Chevron).assertContentDescriptionContains("Show audio devices").performClick()
        assertEquals(1, taps.toggled)
        compose.onNodeWithTag(AudioDeviceSheetTags.Done).assertContentDescriptionContains("close Bluetooth panel", substring = true).performClick()
        assertEquals(1, taps.done)
    }

    @Test
    fun deviceWithoutBatteryOrCodec_showsNeither() {
        show(snapshot(routes = listOf(speaker, buds), active = setOf(7), links = listOf(budsLink.copy(battery = null, codec = null))))

        compose.onNodeWithTag(AudioDeviceSheetTags.Head)
            .assertContentDescriptionContains("Bluetooth headphones, Storm Buds, connected, playing here")
        compose.onNodeWithTag(AudioDeviceSheetTags.Battery).assertDoesNotExist()
        compose.onNodeWithContentDescription("percent", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("codec", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun otherOutput_isListedAndMovesTheMusicWhenTapped() {
        val taps = show(playingOnBuds)

        // The one that is playing cannot be picked again; the other can.
        compose.onNodeWithContentDescription("Bluetooth headphones, Storm Buds, Connected · Playing here").performScrollTo().assertIsNotEnabled()
            .assertIsSelected()
        compose.onNodeWithContentDescription("Phone speaker, This phone, Available").performScrollTo().assertHasClickAction().performClick()
        assertEquals(1, taps.selected?.routeId)
    }

    @Test
    fun connectedButNotPlaying_isNotShownAsPlaying() {
        show(snapshot(routes = listOf(speaker, buds), active = setOf(1), links = listOf(budsLink)))

        compose.onNodeWithTag(AudioDeviceSheetTags.Head).assertContentDescriptionContains("Phone speaker, This phone, playing here")
        compose.onNodeWithContentDescription("Bluetooth headphones, Storm Buds, Connected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(AudioDeviceSheetTags.Battery).assertDoesNotExist()
    }

    @Test
    fun searching_showsDevicesAsTheyAreFound_andPairsTheOneTapped() {
        val taps = show(snapshot(), BluetoothSearchSnapshot(SearchPhase.Searching, listOf(partyBox, airBuds)))

        compose.onNodeWithContentDescription("OTHER DEVICES, searching", ignoreCase = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Bluetooth headphones, Air Buds, not paired").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Bluetooth speaker, Party Box, not paired").performScrollTo().performClick()
        assertEquals("AA:BB:CC:DD:EE:03", taps.paired?.address)
        compose.onNodeWithTag(AudioDeviceSheetTags.SearchAgain).assertDoesNotExist()
    }

    @Test
    fun searchingWithNothingYet_saysSearching() {
        show(snapshot(), BluetoothSearchSnapshot(SearchPhase.Searching, emptyList()))

        compose.onNodeWithTag(AudioDeviceSheetTags.Searching).performScrollTo().assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag(AudioDeviceSheetTags.Found).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun finishedWithNothing_saysSoAndOffersToSearchAgain() {
        val taps = show(snapshot(), BluetoothSearchSnapshot(SearchPhase.Finished, emptyList()))

        compose.onNodeWithTag(AudioDeviceSheetTags.NothingFound).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(AudioDeviceSheetTags.SearchAgain).performScrollTo().performClick()
        assertEquals(1, taps.searchAgain)
    }

    @Test
    fun pairing_showsOnThatDevice_andNoOtherCanBeStarted() {
        show(
            snapshot(),
            BluetoothSearchSnapshot(SearchPhase.Finished, listOf(partyBox.copy(bond = BondState.Bonding), airBuds)),
        )

        compose.onNodeWithContentDescription("Bluetooth speaker, Party Box, pairing").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Bluetooth headphones, Air Buds, not paired").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(AudioDeviceSheetTags.SearchAgain).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun pairedButNotConnected_isListedAsNotConnected() {
        val taps = show(snapshot(paired = listOf(pairedSpeaker)), BluetoothSearchSnapshot(SearchPhase.Finished, emptyList()))

        compose.onNodeWithContentDescription("Bluetooth speaker, Kitchen Speaker, not connected").performScrollTo().performClick()
        assertEquals("Kitchen Speaker", taps.connectPaired?.name)
    }

    @Test
    fun bluetoothOff_offersToTurnItOn() {
        val taps = show(snapshot(radio = BluetoothRadio.Off), availability = SearchAvailability.BluetoothOff)

        compose.onNodeWithTag(AudioDeviceSheetTags.BluetoothOff).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, taps.turnOn)
        compose.onNodeWithTag(AudioDeviceSheetTags.Searching).assertDoesNotExist()
    }

    @Test
    fun noBluetoothOnThePhone_hasNoListToOpen() {
        show(snapshot(radio = BluetoothRadio.Unsupported), availability = SearchAvailability.NoBluetooth)

        compose.onNodeWithTag(AudioDeviceSheetTags.Head).assertContentDescriptionContains("Phone speaker, This phone, playing here")
        compose.onNodeWithTag(AudioDeviceSheetTags.Chevron).assertDoesNotExist()
        compose.onNodeWithTag(AudioDeviceSheetTags.Permission).assertDoesNotExist()
    }

    @Test
    fun permissionMissing_asksForItAndStillShowsTheOutput() {
        val taps = show(
            snapshot(routes = listOf(speaker, buds), active = setOf(7), canRead = false),
            availability = SearchAvailability.PermissionRequired,
            permission = BluetoothPermission.Denied,
        )

        compose.onNodeWithTag(AudioDeviceSheetTags.Head).assertContentDescriptionContains("Bluetooth device, Storm Buds, connected, playing here")
        compose.onNodeWithText("Allow").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(AudioDeviceSheetTags.Permission).performClick()
        assertEquals(1, taps.allow)
    }

    @Test
    fun permissionDeniedForGood_pointsToSettings() {
        show(
            snapshot(canRead = false),
            availability = SearchAvailability.PermissionRequired,
            permission = BluetoothPermission.DeniedForGood,
        )

        compose.onNodeWithText("Settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Allow").assertDoesNotExist()
    }

    @Test
    fun beforeAndroid12_newDevicesArePairedInSettings() {
        val taps = show(snapshot(), availability = SearchAvailability.SettingsOnly)

        compose.onNodeWithTag(AudioDeviceSheetTags.Settings).performScrollTo().performClick()
        assertEquals(1, taps.settings)
        compose.onNodeWithTag(AudioDeviceSheetTags.Searching).assertDoesNotExist()
    }

    @Test
    fun connectingDevice_saysSoAndCannotBeTapped() {
        show(snapshot(links = listOf(budsLink.copy(state = LinkState.Connecting))))

        compose.onNodeWithContentDescription("Bluetooth headphones, Storm Buds, Connecting…").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        // The music has not moved: the phone is still what is playing.
        compose.onNodeWithTag(AudioDeviceSheetTags.Head).assertContentDescriptionContains("Phone speaker, This phone, playing here")
    }

    @Test
    fun failure_isShownInWords() {
        show(playingOnBuds, request = PanelRequest(error = OutputError.PairingFailed, errorDeviceName = "Party Box"))

        compose.onNodeWithText("Couldn't pair with Party Box.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun beforeAnythingIsKnown_theSheetCanStillBeClosed() {
        val taps = show(null)

        assertTrue(compose.onAllNodesWithTag(AudioDeviceSheetTags.Output).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag(AudioDeviceSheetTags.Chevron).assertDoesNotExist()
        compose.onNodeWithTag(AudioDeviceSheetTags.Done).performClick()
        assertEquals(1, taps.done)
    }
}
