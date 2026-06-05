package com.jussicodes.easytimer.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.jussicodes.easytimer.MainActivity
import com.jussicodes.easytimer.model.TimerState

class QuickTimerTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val state = TimerForegroundService.timerState.value
        if (state is TimerState.Running) {
            val cancelIntent = Intent(this, TimerForegroundService::class.java)
                .apply { action = TimerForegroundService.ACTION_CANCEL }
            startService(cancelIntent)
        } else {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(intent)
        }
        refreshTile()
    }

    private fun refreshTile() {
        val state = TimerForegroundService.timerState.value
        qsTile?.let { tile ->
            if (state is TimerState.Running) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "关闭 ${state.appName}"
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "定时关闭"
            }
            tile.updateTile()
        }
    }

}
