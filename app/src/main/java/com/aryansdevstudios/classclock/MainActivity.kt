package com.aryansdevstudios.classclock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // Modern way to handle the result from the settings screen.
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // This block is called when the user returns from the settings screen.
        if (Settings.canDrawOverlays(this)) {
            // Permission has been granted successfully.
            Toast.makeText(this, "Permission granted. Starting service...", Toast.LENGTH_SHORT).show()
            startOverlayService()
        } else {
            // Permission was not granted. Inform the user.
            Toast.makeText(this, "Overlay permission is required for the app to work.", Toast.LENGTH_LONG).show()
        }
        // Finish the activity in both cases after handling the result.
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No setContentView() is called, so the activity remains invisible (using the transparent theme).

        checkPermissionAndStartService()
    }

    private fun checkPermissionAndStartService() {
        if (Settings.canDrawOverlays(this)) {
            // Permission is already granted. Start the service and finish.
            startOverlayService()
            finish()
        } else {
            // Permission not granted. Launch the settings screen to request it.
            // The result will be handled by the overlayPermissionLauncher.
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun startOverlayService() {
        val serviceIntent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
