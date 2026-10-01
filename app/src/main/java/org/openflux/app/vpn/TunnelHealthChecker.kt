package org.openflux.app.vpn

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
) {
    private var job: Job? = null

    fun start() {
        stop()
        job = scope.launch {
            var baselineSent = stats.value.bytesSent
            var baselineReceived = stats.value.bytesReceived
            var oneWaySince: Long? = null

            while (isActive) {
                delay(CHECK_INTERVAL_MS)

                if (!channelReady.value) {
                    baselineSent = stats.value.bytesSent
                    baselineReceived = stats.value.bytesReceived
                    oneWaySince = null
                    onResult(null)
                    continue
                }

                val current = stats.value
                val sentDelta = current.bytesSent - baselineSent
                val receivedDelta = current.bytesReceived - baselineReceived

                when {
                    receivedDelta > 0 -> {
                        baselineSent = current.bytesSent
                        baselineReceived = current.bytesReceived
                        oneWaySince = null
                        onResult(true)
                    }
                    sentDelta >= MIN_SENT_BYTES_TO_JUDGE -> {
                        val since = oneWaySince ?: System.currentTimeMillis().also { oneWaySince = it }
                        if (System.currentTimeMillis() - since >= ONE_WAY_CONFIRM_MS) {
                            onResult(false)
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 2_500L
        private const val MIN_SENT_BYTES_TO_JUDGE = 20_000L
        private const val ONE_WAY_CONFIRM_MS = 7_000L
    }
}
