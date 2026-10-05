package dev.otherworld.shoppinglist.ui.join

import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.data.guest.FakePublicApi
import dev.otherworld.shoppinglist.data.guest.InviteCodes
import dev.otherworld.shoppinglist.data.guest.PublicApi
import dev.otherworld.shoppinglist.data.guest.httpError
import dev.otherworld.shoppinglist.data.remote.OcsResponse
import dev.otherworld.shoppinglist.data.remote.dto.CapabilitiesBlock
import dev.otherworld.shoppinglist.data.remote.dto.CapabilitiesResponse
import dev.otherworld.shoppinglist.data.remote.dto.ShoppingListCaps
import dev.otherworld.shoppinglist.data.tls.CertApprover
import dev.otherworld.shoppinglist.data.tls.UntrustedCertHolder
import dev.otherworld.shoppinglist.domain.guest.ShareLink
import dev.otherworld.shoppinglist.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLException

@OptIn(ExperimentalCoroutinesApi::class)
class JoinEntryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val server = "https://cloud.example.com"
    private val fake = FakePublicApi().apply {
        capabilities = CapabilitiesResponse(CapabilitiesBlock(shoppingList = ShoppingListCaps(features = listOf("invite-codes"))))
        codeToken = "abc123"
    }
    private val holder = UntrustedCertHolder()
    private val approver = FakeApprover()

    /** Runs when the capabilities call starts, the way a failing handshake records its certificate. */
    private var beforeCapabilities: () -> Unit = {}
    private val api = object : PublicApi by fake {
        override suspend fun capabilities(url: String): OcsResponse<CapabilitiesResponse> {
            beforeCapabilities()
            return fake.capabilities(url)
        }
    }

    private fun viewModel() = JoinEntryViewModel(InviteCodes(api, json), approver, holder)

    private class FakeApprover : CertApprover {
        val accepted = mutableListOf<Triple<String, Int, X509Certificate>>()
        override fun accept(host: String, port: Int, cert: X509Certificate) {
            accepted += Triple(host, port, cert)
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    @Test
    fun `a link goes straight to Join without asking the server`() = test {
        val vm = viewModel()
        vm.submit("https://cloud.example.com/apps/shopping_list/s/tok", "")
        assertEquals(ShareLink(server, "tok"), vm.state.value.link)
        assertEquals(emptyList<String>(), fake.calls)
        vm.onLinkHandled()
        assertNull(vm.state.value.link)
    }

    @Test
    fun `input that can't work is refused without a request`() = test {
        val vm = viewModel()
        vm.submit("http://192.168.0.11:8080/K7QM-3XPD", "")
        assertEquals(UiText(R.string.join_entry_insecure), vm.state.value.error)
        vm.submit("hello there", "")
        assertEquals(UiText(R.string.join_entry_invalid), vm.state.value.error)
        vm.submit("K7QM-3XPD", "")
        assertEquals(UiText(R.string.join_entry_server_needed), vm.state.value.error)
        vm.submit("K7QM-3XPD", "ftp://cloud.example.com")
        assertEquals(UiText(R.string.join_entry_server_invalid), vm.state.value.error)
        assertEquals(emptyList<String>(), fake.calls)
    }

    @Test
    fun `a code is looked up and becomes its link`() = test {
        val vm = viewModel()
        vm.submit("cloud.example.com/K7QM-3XPD", "")
        advanceUntilIdle()
        assertEquals(ShareLink(server, "abc123"), vm.state.value.link)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `a second Continue during a lookup is ignored`() = test {
        val vm = viewModel()
        vm.submit("cloud.example.com/K7QM-3XPD", "")
        vm.submit("cloud.example.com/K7QM-3XPD", "")
        advanceUntilIdle()
        assertEquals(1, fake.calls.count { it.startsWith("capabilities") })
    }

    @Test
    fun `a failed lookup says why`() = test {
        val vm = viewModel()
        fake.failNext += "resolveCode" to httpError(404, """{"message":"Not found"}""")
        vm.submit("cloud.example.com/K7QM-3XPD", "")
        advanceUntilIdle()
        assertEquals(UiText(R.string.join_entry_code_not_found), vm.state.value.error)
        assertNull(vm.state.value.link)
    }

    // The holder is shared with background refreshes of other servers' lists.
    @Test
    fun `another server's rejected certificate isn't offered for this one`() = test {
        val vm = viewModel()
        beforeCapabilities = { holder.record("other.example.org", 443, cert(), hostnameMismatch = false) }
        fake.failNext += "capabilities" to SSLException("untrusted")
        vm.submit("cloud.example.com/K7QM-3XPD", "")
        advanceUntilIdle()
        assertNull(vm.state.value.pendingCert)
        assertEquals(UiText(R.string.error_offline), vm.state.value.error)
    }

    @Test
    fun `trusting this server's certificate tries the code again`() = test {
        val vm = viewModel()
        val cert = cert()
        beforeCapabilities = {
            beforeCapabilities = {}
            holder.record("cloud.example.com", 8443, cert, hostnameMismatch = false)
        }
        fake.failNext += "capabilities" to SSLException("untrusted")
        vm.submit("cloud.example.com/K7QM-3XPD", "")
        advanceUntilIdle()
        assertNotNull(vm.state.value.pendingCert)
        vm.trustPendingCert()
        advanceUntilIdle()
        assertEquals(listOf(Triple("cloud.example.com", 8443, cert)), approver.accepted)
        assertEquals(ShareLink(server, "abc123"), vm.state.value.link)
    }

    @Test
    fun `turning the certificate down says so`() = test {
        val vm = viewModel()
        beforeCapabilities = { holder.record("cloud.example.com", 443, cert(), hostnameMismatch = false) }
        fake.failNext += "capabilities" to SSLException("untrusted")
        vm.submit("cloud.example.com/K7QM-3XPD", "")
        advanceUntilIdle()
        vm.dismissPendingCert()
        assertNull(vm.state.value.pendingCert)
        assertEquals(UiText(R.string.login_error_tls), vm.state.value.error)
    }

    private fun cert(): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(CERT_PEM.byteInputStream()) as X509Certificate

    private companion object {
        val CERT_PEM = """
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
    }
}
