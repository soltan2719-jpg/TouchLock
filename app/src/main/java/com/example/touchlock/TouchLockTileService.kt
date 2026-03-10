package com.example.touchlock

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class TouchTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (TouchLockService.isOverlayShowing || TouchLockService.isMonitoring || TouchLockService.isRunning) {
            val stopIntent = Intent(this, TouchLockService::class.java).apply {
                action = TouchLockService.ACTION_STOP_ALL
            }
            startTouchLockService(stopIntent)
        } else {
            val startIntent = Intent(this, TouchLockService::class.java).apply {
                action = TouchLockService.ACTION_START_LOCK_NOW
            }
            startTouchLockService(startIntent)
        }

        updateTile()
    }

    private fun updateTile() {
        qsTile?.let { tile ->
            tile.label = "TouchLock"
            tile.state = if (TouchLockService.isRunning) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            tile.updateTile()
        }
    }

    private fun startTouchLockService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }
}