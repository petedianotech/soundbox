package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.SoundboxNavGraph
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {

    private lateinit var musicViewModel: MusicViewModel

    private val deletePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            musicViewModel.confirmPendingDeletion()
        } else {
            musicViewModel.cancelPendingDeletion()
        }
        musicViewModel.clearPendingDeleteSender()
    }

    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            musicViewModel.confirmPendingWrite()
        } else {
            musicViewModel.cancelPendingWrite()
        }
        musicViewModel.clearPendingWriteSender()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPlaybackAndStoragePermissions()

        setContent {
            val viewModel: MusicViewModel = viewModel()
            musicViewModel = viewModel

            val themeFlow by viewModel.settingsManager.themeFlow.collectAsState()
            val fontFlow by viewModel.settingsManager.fontFlow.collectAsState()
            val pendingDelete by viewModel.pendingDeleteSender.collectAsState()
            val pendingWrite by viewModel.pendingWriteSender.collectAsState()

            // Launch system delete consent UI (Android 10+ scoped storage)
            LaunchedEffect(pendingDelete) {
                val sender = pendingDelete ?: return@LaunchedEffect
                try {
                    deletePermissionLauncher.launch(IntentSenderRequest.Builder(sender).build())
                } catch (e: Exception) {
                    viewModel.cancelPendingDeletion()
                    viewModel.clearPendingDeleteSender()
                }
            }

            // Launch system write consent UI
            LaunchedEffect(pendingWrite) {
                val sender = pendingWrite ?: return@LaunchedEffect
                try {
                    writePermissionLauncher.launch(IntentSenderRequest.Builder(sender).build())
                } catch (e: Exception) {
                    viewModel.cancelPendingWrite()
                    viewModel.clearPendingWriteSender()
                }
            }

            MyApplicationTheme(themeConfig = themeFlow, fontConfig = fontFlow) {
                SoundboxNavGraph(viewModel)
            }
        }
    }

    private fun requestPlaybackAndStoragePermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        try {
            val requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                val readStorageGranted = results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
                val readAudioGranted = results[Manifest.permission.READ_MEDIA_AUDIO] == true
                if (readStorageGranted || readAudioGranted) {
                    val vm = androidx.lifecycle.ViewModelProvider(this@MainActivity)[MusicViewModel::class.java]
                    vm.scanStorage()
                }
            }

            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } catch (e: Exception) {
            // Safe fallback failsafe
        }
    }
}
