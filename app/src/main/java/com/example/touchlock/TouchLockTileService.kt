package com.example.touchlock

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class TouchTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (TouchLockService.isRunning) {
            val stopIntent = Intent(this, TouchLockService::class.java).apply {
                action = TouchLockService.ACTION_STOP
            }
            startService(stopIntent)
        } else {
            val startIntent = Intent(this, TouchLockService::class.java).apply {
                action = TouchLockService.ACTION_START
            }
            startService(startIntent)
        }

        qsTile?.let { tile ->
            tile.state = if (TouchLockService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    private fun updateTile() {
        qsTile?.let { tile ->
            tile.label = "TouchLock"
            tile.state = if (TouchLockService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }
}