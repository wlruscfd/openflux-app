package org.openflux.app.vpn

/**
 * Decides from the engine's byte counters whether a tunnel is one-way, and reports only when the
 * verdict changes: acting on every sample made one long outage count as hundreds of failures and
 * re-posted the alert every few seconds.
 */
class OneWayDetector(
    private val now: () -> Long = System::currentTimeMillis,
    private val confirmMs: Long = ONE_WAY_CONFIRM_MS,
    private val minSentBytes: Long = MIN_SENT_BYTES_TO_JUDGE,
) {
    private var baselineSent = 0L
    private var baselineReceived = 0L
    private var oneWaySince: Long? = null
    private var verdict: Boolean? = null

    class Change(val ok: Boolean?)

    fun start(sent: Long, received: Long) {
        baselineSent = sent
        baselineReceived = received
        oneWaySince = null
        verdict = null
    }

    fun sample(channelReady: Boolean, sent: Long, received: Long): Change? {
        if (!channelReady) {
            baselineSent = sent
            baselineReceived = received
            oneWaySince = null
            return change(null)
        }
        val receivedDelta = received - baselineReceived
        val sentDelta = sent - baselineSent
        return when {
            receivedDelta > 0 -> {
                baselineSent = sent
                baselineReceived = received
                oneWaySince = null
                change(true)
            }
            sentDelta >= minSentBytes -> {
                val since = oneWaySince ?: now().also { oneWaySince = it }
                if (now() - since >= confirmMs) change(false) else null
            }
            else -> null
        }
    }

    private fun change(next: Boolean?): Change? {
        if (verdict == next) return null
        verdict = next
        return Change(next)
    }

    companion object {
        const val MIN_SENT_BYTES_TO_JUDGE = 20_000L
        const val ONE_WAY_CONFIRM_MS = 7_000L
    }
}
