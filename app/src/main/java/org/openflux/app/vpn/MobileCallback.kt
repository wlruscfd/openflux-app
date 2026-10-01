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

// Holds structured data, not pre-formatted text: [MobileCallback] has no Context to build a localized string with.
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

    /** Yandex served a CAPTCHA instead of the doc - detail carries the doc_url to solve it at. */
    CAPTCHA_REQUIRED,
}

data class TunnelLogEntry(
    // For LazyColumn/remember keys: timestampMillis alone can collide within the same millisecond.
    val id: Long,
    val timestampMillis: Long,
    val kind: TunnelLogKind,
    val attempt: Int = 0,
    val delaySeconds: Int = 0,
    val reasonCode: String = "",
    val detail: String = "",
)

/** One line of the engine's internal debug log - only sent when verbose logging is on. */
data class RawLogEntry(val id: Long, val timestampMillis: Long, val line: String)

private const val MAX_LOG_ENTRIES = 500
private const val MAX_RAW_LOG_ENTRIES = 2000
private val nextLogEntryId = AtomicLong(0)

// Republishes the gomobile-bound `mobile.Callback` interface as Kotlin StateFlows, safe to update from any thread.
class MobileCallback : Callback {
    private val _status = MutableStateFlow<TunnelStatus>(TunnelStatus.Stopped)
    val status: StateFlow<TunnelStatus> = _status

    // OnStatus("connected") only means the VPN interface is up, not that the covert channel has finished its handshake.
    private val _channelReady = MutableStateFlow(false)
    val channelReady: StateFlow<Boolean> = _channelReady

    // null = not checked yet (or no longer known good, e.g. after a reconnect). true/false come
    // from TunnelHealthChecker actually round-tripping a request once channelReady is true - the
    // channel handshake succeeding only means our side thinks it's connected, not that packets
    // survive the trip and back.
    private val _connectivityOk = MutableStateFlow<Boolean?>(null)
    val connectivityOk: StateFlow<Boolean?> = _connectivityOk

    fun setConnectivityOk(ok: Boolean?) {
        _connectivityOk.value = ok
    }

    private val _stats = MutableStateFlow(TrafficStats())
    val stats: StateFlow<TrafficStats> = _stats

    // Kept through every retry so the Home screen can surface a specific, actionable failure - see statusLabel.
    private val _lastRetryDetail = MutableStateFlow<String?>(null)
    val lastRetryDetail: StateFlow<String?> = _lastRetryDetail

    // Non-null while a CAPTCHA is blocking the connection - the doc_url to load in a WebView.
    private val _captchaDocUrl = MutableStateFlow<String?>(null)
    val captchaDocUrl: StateFlow<String?> = _captchaDocUrl

    private var pendingCaptchaUrl: String? = null
    private var suppressedCaptchaUrl: String? = null

    // Stays true while a check is unresolved, including after the dialog was dismissed, so the
    // UI can offer to reopen it instead of only auto-showing it the first time.
    private val _captchaPending = MutableStateFlow(false)
    val captchaPending: StateFlow<Boolean> = _captchaPending

    fun dismissCaptchaPrompt() {
        suppressedCaptchaUrl = _captchaDocUrl.value
        _captchaDocUrl.value = null
    }

    fun clearCaptchaPrompt() {
        _captchaDocUrl.value = null
    }

    fun captchaSolved() {
        suppressedCaptchaUrl = _captchaDocUrl.value
        _captchaDocUrl.value = null
    }

    fun reopenCaptchaPrompt() {
        val url = pendingCaptchaUrl ?: return
        suppressedCaptchaUrl = null
        _captchaDocUrl.value = url
    }

    private val _log = MutableStateFlow<List<TunnelLogEntry>>(emptyList())
    val log: StateFlow<List<TunnelLogEntry>> = _log

    private val _rawLog = MutableStateFlow<List<RawLogEntry>>(emptyList())
    val rawLog: StateFlow<List<RawLogEntry>> = _rawLog

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
            _connectivityOk.value = null
            _captchaDocUrl.value = null
            pendingCaptchaUrl = null
            _captchaPending.value = false
        }
        appendLifecycleLog(status)
    }

    override fun onStats(bytesSent: Long, bytesReceived: Long) {
        _stats.value = TrafficStats(bytesSent, bytesReceived)
    }

    // Unlike onStatus, these keep arriving for every silent background reconnect, not just once.
    override fun onLogEvent(code: String, detail: String) {
        when (code) {
            "connecting", "retrying" -> {
                _channelReady.value = false
                _connectivityOk.value = null
            }
            "connected" -> {
                _channelReady.value = true
                _connectivityOk.value = null
                _lastRetryDetail.value = null
                suppressedCaptchaUrl = null
                pendingCaptchaUrl = null
                _captchaPending.value = false
                _captchaDocUrl.value = null
            }
            "captcha_required" -> {
                pendingCaptchaUrl = detail
                _captchaPending.value = true
                if (detail != suppressedCaptchaUrl) _captchaDocUrl.value = detail
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
            "captcha_required" -> TunnelLogEntry(
                id = nextLogEntryId.getAndIncrement(),
                timestampMillis = System.currentTimeMillis(),
                kind = TunnelLogKind.CAPTCHA_REQUIRED,
                detail = detail,
            )
            "retrying" -> {
                // limit=4: the 4th part is the raw error text and may itself contain "|".
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

    override fun onRawLog(line: String) {
        val entry = RawLogEntry(nextLogEntryId.getAndIncrement(), System.currentTimeMillis(), line)
        _rawLog.update { (it + entry).takeLast(MAX_RAW_LOG_ENTRIES) }
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
        _rawLog.value = emptyList()
    }

    fun reset() {
        _status.value = TunnelStatus.Connecting
        _stats.value = TrafficStats()
        _channelReady.value = false
        _connectivityOk.value = null
        _lastRetryDetail.value = null
        suppressedCaptchaUrl = null
        pendingCaptchaUrl = null
        _captchaPending.value = false
        _captchaDocUrl.value = null
        // The log is intentionally not cleared here.
    }
}
