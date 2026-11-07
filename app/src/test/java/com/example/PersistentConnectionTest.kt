package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.TvPreferencesRepository
import com.example.model.ConnectionStatus
import com.example.model.TvDevice
import com.example.network.SsdpDiscovery
import com.example.viewmodel.RemoteViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistentConnectionTest {

    // TEST-NET-1 address: never reachable, so no real TV is ever contacted.
    private val tvIp = "192.0.2.10"
    private val paired = TvDevice(ip = tvIp, name = "Living Room TV", clientKey = "key-123", uuid = "abc-uuid")

    private lateinit var app: Application
    private lateinit var repo: TvPreferencesRepository
    private lateinit var viewModel: RemoteViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        repo = TvPreferencesRepository(app)
        // Create the ViewModel before anything is saved so its init doesn't start connecting.
        viewModel = RemoteViewModel(app)
        repo.saveDevice(paired)
        repo.setLastConnectedIp(tvIp)
    }

    @After
    fun tearDown() {
        viewModel.onAppBackground()
    }

    @Test
    fun `repository persists the TV uuid and client key`() {
        val saved = TvPreferencesRepository(app).getSavedDevices().single { it.ip == tvIp }
        assertEquals("abc-uuid", saved.uuid)
        assertEquals("key-123", saved.clientKey)
    }

    @Test
    fun `ssdp USN header is parsed into a uuid`() {
        val ssdp = SsdpDiscovery(ApplicationProvider.getApplicationContext<Context>())
        val response = "HTTP/1.1 200 OK\r\n" +
            "SERVER: WebOS/4.1.0 UPnP/1.0\r\n" +
            "USN: uuid:4A3B2C1D-0000-1111-2222-333344445555::urn:lge-com:service:webos-second-screen:1\r\n\r\n"
        assertEquals("4a3b2c1d-0000-1111-2222-333344445555", ssdp.parseUuid(response))
        assertNull(ssdp.parseUuid("HTTP/1.1 200 OK\r\nSERVER: x\r\n\r\n"))
    }

    @Test
    fun `picking a scanned TV keeps its saved pairing key`() {
        // Scan results carry no client key; previously this wiped the key and forced re-pairing.
        viewModel.connectToTv(TvDevice(ip = tvIp, name = "LG webOS TV ($tvIp)"))
        val saved = repo.getSavedDevices().single { it.ip == tvIp }
        assertEquals("key-123", saved.clientKey)
        assertEquals("abc-uuid", saved.uuid)
        viewModel.disconnect()
    }

    @Test
    fun `pair fresh still clears the key`() {
        viewModel.clearKeysAndPairFresh(tvIp)
        assertTrue(repo.getSavedDevices().single { it.ip == tvIp }.clientKey.isNullOrBlank())
        viewModel.disconnect()
    }

    @Test
    fun `TV switching off leaves the app waiting for it instead of disconnected`() {
        viewModel.onConnected(paired, "key-123")
        assertTrue(viewModel.uiState.value.connectionStatus is ConnectionStatus.Connected)

        viewModel.onDisconnected()
        val status = viewModel.uiState.value.connectionStatus
        assertTrue("was $status", status is ConnectionStatus.WaitingForTv)
        assertEquals("Living Room TV", (status as ConnectionStatus.WaitingForTv).deviceName)
    }

    @Test
    fun `auto reconnect runs only in the foreground for a paired TV`() {
        viewModel.onDisconnected()
        assertFalse(viewModel.shouldAutoReconnect())

        viewModel.onAppForeground()
        // The first tick may already be probing; force a waiting state for a stable check.
        viewModel.onDisconnected()
        assertTrue(viewModel.shouldAutoReconnect())

        viewModel.onAppBackground()
        assertFalse(viewModel.shouldAutoReconnect())
    }

    @Test
    fun `user disconnect stops auto reconnect until a TV is picked again`() {
        viewModel.onAppForeground()
        viewModel.disconnect()
        assertEquals(ConnectionStatus.Disconnected, viewModel.uiState.value.connectionStatus)
        viewModel.onDisconnected()
        assertEquals(ConnectionStatus.Disconnected, viewModel.uiState.value.connectionStatus)
        assertFalse(viewModel.shouldAutoReconnect())
    }

    @Test
    fun `no auto reconnect while the connection dialog is open`() {
        viewModel.onAppForeground()
        viewModel.onDisconnected()
        viewModel.setShowConnectionDialog(true)
        assertFalse(viewModel.shouldAutoReconnect())
        viewModel.setShowConnectionDialog(false)
        assertTrue(viewModel.shouldAutoReconnect())
    }

    @Test
    fun `unpaired TV is never auto connected`() {
        repo.saveDevice(paired.copy(clientKey = null))
        viewModel.onAppForeground()
        viewModel.onDisconnected()
        assertFalse(viewModel.shouldAutoReconnect())
    }

    @Test
    fun `background failure stays quiet but a user attempt shows the error`() {
        // Background (supervisor) attempt: network failure -> keep waiting.
        viewModel.reconnectIfNeeded()
        viewModel.onConnectionFailed("Could not connect to TV at $tvIp.")
        assertTrue(viewModel.uiState.value.connectionStatus is ConnectionStatus.WaitingForTv)

        // User-initiated attempt: the troubleshooting error is shown.
        viewModel.connectToTv(paired)
        viewModel.onConnectionFailed("Could not connect to TV at $tvIp.")
        assertTrue(viewModel.uiState.value.connectionStatus is ConnectionStatus.Error)
        viewModel.disconnect()
    }

    @Test
    fun `repeated registration failures stop the automatic attempts`() {
        viewModel.onAppForeground()
        repeat(2) {
            viewModel.reconnectIfNeeded()
            viewModel.onConnectionFailed("Pairing timed out.")
        }
        viewModel.onDisconnected()
        assertFalse(viewModel.shouldAutoReconnect())

        // Picking the TV by hand resets the back-off.
        viewModel.connectToTv(paired)
        viewModel.onConnectionFailed("Could not connect to TV at $tvIp.")
        assertTrue(viewModel.shouldAutoReconnect())
        viewModel.disconnect()
    }
}
