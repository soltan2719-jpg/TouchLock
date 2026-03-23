package com.example.touchlock

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat

class TouchTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    @RequiresPermission("android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()

        // 1. Permission Check
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(intent)
            } else {
                startActivity(intent)
            }
            return
        }

        // 2. Logic: Toggle the lock
        val intent = Intent(this, TouchLockService::class.java)
        if (TouchLockService.isOverlayShowing) {
            intent.action = TouchLockService.ACTION_STOP_ALL
        } else {
            intent.action = TouchLockService.ACTION_START_LOCK_NOW
        }

        // Use ContextCompat to start the service in background
        ContextCompat.startForegroundService(this, intent)

        // 3. CLOSE THE MENU WITHOUT OPENING THE APP
        val collapseIntent = Intent(this, CollapseActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ method
            startActivityAndCollapse(collapseIntent)
        } else {
            // Android 12 & 13 method
            startActivity(collapseIntent)
            
            // REMOVED: ACTION_CLOSE_SYSTEM_DIALOGS broadcast is restricted on Android 12+
            // and requires BROADCAST_CLOSE_SYSTEM_DIALOGS permission which regular apps can't get.
            // Using CollapseActivity is the workaround.
        }

        // Update tile UI after short delay to let service state update
        handler.postDelayed({ updateTileState() }, 500)
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun updateTileState() {
        val tile = qsTile ?: return

        // Use isOverlayShowing to determine the "Active" blue color
        if (TouchLockService.isOverlayShowing) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Locked"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "TouchLock"
        }

        tile.updateTile()
    }
}