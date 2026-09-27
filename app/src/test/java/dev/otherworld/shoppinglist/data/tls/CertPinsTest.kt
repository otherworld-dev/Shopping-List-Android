package dev.otherworld.shoppinglist.data.tls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How [AcceptedCertStore] keys and looks up approvals: per host and port, with the entries
 * saved before ports were recorded still matching any port on their host.
 */
class CertPinsTest {

    @Test
    fun `a new approval is keyed by lowercase host and port`() {
        assertEquals("server.local|8443", CertPins.key("Server.LOCAL", 8443))
    }

    @Test
    fun `with the port unknown the key is the host alone`() {
        assertEquals("server.local", CertPins.key("Server.local", -1))
    }

    @Test
    fun `a legacy host-only entry matches on any port`() {
        val pins = mapOf("server.local" to CERT_1)
        assertTrue(CertPins.matches(pins, "server.local", 443, CERT_1))
        assertTrue(CertPins.matches(pins, "server.local", 8443, CERT_1))
        assertTrue(CertPins.matches(pins, "SERVER.local", -1, CERT_1))
        assertFalse(CertPins.matches(pins, "server.local", 8443, CERT_2))
        assertFalse(CertPins.matches(pins, "other.local", 8443, CERT_1))
    }

    @Test
    fun `a host and port entry matches only that port`() {
        val pins = CertPins.put(emptyMap(), "server.local", 8443, CERT_1)
        assertTrue(CertPins.matches(pins, "Server.Local", 8443, CERT_1))
        assertFalse(CertPins.matches(pins, "server.local", 8444, CERT_1))
        assertFalse(CertPins.matches(pins, "server.local", 443, CERT_1))
        assertFalse(CertPins.matches(pins, "server.local", -1, CERT_1))
        assertFalse(CertPins.matches(pins, "server.local", 8443, CERT_2))
    }

    @Test
    fun `rotation replaces only its own port's entry`() {
        val legacy = mapOf("server.local" to CERT_1)
        val two = CertPins.put(CertPins.put(legacy, "server.local", 8443, CERT_1), "server.local", 8444, CERT_2)
        val rotated = CertPins.put(two, "server.local", 8443, CERT_3)

        assertTrue(CertPins.matches(rotated, "server.local", 8443, CERT_3))
        assertTrue("the other port's approval survives", CertPins.matches(rotated, "server.local", 8444, CERT_2))
        assertEquals("the legacy entry is left as it was", CERT_1, rotated["server.local"])
        assertEquals(3, rotated.size)
    }

    @Test
    fun `approving another port adds rather than replaces`() {
        val first = CertPins.put(emptyMap(), "server.local", 8443, CERT_1)
        val both = CertPins.put(first, "server.local", 8444, CERT_2)
        assertTrue(CertPins.matches(both, "server.local", 8443, CERT_1))
        assertTrue(CertPins.matches(both, "server.local", 8444, CERT_2))
    }

    @Test
    fun `IPv6 hosts keep their colons apart from the port`() {
        val pins = CertPins.put(emptyMap(), "2001:DB8::1", 8443, CERT_1)
        assertEquals(setOf("2001:db8::1|8443"), pins.keys)
        assertTrue(CertPins.matches(pins, "2001:db8::1", 8443, CERT_1))
        assertFalse(CertPins.matches(pins, "2001:db8::1", 8444, CERT_1))
        // A host that reads like the address with the port glued on is a different host.
        assertFalse(CertPins.matches(pins, "2001:db8::1:8443", -1, CERT_1))
        assertFalse(CertPins.matches(pins, "2001:db8::1:8443", 443, CERT_1))

        val legacy = mapOf("2001:db8::1" to CERT_2)
        assertTrue(CertPins.matches(legacy, "2001:db8::1", 8443, CERT_2))
    }

    private companion object {
        const val CERT_1 = "MIIB-one"
        const val CERT_2 = "MIIB-two"
        const val CERT_3 = "MIIB-three"
    }
}
