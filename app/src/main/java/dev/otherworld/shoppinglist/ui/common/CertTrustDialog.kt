package dev.otherworld.shoppinglist.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.data.tls.CertInfo
import dev.otherworld.shoppinglist.data.tls.CertReason

/**
 * Prompt asking the user to approve a server certificate the platform couldn't validate.
 * Shared by the login screen (first connection) and the app-level prompt (a certificate that
 * changed mid-session). [showBrowserNote] is for the login path only, where a browser sign-in
 * follows; mid-session there's no browser step, so it's hidden there.
 */
@Composable
fun CertTrustDialog(
    info: CertInfo,
    onTrust: () -> Unit,
    onDismiss: () -> Unit,
    showBrowserNote: Boolean = true,
) {
    val introRes = when (info.reason) {
        CertReason.NAME_MISMATCH -> R.string.cert_dialog_mismatch_intro
        CertReason.UNTRUSTED_AND_MISMATCH -> R.string.cert_dialog_both_intro
        CertReason.UNTRUSTED -> R.string.cert_dialog_intro
    }
    val issuer = if (info.selfSigned) stringResource(R.string.cert_issuer_self_signed) else info.issuer
    val validity = if (info.expired) {
        stringResource(R.string.cert_validity_expired, info.validity)
    } else {
        info.validity
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cert_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(introRes))
                CertField(R.string.cert_field_server, info.host)
                CertField(R.string.cert_field_subject, info.subject)
                CertField(R.string.cert_field_issuer, issuer)
                CertField(R.string.cert_field_validity, validity)
                CertField(R.string.cert_field_fingerprint, info.fingerprint, monospace = true)
                if (showBrowserNote) {
                    // The sign-in page opens in the browser, which doesn't share this approval,
                    // so forewarn about its separate certificate warning.
                    Text(
                        stringResource(R.string.cert_dialog_browser_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onTrust) { Text(stringResource(R.string.cert_action_trust)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CertField(@StringRes labelRes: Int, value: String, monospace: Boolean = false) {
    // Merge label + value into one node so TalkBack reads them together.
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else null,
        )
    }
}
