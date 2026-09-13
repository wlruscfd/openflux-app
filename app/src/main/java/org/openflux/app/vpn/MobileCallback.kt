package org.openflux.app.vpn

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import mobile.Callback

sealed interface TunnelStatus {
    data object Stopped : TunnelStatus
    data object Connecting : TunnelStatus
    data object Connected : TunnelStatus
    data class Error(val message: String) : TunnelStatus
}

data class TrafficStats(val bytesSent: Long = 0, val bytesReceived: Long = 0)

/**
 * One line of the Logs tab. Deliberately holds structured data rather than
 * pre-formatted text - [MobileCallback] has no Context to build a localized
 * string with, and formatting belongs in the Composable that renders it
 * anyway (see ui/logs/TunnelLogsScreen.kt).
 */
enum class TunnelLogKind {
    /** Overall tunnel setup starting (mirrors TunnelStatus.Connecting). */
    STARTING,

    /** Overall tunnel is up (mirrors TunnelStatus.Connected). */
    STARTED,

    /** Tunnel stopped, by the user or a fatal setup error. */
    STOPPED,

    /** A fatal error - detail carries the raw message. */
    ERROR,

    /** One connection attempt beginning. attempt is 1-based. */
    ATTEMPT_CONNECTING,

    /** One connection attempt succeeded. attempt is 1-based. */
    ATTEMPT_CONNECTED,

    /** An attempt failed and another is queued after delaySeconds. */
    ATTEMPT_RETRY,
}

data class TunnelLogEntry(
    // Identifies this entry for LazyColumn/remember keys - timestampMillis
    // itself can collide, since a background reconnect and the lifecycle
    // callback it triggers can land in the same millisecond.
    val id: Long,
    val timestampMillis: Long,
    val kind: TunnelLogKind,
    val attempt: Int = 0,
    val delaySeconds: Int = 0,
    val reasonCode: String = "",
    val detail: String = "",
)

private const val MAX_LOG_ENTRIES = 500
private val nextLogEntryId = AtomicLong(0)

/**
 * Implements the gomobile-bound `mobile.Callback` interface (see
 * mobile/mobile.go) and republishes it as Kotlin StateFlows the UI can
 * collect. Go calls these methods from its own goroutines, so all flows
 * are safe to update from any thread.
 */
class MobileCallback : Callback {
    private val _status = MutableStateFlow<TunnelStatus>(TunnelStatus.Stopped)
    val status: StateFlow<TunnelStatus> = _status

    // OnStatus("connected") only means the local VPN interface came up and
    // the transport was told to start - it fires immediately, well before
    // the covert channel (Yandex Docs, ...) has actually finished its own
    // handshake. Without this, the UI had no way to tell "connected" (VPN
    // up) apart from "actually relaying traffic yet" - looking done the
    // moment the tunnel started, even though it could still be several
    // retries away from working. True once transport.EventConnected fires
    // (see onLogEvent's "connected" case) and false again on any new
    // attempt or retry, so a mid-session drop shows as reconnecting too.
    private val _channelReady = MutableStateFlow(false)
    val channelReady: StateFlow<Boolean> = _channelReady

    private val _stats = MutableStateFlow(TrafficStats())
    val stats: StateFlow<TrafficStats> = _stats

    // The raw detail text of the most recent retry, so the Home screen can
    // recognize specific, actionable failures (e.g. a doc_url that needs
    // Volga instead of Yandex Docs - see HomeScreen.statusLabel) instead of
    // just showing a generic "connecting" spinner through every retry, no
    // matter how many times the exact same non-recoverable error repeats.
    // Cleared once the channel actually connects or the tunnel stops, kept
    // through every retry in between (that's the whole point).
    private val _lastRetryDetail = MutableStateFlow<String?>(null)
    val lastRetryDetail: StateFlow<String?> = _lastRetryDetail

    private val _log = MutableStateFlow<List<TunnelLogEntry>>(emptyList())
    val log: StateFlow<List<TunnelLogEntry>> = _log

    override fun onStatus(status: String) {
        _status.value = when {
            status == "connecting" -> TunnelStatus.Connecting
            status == "connected" -> TunnelStatus.Connected
            status == "stopped" -> TunnelStatus.Stopped
            status.startsWith("error:") -> TunnelStatus.Error(status.removePrefix("error:"))
            else -> TunnelStatus.Error(status)
        }
        if (status == "stopped" || status.startsWith("error:")) {
            _channelReady.value = false
        }
        appendLifecycleLog(status)
    }

    override fun onStats(bytesSent: Long, bytesReceived: Long) {
        _stats.value = TrafficStats(bytesSent, bytesReceived)
    }

    /**
     * Fine-grained connection events from the transport (see
     * transport.Event* in transport/transport.go) - unlike onStatus, these
     * keep arriving for every silent background reconnect, not just once.
     */
    override fun onLogEvent(code: String, detail: String) {
        when (code) {
            "connecting", "retrying" -> _channelReady.value = false
            "connected" -> {
                _channelReady.value = true
                _lastRetryDetail.value = null
            }
        }
        val entry = when (code) {
            "connecting" -> TunnelLogEntry(
                id = nextLogEntryId.getAndIncrement(),
                timestampMillis = System.currentTimeMillis(),
                kind = TunnelLogKind.ATTEMPT_CONNECTING,
                attempt = detail.toIntOrNull() ?: 0,
            )
            "connected" -> TunnelLogEntry(
                id = nextLogEntryId.getAndIncrement(),
                timestampMillis = System.currentTimeMillis(),
                kind = TunnelLogKind.ATTEMPT_CONNECTED,
                attempt = detail.toIntOrNull() ?: 0,
            )
            "retrying" -> {
                // limit=4: the 4th part is the raw underlying error text and
                // may itself contain "|" - only the first three separators
                // are ours to split on (see transport.EventRetrying's doc).
                val parts = detail.split("|", limit = 4)
                _lastRetryDetail.value = parts.getOrNull(3).orEmpty()
                TunnelLogEntry(
                    id = nextLogEntryId.getAndIncrement(),
                    timestampMillis = System.currentTimeMillis(),
                    kind = TunnelLogKind.ATTEMPT_RETRY,
                    attempt = parts.getOrNull(0)?.toIntOrNull() ?: 0,
                    delaySeconds = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                    reasonCode = parts.getOrNull(2).orEmpty(),
                    detail = parts.getOrNull(3).orEmpty(),
                )
            }
            else -> return
        }
        appendLog(entry)
    }

    private fun appendLifecycleLog(status: String) {
        val kind = when {
            status == "connecting" -> TunnelLogKind.STARTING
            status == "connected" -> TunnelLogKind.STARTED
            status == "stopped" -> TunnelLogKind.STOPPED
            status.startsWith("error:") -> TunnelLogKind.ERROR
            else -> TunnelLogKind.ERROR
        }
        val detail = if (kind == TunnelLogKind.ERROR) status.removePrefix("error:") else ""
        appendLog(
            TunnelLogEntry(
                id = nextLogEntryId.getAndIncrement(),
                timestampMillis = System.currentTimeMillis(),
                kind = kind,
                detail = detail,
            ),
        )
    }

    private fun appendLog(entry: TunnelLogEntry) {
        _log.update { (it + entry).takeLast(MAX_LOG_ENTRIES) }
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    fun reset() {
        _status.value = TunnelStatus.Stopped
        _stats.value = TrafficStats()
        _channelReady.value = false
        _lastRetryDetail.value = null
        // The log is intentionally not cleared here - right after a failed
        // attempt is exactly when seeing what just happened matters most.
    }
}
