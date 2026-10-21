package org.openflux.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OneWayDetectorTest {
    private var clock = 0L
    private val detector = OneWayDetector(now = { clock }, confirmMs = 7_000, minSentBytes = 20_000)

    @Test
    fun nothingIsReportedUntilTheChannelIsReady() {
        assertNull(detector.sample(false, 0, 0))
        assertNull(detector.sample(false, 50_000, 0))
    }

    @Test
    fun trafficComingBackMeansOkAndIsReportedOnce() {
        assertNull(detector.sample(true, 1_000, 0))
        assertEquals(true, detector.sample(true, 2_000, 500)?.ok)
        assertNull(detector.sample(true, 3_000, 900))
    }

    @Test
    fun aOneWayTunnelIsReportedOnceAfterTheConfirmWindow() {
        assertNull(detector.sample(true, 30_000, 0))
        clock = 6_000
        assertNull(detector.sample(true, 40_000, 0))
        clock = 7_500
        assertEquals(false, detector.sample(true, 50_000, 0)?.ok)
        clock = 10_000
        assertNull(detector.sample(true, 60_000, 0))
        clock = 20_000
        assertNull(detector.sample(true, 90_000, 0))
    }

    @Test
    fun recoveryAfterAOneWayVerdictIsReported() {
        detector.sample(true, 30_000, 0)
        clock = 8_000
        assertEquals(false, detector.sample(true, 60_000, 0)?.ok)
        clock = 9_000
        assertEquals(true, detector.sample(true, 61_000, 400)?.ok)
    }

    @Test
    fun aSmallAmountSentWithoutAnAnswerIsNotJudged() {
        clock = 1_000
        assertNull(detector.sample(true, 5_000, 0))
        clock = 60_000
        assertNull(detector.sample(true, 6_000, 0))
    }

    @Test
    fun losingTheChannelClearsTheVerdictOnce() {
        detector.sample(true, 1_000, 10)
        assertEquals(null, detector.sample(false, 1_000, 10)?.ok)
        assertNull(detector.sample(false, 1_000, 10))
    }
}
