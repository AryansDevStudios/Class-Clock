package com.aryansdevstudios.classclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Check if the received action is the boot completed event.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            // IMPORTANT: Only start the service if the required permission has already been granted.
            if (Settings.canDrawOverlays(context)) {
                val serviceIntent = Intent(context, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
