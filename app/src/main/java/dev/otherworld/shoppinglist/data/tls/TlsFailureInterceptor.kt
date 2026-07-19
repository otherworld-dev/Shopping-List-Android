package dev.otherworld.shoppinglist.data.tls

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/**
 * Reports certificate/hostname failures on authenticated calls to [CertAlertController] so the
 * app can prompt the user to re-approve a changed server certificate. This is the single choke
 * point for all authenticated traffic (sync, refresh, capabilities), so no view model needs to
 * classify TLS errors itself. Transient network errors pass through untouched.
 */
class TlsFailureInterceptor @Inject constructor(
    private val alerts: CertAlertController,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            return chain.proceed(chain.request())
        } catch (e: IOException) {
            if (isCertFailure(e)) alerts.onTlsFailure()
            throw e
        }
    }
}
