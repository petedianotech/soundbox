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

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    requestPlaybackAndStoragePermissions()

    setContent {
      MyApplicationTheme {
        SoundboxNavGraph()
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
    }

    try {
      val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
      ) { /* Optional handle feedback, main UI handles state checks */ }

      requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
    } catch (e: Exception) {
      // Safe fallback failsafe
    }
  }
}
