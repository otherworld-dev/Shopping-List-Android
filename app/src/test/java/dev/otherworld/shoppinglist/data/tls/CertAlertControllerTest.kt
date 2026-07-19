package dev.otherworld.shoppinglist.data.tls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * The mid-session prompt behaviour: a failed handshake surfaces one prompt, dismissing it
 * suppresses re-prompting for that exact certificate (but not a different one), and approving
 * pins the certificate and clears the suppression.
 */
class CertAlertControllerTest {

    private val certA = parse(CERT_A_PEM)
    private val certB = parse(CERT_B_PEM)

    private class FakeApprover : CertApprover {
        val accepted = mutableListOf<Pair<String, X509Certificate>>()
        override fun accept(host: String, cert: X509Certificate) {
            accepted += host to cert
        }
    }

    private val holder = UntrustedCertHolder()
    private val approver = FakeApprover()
    private var currentHost: String? = "server.local"
    private val controller = CertAlertController(holder, approver, ActiveServerHost { currentHost })

    @Test
    fun `no recorded failure means no prompt`() {
        controller.onTlsFailure()
        assertNull(controller.alert.value)
    }

    @Test
    fun `a recorded failure surfaces a prompt for that host`() {
        holder.record("server.local", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        assertEquals("server.local", controller.alert.value?.host)
    }

    @Test
    fun `a record without a host does not prompt`() {
        holder.record(null, certA, hostnameMismatch = false)
        controller.onTlsFailure()
        assertNull(controller.alert.value)
    }

    @Test
    fun `while a prompt is showing a second failure is ignored`() {
        holder.record("server.local", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        val first = controller.alert.value
        holder.record("server.local", certB, hostnameMismatch = false)
        controller.onTlsFailure()
        assertSame("still the first prompt", first, controller.alert.value)
    }

    @Test
    fun `a record for a different host than the active server is ignored`() {
        holder.record("other.server", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        assertNull(controller.alert.value)
    }

    @Test
    fun `a failure after logout does not arm a prompt`() {
        currentHost = null // logged out
        holder.record("server.local", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        assertNull(controller.alert.value)
    }

    @Test
    fun `trust does not pin if the active server changed`() {
        holder.record("server.local", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        currentHost = "different.server" // user switched servers before tapping Trust
        controller.trust()
        assertTrue("must not pin against a different server", approver.accepted.isEmpty())
    }

    @Test
    fun `dismiss leaves a persistent banner that review re-opens`() {
        holder.record("server.local", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        controller.dismiss()
        assertNull(controller.alert.value)
        assertNotNull("banner remains after dismiss", controller.suppressed.value)

        controller.review()
        assertNotNull("review re-opens the dialog", controller.alert.value)
        assertNull(controller.suppressed.value)
    }

    @Test
    fun `a newly rotated cert supersedes the banner with a fresh prompt`() {
        holder.record("server.local", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        controller.dismiss()
        assertNotNull(controller.suppressed.value)

        holder.record("server.local", certB, hostnameMismatch = false)
        controller.onTlsFailure()
        assertNotNull(controller.alert.value)
        assertNull("banner cleared by the new prompt", controller.suppressed.value)
    }

    @Test
    fun `dismiss suppresses re-prompting for the same certificate`() {
        holder.record("server.local", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        controller.dismiss()
        assertNull(controller.alert.value)

        holder.record("server.local", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        assertNull("same cert must not nag again after dismissal", controller.alert.value)
    }

    @Test
    fun `a different certificate still prompts after a dismissal`() {
        holder.record("server.local", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        controller.dismiss()

        holder.record("server.local", certB, hostnameMismatch = false)
        controller.onTlsFailure()
        assertNotNull("a newly rotated cert must still prompt", controller.alert.value)
    }

    @Test
    fun `trust pins the certificate for its host and clears the prompt`() {
        holder.record("server.local", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        controller.trust()

        assertNull(controller.alert.value)
        assertEquals(listOf("server.local" to certA), approver.accepted)
    }

    @Test
    fun `trust clears the dismissal suppression`() {
        // Dismiss cert A, then (later) approve it: a subsequent failure with A must prompt again
        // only if it is not pinned — but since trust pins it, this asserts suppression is reset,
        // not that the pin is bypassed. Use a fresh failure after trust to prove the flag cleared.
        holder.record("server.local", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        controller.dismiss()
        // Approve a different cert to clear suppression without pinning A.
        holder.record("server.local", certB, hostnameMismatch = false)
        controller.onTlsFailure()
        controller.trust() // pins B, clears dismissedCert

        holder.record("server.local", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        assertNotNull("dismissal suppression must reset after an approval", controller.alert.value)
    }

    @Test
    fun `logout resets the dismissal suppression`() {
        holder.record("server.local", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        controller.dismiss()
        controller.onLoggedOut()

        holder.record("server.local", certA, hostnameMismatch = false)
        controller.onTlsFailure()
        assertTrue("after logout the same cert prompts again", controller.alert.value != null)
    }

    private fun parse(pem: String): X509Certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(pem.byteInputStream()) as X509Certificate

    private companion object {
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
