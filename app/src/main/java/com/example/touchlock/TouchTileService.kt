package com.example.touchlock

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class TouchTileService : TileService() {

    // Called when the tile is added or the system needs to update it
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    // Called when the user clicks the tile
    override fun onClick() {
        super.onClick()

        // Close the notification shade
        val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        @Suppress("DEPRECATION")
        sendBroadcast(closeIntent)

        // Start the lock service
        val intent = Intent(this, TouchLockService::class.java).apply {
            action = TouchLockService.ACTION_START_LOCK_NOW
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        // You can change the tile look based on whether the service is running
        tile.state = if (TouchLockService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}