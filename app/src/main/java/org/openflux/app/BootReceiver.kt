package org.openflux.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openflux.app.vpn.OpenFluxVpnService

// Only auto-connects if VPN permission was already granted; a broadcast receiver can't prompt for consent.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as OpenFluxApplication
                val startOnBoot = app.settingsRepository.startOnBoot.first()
                val activeProfileId = app.settingsRepository.activeProfileId.first()

                if (startOnBoot && activeProfileId != null && VpnService.prepare(context) == null) {
                    val serviceIntent = Intent(context, OpenFluxVpnService::class.java).apply {
                        action = OpenFluxVpnService.ACTION_CONNECT
                        putExtra(OpenFluxVpnService.EXTRA_PROFILE_ID, activeProfileId)
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
