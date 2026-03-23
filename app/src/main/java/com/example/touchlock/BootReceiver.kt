package com.example.touchlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if the user had "Monitor Mode" active before the restart
            // We check your Prefs file (Assuming you save the state there)
            val isMonitorEnabled = Prefs.isMonitorEnabled(context)

            if (isMonitorEnabled) {
                val serviceIntent = Intent(context, TouchLockService::class.java).apply {
                    action = TouchLockService.ACTION_START_MONITOR
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
