package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.SlrNoteBuRoot
import com.example.ui.theme.SlrNoteBuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SlrNoteBuApp
        setContent {
            val vm: MainViewModel = viewModel(factory = MainViewModel.Factory(app.container))
            val uiState by vm.uiState.collectAsState()
            SlrNoteBuTheme(themeMode = uiState.themeMode) {
                SlrNoteBuRoot(viewModel = vm, codec = app.container.codec)
            }
        }
    }
}
