package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.model.RemoteMode
import com.example.ui.components.TrackpadControl
import com.example.ui.screens.RemoteMainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.RemoteViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemoteScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `settings menu no longer offers the TV settings menu`() {
        val viewModel = RemoteViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { MyApplicationTheme { RemoteMainScreen(viewModel = viewModel) } }

        compose.onNodeWithTag("settings_btn").performClick()
        compose.onNodeWithText("TV connection").assertIsDisplayed()
        compose.onNodeWithText("Haptic feedback").assertIsDisplayed()
        compose.onNodeWithText("TV settings menu").assertDoesNotExist()
    }

    @Test
    fun `trackpad renders in touchpad mode`() {
        val viewModel = RemoteViewModel(ApplicationProvider.getApplicationContext<Application>())
        viewModel.setRemoteMode(RemoteMode.TOUCHPAD)
        compose.setContent { MyApplicationTheme { RemoteMainScreen(viewModel = viewModel) } }
        compose.onNodeWithTag("trackpad").assertIsDisplayed()
    }

    @Test
    fun `tapping the trackpad clicks`() {
        var clicks = 0
        compose.setContent {
            MyApplicationTheme {
                TrackpadControl(onPointerMove = { _, _ -> }, onPointerClick = { clicks++ }, onScroll = {})
            }
        }
        compose.onNodeWithTag("trackpad").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
