package dev.otherworld.shoppinglist.data.tls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Which client certificate goes to a server behind mutual TLS (#35): the one picked for its
 * host, on any port, and none otherwise; every request is recorded so a failed sign-in can
 * offer the picker.
 */
class ClientKeyManagerTest {

    private class FakeAliases : ClientCertAliases {
        val map = mutableMapOf<String, String>()
        override fun aliasFor(host: String) = map[host.lowercase()]
        override fun set(host: String, alias: String) { map[host.lowercase()] = alias }
    }

    private class FakeKeys(val present: Set<String>) : ClientKeySource {
        private val key: PrivateKey = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair().private
        override fun chain(alias: String): Array<X509Certificate>? = null
        override fun key(alias: String): PrivateKey? = key.takeIf { alias in present }
    }

    private val aliases = FakeAliases()
    private val requests = ClientCertRequests()

    private fun manager(present: Set<String> = setOf("mine")) = ClientKeyManager(aliases, FakeKeys(present), requests)

    @Test
    fun `no certificate picked for the host sends none and records the request`() {
        assertNull(manager().pick("cloud.example.com", 443))
        assertEquals(ClientCertRequests.Request("cloud.example.com", 443, null), requests.consume())
    }

    @Test
    fun `the picked certificate goes to its host on any port`() {
        aliases.set("Cloud.Example.com", "mine")
        assertEquals("mine", manager().pick("cloud.example.com", 8443))
        assertEquals(ClientCertRequests.Request("cloud.example.com", 8443, "mine"), requests.consume())
    }

    @Test
    fun `another host gets nothing`() {
        aliases.set("cloud.example.com", "mine")
        assertNull(manager().pick("other.example.com", 443))
    }

    @Test
    fun `a certificate removed from the phone counts as none`() {
        aliases.set("cloud.example.com", "gone")
        assertNull(manager(present = setOf("mine")).pick("cloud.example.com", 443))
        assertNull(requests.consume()!!.sent)
    }

    @Test
    fun `no host means no certificate and no request`() {
        assertNull(manager().pick(null, 443))
        assertNull(requests.consume())
    }
}
