package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.screens.RemoteMainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.RemoteViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light remote background -> dark status/navigation bar icons, even in system dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            MyApplicationTheme {
                RemoteMainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The socket usually dies while the phone sleeps; reconnect silently on return and keep
        // watching for the paired TV (e.g. it gets switched on) while the app is visible.
        viewModel.onAppForeground()
    }

    override fun onStop() {
        viewModel.onAppBackground()
        super.onStop()
    }
}
