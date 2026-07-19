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

    /** X509Certificate.equals compares DER encodings, so a Set gives byte-exact matching. */
    private class FakeTrusted(private val certs: Set<X509Certificate>) : TrustedCerts {
        var consulted = false
        override fun isTrusted(cert: X509Certificate): Boolean {
            consulted = true
            return cert in certs
        }
        override fun isTrustedForHost(host: String, cert: X509Certificate): Boolean =
            cert in certs
    }

    @Test
    fun `platform-valid chain passes without consulting the store`() {
        val trusted = FakeTrusted(emptySet())
        val holder = UntrustedCertHolder()
        val tm = TofuTrustManager(PassingDelegate(), trusted, holder)
        tm.checkServerTrusted(arrayOf(certA), "RSA")
        assertFalse("store must not be consulted when platform validation passes", trusted.consulted)
        assertNull(holder.consume())
    }

    @Test
    fun `unknown cert is rejected and recorded for the prompt`() {
        val holder = UntrustedCertHolder()
        val tm = TofuTrustManager(ThrowingDelegate(), FakeTrusted(emptySet()), holder)
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
        val tm = TofuTrustManager(ThrowingDelegate(), FakeTrusted(setOf(certA)), holder)
        tm.checkServerTrusted(arrayOf(certA), "RSA")
        assertNull("no prompt when the cert is already accepted", holder.consume())
    }

    @Test
    fun `a different cert than the accepted one is still rejected`() {
        val holder = UntrustedCertHolder()
        val tm = TofuTrustManager(ThrowingDelegate(), FakeTrusted(setOf(certB)), holder)
        try {
            tm.checkServerTrusted(arrayOf(certA), "RSA")
            fail("expected CertificateException — accepting B must not trust A")
        } catch (expected: CertificateException) {
        }
        assertEquals(certA, holder.consume()?.certificate)
    }

    @Test
    fun `only the leaf is TOFU-matched, not intermediates`() {
        // Chain [A, B] with B accepted: the presented identity is A, so this must fail.
        val holder = UntrustedCertHolder()
        val tm = TofuTrustManager(ThrowingDelegate(), FakeTrusted(setOf(certB)), holder)
        try {
            tm.checkServerTrusted(arrayOf(certA, certB), "RSA")
            fail("expected CertificateException — accepted intermediate must not trust the leaf")
        } catch (expected: CertificateException) {
        }
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
