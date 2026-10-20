package org.openflux.app

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.openflux.app.data.SettingsRepository
import org.openflux.app.data.ThemeDesign
import org.openflux.app.data.ThemeMode
import org.openflux.app.data.UiStyle
import org.openflux.app.ui.design.LocalUiStyle
import org.openflux.app.ui.OpenFluxNavHost
import org.openflux.app.ui.theme.OpenFluxTheme
import org.openflux.app.vpn.OpenFluxSocks5Service
import org.openflux.app.vpn.OpenFluxVpnService

class MainActivity : ComponentActivity() {

    private var pendingProfileId: String? = null

    // MainActivity is launchMode="singleTask", so onNewIntent handles the deep link, not a fresh onCreate.
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
            val settings = remember { SettingsRepository(applicationContext) }
            val design by settings.themeDesign.collectAsState(initial = ThemeDesign.DYNAMIC)
            val mode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val uiStyle by settings.uiStyle.collectAsState(initial = UiStyle.STANDARD)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (mode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CompositionLocalProvider(
                LocalOpenFluxApp provides application as OpenFluxApplication,
                LocalUiStyle provides uiStyle,
            ) {
                OpenFluxTheme(darkTheme = darkTheme, design = design) {
                    OpenFluxNavHost(
                        onConnectRequested = ::requestConnect,
                        onDisconnectRequested = ::disconnect,
                        onSocks5Requested = ::startSocks5Service,
                        onSocks5StopRequested = ::stopSocks5Service,
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

    // Only needed the first time: Android's VPN consent dialog can't be shown from a TileService.
    private fun handleTileConnectIntent(intent: Intent?) {
        if (intent?.action != ACTION_CONNECT_FROM_TILE) return
        intent.getStringExtra(OpenFluxVpnService.EXTRA_PROFILE_ID)?.let(::requestConnect)
    }

    private fun requestConnect(profileId: String) {
        stopSocks5Service()
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

    // No VpnService.prepare() dance needed: OpenFluxSocks5Service never touches VpnService.
    private fun startSocks5Service(profileId: String) {
        // Both modes are mutually exclusive in the Go layer, so switching has to tear the
        // other one down first - leaving it to a disabled button is what used to wedge the UI.
        disconnect()
        val intent = Intent(this, OpenFluxSocks5Service::class.java).apply {
            action = OpenFluxSocks5Service.ACTION_START
            putExtra(OpenFluxSocks5Service.EXTRA_PROFILE_ID, profileId)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopSocks5Service() {
        val intent = Intent(this, OpenFluxSocks5Service::class.java).apply {
            action = OpenFluxSocks5Service.ACTION_STOP
        }
        startService(intent)
    }

    companion object {
        /** See handleTileConnectIntent - OpenFluxTileService's own consent hand-off. */
        const val ACTION_CONNECT_FROM_TILE = "org.openflux.app.action.CONNECT_FROM_TILE"
    }
}
