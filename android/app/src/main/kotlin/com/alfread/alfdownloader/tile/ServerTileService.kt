package com.alfread.alfdownloader.tile

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.alfread.alfdownloader.R
import com.alfread.alfdownloader.data.SettingsStore
import com.alfread.alfdownloader.network.Api
import com.alfread.alfdownloader.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Ubin Pengaturan Cepat: ketuk untuk menyalakan server ALF (lewat Termux) dari shade. */
class ServerTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val settings = SettingsStore(this)
        val prefs = settings.load()
        scope.launch {
            val online = runCatching { Api(prefs.serverUrl).health().ok }.getOrDefault(false)
            if (!online) {
                com.alfread.alfdownloader.termux.TermuxRunner(this@ServerTileService).startServer(prefs.serverDir)
                tile?.apply { state = Tile.STATE_UNAVAILABLE; label = "Menyalakan…"; updateTile() }
            } else if (OverlayController.canDrawOverlays(this@ServerTileService)) {
                if (OverlayController.isRunning) OverlayController.stop(this@ServerTileService) else OverlayController.start(this@ServerTileService)
                refresh()
            }
        }
    }

    private fun refresh() {
        val settings = SettingsStore(this)
        val prefs = settings.load()
        scope.launch {
            val online = runCatching { Api(prefs.serverUrl).health().ok }.getOrDefault(false)
            tile?.apply {
                state = if (online) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                label = if (online) "ALF online" else "ALF offline"
                icon = Icon.createWithResource(this@ServerTileService, R.drawable.ic_stat_alf)
                updateTile()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
