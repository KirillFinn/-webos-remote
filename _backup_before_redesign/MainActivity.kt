package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                RemoteMainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The socket usually dies while the phone sleeps; reconnect silently on return.
        viewModel.reconnectIfNeeded()
    }
}
