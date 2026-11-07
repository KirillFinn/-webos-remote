package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.TvPreferencesRepository
import com.example.model.RemoteMode
import com.example.model.TvDevice
import com.example.viewmodel.RemoteViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("WebOS Remote", appName)
    }

    @Test
    fun `test preferences repository device saving`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = TvPreferencesRepository(context)

        val device = TvDevice(ip = "192.168.1.100", name = "Living Room LG TV", clientKey = "test_key_123")
        repo.saveDevice(device)

        val saved = repo.getSavedDevices()
        assertTrue(saved.any { it.ip == "192.168.1.100" })
    }

    @Test
    fun `test viewModel state updates`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = RemoteViewModel(app)

        // Test mute state reported by the TV
        viewModel.onVolumeChanged(35, isMuted = true)
        assertTrue(viewModel.uiState.value.isMuted)

        // Test web url launch is recorded in recent URLs
        viewModel.openUrl("example.com")
        assertEquals("https://example.com", viewModel.uiState.value.recentUrls.first())

        // Test mode switch
        viewModel.setRemoteMode(RemoteMode.TOUCHPAD)
        assertEquals(RemoteMode.TOUCHPAD, viewModel.uiState.value.remoteMode)
    }
}
