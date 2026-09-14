package org.openflux.app.vpn

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

/**
 * The connect/disconnect tile in the system's Quick Settings panel (next to
 * Wi-Fi, Bluetooth, the flashlight, ...). Toggles the app's one active
 * profile - there's no per-tile profile picker, same as the main screen has
 * exactly one "Connect" button for whichever profile is currently active.
 *
 * Disconnecting never needs anything beyond this service -
 * OpenFluxVpnService.ACTION_DISCONNECT is a plain Intent, identical to what
 * the persistent notification's own action sends. Connecting needs a
 * profile to connect with and, only the very first time, the user's VPN
 * consent - Android will only show that consent dialog from an Activity,
 * never a TileService, so that one case hands off to MainActivity instead
 * of trying (and failing) to connect directly from here.
 */
class OpenFluxTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTile(OpenFluxVpnService.callback.status.value)
        // Keeps the tile in sync with a connect/disconnect triggered from
        // the app itself or the notification while the Quick Settings panel
        // happens to be open - not just the state at the moment it opened.
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
                // Nothing to connect to - open the app so the user can pick
                // a profile, same as tapping the empty-state button on Home.
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
