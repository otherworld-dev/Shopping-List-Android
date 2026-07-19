package dev.otherworld.shoppinglist.data.tls

import okhttp3.internal.tls.OkHostnameVerifier
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

/** Why a server certificate was rejected — drives which warning the approval prompt shows. */
enum class CertReason { UNTRUSTED, NAME_MISMATCH, UNTRUSTED_AND_MISMATCH }

/** Human-readable summary of a certificate the platform couldn't validate, for the prompt. */
data class CertInfo(
    val host: String,
    val subject: String,
    val issuer: String,
    val validity: String,
    val fingerprint: String,
    val reason: CertReason,
    val expired: Boolean,
    val selfSigned: Boolean,
)

/**
 * Builds the display summary for [cert] as presented by [host]. [mismatch] true means the
 * chain validated but the name didn't (recorded by the hostname verifier); otherwise the
 * chain itself wasn't validated, and we additionally check whether the certificate covers the
 * host so one that is both unvalidated and doesn't match the address is labelled as such.
 */
fun describeCert(host: String, cert: X509Certificate, mismatch: Boolean): CertInfo {
    val df = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
    return CertInfo(
        host = host,
        subject = friendlyName(cert.subjectX500Principal),
        issuer = friendlyName(cert.issuerX500Principal),
        validity = "${df.format(cert.notBefore)} – ${df.format(cert.notAfter)}",
        fingerprint = sha256Fingerprint(cert),
        reason = when {
            mismatch -> CertReason.NAME_MISMATCH
            runCatching { OkHostnameVerifier.verify(host, cert) }.getOrDefault(true) -> CertReason.UNTRUSTED
            else -> CertReason.UNTRUSTED_AND_MISMATCH
        },
        expired = cert.notAfter.before(java.util.Date()),
        selfSigned = cert.subjectX500Principal == cert.issuerX500Principal,
    )
}

/** True for a deterministic certificate/hostname failure (vs. a transient network error). */
fun isCertFailure(e: Throwable): Boolean {
    if (e is javax.net.ssl.SSLPeerUnverifiedException) return true
    var cause: Throwable? = e
    while (cause != null) {
        if (cause is CertificateException) return true
        cause = cause.cause
    }
    return false
}

private fun friendlyName(principal: javax.security.auth.x500.X500Principal): String {
    // RFC1779 quotes values containing commas as CN="Acme, Inc"; capture the quoted form first
    // so a comma in the CN doesn't truncate it.
    val dn = principal.getName(javax.security.auth.x500.X500Principal.RFC1779)
    val m = Regex("""CN=(?:"([^"]*)"|([^,]+))""").find(dn) ?: return dn
    return m.groupValues[1].ifEmpty { m.groupValues[2] }
}

/** Uppercase colon-separated SHA-256, grouped so it wraps on line breaks instead of mid-octet. */
private fun sha256Fingerprint(cert: X509Certificate): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        .joinToString(":") { "%02X".format(it) }
        .chunked(24).joinToString("\n") // 8 octets ("AA:") per line
