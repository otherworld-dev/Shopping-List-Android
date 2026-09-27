package dev.otherworld.shoppinglist.data.tls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How [AcceptedCertStore] keys and looks up approvals: saved per host and port, so approving
 * one server never replaces another's approval, but trusted on any port of their host, since a
 * proxy hides the server's port from the TLS layer. Entries saved before ports were recorded
 * are keyed by the host alone.
 */
class CertPinsTest {

    private fun approve(pins: Map<String, String>, host: String, port: Int, cert: String) =
        pins + (CertPins.key(host, port) to cert)

    @Test
    fun `a new approval is keyed by lowercase host and port`() {
        assertEquals("server.local|8443", CertPins.key("Server.LOCAL", 8443))
    }

    @Test
    fun `with the port unknown the key is the host alone`() {
        assertEquals("server.local", CertPins.key("Server.local", -1))
    }

    @Test
    fun `a legacy host-only entry matches its host`() {
        val pins = mapOf("server.local" to CERT_1)
        assertTrue(CertPins.matches(pins, "server.local", CERT_1))
        assertTrue(CertPins.matches(pins, "SERVER.local", CERT_1))
        assertFalse(CertPins.matches(pins, "server.local", CERT_2))
        assertFalse(CertPins.matches(pins, "other.local", CERT_1))
    }

    @Test
    fun `a host and port entry matches its host whatever the port`() {
        val pins = approve(emptyMap(), "server.local", 8443, CERT_1)
        assertTrue(CertPins.matches(pins, "Server.Local", CERT_1))
        assertFalse(CertPins.matches(pins, "server.local", CERT_2))
        assertFalse(CertPins.matches(pins, "other.local", CERT_1))
    }

    @Test
    fun `an entry doesn't match a host its host merely starts or ends with`() {
        val pins = approve(mapOf("server.local" to CERT_2), "server.local", 8443, CERT_1)
        assertFalse(CertPins.matches(pins, "server.lo", CERT_1))
        assertFalse(CertPins.matches(pins, "server.local.evil", CERT_1))
        assertFalse(CertPins.matches(pins, "evil-server.local", CERT_1))
        assertFalse(CertPins.matches(pins, "server.lo", CERT_2))
        assertFalse(CertPins.matches(pins, "server.local.evil", CERT_2))
    }

    @Test
    fun `rotation replaces only its own port's entry`() {
        val legacy = mapOf("server.local" to CERT_1)
        val two = approve(approve(legacy, "server.local", 8443, CERT_1), "server.local", 8444, CERT_2)
        val rotated = approve(two, "server.local", 8443, CERT_3)

        assertEquals(CERT_3, rotated["server.local|8443"])
        assertEquals("the other port's approval survives", CERT_2, rotated["server.local|8444"])
        assertEquals("the legacy entry is left as it was", CERT_1, rotated["server.local"])
        assertEquals(3, rotated.size)
        assertTrue(CertPins.matches(rotated, "server.local", CERT_3))
        assertTrue(CertPins.matches(rotated, "server.local", CERT_2))
    }

    @Test
    fun `approving another port adds rather than replaces, and both are trusted`() {
        val first = approve(emptyMap(), "server.local", 8443, CERT_1)
        val both = approve(first, "server.local", 8444, CERT_2)
        assertEquals(setOf("server.local|8443", "server.local|8444"), both.keys)
        assertTrue(CertPins.matches(both, "server.local", CERT_1))
        assertTrue(CertPins.matches(both, "server.local", CERT_2))
    }

    @Test
    fun `IPv6 hosts keep their colons apart from the port`() {
        val pins = approve(emptyMap(), "2001:DB8::1", 8443, CERT_1)
        assertEquals(setOf("2001:db8::1|8443"), pins.keys)
        assertTrue(CertPins.matches(pins, "2001:db8::1", CERT_1))
        // Addresses that share a prefix, or read like the address with a port glued on, differ.
        assertFalse(CertPins.matches(pins, "2001:db8::", CERT_1))
        assertFalse(CertPins.matches(pins, "2001:db8::1:8443", CERT_1))

        val legacy = mapOf("2001:db8::1" to CERT_2)
        assertTrue(CertPins.matches(legacy, "2001:db8::1", CERT_2))
        assertFalse(CertPins.matches(legacy, "2001:db8::", CERT_2))
    }

    private companion object {
        const val CERT_1 = "MIIB-one"
        const val CERT_2 = "MIIB-two"
        const val CERT_3 = "MIIB-three"
    }
}
