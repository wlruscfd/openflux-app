package org.openflux.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalizeSiteTest {
    @Test
    fun aUrlIsReducedToItsHost() {
        assertEquals("example.com", normalizeSite("https://www.Example.com/path?q=1"))
    }

    @Test
    fun wildcardsAndLeadingDotsMeanTheWholeTld() {
        assertEquals("ru", normalizeSite("*.ru"))
        assertEquals("ru", normalizeSite(".ru"))
    }

    @Test
    fun ipAddressesPassThrough() {
        assertEquals("10.0.0.1", normalizeSite("10.0.0.1"))
    }

    @Test
    fun aBareWordIsNotADomain() {
        assertNull(normalizeSite("localhost-only"))
        assertNull(normalizeSite(""))
        assertNull(normalizeSite("exa mple.com"))
    }
}
