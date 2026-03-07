package com.example.touchlock

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.touchlock.TouchLockState
import com.example.touchlock.TouchLockService

class TouchLockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        // Toggle
        val locked = TouchLockState.isLocked(this)
        if (locked) {
            TouchLockService.stop(this)
        } else {
            TouchLockService.start(this)
        }
        // Update tile after click
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val locked = TouchLockState.isLocked(this)

        tile.state = if (locked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (locked) "Touch Lock: ON" else "Touch Lock: OFF"
        tile.updateTile()
    }
}