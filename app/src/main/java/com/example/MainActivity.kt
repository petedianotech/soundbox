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
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
  private var musicViewModel: MusicViewModel? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      val viewModel: MusicViewModel = viewModel()
      musicViewModel = viewModel
      val themeFlow by viewModel.settingsManager.themeFlow.collectAsState()
      
      MyApplicationTheme(themeConfig = themeFlow) {
        SoundboxNavGraph(viewModel)
      }
    }

    requestPlaybackAndStoragePermissions()
  }

  private fun requestPlaybackAndStoragePermissions() {
    val permissionsToRequest = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
      permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
      permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      Manifest.permission.READ_MEDIA_AUDIO
    } else {
      Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = registerForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    ) {
      if (ContextCompat.checkSelfPermission(this, readPermission) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
      ) {
        musicViewModel?.scanStorage()
      }
    }

    if (ContextCompat.checkSelfPermission(this, readPermission) ==
      android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
      // A previously granted permission does not trigger a result callback.
      musicViewModel?.scanStorage()
    } else {
      permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }
  }
}
