package dev.otherworld.shoppinglist.data.tls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

/**
 * The security invariants of the TOFU layer: platform validation always runs first, only a
 * byte-identical user-accepted certificate may override a failure, everything else is
 * rejected and merely recorded for the UI prompt.
 */
class TofuTrustManagerTest {

    private val certA = parse(CERT_A_PEM)
    private val certB = parse(CERT_B_PEM)

    private class ThrowingDelegate : X509ExtendedTrustManager() {
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) =
            throw CertificateException("untrusted")
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
            throw CertificateException("untrusted")
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
            throw CertificateException("untrusted")
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            throw CertificateException("untrusted")
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
            throw CertificateException("untrusted")
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
            throw CertificateException("untrusted")
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private class PassingDelegate : X509ExtendedTrustManager() {
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {}
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {}
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /**
     * Pins keyed as the store keys them, `host|port` (or a legacy bare host), matched with the
     * store's own lookup so these tests exercise the real key logic.
     */
    private class FakeTrusted(private val pins: Map<String, String>) : TrustedCerts {
        var consulted = false
        override fun isTrustedForHost(host: String, port: Int, cert: X509Certificate): Boolean {
            consulted = true
            return CertPins.matches(pins, host, port, encode(cert))
        }
    }

    private fun trusted(vararg pins: Pair<String, X509Certificate>) =
        FakeTrusted(pins.associate { (key, cert) -> key to encode(cert) })

    /** An engine for a handshake with [host], which is how the trust manager learns the peer. */
    private fun engineFor(host: String, port: Int = 443): SSLEngine =
        SSLContext.getDefault().createSSLEngine(host, port)

    @Test
    fun `platform-valid chain passes without consulting the store`() {
        val trusted = trusted()
        val holder = UntrustedCertHolder()
        val tm = TofuTrustManager(PassingDelegate(), trusted, holder)
        tm.checkServerTrusted(arrayOf(certA), "RSA")
        assertFalse("store must not be consulted when platform validation passes", trusted.consulted)
        assertNull(holder.consume())
    }

    @Test
    fun `unknown cert is rejected and recorded for the prompt`() {
        val holder = UntrustedCertHolder()
        val tm = TofuTrustManager(ThrowingDelegate(), trusted(), holder)
        try {
            tm.checkServerTrusted(arrayOf(certA), "RSA")
            fail("expected CertificateException")
        } catch (expected: CertificateException) {
            // rejected — and the leaf is available for the UI prompt
        }
        val recorded = holder.consume()
        assertNotNull(recorded)
        assertEquals(certA, recorded!!.certificate)
        assertFalse(recorded.hostnameMismatch)
        assertNull("consume must clear the record", holder.consume())
    }

    @Test
    fun `explicitly accepted cert passes after platform rejection`() {
        val holder = UntrustedCertHolder()
        val tm = TofuTrustManager(ThrowingDelegate(), trusted("$HOST_A|443" to certA), holder)
        tm.checkServerTrusted(arrayOf(certA), "RSA", engineFor(HOST_A))
        assertNull("no prompt when the cert is already accepted", holder.consume())
    }

    @Test
    fun `a cert accepted for one host isn't trusted for another`() {
        val holder = UntrustedCertHolder()
        val tm = TofuTrustManager(ThrowingDelegate(), trusted("$HOST_A|443" to certA), holder)
        try {
            tm.checkServerTrusted(arrayOf(certA), "RSA", engineFor(HOST_B))
            fail("expected CertificateException: accepting A for its host must not trust it for another")
        } catch (expected: CertificateException) {
        }
        assertEquals(HOST_B, holder.consume()?.host)
    }

    @Test
    fun `with no host known, an accepted cert isn't trusted`() {
        val holder = UntrustedCertHolder()
        val tm = TofuTrustManager(ThrowingDelegate(), trusted(HOST_A to certA), holder)
        try {
            tm.checkServerTrusted(arrayOf(certA), "RSA")
            fail("expected CertificateException: an approval can't be matched without the host")
        } catch (expected: CertificateException) {
        }
        assertEquals(-1, holder.consume()?.port)
    }

    @Test
    fun `a different cert than the accepted one is still rejected`() {
        val holder = UntrustedCertHolder()
        val trusted = trusted("$HOST_A|443" to certB)
        val tm = TofuTrustManager(ThrowingDelegate(), trusted, holder)
        try {
            tm.checkServerTrusted(arrayOf(certA), "RSA", engineFor(HOST_A))
            fail("expected CertificateException — accepting B must not trust A")
        } catch (expected: CertificateException) {
        }
        assertTrue("the store must actually be asked", trusted.consulted)
        assertEquals(certA, holder.consume()?.certificate)
    }

    @Test
    fun `only the leaf is TOFU-matched, not intermediates`() {
        // Chain [A, B] with B accepted: the presented identity is A, so this must fail.
        val holder = UntrustedCertHolder()
        val trusted = trusted("$HOST_A|443" to certB)
        val tm = TofuTrustManager(ThrowingDelegate(), trusted, holder)
        try {
            tm.checkServerTrusted(arrayOf(certA, certB), "RSA", engineFor(HOST_A))
            fail("expected CertificateException — accepted intermediate must not trust the leaf")
        } catch (expected: CertificateException) {
        }
        assertTrue("the store must actually be asked", trusted.consulted)
        assertEquals(certA, holder.consume()?.certificate)
    }

    @Test
    fun `a cert approved for one port isn't trusted on another port of that host`() {
        val holder = UntrustedCertHolder()
        val tm = TofuTrustManager(ThrowingDelegate(), trusted("$HOST_A|8443" to certA), holder)
        tm.checkServerTrusted(arrayOf(certA), "RSA", engineFor(HOST_A, 8443))
        try {
            tm.checkServerTrusted(arrayOf(certA), "RSA", engineFor(HOST_A, 8444))
            fail("expected CertificateException: an approval for :8443 must not trust :8444")
        } catch (expected: CertificateException) {
        }
        val recorded = holder.consume()
        assertEquals(HOST_A, recorded?.host)
        assertEquals(8444, recorded?.port)
    }

    @Test
    fun `approving another cert for a second port keeps the first port's approval`() {
        val first = CertPins.put(emptyMap(), HOST_A, 8443, encode(certA))
        val both = CertPins.put(first, HOST_A, 8444, encode(certB))
        assertTrue(CertPins.matches(both, HOST_A, 8443, encode(certA)))
        assertTrue(CertPins.matches(both, HOST_A, 8444, encode(certB)))

        val holder = UntrustedCertHolder()
        val tm = TofuTrustManager(ThrowingDelegate(), FakeTrusted(both), holder)
        tm.checkServerTrusted(arrayOf(certA), "RSA", engineFor(HOST_A, 8443))
        tm.checkServerTrusted(arrayOf(certB), "RSA", engineFor(HOST_A, 8444))
        assertNull(holder.consume())
    }

    @Test
    fun `a legacy host-only approval is trusted on any port`() {
        val holder = UntrustedCertHolder()
        val tm = TofuTrustManager(ThrowingDelegate(), trusted(HOST_A to certA), holder)
        tm.checkServerTrusted(arrayOf(certA), "RSA", engineFor(HOST_A))
        tm.checkServerTrusted(arrayOf(certA), "RSA", engineFor(HOST_A, 8443))
        tm.checkServerTrusted(arrayOf(certA), "RSA", engineFor(HOST_A, 8444))
        assertNull(holder.consume())
    }

    @Test
    fun `certificates parse and differ`() {
        assertTrue(certA != certB)
        assertEquals("CN=test-a.example", certA.subjectX500Principal.name)
        assertEquals("CN=test-b.example", certB.subjectX500Principal.name)
    }

    private fun parse(pem: String): X509Certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(pem.byteInputStream()) as X509Certificate

    private companion object {
        fun encode(cert: X509Certificate): String = Base64.getEncoder().encodeToString(cert.encoded)

        const val HOST_A = "test-a.example"
        const val HOST_B = "test-b.example"

        // Throwaway self-signed test certs (openssl req -x509, EC P-256); no keys involved.
        val CERT_A_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIBhjCCAS2gAwIBAgIUGwpnnKj3eF4XLf/IcT2zJjn4HogwCgYIKoZIzj0EAwIw
            GTEXMBUGA1UEAwwOdGVzdC1hLmV4YW1wbGUwHhcNMjYwNzE5MTQ1MTQ5WhcNNDYw
            NzE0MTQ1MTQ5WjAZMRcwFQYDVQQDDA50ZXN0LWEuZXhhbXBsZTBZMBMGByqGSM49
            AgEGCCqGSM49AwEHA0IABL5fs9XxxxjZVecCRI/0/Xq6fl0d5rFhrxn1yQ9GTd45
            rCbEuipvSBPfnOf3KXwHnayh/39+Oubr9XJdnIlTvQWjUzBRMB0GA1UdDgQWBBRE
            Xgt/8itgfy3geA3X5ycNe5822zAfBgNVHSMEGDAWgBREXgt/8itgfy3geA3X5ycN
            e5822zAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0cAMEQCIAt0ubfVIOs7
            +EX+LRAAiktpwi3XdsAH0fpAI2wmI9RkAiATo/5rT3GNJY70D5SkZuzq/wMOZEA1
            IyYrdxvdnEF3iA==
            -----END CERTIFICATE-----
        """.trimIndent()

        val CERT_B_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIBiDCCAS2gAwIBAgIUJ8c+aQRD1Gwjd47Yl5g+9wiQLjIwCgYIKoZIzj0EAwIw
            GTEXMBUGA1UEAwwOdGVzdC1iLmV4YW1wbGUwHhcNMjYwNzE5MTQ1MjA2WhcNNDYw
            NzE0MTQ1MjA2WjAZMRcwFQYDVQQDDA50ZXN0LWIuZXhhbXBsZTBZMBMGByqGSM49
            AgEGCCqGSM49AwEHA0IABLXpy+uHYXDbXI9w6r+ff+HYAYuotC8tgr7DkIREpZ6d
            GLEtHa0ifHYu7RDC/oitNAPoJKHnOmOV/AGDsmS7pSWjUzBRMB0GA1UdDgQWBBRD
            IlqHwu4OaUIQUthV1Yj3lARKazAfBgNVHSMEGDAWgBRDIlqHwu4OaUIQUthV1Yj3
            lARKazAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0kAMEYCIQCUx7r7K6ya
            cKQchPTmGycZ4rm8Nkb0HT6BD2y6HTlCswIhALxZTVYssKpQeRYMslsmwSSxFP/F
            oUTGy8PsMyryVxUG
            -----END CERTIFICATE-----
        """.trimIndent()
    }
}
