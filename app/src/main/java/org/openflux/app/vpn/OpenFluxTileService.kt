package org.openflux.app.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openflux.app.MainActivity
import org.openflux.app.OpenFluxApplication
import org.openflux.app.R

// Connecting for the first time hands off to MainActivity, since Android only shows the VPN consent dialog from an Activity.
class OpenFluxTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTile(OpenFluxVpnService.callback.status.value)
        // Keeps the tile in sync with a connect/disconnect triggered elsewhere while the panel is open.
        listenJob = scope.launch {
            OpenFluxVpnService.callback.status.collect { updateTile(it) }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()

        val status = OpenFluxVpnService.callback.status.value
        if (status is TunnelStatus.Connected || status is TunnelStatus.Connecting) {
            startService(Intent(this, OpenFluxVpnService::class.java).setAction(OpenFluxVpnService.ACTION_DISCONNECT))
            return
        }

        scope.launch {
            val app = application as OpenFluxApplication
            val profileId = app.settingsRepository.activeProfileId.first()
            if (profileId == null) {
                // Nothing to connect to - open the app so the user can pick a profile.
                openApp(profileIdToConnect = null)
                return@launch
            }
            if (VpnService.prepare(this@OpenFluxTileService) == null) {
                val intent = Intent(this@OpenFluxTileService, OpenFluxVpnService::class.java).apply {
                    action = OpenFluxVpnService.ACTION_CONNECT
                    putExtra(OpenFluxVpnService.EXTRA_PROFILE_ID, profileId)
                }
                ContextCompat.startForegroundService(this@OpenFluxTileService, intent)
            } else {
                openApp(profileIdToConnect = profileId)
            }
        }
    }

    // @Suppress("DEPRECATION") on the call site silences the compiler warning but not this Lint check.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp(profileIdToConnect: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (profileIdToConnect != null) {
                action = MainActivity.ACTION_CONNECT_FROM_TILE
                putExtra(OpenFluxVpnService.EXTRA_PROFILE_ID, profileIdToConnect)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(status: TunnelStatus) {
        val tile = qsTile ?: return
        val active = status is TunnelStatus.Connected || status is TunnelStatus.Connecting
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                when (status) {
                    is TunnelStatus.Connected -> R.string.tile_subtitle_connected
                    is TunnelStatus.Connecting -> R.string.tile_subtitle_connecting
                    else -> R.string.tile_subtitle_disconnected
                },
            )
        }
        tile.updateTile()
    }
}
