package org.openflux.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTest {
    @Test
    fun everyTransportNameSurvivesTheRoundTrip() {
        for (transport in ManualTransport.entries) {
            val name = transportName(transport)
            val back = parseTransportName(name)
            if (transport == ManualTransport.YANDEX) assertEquals(ManualTransport.YANDEX, back) else assertEquals(transport, back)
        }
    }

    @Test
    fun anUnknownTransportNameFallsBackToYandex() {
        assertEquals(ManualTransport.YANDEX, parseTransportName("something-new"))
        assertEquals(ManualTransport.YANDEX, parseTransportName(""))
    }

    @Test
    fun aKeyProfileNeedsItsDocumentBeforeItCanConnect() {
        val blank = Profile(id = "1", name = "k", mode = ProfileMode.KEY, controlUrl = "https://x", keyToken = "t")
        assertFalse(blank.isReadyToConnect)
        assertTrue(blank.copy(docUrl = "https://doc").isReadyToConnect)
    }

    @Test
    fun aManualProfileIsAlwaysReadyExceptMultistreamWithTooFewDocuments() {
        val manual = Profile(id = "1", name = "m", mode = ProfileMode.MANUAL)
        assertTrue(manual.isReadyToConnect)
        val multi = manual.copy(manualTransport = ManualTransport.YANDEX_MULTISTREAM, docUrls = listOf("a", " "))
        assertFalse(multi.isReadyToConnect)
        assertTrue(multi.copy(docUrls = listOf("a", "b")).isReadyToConnect)
    }
}
