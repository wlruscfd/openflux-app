package org.openflux.app.vpn

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches a transport's own byte counters for a one-way tunnel: data keeps going out but nothing
 * ever comes back. An explicit HTTP probe would need to dodge this app's own split-tunnel
 * exclusion (it normally keeps its own traffic out of the VPN it builds), so this reads the
 * counters the Go layer already keeps for every real packet instead - no extra network path to
 * get wrong, and it reflects actual app traffic rather than a synthetic request.
 */
class TunnelHealthChecker(
    private val scope: CoroutineScope,
    private val channelReady: StateFlow<Boolean>,
    private val stats: StateFlow<TrafficStats>,
    private val onResult: (Boolean?) -> Unit,
    private val tag: String = "tunnel",
) {
    private var job: Job? = null

    fun start() {
        stop()
        job = scope.launch {
            val detector = OneWayDetector()
            detector.start(stats.value.bytesSent, stats.value.bytesReceived)

            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                val current = stats.value
                val change = detector.sample(channelReady.value, current.bytesSent, current.bytesReceived) ?: continue
                if (change.ok == false) {
                    Log.i("OpenFluxHealth", "$tag verdict=ONE_WAY totalSent=${current.bytesSent} totalRecv=${current.bytesReceived}")
                }
                onResult(change.ok)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 2_500L
    }
}
