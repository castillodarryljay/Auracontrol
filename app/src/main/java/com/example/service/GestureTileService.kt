package com.example.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class GestureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = GestureService.isServiceRunning
        val serviceIntent = Intent(this, GestureService::class.java)

        if (isRunning) {
            stopService(serviceIntent)
            updateTileState(false)
        } else {
            // Start service
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                updateTileState(true)
            } catch (e: Exception) {
                Log.e("GestureTileService", "Failed to start service from tile", e)
            }
        }
    }

    private fun updateTileState(explicitState: Boolean? = null) {
        val tile = qsTile ?: return
        val isRunning = explicitState ?: GestureService.isServiceRunning

        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Touchless OS"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isRunning) "Active" else "Inactive"
        }

        tile.updateTile()
    }
}
