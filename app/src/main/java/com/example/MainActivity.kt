package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.navigation.SoundboxNavGraph
import com.example.ui.theme.MyApplicationTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.MusicViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val readStorageGranted = results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        val readAudioGranted = results[Manifest.permission.READ_MEDIA_AUDIO] == true
        if (readStorageGranted || readAudioGranted) {
            try {
                val vm = androidx.lifecycle.ViewModelProvider(this)[MusicViewModel::class.java]
                vm.scanStorage()
            } catch (e: Exception) {
                // Safe ignore
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()

        setContent {
            val viewModel: MusicViewModel = viewModel()
            val themeFlow by viewModel.settingsManager.themeFlow.collectAsState()
            
            MyApplicationTheme(themeConfig = themeFlow) {
                SoundboxNavGraph(viewModel)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        try {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } catch (e: Exception) {
            // Safe fallback failsafe
        }
    }
}
