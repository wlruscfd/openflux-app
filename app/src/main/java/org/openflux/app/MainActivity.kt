package org.openflux.app

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.openflux.app.ui.OpenFluxNavHost
import org.openflux.app.ui.theme.OpenFluxTheme
import org.openflux.app.vpn.OpenFluxVpnService

class MainActivity : ComponentActivity() {

    private var pendingProfileId: String? = null

    // Backs the openflux://import deep link (see ProfileDeepLink). A plain
    // mutableStateOf is enough here: MainActivity is launchMode="singleTask"
    // (see the manifest), so onNewIntent - not a fresh onCreate - handles
    // the link while the app is already running.
    private var deepLinkUri by mutableStateOf<Uri?>(null)

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val profileId = pendingProfileId
        pendingProfileId = null
        if (result.resultCode == RESULT_OK && profileId != null) {
            startVpnService(profileId)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* connecting proceeds regardless; the foreground notification just won't show without it pre-33 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        deepLinkUri = intent?.data
        handleTileConnectIntent(intent)

        setContent {
            CompositionLocalProvider(LocalOpenFluxApp provides application as OpenFluxApplication) {
                OpenFluxTheme {
                    OpenFluxNavHost(
                        onConnectRequested = ::requestConnect,
                        onDisconnectRequested = ::disconnect,
                        deepLinkUri = deepLinkUri,
                        onDeepLinkHandled = { deepLinkUri = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUri = intent.data
        handleTileConnectIntent(intent)
    }

    // The Quick Settings tile (OpenFluxTileService) connects directly by
    // itself whenever VPN consent is already granted - this path only runs
    // the very first time, when Android's consent dialog can only be shown
    // from an Activity, never a TileService. requestConnect below already
    // knows how to ask for and wait on that consent, so this just forwards
    // into the exact same flow a manual tap on the home screen uses.
    private fun handleTileConnectIntent(intent: Intent?) {
        if (intent?.action != ACTION_CONNECT_FROM_TILE) return
        intent.getStringExtra(OpenFluxVpnService.EXTRA_PROFILE_ID)?.let(::requestConnect)
    }

    private fun requestConnect(profileId: String) {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            pendingProfileId = profileId
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            startVpnService(profileId)
        }
    }

    private fun startVpnService(profileId: String) {
        val intent = Intent(this, OpenFluxVpnService::class.java).apply {
            action = OpenFluxVpnService.ACTION_CONNECT
            putExtra(OpenFluxVpnService.EXTRA_PROFILE_ID, profileId)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun disconnect() {
        val intent = Intent(this, OpenFluxVpnService::class.java).apply {
            action = OpenFluxVpnService.ACTION_DISCONNECT
        }
        startService(intent)
    }

    companion object {
        /** See handleTileConnectIntent - OpenFluxTileService's own consent hand-off. */
        const val ACTION_CONNECT_FROM_TILE = "org.openflux.app.action.CONNECT_FROM_TILE"
    }
}
